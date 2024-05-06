package app.s.m;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AppRunnerTest {

    @Test
    void testCommand() {
        AppRunner runner = new AppRunner();
        runner.apiEndPoint = "http://localhost:8088/test";
        runner.concurrentRequests = 2;
        runner.totalRequestsToGenerate = 20;
        runner.sample = """
                {
                    "userId":"{RANDOM(UUID)}",
                    "dob":"{RANDOM(DATETIME,yyyy-MM-dd HH:mm:ss)}",
                    "gender":"{ANY(male,female)}"
                }
                """;
        runner.delayMs = 50;
        runner.run();
    }
}