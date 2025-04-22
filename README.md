# SmolRX

**SmolRX** is a Java-based remote execution network app and protocol designed for secure and efficient distributed job processing.

## Features
- **Remote Job Execution**: Server assigns jobs; clients execute them locally, using transmitted JARs.
- **AES Encryption**: Secure communication with RSA-negotiated AES encryption.
- **Deflate Compression**: Optimized data transfer for reduced network overhead.
- **Job Dependency Management**: Supports job prerequisites and redundancy mechanisms.
- **Zero External Dependencies**: Lightweight and built purely with Java.

## Details
- **Build Tool**: Gradle
- Licensed under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- Uses GCM, aligns with security best-practices such as AEAD.
- ~260ms interconnect overhead. See [Benchmarks](tests/Benchmarks.md) for more details.

## Current Progress
- [x] RSA-negotiated AES encryption
- [x] Deflate compression
- [x] Job and program configuration
- [x] Implementation of remote job execution

## Future Enhancements
- Better client-side implementations.
- Emergent scheduling.
- More tests and benchmarks.
- Support for QUIC 

Contributions and feedback are encouraged! Feel free to submit issues or pull requests.
