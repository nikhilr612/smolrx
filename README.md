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

## Current Progress
- [x] RSA-negotiated AES encryption
- [x] Deflate compression
- [x] Job and program configuration
- [x] Implementation of remote job execution

## Future Enhancements
- Better client-side implementations.
- Emergent scheduling.
- More tests and benchmarks.

# Demo - `bfcarm`
A demo program to classify Carmichael numbers under 1000, through a naive-brute-force approach with 1010 jobs is implemented for testing purposes.
A dummy client which disconnects immediately upon Jar delivery after job selection was used to time the delay suffered through the usage of this protocol, as compared to an ideal scenario where job execution begins immediately after static scheduling. The delay, hereby termed - interconnect latency is within 287.5-296.1 ms (mean = 291.798, variance = 4730.031, N = 1000, p = 0.05) for processes on the same machine. For similar or more intensive loads, ~287ms can be considered as the best-case interconnect latency, as this will likely increase with size and complexity of server jobs. 

Contributions and feedback are encouraged! Feel free to submit issues or pull requests.