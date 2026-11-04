Detected stack: Rust workspace plus Android Gradle packaging; no tracked Node, Python, Go, or C/C++ project manifests detected.
Rust build tool: Cargo; workspace edition 2024; declared workspace minimum Rust 1.85, but the game workspace packages require Rust 1.90 and `horizon-mods` requires 1.95.
Pinned Rust toolchain: 1.95.0; test actual full-workspace MSRV 1.85 before treating it as a supported compiler.
Android build tool: Gradle wrapper/configuration (Gradle 8.14 in existing APK workflow) and Android Gradle Plugin 8.11.1; project docs require JDK 25.
Targets: GitHub Linux host checks; Android native target checks remain opt-in/heavy in the existing workflow.
CI scope: Rust fast checks run on relevant Rust changes; Android source checks/build workflow remains unchanged; no full APK added to routine CI.
Workflow changes: new Rust stable/beta/1.95.0 (workspace MSRV, set by `horizon-mods`) matrix plus rustfmt gate in `.github/workflows/rust-ci.yml`, with an opt-in `workflow_dispatch` `toolchain` input and a manual aarch64 Android target check; `android-dev.yml` and `android-source.yml` are unchanged.
