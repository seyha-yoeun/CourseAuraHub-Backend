package com.course_aura_hub.common.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Runs a scheduled job on one instance at a time, using a lease row in {@code scheduled_job_locks}.
 *
 * <p>The lease uses the database clock, so instances with skewed clocks still agree. {@code lockAtMostFor} frees
 * the lease if an instance dies mid-run. {@code lockAtLeastFor} keeps it after a quick run, so an instance whose
 * cron fires a moment later skips instead of running the job twice. The instance holding a lease may take it
 * again at any time, which keeps a frequent job, such as polling, on one instance until that instance stops.</p>
 */
@Slf4j
@Component
public class ClusterJobLock {

    private static final String ACQUIRE = """
            insert into scheduled_job_locks (name, locked_until, locked_at, locked_by)
            values (?, now() + make_interval(secs => ?), now(), ?)
            on conflict (name) do update
                set locked_until = excluded.locked_until, locked_at = excluded.locked_at, locked_by = excluded.locked_by
                where scheduled_job_locks.locked_until <= now() or scheduled_job_locks.locked_by = excluded.locked_by
            """;

    private static final String RELEASE = """
            update scheduled_job_locks
            set locked_until = greatest(now(), locked_at + make_interval(secs => ?))
            where name = ? and locked_by = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate newTransaction;
    private final ClusterNode node;

    public ClusterJobLock(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, ClusterNode node) {
        this.jdbcTemplate = jdbcTemplate;
        this.node = node;
        // Lease changes commit on their own, never inside a job's transaction
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs {@code task} if this instance gets the lease.
     *
     * @return whether the task ran
     */
    public boolean runExclusively(String name, Duration lockAtMostFor, Duration lockAtLeastFor, Runnable task) {
        if (!acquire(name, lockAtMostFor)) {
            log.debug("Skipping job {}: another instance holds its lease", name);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(name, lockAtLeastFor);
        }
    }

    private boolean acquire(String name, Duration lockAtMostFor) {
        try {
            Integer updated = newTransaction.execute(status ->
                    jdbcTemplate.update(ACQUIRE, name, seconds(lockAtMostFor), node.id()));
            return updated != null && updated == 1;
        } catch (DataAccessException ex) {
            log.warn("Could not take the lease for job {}; skipping this run", name, ex);
            return false;
        }
    }

    private void release(String name, Duration lockAtLeastFor) {
        try {
            newTransaction.executeWithoutResult(status ->
                    jdbcTemplate.update(RELEASE, seconds(lockAtLeastFor), name, node.id()));
        } catch (DataAccessException ex) {
            // The lease still ends at lockAtMostFor
            log.warn("Could not release the lease for job {}", name, ex);
        }
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }
}
