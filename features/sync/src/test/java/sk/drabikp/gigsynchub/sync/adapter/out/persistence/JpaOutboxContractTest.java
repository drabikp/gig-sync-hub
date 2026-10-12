package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.drabikp.gigsynchub.gig.domain.TestBands;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutbox;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncOutboxContract;

/**
 * The JPA outbox keeps the outbox's contract (the same tests the service tests' fake passes).
 * The tests are inherited, so a class-level {@code @Transactional} wouldn't roll them back:
 * each starts from empty tables and leaves them empty instead.
 */
@SpringBootTest
class JpaOutboxContractTest extends SyncOutboxContract {

    @Autowired
    private JpaSyncOutbox outbox;
    @Autowired
    private SyncTaskJpaRepository tasks;
    @Autowired
    private SyncLogJpaRepository log;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void empty() {
        log.deleteAll();
        tasks.deleteAll();
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Override
    protected SyncOutbox outbox() {
        return outbox;
    }
}
