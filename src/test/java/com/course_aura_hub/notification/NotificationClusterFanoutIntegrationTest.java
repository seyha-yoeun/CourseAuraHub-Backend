package com.course_aura_hub.notification;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Simulates another instance by saving a row directly and announcing it the way that instance would. */
class NotificationClusterFanoutIntegrationTest extends LearnerIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Test
    void notificationsAnnouncedByAnotherInstanceReachLocalStreams() throws Exception {
        TestUser learner = registerUser();
        BlockingQueue<String> lines = openStream(learner);
        // The stream sends its opening unread count first
        assertThat(nextLine(lines, "data:")).contains("unreadCount");

        UUID userId = userRepository.findByEmail(learner.email()).orElseThrow().getId();
        String title = "From node B " + System.nanoTime();
        Long id = jdbcTemplate.queryForObject("""
                insert into notifications (user_id, type, title, message, link)
                values (?, 'NEW_LESSON', ?, 'Hello', '/learn')
                returning id
                """, Long.class, userId, title);
        jdbcTemplate.queryForObject("select pg_notify('course_aura_notifications', ?)", Object.class, "other-node|" + id);

        assertThat(nextLine(lines, "data:")).contains(title);
    }

    private BlockingQueue<String> openStream(TestUser user) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me/notifications/stream"))
                .header("Authorization", "Bearer " + user.accessToken())
                .build();
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread.ofVirtual().start(() -> {
            Iterator<String> iterator = response.body().iterator();
            while (iterator.hasNext()) {
                lines.add(iterator.next());
            }
        });
        return lines;
    }

    private static String nextLine(BlockingQueue<String> lines, String prefix) throws Exception {
        while (true) {
            String line = lines.poll(10, TimeUnit.SECONDS);
            assertThat(line).as("waiting for " + prefix).isNotNull();
            if (line.startsWith(prefix)) {
                return line;
            }
        }
    }
}
