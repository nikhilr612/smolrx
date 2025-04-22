# Demo - `bfcarm`
A demo program to classify Carmichael numbers under 1000, through a naive-brute-force approach with 1010 jobs is implemented for testing purposes.
A dummy client which disconnects immediately upon Jar delivery after job selection was used to time the delay suffered through the usage of this protocol, as compared to an ideal scenario where job execution begins immediately after static scheduling. 
The delay, hereby termed - interconnect latency is within 261.74-270.06 ms (mean = 265.898, sample variance = 4494.374, median = 253.5, N = 1000, p = 0.05) for processes on the same machine. 
For similar or more intensive loads, ~260ms can be considered as the best-case interconnect latency, as this will likely increase with size and complexity of server jobs.
