package com.course_aura_hub.notification;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Uses a real HTTP connection: MockMvc does not perform the async dispatch that ends a stream. */
@TestPropertySource(properties = "app.notifications.stream-timeout=4s")
class NotificationStreamIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String END = "<end>";
    private static final String BROKEN = "<broken>";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private BlockingQueue<String> openStream(TestUser user, String lastEventId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(streamUri())
                .header("Authorization", "Bearer " + user.accessToken());
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<Stream<String>> response = client.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");

        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread.ofVirtual().start(() -> {
            try {
                Iterator<String> iterator = response.body().iterator();
                while (iterator.hasNext()) {
                    lines.add(iterator.next());
                }
                lines.add(END);
            } catch (RuntimeException ex) {
                lines.add(BROKEN);
            }
        });
        return lines;
    }

    private URI streamUri() {
        return URI.create("http://localhost:" + port + "/api/v1/me/notifications/stream");
    }

    private String nextLineStartingWith(BlockingQueue<String> lines, String prefix) throws Exception {
        while (true) {
            String line = lines.poll(10, TimeUnit.SECONDS);
            assertThat(line).as("waiting for " + prefix).isNotNull().isNotEqualTo(END).isNotEqualTo(BROKEN);
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
    }

    private String newestNotificationId(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/me/notifications")))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.data.items[0].id");
        return id.toString();
    }

    @Test
    void stream_rejectsMissingCredentials() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(streamUri()).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void stream_pushesCommittedNotificationsToTheirOwnerOnly() throws Exception {
        TestUser learner = registerUser();
        TestUser other = registerUser();
        BlockingQueue<String> mine = openStream(learner, null);
        BlockingQueue<String> theirs = openStream(other, null);
        assertThat(nextLineStartingWith(mine, "data:")).contains("\"unreadCount\":0");
        assertThat(nextLineStartingWith(theirs, "data:")).contains("\"unreadCount\":0");

        enroll(learner, FREE_COURSE);

        assertThat(nextLineStartingWith(mine, "event:")).isEqualTo("notification");
        String data = nextLineStartingWith(mine, "data:");
        assertThat(JsonPath.<String>read(data, "$.notification.type")).isEqualTo("enrolled");
        assertThat(JsonPath.<Integer>read(data, "$.unreadCount")).isEqualTo(1);
        assertThat(JsonPath.<Object>read(data, "$.notification.createdAt")).isInstanceOf(String.class);

        // The other learner's stream closes on timeout without having seen it
        assertThat(nextEventsUntilClosed(theirs)).doesNotContain("event:notification");
    }

    @Test
    void stream_replaysWhatAReconnectingClientMissed() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        String seen = newestNotificationId(learner);

        BlockingQueue<String> upToDate = openStream(learner, seen);
        BlockingQueue<String> behind = openStream(learner, "0");

        assertThat(nextLineStartingWith(behind, "id:")).isEqualTo(seen);
        assertThat(nextEventsUntilClosed(upToDate)).doesNotContain("event:notification");
    }

    @Test
    void stream_ignoresAnInvalidLastEventId() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);

        assertThat(nextEventsUntilClosed(openStream(learner, "not-a-number"))).doesNotContain("event:notification");
    }

    @Test
    void stream_closesCleanlyOnTimeoutSoClientsReconnect() throws Exception {
        TestUser learner = registerUser();

        List<String> lines = nextEventsUntilClosed(openStream(learner, null));

        assertThat(lines).last().isEqualTo(END);
    }

    private List<String> nextEventsUntilClosed(BlockingQueue<String> lines) throws Exception {
        List<String> seen = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            String line = lines.poll(1, TimeUnit.SECONDS);
            if (line == null) {
                continue;
            }
            seen.add(line.replace("event: ", "event:"));
            if (END.equals(line) || BROKEN.equals(line)) {
                return seen;
            }
        }
        throw new AssertionError("Stream did not close: " + seen);
    }
}
