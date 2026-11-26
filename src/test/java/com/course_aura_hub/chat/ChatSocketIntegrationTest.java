package com.course_aura_hub.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real WebSocket connections: STOMP needs a running server, which MockMvc doesn't provide. */
class ChatSocketIntegrationTest extends ChatTestSupport {

    private static final String ORIGIN = "http://localhost:3000";

    @LocalServerPort
    private int port;

    @Autowired
    private SimpUserRegistry userRegistry;

    private final List<StompSession> sessions = new ArrayList<>();
    private final WebSocketStompClient client = stompClient();

    private static WebSocketStompClient stompClient() {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        return stomp;
    }

    @AfterEach
    void disconnect() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
    }

    private record Connection(StompSession session, BlockingQueue<Map<String, Object>> events) {
    }

    private String ticket(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, post(CHAT + "/socket-ticket")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.ticket");
    }

    private StompSession connect(String ticket, String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        StompSession session = client.connectAsync(URI.create("ws://localhost:" + port + "/ws/chat?ticket=" + ticket),
                headers, new StompHeaders(), new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private Connection listen(TestUser user) throws Exception {
        StompSession session = connect(ticket(user), ORIGIN);
        BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((Map<String, Object>) payload);
            }
        });
        awaitSubscribed(id(user).toString());
        return new Connection(session, events);
    }

    private void awaitSubscribed(String name) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            SimpUser user = userRegistry.getUser(name);
            if (user != null && user.getSessions().stream().anyMatch(session -> !session.getSubscriptions().isEmpty())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Subscription never registered for " + name);
    }

    /** The next event of this type, skipping others. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> next(Connection connection, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = connection.events().poll(500, TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type"))) {
                return (Map<String, Object>) event.get("data");
            }
        }
        throw new AssertionError("No " + type + " event arrived");
    }

    /** The next message.created event with this body, skipping system lines and other events. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nextMessage(Connection connection, String body) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = connection.events().poll(500, TimeUnit.MILLISECONDS);
            if (event != null && "message.created".equals(event.get("type"))
                    && body.equals(((Map<String, Object>) event.get("data")).get("body"))) {
                return (Map<String, Object>) event.get("data");
            }
        }
        throw new AssertionError("No message \"" + body + "\" arrived");
    }

    private static boolean receivesWithin(Connection connection, String type, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = connection.events().poll(100, TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type"))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void messagesReachTheOtherParticipantLive_andSocketSendsAreAcknowledged() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        Connection janeSocket = listen(jane);
        Connection boSocket = listen(bo);

        send(jane, conversation, "over REST");
        Map<String, Object> created = nextMessage(boSocket, "over REST");
        assertThat(created.get("body")).isEqualTo("over REST");
        assertThat(created.get("conversationId")).isEqualTo(conversation);
        assertThat(((Map<?, ?>) created.get("sender")).get("name")).isEqualTo("Jane L.");
        assertThat(next(boSocket, "unread").get("total")).isEqualTo(1);

        UUID clientId = UUID.randomUUID();
        janeSocket.session().send("/app/chat.send", Map.of("conversationId", conversation, "clientMessageId", clientId,
                "body", "over the socket"));
        Map<String, Object> ack = next(janeSocket, "ack");
        assertThat(ack.get("clientMessageId")).isEqualTo(clientId.toString());
        Map<?, ?> acked = (Map<?, ?>) ack.get("message");
        assertThat(acked.get("body")).isEqualTo("over the socket");
        nextMessage(boSocket, "over the socket");

        boSocket.session().send("/app/chat.typing", Map.of("conversationId", conversation));
        assertThat(((Map<?, ?>) next(janeSocket, "typing").get("user")).get("id")).isEqualTo(id(bo).toString());

        boSocket.session().send("/app/chat.read", Map.of("conversationId", conversation, "messageId", acked.get("id")));
        Map<String, Object> read = next(janeSocket, "read");
        assertThat(read.get("userId")).isEqualTo(id(bo).toString());
        assertThat(read.get("messageId")).isEqualTo(acked.get("id"));

        janeSocket.session().send("/app/chat.send", Map.of("conversationId", 0, "clientMessageId", UUID.randomUUID(),
                "body", "nowhere"));
        assertThat(next(janeSocket, "error").get("code")).isEqualTo("not_found");
    }

    @Test
    void aRemovedMember_losesAccessLive_andHearsNothingMore() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(owner, bo, cy)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo, cy);
        join(bo, group);
        Connection cySocket = listen(cy);
        Connection boSocket = listen(bo);

        join(cy, group);
        assertThat(next(boSocket, "member.joined").get("conversationId")).isEqualTo(group);

        mockMvc.perform(as(owner, delete(CHAT + "/groups/" + group + "/members/" + id(cy)))).andExpect(status().isOk());
        assertThat(next(cySocket, "access.lost").get("conversationId")).isEqualTo(group);
        cySocket.events().clear();

        send(owner, group, "after cy left");
        nextMessage(boSocket, "after cy left");
        assertThat(receivesWithin(cySocket, "message.created", 1_500)).isFalse();
    }

    @Test
    void presence_reachesDirectContactsOnly_andFollowsTheSetting() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(jane, bo, cy)) {
            enroll(user, slug);
        }
        int conversation = openDirect(jane, bo);
        Connection janeSocket = listen(jane);
        Connection cySocket = listen(cy);

        Connection boSocket = listen(bo);
        Map<String, Object> online = next(janeSocket, "presence");
        assertThat(online.get("userId")).isEqualTo(id(bo).toString());
        assertThat(((Map<?, ?>) online.get("presence")).get("online")).isEqualTo(true);
        // Cy shares no direct conversation with Bo
        assertThat(receivesWithin(cySocket, "presence", 1_000)).isFalse();
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerPresence.online").value(true));

        // Offline once the grace period after the last session has passed
        boSocket.session().disconnect();
        Map<?, ?> offline = (Map<?, ?>) next(janeSocket, "presence").get("presence");
        assertThat(offline.get("online")).isEqualTo(false);
        assertThat(offline.get("lastSeenAt")).isNotNull();

        // Hiding it: contacts hear the status is gone, and the conversation stops showing it
        mockMvc.perform(asJson(bo, put(CHAT + "/settings"), Map.of("directMessages", "course_members", "showPresence", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showPresence").value(false));
        assertThat(next(janeSocket, "presence").get("presence")).isNull();
        mockMvc.perform(as(jane, get(CHAT + "/conversations/" + conversation)))
                .andExpect(jsonPath("$.data.peerPresence").doesNotExist());
    }

    @Test
    void invitesArriveLive() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        enroll(owner, slug);
        enroll(bo, slug);
        int group = createGroup(owner, slug);
        Connection boSocket = listen(bo);

        mockMvc.perform(asJson(owner, post(CHAT + "/groups/" + group + "/invites"), Map.of("userIds", List.of(id(bo)))))
                .andExpect(status().isOk());
        Map<String, Object> invite = next(boSocket, "invite.received");
        assertThat(invite.get("conversationId")).isEqualTo(group);
        assertThat(invite.get("groupTitle")).isEqualTo("Study group");
    }

    @Test
    void handshakes_needAFreshTicketAndATrustedOrigin() throws Exception {
        TestUser jane = registerUser();
        assertThatThrownBy(() -> connect("not-a-ticket", ORIGIN)).isInstanceOf(ExecutionException.class);

        String ticket = ticket(jane);
        assertThatThrownBy(() -> connect(ticket, "https://evil.example")).isInstanceOf(ExecutionException.class);
        // The refused origin did not spend the ticket
        connect(ticket, ORIGIN);
        // A ticket works once
        assertThatThrownBy(() -> connect(ticket, ORIGIN)).isInstanceOf(ExecutionException.class);
    }
}
