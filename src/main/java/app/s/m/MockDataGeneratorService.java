package app.s.m;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MockDataGeneratorService {

    private final Random random;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;  // Managed executor service in Quarkus

    private final int totalRequestsToGenerate;
    private final AtomicInteger progress = new AtomicInteger(0);

    public MockDataGeneratorService(int numberOfUsers, int totalRequestsToGenerate) {
        this.random = new Random();
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(numberOfUsers);
        this.totalRequestsToGenerate = totalRequestsToGenerate;
    }


    public void testAPI(String sampleJson, String apiEndpoint, int delayMs) throws InterruptedException, JsonProcessingException {
        executeApiConcurrently(apiEndpoint, sampleJson, totalRequestsToGenerate);
    }

    private void replacePlaceholders(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                String value = entry.getValue().asText();
                if (value.startsWith("{")) {
                    node.put(entry.getKey(), resolvePlaceholder(value));
                }
            } else if (entry.getValue().isObject()) {
                replacePlaceholders((ObjectNode) entry.getValue());
            }
        });
    }

    private String resolvePlaceholder(String placeholder) {
        if (placeholder.startsWith("{RANDOM(UUID)}")) {
            return UUID.randomUUID().toString();
        } else if (placeholder.startsWith("{RANDOM(DATETIME,")) {
            var params = placeholder.substring(8, placeholder.length() - 2);
            var args = params.split(",");
            var dateFormat = args[1];
            var utcDateTimeForCurrentDateTime = LocalDateTime.now();
            var dateFormatter = DateTimeFormatter.ofPattern(dateFormat);
            return dateFormatter.format(utcDateTimeForCurrentDateTime);
        } else if (placeholder.startsWith("{ANY(")) {
            var options = placeholder.substring(5, placeholder.length() - 2);
            var choices = options.split(",");
            return choices[random.nextInt(choices.length)].trim();
        }
        return placeholder;
    }

    private void executeApiConcurrently(String apiEndpoint, String sampleJson, int totalRequestsToGenerate) throws InterruptedException, JsonProcessingException {
        CountDownLatch latch = new CountDownLatch(totalRequestsToGenerate);

        for (int i = 0; i < totalRequestsToGenerate; i++) {
            JsonNode rootNode = objectMapper.readTree(sampleJson);
            replacePlaceholders((ObjectNode) rootNode);
            executorService.submit(() -> {
                try {
                    System.out.println();
                    executeEndpoint(apiEndpoint, objectMapper.writeValueAsString(rootNode));
                    System.out.println();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }

    private void executeEndpoint(String apiEndpoint, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(apiEndpoint)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if(response.isSuccessful()){
                clear();
                int totalDone = progress.incrementAndGet();
                System.out.printf("%s out of %s done", totalDone, totalRequestsToGenerate);
            }
        }
    }

    private static void clear(){
        //Clears Screen in java
        try {
            if (System.getProperty("os.name").contains("Windows"))
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            else
                Runtime.getRuntime().exec("clear");
        } catch (IOException | InterruptedException ex) {}
    }
}
