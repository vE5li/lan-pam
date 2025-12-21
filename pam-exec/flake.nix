{
  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    rust-overlay.url = "github:oxalica/rust-overlay";
  };

  outputs = {
    self,
    flake-utils,
    rust-overlay,
    nixpkgs,
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      overlays = [(import rust-overlay)];
      pkgs = (import nixpkgs) {inherit system overlays;};

      # Test devices
      emulator = {
        name = "Emulator";
        ip_address = "localhost:4200";
        public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1nflaSsCrXwBM1ORATatZq7+M0sbCOROE6/E5toyu0K+XYIrtsqMEJhb/U5OUfLgtp4QjkRdZ/zIB+ldhLH0lRlh6IlYDL2xt33QfEDb69ia6SLrgn5xoFHEksDdQ8GU+6SXNNNosXViw+5f9c4cmW8NLmOUSSh168yrXAL/emQJWINNXa3l3S5eb+OD8ll7992wT4S+SqdiePhPxQTk84nKI74CRU5oz039TzXGeHmcpZahU3+xBgzEZ4GBslXuQuPW7aOnke8l5rPRZrsxmj1X46V6tUhlN4oVxjLZDkWXEWL4iGqlnH4OmTk8nnc6xhyzr/cwZ8gd4K0lKzaniwIDAQAB";
      };
      phone = {
        name = "Phone";
        ip_address = "192.168.188.12:4200";
        public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1SN9GdLat9NI7yhczu8DgGX0VbewMvDXWNo6+CnTCpyITXmYCzv/0GkuuCXrG3C876R9HC3f2j6PfWEsc1NWTgQyI01OqATVw5PD0eEwaH3vAmdaHXSJurXJDbg9B2XQ+L2IkPf1uWlMyxrOGlMtRSt8Zn+Tm1eitrc8K4pQp+xCYPe26vie3Rvq11l9mIHWrj7y4mdGL0ILQQ3LkQunOxgtARRIs5ZXmskAeQQIiyT5d9MTVhz6EglB7bwnVTg+Ku3o02LVC27YNNHfFtqC1TAc2y4VV9pRPuVRCHZkKQvUaHbVyQbkR0TAjd/RtC8ys/KV/zynAsNipn/gStxAMQIDAQAB";
      };

      make-configuration = devices: {
        source_name = "test-computer";
        inherit devices;
      };

      # For testing in the Android emulator (with a port forward on port 4200).
      emulator-configuration-file = pkgs.writeText "emulator-config.json" (nixpkgs.lib.strings.toJSON (make-configuration [emulator]));

      # For testing on my real phone.
      phone-configuration-file = pkgs.writeText "phone-config.json" (nixpkgs.lib.strings.toJSON (make-configuration [phone]));

      # For testing on both at the same time.
      full-configuration-file = pkgs.writeText "combined-config.json" (nixpkgs.lib.strings.toJSON (make-configuration [emulator phone]));
    in rec {
      formatter = pkgs.alejandra;

      devShells.default = pkgs.mkShell {
        nativeBuildInputs = with pkgs; [
          (rust-bin.fromRustupToolchainFile ./rust-toolchain.toml)
          pkg-config
          openssl
        ];

        # For any tools that need to see the rust toolchain src
        RUST_SRC_PATH = pkgs.rustPlatform.rustLibSrc;

        # Test environment variables.
        PAM_USER = "test";
        PAM_SERVICE = "sshd";
        PAM_TYPE = "auth";

        # Test configuration files.
        EMULATOR_CONFIG = emulator-configuration-file;
        PHONE_CONFIG = phone-configuration-file;
        FULL_CONFIG = full-configuration-file;
      };

      packages.lan-pam = pkgs.rustPlatform.buildRustPackage rec {
        pname = "lan-pam";
        version = "0.1.0";
        src = ./.;
        cargoLock.lockFile = ./Cargo.lock;

        nativeBuildInputs = with pkgs; [
          pkg-config
        ];

        buildInputs = with pkgs; [
          openssl
        ];

        meta.mainProgram = pname;
      };

      packages.default = packages.lan-pam;
    });
}
