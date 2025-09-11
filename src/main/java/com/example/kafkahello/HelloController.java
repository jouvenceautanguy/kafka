package com.example.kafkahello;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


@RestController
public class HelloController {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final AtomicLong cpuTestCount = new AtomicLong(0);
    private final AtomicLong memoryTestCount = new AtomicLong(0);
    private final AtomicReference<List<byte[]>> heldMemory = new AtomicReference<>(new ArrayList<>());


    public HelloController(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        // Enregistrer les gauges pour les tests
        Gauge.builder("app_cpu_tests_total", cpuTestCount, AtomicLong::doubleValue)
            .description("Number of CPU tests executed")
            .register(meterRegistry);
            
        Gauge.builder("app_memory_tests_total", memoryTestCount, AtomicLong::doubleValue)
            .description("Number of memory tests executed")
            .register(meterRegistry);
            
        Gauge.builder("app_held_memory_mb", heldMemory, list -> (double) list.get().size())
            .description("Memory held by tests in MB")
            .register(meterRegistry);
    }


    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }


    @GetMapping("/publish")
    public String publish(@RequestParam(defaultValue = "Hello Kafka!") String msg) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            kafkaTemplate.send("demo", msg);
            meterRegistry.counter("app_messages_produced_total").increment();
            return "Sent to Kafka: " + msg;
        } finally {
            sample.stop(Timer.builder("app_publish_latency_seconds")
                    .description("Time to publish a message to Kafka")
                    .register(meterRegistry));
        }
    }

    // Exécuter un Runnable pendant ~durMs (utile pour borner un test CPU)
    public static void runForMillis(long durMs, Runnable r) {
        long end = System.currentTimeMillis() + Math.max(1, durMs);
        while (System.currentTimeMillis() < end) r.run();
    }

    // Consomme un peu de CPU (boucle vide) — baseline
    public static void busySpin(long durMs) {
        runForMillis(durMs, () -> {});
    }

    @GetMapping("/test/cpu/busy-spin")
    public String testBusySpin(@RequestParam(defaultValue = "1000") long durationMs) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            busySpin(durationMs);
            cpuTestCount.incrementAndGet();
            meterRegistry.counter("app_cpu_busy_spin_total").increment();
            return String.format("Busy spin completed for %d ms", durationMs);
        } finally {
            sample.stop(Timer.builder("app_cpu_test_latency_seconds")
                    .description("Time to execute CPU test")
                    .register(meterRegistry));
        }
    }

    @GetMapping("/test/cpu/hash-sha256")
    public String testHashSha256(@RequestParam(defaultValue = "1000") long durationMs,
                                 @RequestParam(defaultValue = "1") int payloadKb) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            cpuHashSha256(durationMs, payloadKb);
            cpuTestCount.incrementAndGet();
            meterRegistry.counter("app_cpu_hash_total").increment();
            return String.format("SHA-256 hash test completed for %d ms with %d KB payload", durationMs, payloadKb);
        } catch (Exception e) {
            meterRegistry.counter("app_cpu_test_errors_total").increment();
            return "Error: " + e.getMessage();
        } finally {
            sample.stop(Timer.builder("app_cpu_test_latency_seconds")
                    .description("Time to execute CPU test")
                    .register(meterRegistry));
        }
    }

    // 1) Hash intensif (SHA-256) – CPU pur, peu mémoire
    public static void cpuHashSha256(long durMs, int payloadKb) throws Exception {
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] data = new byte[Math.max(1, payloadKb) * 1024];
        new Random(42).nextBytes(data);
        runForMillis(durMs, () -> {
            md.update(data);
            md.digest();
        });
    }

    @GetMapping("/test/cpu/sort-arrays")
    public String testSortArrays(@RequestParam(defaultValue = "10") int arrays,
                                 @RequestParam(defaultValue = "10000") int size) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            sortLargeArrays(arrays, size);
            cpuTestCount.incrementAndGet();
            meterRegistry.counter("app_cpu_sort_total").increment();
            return String.format("Sorted %d arrays of size %d", arrays, size);
        } finally {
            sample.stop(Timer.builder("app_cpu_test_latency_seconds")
                    .description("Time to execute CPU test")
                    .register(meterRegistry));
        }
    }

    // 2) Tri de gros tableaux — allocation + CPU (O(n log n))
    public static void sortLargeArrays(int arrays, int size) {
        Random rnd = new Random(123);
        for (int i = 0; i < arrays; i++) {
            int[] a = new int[size];
            for (int j = 0; j < size; j++) a[j] = rnd.nextInt();
            Arrays.sort(a);
        }
    }

    @GetMapping("/test/cpu/matrix-multiply")
    public String testMatrixMultiply(@RequestParam(defaultValue = "100") int n,
                                     @RequestParam(defaultValue = "5") int reps) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            long result = matMul(n, reps);
            cpuTestCount.incrementAndGet();
            meterRegistry.counter("app_cpu_matrix_total").increment();
            return String.format("Matrix multiply %dx%d repeated %d times, result: %d", n, n, reps, result);
        } finally {
            sample.stop(Timer.builder("app_cpu_test_latency_seconds")
                    .description("Time to execute CPU test")
                    .register(meterRegistry));
        }
    }

    // 3) Multiplication de matrices denses — CPU intensif, cache-friendly
    public static long matMul(int n, int reps) {
        double[][] A = new double[n][n];
        double[][] B = new double[n][n];
        double[][] C = new double[n][n];
        Random r = new Random(7);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                A[i][j] = r.nextDouble();
                B[i][j] = r.nextDouble();
            }
        long acc = 0;
        for (int rep = 0; rep < reps; rep++) {
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < n; k++) {
                    double aik = A[i][k];
                    for (int j = 0; j < n; j++) {
                        C[i][j] += aik * B[k][j];
                    }
                }
            }
            acc += (long) C[0][0];
        }
        return acc;
    }

    @GetMapping("/test/cpu/sieve")
    public String testSieve(@RequestParam(defaultValue = "1000000") int limit) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int count = sieveCountPrimes(limit);
            cpuTestCount.incrementAndGet();
            meterRegistry.counter("app_cpu_sieve_total").increment();
            return String.format("Found %d primes up to %d", count, limit);
        } finally {
            sample.stop(Timer.builder("app_cpu_test_latency_seconds")
                    .description("Time to execute CPU test")
                    .register(meterRegistry));
        }
    }

    // 4) Sieve de Eratosthène — test algorithmique classique
    public static int sieveCountPrimes(int limit) {
        boolean[] isPrime = new boolean[limit + 1];
        Arrays.fill(isPrime, true);
        isPrime[0] = isPrime[1] = false;
        for (int p = 2; p * p <= limit; p++) {
            if (isPrime[p]) {
                for (int k = p * p; k <= limit; k += p) isPrime[k] = false;
            }
        }
        int c = 0;
        for (int i = 2; i <= limit; i++) if (isPrime[i]) c++;
        return c;
    }

    @GetMapping("/test/memory/allocate")
    public String testMemoryAllocate(@RequestParam(defaultValue = "50") int mb) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<byte[]> memory = holdMegabytes(mb);
            heldMemory.set(memory);
            memoryTestCount.incrementAndGet();
            meterRegistry.counter("app_memory_allocated_total").increment();
            return String.format("Allocated and held %d MB of memory", mb);
        } finally {
            sample.stop(Timer.builder("app_memory_test_latency_seconds")
                    .description("Time to execute memory test")
                    .register(meterRegistry));
        }
    }

    @GetMapping("/test/memory/release")
    public String testMemoryRelease() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int released = heldMemory.get().size();
            heldMemory.set(new ArrayList<>());
            System.gc(); // Suggérer un GC
            meterRegistry.counter("app_memory_released_total").increment();
            return String.format("Released %d MB of memory", released);
        } finally {
            sample.stop(Timer.builder("app_memory_test_latency_seconds")
                    .description("Time to execute memory test")
                    .register(meterRegistry));
        }
    }

    // 5) Allocation massive retenue (pression heap contrôlée)
    public static List<byte[]> holdMegabytes(int mb) {
        int block = 1 * 1024 * 1024;
        List<byte[]> list = new ArrayList<>(mb);
        for (int i = 0; i < mb; i++) list.add(new byte[block]);
        return list; // renvoie la référence pour décider quand libérer
    }

    @GetMapping("/test/load")
    public String testLoad(@RequestParam(defaultValue = "10") int iterations) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            for (int i = 0; i < iterations; i++) {
                // Mélange de tests CPU et mémoire
                busySpin(100);
                cpuHashSha256(200, 1);
                sortLargeArrays(2, 1000);
            }
            meterRegistry.counter("app_load_tests_total").increment();
            return String.format("Completed %d iterations of mixed CPU tests", iterations);
        } catch (Exception e) {
            meterRegistry.counter("app_load_test_errors_total").increment();
            return "Error: " + e.getMessage();
        } finally {
            sample.stop(Timer.builder("app_load_test_latency_seconds")
                    .description("Time to execute load test")
                    .register(meterRegistry));
        }
    }
}