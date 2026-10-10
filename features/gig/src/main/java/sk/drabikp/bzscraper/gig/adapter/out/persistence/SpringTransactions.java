package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;

import java.util.function.Supplier;

/** {@link Transactions} on Spring's transaction manager; repositories called inside join it. */
@Component
class SpringTransactions implements Transactions {

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
        try {
            return template.execute(status -> work.get());
        } catch (OptimisticLockingFailureException e) {
            throw ConcurrentChangeException.changed(e);
        }
    }
}
