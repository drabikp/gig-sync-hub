package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.gigsynchub.gig.application.ConcurrentChangeException;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;

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

    /** The same gig under another title — two writers edit one gig, they do not make two. */
    private static Gig titled(Gig gig, String title) {
        return Gig.create(gig.id(), title, gig.schedule(), gig.location(), gig.lineup(), Admission.free(),
                null, null, null, null);
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

    /**
     * The store is the backstop under {@code GigWrites}' own refusal: a band may not have two gigs
     * on one day at one venue, and nothing — not an import, not a second window — gets past it.
     * Not {@code @Transactional}, because a refused insert must not poison the test's own work.
     */
    @Test
    void a_second_gig_on_one_day_and_venue_is_refused_by_the_store_itself() {
        repository.save(BAND, gig);
        Gig twin = Gig.create("Someone else's idea of the same night", gig.schedule(), gig.location(),
                gig.lineup(), Admission.free(), null, null, null, null);

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.save(BAND, twin)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findByMatch(BAND, gig.match())).contains(gig);
    }

    @Test
    void saves_one_after_another_each_build_on_the_last() {
        repository.save(BAND, gig);
        transactions.inTransaction(() -> repository.save(BAND, titled(gig, "Second")));
        transactions.inTransaction(() -> repository.save(BAND, titled(gig, "Third")));

        assertThat(repository.findById(BAND, gig.id())).get().extracting(Gig::title).isEqualTo("Third");
    }
}
