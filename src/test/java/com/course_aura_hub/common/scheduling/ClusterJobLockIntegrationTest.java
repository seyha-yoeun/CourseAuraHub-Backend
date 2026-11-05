package com.course_aura_hub.common.scheduling;

import com.course_aura_hub.auth.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterJobLockIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ClusterJobLock instance() {
        return new ClusterJobLock(jdbcTemplate, transactionManager, new ClusterNode());
    }

    @Test
    void anotherInstanceSkipsWhileTheLeaseLasts_andTheHolderMayRunAgain() {
        ClusterJobLock first = instance();
        ClusterJobLock second = instance();
        String job = "job-" + System.nanoTime();
        AtomicInteger runs = new AtomicInteger();

        assertThat(first.runExclusively(job, Duration.ofMinutes(5), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(second.runExclusively(job, Duration.ofMinutes(5), Duration.ofMinutes(5), runs::incrementAndGet)).isFalse();
        assertThat(first.runExclusively(job, Duration.ofMinutes(5), Duration.ofMinutes(5), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void aRunningJobBlocksOtherInstances_andAShortLeaseFreesUpAfterwards() {
        ClusterJobLock first = instance();
        ClusterJobLock second = instance();
        String job = "job-" + System.nanoTime();
        AtomicBoolean overlapped = new AtomicBoolean(true);

        first.runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () ->
                overlapped.set(second.runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () -> { })));

        assertThat(overlapped).isFalse();
        assertThat(second.runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
    }

    @Test
    void anExpiredLeaseCanBeTaken() {
        ClusterJobLock first = instance();
        String job = "job-" + System.nanoTime();
        first.runExclusively(job, Duration.ofMinutes(5), Duration.ofHours(1), () -> { });
        jdbcTemplate.update("update scheduled_job_locks set locked_until = now() - interval '1 second' where name = ?", job);

        assertThat(instance().runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
    }

    @Test
    void theLeaseIsReleasedEvenWhenTheJobFails() {
        ClusterJobLock first = instance();
        String job = "job-" + System.nanoTime();
        try {
            first.runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the failure still reaches the scheduler
        }
        assertThat(instance().runExclusively(job, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
    }
}
