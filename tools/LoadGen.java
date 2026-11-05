import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open-loop HTTP load generator with no dependencies: `java LoadGen.java --mode read --rate 2000`.
 *
 * Requests are issued on a fixed schedule whether or not earlier ones have finished, and
 * latency runs from when a request was due. A closed-loop tool slows down when the server
 * does and then under-reports exactly the stalls worth knowing about. This one cannot.
 *
 * Prints one JSON line. The measured window follows a warm-up, because a cold JVM is much
 * slower than a warm one and would otherwise dominate the tail.
 */
public class LoadGen {
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");

    public static void main(String[] argv) throws Exception {
        Map<String, String> args = new HashMap<>();
        for (int i = 0; i + 1 < argv.length; i += 2) {
            args.put(argv[i].replaceFirst("^--", ""), argv[i + 1]);
        }
        String orderUrl = args.getOrDefault("order-url", "http://order:8081");
        String inventoryUrl = args.getOrDefault("inventory-url", "http://inventory:8082");
        String mode = args.getOrDefault("mode", "read");
        int rate = Integer.parseInt(args.getOrDefault("rate", "1000"));
        int warmup = Integer.parseInt(args.getOrDefault("warmup", "20"));
        int duration = Integer.parseInt(args.getOrDefault("duration", "30"));
        int seed = Integer.parseInt(args.getOrDefault("seed-orders", "2000"));
        double readShare = Double.parseDouble(args.getOrDefault("read-share", "0.9"));
        String label = args.getOrDefault("label", "");

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String sku = "LOAD-" + UUID.randomUUID().toString().substring(0, 8);
        post(client, inventoryUrl + "/api/products",
                "{\"sku\":\"" + sku + "\",\"name\":\"Load\",\"description\":\"load test\",\"qty\":100000000}");

        List<String> ids = seedOrders(client, orderUrl, sku, seed);
        if (ids.isEmpty()) {
            throw new IllegalStateException("could not create any seed orders");
        }

        run(client, orderUrl, sku, mode, rate, warmup, ids, readShare, null);
        Result result = new Result();
        double elapsed = run(client, orderUrl, sku, mode, rate, duration, ids, readShare, result);
        // In-flight requests finish before the scheduling loop returns, so this is complete.
        System.out.println(result.toJson(label, mode, rate, duration, elapsed));
    }

    private static List<String> seedOrders(HttpClient client, String orderUrl, String sku, int count) throws Exception {
        List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pending.add(client.sendAsync(orderRequest(orderUrl, sku), HttpResponse.BodyHandlers.ofString()));
            if (pending.size() == 32) {
                drain(pending, ids);
            }
        }
        drain(pending, ids);
        return ids;
    }

    private static void drain(List<CompletableFuture<HttpResponse<String>>> pending, List<String> ids) {
        for (CompletableFuture<HttpResponse<String>> future : pending) {
            Matcher matcher = ID.matcher(future.join().body());
            if (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        pending.clear();
    }

    private static HttpRequest orderRequest(String orderUrl, String sku) {
        return HttpRequest.newBuilder(URI.create(orderUrl + "/api/orders"))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"sku\":\"" + sku + "\",\"qty\":1,\"amountCents\":1800,\"customerId\":\"load\"}"))
                .build();
    }

    private static double run(HttpClient client, String orderUrl, String sku, String mode, int rate, int seconds,
                              List<String> ids, double readShare, Result result) throws Exception {
        long total = (long) rate * seconds;
        long start = System.nanoTime();
        double perNano = rate / 1e9;
        List<CompletableFuture<?>> inFlight = new ArrayList<>((int) Math.min(total, 2_000_000));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (long sent = 0; sent < total; sent++) {
            long due = start + (long) (sent / perNano);
            long wait;
            while ((wait = due - System.nanoTime()) > 0) {
                if (wait > 200_000) {
                    Thread.sleep(0, 100_000);
                } else {
                    Thread.onSpinWait();
                }
            }
            boolean read = mode.equals("read") || (mode.equals("mixed") && random.nextDouble() < readShare);
            HttpRequest request = read
                    ? HttpRequest.newBuilder(URI.create(orderUrl + "/api/orders/" + ids.get(random.nextInt(ids.size()))))
                        .timeout(Duration.ofSeconds(30)).GET().build()
                    : orderRequest(orderUrl, sku);
            CompletableFuture<?> future = client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (result != null) {
                            result.record(System.nanoTime() - due, response == null ? -1 : response.statusCode());
                        }
                    });
            inFlight.add(future);
        }
        for (CompletableFuture<?> future : inFlight) {
            try {
                future.join();
            } catch (RuntimeException ignored) {
                // Already counted as a failure above.
            }
        }
        return (System.nanoTime() - start) / 1e9;
    }

    private static void post(HttpClient client, String url, String body) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(url + " returned " + response.statusCode());
        }
    }

    private static final class Result {
        private static final int MAX = 4_000_000;
        private final AtomicLongArray latencies = new AtomicLongArray(MAX);
        private final AtomicLong count = new AtomicLong();
        private final Map<Integer, AtomicLong> statuses = new ConcurrentHashMap<>();

        void record(long nanos, int status) {
            statuses.computeIfAbsent(status, s -> new AtomicLong()).incrementAndGet();
            long slot = count.getAndIncrement();
            if (slot < MAX) {
                latencies.set((int) slot, nanos);
            }
        }

        String toJson(String label, String mode, int rate, int duration, double elapsed) {
            int n = (int) Math.min(count.get(), MAX);
            long[] sorted = new long[n];
            for (int i = 0; i < n; i++) {
                sorted[i] = latencies.get(i);
            }
            Arrays.sort(sorted);
            long ok = 0;
            long failed = 0;
            StringBuilder codes = new StringBuilder();
            for (Map.Entry<Integer, AtomicLong> entry : statuses.entrySet()) {
                if (entry.getKey() >= 200 && entry.getKey() < 300) {
                    ok += entry.getValue().get();
                } else {
                    failed += entry.getValue().get();
                }
                codes.append(codes.isEmpty() ? "" : ",").append('"').append(entry.getKey()).append("\":").append(entry.getValue().get());
            }
            return String.format(
                    "{\"label\":\"%s\",\"mode\":\"%s\",\"target_rps\":%d,\"achieved_rps\":%.0f,\"requests\":%d,\"ok\":%d,\"failed\":%d,"
                            + "\"statuses\":{%s},\"latency_ms\":{\"p50\":%.2f,\"p95\":%.2f,\"p99\":%.2f,\"max\":%.2f}}",
                    label, mode, rate, n / elapsed, n, ok, failed, codes,
                    pct(sorted, 50), pct(sorted, 95), pct(sorted, 99), n == 0 ? 0.0 : sorted[n - 1] / 1e6);
        }

        private static double pct(long[] sorted, double q) {
            if (sorted.length == 0) {
                return 0;
            }
            int rank = Math.min((int) (q / 100 * sorted.length), sorted.length - 1);
            return sorted[rank] / 1e6;
        }
    }
}
