package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.gigsynchub.gig.application.ConcurrentChangeException;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;

import java.util.function.Supplier;

/** {@link Transactions} on Spring's transaction manager; repositories called inside join it. */
@Component
class SpringTransactions implements Transactions {

    private static final Logger log = LoggerFactory.getLogger(SpringTransactions.class);

    private final TransactionTemplate template;

    public SpringTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public void inTransaction(Runnable work) {
        computeInTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** A row another transaction changed meanwhile (optimistic lock) → {@link ConcurrentChangeException}. */
    @Override
    public <T> T computeInTransaction(Supplier<T> work) {
        return once(work);
    }

    @Override
    public void inRepeatableTransaction(Runnable work) {
        computeInRepeatableTransaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * The lost race is run again, once. A unique key the winner filled is not an error to show
     * anyone: the second run re-reads, finds that row, and answers the way the work's own check
     * always would have. A SECOND failure is not a race any more, so it is left to propagate.
     */
    @Override
    public <T> T computeInRepeatableTransaction(Supplier<T> work) {
        try {
            return once(work);
        } catch (DataIntegrityViolationException lost) {
            log.debug("Another transaction filled a unique key first; running the work again", lost);
            return once(work);
        }
    }

    private <T> T once(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (OptimisticLockingFailureException e) {
            throw ConcurrentChangeException.changed(e);
        }
    }
}
