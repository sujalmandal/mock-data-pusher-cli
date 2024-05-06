package app.s.m;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static app.s.m.Constants.*;
import static java.util.concurrent.Executors.newWorkStealingPool;

public class MockDataGeneratorService {


    private final Random random;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;  // Managed executor service in Quarkus
    private final ConcurrentHashMap<String, Integer> responseCodeCountStats;

    private final int delayMs;
    private final String sample;
    private final String apiEndPoint;
    private final AtomicInteger progress;
    private final AtomicLong totalTimeTakenMs;
    private final int totalRequestsToGenerate;

    public MockDataGeneratorService(
            int delayMs, String sample, String apiEndPoint, int concurrentRequests, int totalRequestsToGenerate) {
        this.delayMs = delayMs;
        this.sample = sample;
        this.apiEndPoint = apiEndPoint;
        this.totalRequestsToGenerate = totalRequestsToGenerate;

        this.random = new Random();
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.progress = new AtomicInteger(0);
        this.totalTimeTakenMs = new AtomicLong(0);
        this.responseCodeCountStats = new ConcurrentHashMap<>();
        this.executorService = newWorkStealingPool(concurrentRequests);
    }

    void execute() throws InterruptedException, JsonProcessingException {
        long appStartTime = System.currentTimeMillis();
        var latch = new CountDownLatch(totalRequestsToGenerate);
        for (int i = 0; i < totalRequestsToGenerate; i++) {
            JsonNode rootNode = objectMapper.readTree(sample);
            replacePlaceholders((ObjectNode) rootNode);
            executorService.submit(() -> {
                long startTime = System.currentTimeMillis();
                int randomDelayMs = getRandomDelayMs(delayMs);
                try {
                    Thread.sleep(randomDelayMs);
                    executeEndpoint(objectMapper.writeValueAsString(rootNode));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    long duration = System.currentTimeMillis() - (startTime + randomDelayMs);
                    totalTimeTakenMs.addAndGet(duration);
                    latch.countDown();
                }
            });
        }
        latch.await();
        clearScreen();
        double averageTime = totalRequestsToGenerate > 0 ? (double) totalTimeTakenMs.get() / totalRequestsToGenerate : 0;
        System.out.println("Total responses");
        responseCodeCountStats.forEach((status, count)-> System.out.printf("response status: %s - count: %s %n", status, count));
        System.out.printf("Average Time per Request: %.2f ms\n", averageTime);
        System.out.printf("Total time taken (including wait time): %s%n", System.currentTimeMillis() - appStartTime);
    }

    private void executeEndpoint(String jsonBody) throws IOException {
        var body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        var request = new Request.Builder().url(apiEndPoint).post(body).build();

        try (var response = httpClient.newCall(request).execute()) {
            clearScreen();
            var responseCode = response.code();
            responseCodeCountStats.putIfAbsent(String.valueOf(responseCode), 0);
            responseCodeCountStats.computeIfPresent(String.valueOf(responseCode), (k, v)-> v+1);
            var totalDone = progress.incrementAndGet();
            System.out.printf("%s out of %s done..\n", totalDone, totalRequestsToGenerate);
        }
    }


    private void replacePlaceholders(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                String value = entry.getValue().asText();
                if (value.startsWith(EXPR_START)) {
                    node.put(entry.getKey(), resolvePlaceholder(value));
                }
            } else if (entry.getValue().isObject()) {
                replacePlaceholders((ObjectNode) entry.getValue());
            }
        });
    }

    private String resolvePlaceholder(String placeholder) {
        var any_string_placeholder_start = EXPR_START + ANY_EXPR + PARAM_START;
        var random_date_placeholder_start = EXPR_START + RANDOM_EXPR + PARAM_START + DATETIME_PARAM + SEPARATOR;
        var random_uuid_placeholder = EXPR_START + RANDOM_EXPR + PARAM_START + UUID_PARAM + PARAM_END + EXPR_END;

        if (placeholder.startsWith(random_uuid_placeholder)) {
            return UUID.randomUUID().toString();
        } else {
            if (placeholder.startsWith(random_date_placeholder_start)) {
                var params = placeholder.substring(8, placeholder.length() - 2);
                var args = params.split(SEPARATOR);
                var dateFormat = args[1];
                var utcDateTimeForCurrentDateTime = LocalDateTime.now();
                var dateFormatter = DateTimeFormatter.ofPattern(dateFormat);
                return dateFormatter.format(utcDateTimeForCurrentDateTime);
            } else {
                if (placeholder.startsWith(any_string_placeholder_start)) {
                    var options = placeholder.substring(5, placeholder.length() - 2);
                    var choices = options.split(SEPARATOR);
                    return choices[random.nextInt(choices.length)].trim();
                }
            }
        }
        return placeholder;
    }

    private int getRandomDelayMs(int upperBound) {
        return random.nextInt(upperBound) + 1;
    }

    private static void clearScreen(){
        try {
            if (System.getProperty("os.name").contains("Windows"))
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            else
                Runtime.getRuntime().exec("clear");
        } catch (IOException | InterruptedException ignored) {}
    }
}
