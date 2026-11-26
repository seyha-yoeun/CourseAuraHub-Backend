package com.course_aura_hub.chat;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.service.ChatGroupService;
import com.course_aura_hub.chat.service.ChatService;
import com.course_aura_hub.common.security.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Races the plan calls out: parallel accepts at the member cap, transfers, duplicate DMs, sends and invites. */
@TestPropertySource(properties = "app.chat.group-member-limit=3")
class ChatConcurrencyIntegrationTest extends ChatTestSupport {

    @Autowired
    private ChatGroupService groupService;

    @Autowired
    private ChatService chatService;

    private Actor actor(TestUser user) {
        return new Actor(id(user), user.email(), false);
    }

    /** Runs every task at the same moment; returns how many finished without throwing. */
    private static int race(List<Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (java.util.concurrent.ExecutionException ex) {
                // Expected for the losers of the race
            }
        }
        pool.shutdownNow();
        return succeeded;
    }

    @Test
    void parallelAccepts_neverGoOverTheMemberCap() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        enroll(owner, slug);
        List<TestUser> invitees = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestUser user = registerUser();
            enroll(user, slug);
            invitees.add(user);
        }
        int group = createGroup(owner, slug, invitees.toArray(TestUser[]::new));
        List<Callable<?>> accepts = new ArrayList<>();
        for (TestUser invitee : invitees) {
            int invite = inviteId(invitee, group);
            accepts.add(() -> groupService.accept(actor(invitee), invite));
        }

        assertThat(race(accepts)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from chat_participants where conversation_id = ? and left_at is null", Integer.class, group))
                .isEqualTo(3);
    }

    @Test
    void simultaneousTransfers_leaveExactlyOneOwner() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        TestUser cy = registerUser();
        for (TestUser user : List.of(owner, bo, cy)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, bo, cy);
        join(bo, group);
        join(cy, group);

        int succeeded = race(List.of(
                () -> {
                    groupService.transfer(actor(owner), group, id(bo));
                    return null;
                },
                () -> {
                    groupService.transfer(actor(owner), group, id(cy));
                    return null;
                }));
        assertThat(succeeded).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from chat_participants where conversation_id = ? and role = 'OWNER' and left_at is null",
                Integer.class, group)).isEqualTo(1);
        UUID ownerId = jdbcTemplate.queryForObject("select owner_id from chat_conversations where id = ?", UUID.class, group);
        assertThat(ownerId).isIn(id(bo), id(cy));
    }

    @Test
    void bothSidesOpeningADirectConversation_getTheSameOne() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        List<Integer> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Callable<?>> opens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boolean fromJane = i % 2 == 0;
            opens.add(() -> ids.add(fromJane
                    ? chatService.openDirect(actor(jane), id(bo)).id()
                    : chatService.openDirect(actor(bo), id(jane)).id()));
        }
        assertThat(race(opens)).isEqualTo(4);
        assertThat(ids).hasSize(4).containsOnly(ids.getFirst());
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_participants where conversation_id = ?",
                Integer.class, ids.getFirst())).isEqualTo(2);
    }

    @Test
    void retriedSendsInParallel_storeOneMessage() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser jane = registerUser();
        TestUser bo = registerUser();
        enroll(jane, slug);
        enroll(bo, slug);
        int conversation = openDirect(jane, bo);
        UUID clientId = UUID.randomUUID();
        List<Integer> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Callable<?>> sends = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sends.add(() -> ids.add(chatService.send(actor(jane), conversation,
                    new ChatDtos.SendMessageRequest(clientId, "once", null)).id()));
        }
        assertThat(race(sends)).isEqualTo(5);
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(jdbcTemplate.queryForObject("select count(*) from chat_messages where conversation_id = ?",
                Integer.class, conversation)).isEqualTo(1);
    }

    @Test
    void parallelInvites_leaveOnePendingInvite() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser admin = registerUser();
        TestUser bo = registerUser();
        for (TestUser user : List.of(owner, admin, bo)) {
            enroll(user, slug);
        }
        int group = createGroup(owner, slug, admin);
        join(admin, group);
        groupService.changeRole(actor(owner), group, id(admin), "admin");

        race(List.of(
                () -> groupService.invite(actor(owner), group, List.of(id(bo))),
                () -> groupService.invite(actor(admin), group, List.of(id(bo)))));
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from chat_group_invites i join users u on u.id = i.invitee_id
                where i.conversation_id = ? and u.email = ? and i.status = 'PENDING'""", Integer.class, group, bo.email()))
                .isEqualTo(1);
    }

    @Test
    void aSendRacingARemoval_eitherLandsBeforeItOrIsRefused() throws Exception {
        String slug = courseOwnedBy(registerInstructor());
        TestUser owner = registerUser();
        TestUser bo = registerUser();
        enroll(owner, slug);
        enroll(bo, slug);
        int group = createGroup(owner, slug, bo);
        join(bo, group);
        List<Integer> sent = java.util.Collections.synchronizedList(new ArrayList<>());

        race(List.of(
                () -> sent.add(chatService.send(actor(bo), group,
                        new ChatDtos.SendMessageRequest(UUID.randomUUID(), "last words", null)).id()),
                () -> {
                    groupService.removeMember(actor(owner), group, id(bo));
                    return null;
                }));
        assertThat(jdbcTemplate.queryForObject(
                "select left_reason from chat_participants p join users u on u.id = p.user_id where p.conversation_id = ? and u.email = ?",
                String.class, group, bo.email())).isEqualTo("REMOVED");
        // Ids follow commit order here (both lock the group row), so a message that got in came before the removal
        Integer removal = jdbcTemplate.queryForObject(
                "select max(id) from chat_messages where conversation_id = ? and kind = 'SYSTEM' and body like '% removed %'",
                Integer.class, group);
        assertThat(removal).isNotNull();
        for (Integer messageId : sent) {
            assertThat(messageId).isLessThan(removal);
        }
    }
}
