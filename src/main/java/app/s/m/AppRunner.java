package app.s.m;

import com.fasterxml.jackson.core.JsonProcessingException;
import picocli.CommandLine;

@CommandLine.Command
public class AppRunner implements Runnable {

    @CommandLine.Option(names = {"-s", "--sample"}, description = "JSON schema of the input body accepted by the API")
    String sample;
    @CommandLine.Option(names = {"-api", "--api-endpoint"}, description = "API endpoint to test")
    String apiEndPoint;
    @CommandLine.Option(names = {"-cr", "--concurrent-requests"}, description = "Total concurrent users")
    int concurrentRequests;
    @CommandLine.Option(names = {"-t", "--total-requests"}, description = "Total requests to generate users")
    int totalRequestsToGenerate;
    @CommandLine.Option(names = {"-d", "--delay-between-requests"}, description = "Delay upper limit between requests in milliseconds")
    int delayMs;

    @Override
    public void run() {
        MockDataGeneratorService mockDataGeneratorService = new MockDataGeneratorService(concurrentRequests, totalRequestsToGenerate);
        try {
            mockDataGeneratorService.testAPI(sample, apiEndPoint, delayMs);
        } catch (InterruptedException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
