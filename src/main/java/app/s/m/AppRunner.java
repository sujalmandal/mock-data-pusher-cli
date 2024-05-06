package app.s.m;

import com.fasterxml.jackson.core.JsonProcessingException;
import picocli.CommandLine;

@CommandLine.Command
public class AppRunner implements Runnable {

    @CommandLine.Option(names = {"--sample"}, description = "JSON schema of the input body accepted by the API")
    String sample;
    @CommandLine.Option(names = {"--api-endpoint"}, description = "API endpoint to test")
    String apiEndPoint;
    @CommandLine.Option(names = {"--concurrent-requests"}, description = "Total concurrent users")
    int concurrentRequests;
    @CommandLine.Option(names = {"--total-requests"}, description = "Total requests to generate users")
    int totalRequestsToGenerate;
    @CommandLine.Option(names = {"--delay-between-requests"}, description = "Delay upper limit between requests in milliseconds", defaultValue = "250")
    int delayMs;

    @Override
    public void run() {
        var mockDataGeneratorService = new MockDataGeneratorService(
                delayMs, sample, apiEndPoint, concurrentRequests, totalRequestsToGenerate);
        try {
            mockDataGeneratorService.execute();
        } catch (InterruptedException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
