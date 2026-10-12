package sk.drabikp.gigsynchub.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.MembersUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.out.BandStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.MembershipStore;
import sk.drabikp.gigsynchub.accounts.application.port.out.UserStore;
import sk.drabikp.gigsynchub.accounts.domain.Band;
import sk.drabikp.gigsynchub.accounts.domain.Membership;
import sk.drabikp.gigsynchub.accounts.domain.Role;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A band never ends up with nobody who owns it — the one rule its roster has, under two people
 * changing it at the same moment. Both of its owners may leave; they just may not both be the one
 * who leaves last. The rule itself is read from a roster, so it is only as true as the roster the
 * decision was made on: the store holds it (a lock on the band's row) and these are the tests that
 * say the holding works. Not {@code @Transactional} — it is about real, separate transactions.
 */
@SpringBootTest
class BandKeepsAnOwnerTest {

    @Autowired
    private MembersUseCase members;
    @Autowired
    private MembershipStore memberships;
    @Autowired
    private UserStore users;
    @Autowired
    private BandStore bands;
    @Autowired
    private Transactions transactions;

    private BandId band;
    private UserId ann;
    private UserId ben;

    @BeforeEach
    void aBandWithTwoOwners() {
        band = new BandId(UUID.randomUUID());
        ann = new UserId(UUID.randomUUID());
        ben = new UserId(UUID.randomUUID());
        transactions.inTransaction(() -> {
            users.save(new User(ann, "sub-" + ann.value(), ann.value() + "@band.test", "Ann"));
            users.save(new User(ben, "sub-" + ben.value(), ben.value() + "@band.test", "Ben"));
            bands.save(new Band(band, "two-owners-" + band.value().toString().substring(0, 8), "Two owners"));
            memberships.save(new Membership(ann, band, Role.OWNER, null));
            memberships.save(new Membership(ben, band, Role.OWNER, null));
        });
    }

    @AfterEach
    void cleanUp() {
        transactions.inTransaction(() -> {
            bands.delete(band);
            users.delete(ann);
            users.delete(ben);
        });
    }

    @Test
    void both_owners_leaving_at_the_same_moment_cannot_both_be_the_last_one_out() throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        List<Throwable> refused = new ArrayList<>();

        List<Thread> leaving = List.of(leaves(ann, together, refused), leaves(ben, together, refused));
        leaving.forEach(Thread::start);
        for (Thread thread : leaving) {
            thread.join(TimeUnit.SECONDS.toMillis(20));
        }

        assertThat(memberships.ofBand(band).hasOwner()).as("the band still has an owner").isTrue();
        assertThat(memberships.ofBand(band).size()).as("one of them left").isEqualTo(1);
        assertThat(refused).singleElement().isInstanceOfSatisfying(UserFacingException.class,
                e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.LAST_OWNER));
    }

    private Thread leaves(UserId who, CyclicBarrier together, List<Throwable> refused) {
        return new Thread(() -> {
            try {
                together.await(20, TimeUnit.SECONDS);
                members.remove(who, band, who);
            } catch (Throwable e) {
                synchronized (refused) {
                    refused.add(e);
                }
            }
        }, "leaving-" + who.value());
    }

    @Test
    void while_one_change_holds_the_roster_the_next_one_waits_for_it() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch past = new CountDownLatch(1);

        Thread second = new Thread(() -> {
            try {
                held.await(20, TimeUnit.SECONDS);
                transactions.inTransaction(() -> {
                    memberships.hold(band);
                    past.countDown();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "the-second-change");
        second.start();

        transactions.inTransaction(() -> {
            memberships.hold(band);
            held.countDown();
            try {
                // the other thread is inside hold() and stays there: a "did not happen" that a slow
                // machine only makes more certain, and that fails exactly when the holding doesn't work
                assertThat(past.await(400, TimeUnit.MILLISECONDS)).as("got past a held roster").isFalse();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(past.await(20, TimeUnit.SECONDS)).as("and goes on once the hold ends").isTrue();
        second.join(TimeUnit.SECONDS.toMillis(20));
    }
}
