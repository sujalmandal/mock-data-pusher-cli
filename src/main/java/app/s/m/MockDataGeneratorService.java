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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static app.s.m.Constants.*;
import static java.util.concurrent.Executors.newWorkStealingPool;

public class MockDataGeneratorService {


    private final Random random;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;  // Managed executor service in Quarkus

    private final int delayMs;
    private final String sample;
    private final String apiEndPoint;
    private final int totalRequestsToGenerate;
    private final ConcurrentHashMap<String, Integer> responseStats;

    private final AtomicInteger progress = new AtomicInteger(0);

    public MockDataGeneratorService(int delayMs, String sample, String apiEndPoint, int concurrentRequests, int totalRequestsToGenerate) {
        this.delayMs = delayMs;
        this.sample = sample;
        this.apiEndPoint = apiEndPoint;
        this.totalRequestsToGenerate = totalRequestsToGenerate;

        this.random = new Random();
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.responseStats = new ConcurrentHashMap<>();
        this.executorService = newWorkStealingPool(concurrentRequests);
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

    void execute() throws InterruptedException, JsonProcessingException {
        CountDownLatch latch = new CountDownLatch(totalRequestsToGenerate);
        for (int i = 0; i < totalRequestsToGenerate; i++) {
            JsonNode rootNode = objectMapper.readTree(sample);
            replacePlaceholders((ObjectNode) rootNode);
            executorService.submit(() -> {
                try {
                    System.out.println();
                    Thread.sleep(getRandomDelayMs(delayMs));
                    executeEndpoint(objectMapper.writeValueAsString(rootNode));
                    System.out.println();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }

    private int getRandomDelayMs(int upperBound) {
        return random.nextInt(upperBound) + 1;
    }

    private void executeEndpoint(String jsonBody) throws IOException {
        var body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        var request = new Request.Builder().url(apiEndPoint).post(body).build();

        try (var response = httpClient.newCall(request).execute()) {
            clearScreen();
            var responseCode = response.code();
            responseStats.putIfAbsent(String.valueOf(responseCode), 0);
            responseStats.computeIfPresent(String.valueOf(responseCode), (k, v)-> v+1);
            var totalDone = progress.incrementAndGet();
            System.out.println("#####################################################");
            System.out.printf("%s out of %s done", totalDone, totalRequestsToGenerate);
            System.out.printf("\n stats %s\n", responseStats);
            System.out.println("#####################################################");
        }
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
