package com.marketplace.luckydraw.infrastructure.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.luckydraw.domain.DomainException;
import com.marketplace.luckydraw.service.CampaignLifecycleService;
import com.marketplace.luckydraw.service.EntryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class WriteContextIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private JdbcTemplate jdbc;
    private JdbcWriteRepository repository;
    private TransactionTemplate transaction;

    @BeforeEach
    void migrate() {
        var dataSource = new DriverManagerDataSource(
                System.getenv("TEST_DB_URL"), System.getenv().getOrDefault("TEST_DB_USER", "lucky"),
                System.getenv().getOrDefault("TEST_DB_PASSWORD", "lucky"));
        Flyway flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false)
                .locations("classpath:db/migration").load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcWriteRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void concurrentSubmissionNeverExceedsQuotaAndRollsBackLosingTickets() throws Exception {
        insertCampaign("concurrent", 2);
        for (int i = 0; i < 10; i++) repository.issueForOrder("order-" + i, "customer");
        List<String> ticketIds = jdbc.queryForList(
                "SELECT id FROM tickets WHERE user_id='customer' ORDER BY order_id", String.class);
        var service = new EntryService(repository, repository, repository, repository, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(10);
        var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
        for (String ticketId : ticketIds) {
            results.add(pool.submit(() -> {
                start.await();
                for (int attempt = 0; attempt < 5; attempt++) {
                    try {
                        transaction.executeWithoutResult(ignored ->
                                service.submit("customer", "concurrent", ticketId, "test"));
                        return true;
                    } catch (CannotAcquireLockException exception) {
                        if (attempt == 4) return false;
                    } catch (DomainException exception) {
                        return false;
                    }
                }
                return false;
            }));
        }
        start.countDown();
        int successes = 0;
        for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
        pool.shutdownNow();

        assertThat(successes).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT used FROM user_entry_quota WHERE campaign_id='concurrent'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entries WHERE campaign_id='concurrent'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE status='ISSUED'", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type='EntrySubmitted'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void ticketIssuanceAndCampaignCloseReleasePendingRewards() {
        insertCampaign("draw-test", 3);
        repository.issueForOrder("one-order", "customer");
        repository.issueForOrder("one-order", "customer");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE order_id='one-order'", Integer.class))
                .isEqualTo(1);

        repository.issueForOrder("second-order", "customer");
        var tickets = jdbc.queryForList("SELECT id FROM tickets WHERE user_id='customer'", String.class);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var entries = new EntryService(repository, repository, repository, repository, repository, clock);
        for (String ticket : tickets) transaction.executeWithoutResult(ignored ->
                entries.submit("customer", "draw-test", ticket, "test"));

        jdbc.update("UPDATE entries SET reward_pending=FALSE WHERE campaign_id='draw-test'");
        jdbc.update("UPDATE entries SET reward_pending=TRUE, wheel_segment=1 WHERE campaign_id='draw-test' ORDER BY seq LIMIT 1");
        var campaigns = new CampaignLifecycleService(repository, repository, repository, repository, clock);
        transaction.executeWithoutResult(ignored -> campaigns.end("draw-test", "seller"));

        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id='draw-test'", String.class))
                .isEqualTo("DRAWN");
        assertThat(jdbc.queryForObject("SELECT snapshot_hash FROM campaigns WHERE id='draw-test'", String.class))
                .hasSize(64);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM draw_snapshot_items WHERE campaign_id='draw-test'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE event_type='WinnerPicked'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void closeWaitsForSharedSubmissionLock() throws Exception {
        insertCampaign("race", 1);
        var sharedLocked = new CountDownLatch(1);
        var releaseSubmit = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var submit = pool.submit(() -> transaction.executeWithoutResult(ignored -> {
            repository.lockShared("race").orElseThrow();
            sharedLocked.countDown();
            try {
                releaseSubmit.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(sharedLocked.await(5, TimeUnit.SECONDS)).isTrue();
        var close = pool.submit(() -> transaction.executeWithoutResult(ignored ->
                jdbc.update("UPDATE campaigns SET status='ENDED' WHERE id='race' AND status='ACTIVE'")));
        Thread.sleep(200);
        assertThat(close.isDone()).isFalse();
        releaseSubmit.countDown();
        submit.get(5, TimeUnit.SECONDS);
        close.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id='race'", String.class)).isEqualTo("ENDED");
    }

    private void insertCampaign(String id, int limit) {
        transaction.executeWithoutResult(ignored -> jdbc.update("""
                INSERT INTO campaigns
                  (id,seller_id,name,status,max_entries_per_user,start_at,end_at,reward_type,reward_reference)
                VALUES (?, 'seller', ?, 'ACTIVE', ?, ?, ?, 'COUPON', 'C50')
                """, id, id, limit, NOW.minusSeconds(60), NOW.plusSeconds(3600)));
    }
}
