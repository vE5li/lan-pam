use std::time::Duration;

use base64::{engine::general_purpose::STANDARD, Engine};
use openssl::{
    encrypt::Encrypter,
    pkey::PKey,
    rand::rand_bytes,
    rsa::{Padding, Rsa},
    symm::{decrypt, encrypt, Cipher},
};
use serde::{Deserialize, Serialize};
use tokio::{io::AsyncReadExt, task::JoinSet};
use tokio::{io::AsyncWriteExt, net::TcpStream};

/// Android MTU is ~1500. We want to make sure we are well below that so we don't fragment our
/// request.
const MTU: usize = 1200;

#[derive(Serialize)]
struct LanPamRequest {
    encrypted_key: String,
    encrypted_body: String,
}

#[derive(Serialize)]
struct LanPamRequestBody {
    /// Dispaly name of the request source.
    source: String,
    /// PAM user.
    user: String,
    /// PAM service.
    service: String,
    /// PAM type.
    r#type: String,
}

#[derive(Deserialize)]
struct LanPamResponse {
    /// Weather or not the request was accepted or denied.
    accepted: bool,
}

#[derive(Deserialize)]
struct Device {
    /// Display name of the device that responded.
    name: String,
    /// IP address of the device.
    ip_address: String,
    /// Public key
    public_key: String,
}

#[derive(Deserialize)]
struct Configuration {
    source_name: String,
    devices: Vec<Device>,
}

async fn handle_device(device: Device, encoded_body: String) {
    let Ok(mut tcp_stream) = TcpStream::connect(&device.ip_address).await else {
        println!(
            "failed to connect to device {} on ip {}",
            device.name, device.ip_address
        );
        return;
    };

    let Ok(public_key_der) = STANDARD.decode(&device.public_key) else {
        println!("failed to decode base64 public key of {}", device.name);
        return;
    };

    let Ok(device_rsa) = Rsa::public_key_from_der(&public_key_der) else {
        println!("failed to create public key of {}", device.name);
        return;
    };

    let Ok(device_pkey) = PKey::from_rsa(device_rsa) else {
        println!("failed to create pkey from rsa key of {}", device.name);
        return;
    };

    let mut encrypter = Encrypter::new(&device_pkey).unwrap();
    encrypter.set_rsa_padding(Padding::PKCS1).unwrap();

    let cipher_key = {
        let mut cipher_key = [0u8; 32];
        rand_bytes(&mut cipher_key).unwrap();
        cipher_key
    };

    let encrypted_body = encrypt(
        Cipher::aes_256_ecb(),
        &cipher_key,
        None,
        encoded_body.as_bytes(),
    )
    .unwrap();

    // Create an output buffer
    let buffer_len = encrypter.encrypt_len(&cipher_key).unwrap();
    let mut encrypted_key = vec![0; buffer_len];

    // Encrypt and truncate the buffer
    let encrypted_len = encrypter.encrypt(&cipher_key, &mut encrypted_key).unwrap();
    encrypted_key.truncate(encrypted_len);

    let request = LanPamRequest {
        encrypted_key: STANDARD.encode(&encrypted_key),
        encrypted_body: STANDARD.encode(&encrypted_body),
    };
    let encoded_request = serde_json::to_string(&request).unwrap();

    assert!(
        encoded_request.as_bytes().len() < MTU,
        "request exceeded the MTU"
    );

    println!("Sending {} bytes", encoded_request.as_bytes().len());

    if tcp_stream
        .write_all(encoded_request.as_bytes())
        .await
        .is_err()
    {
        println!("failed to send request to {}", device.name);
        return;
    };

    let mut receive_buffer = [0u8; 512];

    let Ok(response_length) = tcp_stream.read(&mut receive_buffer).await else {
        println!("failed to get response from {}", device.name);
        return;
    };

    println!("{} bytes response from {}", response_length, device.name);

    let Ok(decrypted_response) = decrypt(
        Cipher::aes_256_ecb(),
        &cipher_key,
        None,
        &receive_buffer[..response_length],
    ) else {
        // Delay the end of the program to prevent brute forcing.
        tokio::time::sleep(Duration::from_secs(3)).await;

        println!("failed to decrypt response from {}", device.name);
        return;
    };

    let Ok(response) = std::str::from_utf8(&decrypted_response) else {
        // Delay the end of the program to prevent brute forcing.
        tokio::time::sleep(Duration::from_secs(3)).await;

        println!("invalid string from {}", device.name);
        return;
    };

    let Ok(response) = serde_json::from_str::<'_, LanPamResponse>(response) else {
        // Delay the end of the program to prevent brute forcing.
        tokio::time::sleep(Duration::from_secs(3)).await;

        println!("invalid response format from {}", device.name);
        return;
    };

    if response.accepted {
        println!("request accepted by {}", device.name);

        // Request accepted.
        std::process::exit(0);
    } else {
        println!("request rejected by {}", device.name);

        // Request rejected.
        std::process::exit(1);
    }
}

#[tokio::main(flavor = "current_thread")]
async fn main() {
    // I'm assuming that these are always set. Sadly I couldn't find any documentation that makes
    // this assumption explicit.
    let pam_user = std::env::var("PAM_USER").expect("PAM_USER not set");
    let pam_service = std::env::var("PAM_SERVICE").expect("PAM_SERVICE not set");
    let pam_type = std::env::var("PAM_TYPE").expect("PAM_TYPE not set");

    // Load the configuration file.
    let configuration: Configuration = {
        let configuration_path: String = std::env::args()
            .nth(1)
            .expect("no configuration file specified");

        let file_contents =
            std::fs::read_to_string(configuration_path).expect("failed to read configuration file");

        serde_json::from_str(&file_contents).expect("failed to parse configuration file")
    };

    // Create and encode the requset body using the cipher and key.
    let encoded_body = {
        let body = LanPamRequestBody {
            source: configuration.source_name.clone(),
            user: pam_user.clone(),
            service: pam_service.clone(),
            r#type: pam_type.clone(),
        };
        serde_json::to_string(&body).unwrap()
    };

    // Tasks for each device to handle the networking + encryption.
    {
        let mut join_set = JoinSet::new();

        configuration.devices.into_iter().for_each(|device| {
            join_set.spawn(handle_device(device, encoded_body.clone()));
        });

        let _ = tokio::time::timeout(Duration::from_secs(30), join_set.join_all()).await;
    }

    // This can mean that:
    // - no device was reachable
    // - no device was corectly configured
    // - no device was used to accept the request before the timeout
    std::process::exit(1);
}
