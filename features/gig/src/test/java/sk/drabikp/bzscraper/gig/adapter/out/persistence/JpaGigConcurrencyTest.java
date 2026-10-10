package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;

/** Optimistic locking of gigs across real transactions (so not @Transactional itself). */
@SpringBootTest
class JpaGigConcurrencyTest {

    @Autowired
    private JpaGigRepository repository;

    @Autowired
    private SpringTransactions transactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    @AfterEach
    void cleanUp() {
        repository.deleteById(BAND, gig.id());
    }

    private static Gig titled(Gig gig, String title) {
        return Gig.create(title, gig.schedule(), gig.location(), gig.lineup(), Admission.free(), null, null, null, null);
    }

    @Test
    void two_saves_of_the_same_gig_at_the_same_time_cant_both_win() {
        repository.save(BAND, gig);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
            repository.save(BAND, titled(gig, "Mine"));                                  // read version 0
            other.executeWithoutResult(s -> repository.save(BAND, titled(gig, "Theirs"))); // commits version 1
        })).isInstanceOf(ConcurrentChangeException.class);

        assertThat(repository.findById(BAND, gig.id())).get().extracting(Gig::title).isEqualTo("Theirs");
    }

    @Test
    void saves_one_after_another_each_build_on_the_last() {
        repository.save(BAND, gig);
        transactions.inTransaction(() -> repository.save(BAND, titled(gig, "Second")));
        transactions.inTransaction(() -> repository.save(BAND, titled(gig, "Third")));

        assertThat(repository.findById(BAND, gig.id())).get().extracting(Gig::title).isEqualTo("Third");
    }
}
