package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import sk.drabikp.gigsynchub.gig.application.port.out.RowScope;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@link RowScope} kept per thread; {@link RowScopedDataSource} tells the database at every connection's
 * checkout ({@code app.band}: a band's id, {@value #NOTHING} for none — also when nothing was opened —,
 * {@value #EVERY} for every band's). A transaction keeps the scope its connection was checked out with.
 */
public final class ThreadRowScope implements RowScope {

    static final String EVERY = "*";
    static final String NOTHING = "";

    private static final ThreadLocal<Deque<String>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Scope open(BandId band) {
        return push(band.toString());
    }

    @Override
    public Scope nothing() {
        return push(NOTHING);
    }

    @Override
    public Scope everyBand() {
        return push(EVERY);
    }

    /** What this thread may see now: a band's id, every band's, or — when nothing was opened — none. */
    static String current() {
        String top = SCOPES.get().peek();
        return top == null ? NOTHING : top;
    }

    private static Scope push(String scope) {
        Deque<String> scopes = SCOPES.get();
        scopes.push(scope);
        int depth = scopes.size();
        return () -> {
            Deque<String> now = SCOPES.get();
            while (now.size() >= depth) {
                now.pop();
            }
            if (now.isEmpty()) {
                SCOPES.remove();
            }
        };
    }
}
