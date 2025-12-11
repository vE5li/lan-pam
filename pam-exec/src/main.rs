use std::net::UdpSocket;

use base64::{engine::general_purpose::STANDARD, Engine};
use openssl::{
    encrypt::Encrypter,
    pkey::PKey,
    rand::rand_bytes,
    rsa::{Padding, Rsa},
    symm::{decrypt, encrypt, Cipher},
};
use serde::{Deserialize, Serialize};

const SECRET_SIZE: usize = 128;
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
    // TODO: Maybe this should not be optional.
    user: Option<String>,
    /// PAM service.
    // TODO: Maybe this should not be optional.
    service: Option<String>,
    /// PAM type.
    // TODO: Maybe this should not be optional.
    r#type: Option<String>,
    /// Baes64 encoded secret.
    secret: String,
}

#[derive(Deserialize)]
struct LanPamResponse {
    /// Display name of the device that responded.
    device: String,
    /// Baes64 encoded secret.
    secret: String,
    /// Weather or not the request was accepted or denied.
    accepted: bool,
}

#[derive(Deserialize)]
struct Device {
    public_key: String,
}

#[derive(Deserialize)]
struct Configuration {
    source_name: String,
    devices: Vec<Device>,
}

fn main() {
    let pam_user = std::env::var("PAM_USER").ok();
    let pam_service = std::env::var("PAM_SERVICE").ok();
    let pam_type = std::env::var("PAM_TYPE").ok();

    let args: Vec<String> = std::env::args().collect();
    let configuration_path = &args[1];

    let configuration = match std::fs::read_to_string(configuration_path) {
        Ok(configuration) => configuration,
        Err(error) => {
            panic!("failed to read configuration file: {error}");
        }
    };

    let configuration: Configuration = match serde_json::from_str(&configuration) {
        Ok(configuration) => configuration,
        Err(error) => {
            panic!("failed to parse configuration file: {error}");
        }
    };

    let cipher = Cipher::aes_256_ecb();
    // Max secret length for RSA 2048 with PKCS1 padding is 214.
    // Generate cryptographically secure key.
    let mut cipher_key = [0u8; 32];
    rand_bytes(&mut cipher_key).expect("failed to generate secret");

    // Generate cryptographically secure random bytes.
    let mut secret = [0u8; SECRET_SIZE];
    rand_bytes(&mut secret).expect("failed to generate secret");

    let request_body = LanPamRequestBody {
        source: configuration.source_name.clone(),
        user: pam_user.clone(),
        service: pam_service.clone(),
        r#type: pam_type.clone(),
        secret: STANDARD.encode(&secret),
    };
    let encoded_body = serde_json::to_string(&request_body).unwrap();
    let encrypted_body = encrypt(cipher, &cipher_key, None, encoded_body.as_bytes())
        .expect("failed to encypt request body");

    let socket: UdpSocket = UdpSocket::bind("0.0.0.0:8065").unwrap();
    socket
        .set_read_timeout(Some(std::time::Duration::new(30, 0)))
        .unwrap();
    socket.set_broadcast(true).unwrap();

    const BROADCAST_PORT: u16 = 4200;

    // broadcast port must be equal
    // broadcaster use "255.255.25
    // 5.255" address
    // let broadcast_addr = ("255.255.255.255", BROADCAST_PORT);
    let broadcast_addr = ("192.168.188.255", BROADCAST_PORT);
    // let broadcast_addr = ("192.168.188.12", BROADCAST_PORT);
    // let broadcast_addr = ("127.0.0.1", BROADCAST_PORT);

    for device in configuration.devices {
        // Decode base64 public key
        let public_key_der = STANDARD
            .decode(&device.public_key)
            .expect("failed to decode base64 public key");

        let device_rsa =
            Rsa::public_key_from_der(&public_key_der).expect("failed to parse device public key");
        let device_pkey = PKey::from_rsa(device_rsa).unwrap();

        let mut encrypter = Encrypter::new(&device_pkey).expect("failed to create encrypter");
        encrypter.set_rsa_padding(Padding::PKCS1).unwrap();

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

        match socket.send_to(&encoded_request.as_bytes(), broadcast_addr) {
            Ok(send_count) => {
                if send_count != encoded_request.as_bytes().len() {
                    // FIX: Don't panic
                    panic!("failed to send all bytes");
                } else {
                    // Do nothing because we sent the number of bytes we expected to send
                }
            }
            // FIX: Don't panic
            Err(error) => panic!("failed to send broadcast message: {error}"),
        }
    }

    println!("Awaiting responses...");

    let mut receive_buffer = [0u8; 512];
    while let Ok((response_length, address)) = socket.recv_from(&mut receive_buffer) {
        println!("{} bytes response from {:?}", response_length, address);

        // FIX: Don't panic, just continue;
        let decrypted_response = decrypt(
            cipher,
            &cipher_key,
            None,
            &receive_buffer[..response_length],
        )
        .expect("failed to decrypt response");

        // FIX: Don't panic, just continue;
        let response = std::str::from_utf8(&decrypted_response).expect("Not valid UTF8");
        let response: LanPamResponse = serde_json::from_str(response).expect("Not valid JSON");

        let response_secret = STANDARD
            .decode(&response.secret)
            .expect("Invalid base64 secret");

        if response_secret == secret {
            if response.accepted {
                println!("User logged in via {}", response.device);
                // Login success.
                std::process::exit(0);
            } else {
                println!("Login rejected via {}", response.device);
                std::process::exit(1);
            }
        } else {
            println!("Invalid response");
        }
    }

    std::process::exit(1);
}
