package sk.drabikp.gigsynchub.portal;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

/**
 * One call to a platform's site, as a span.
 *
 * <p>This is where this app's time actually goes. A publish is a handful of HTTP calls to somebody
 * else's server, paced on purpose like a person's; the framework instruments what it owns — the
 * requests this app SERVES and the statements it runs — and knows nothing of the requests it
 * MAKES, because {@code java.net.http} is the JDK's and nothing wraps it. So a trace of a sync run
 * without this is a span that took fourteen seconds with nothing inside it.
 *
 * <p>The platform's name is low cardinality and becomes a metric tag as well (how long Bandzone
 * takes, how often it fails); the path is high cardinality — it carries gig and event ids — so it
 * stays in the trace and never reaches a metric, where it would make one series per gig.
 *
 * <p>With no registry (a module test, or an app without actuator) {@link ObservationRegistry#NOOP}
 * costs a call and nothing else, so the clients need no second code path for "not observed".
 */
public final class PortalCall {

    private PortalCall() {
    }

    /** Runs {@code call} as a span named {@code platform.request}; whatever it throws is recorded and rethrown. */
    public static <T, E extends Exception> T observed(ObservationRegistry registry, String platform, String method,
                                                      String path, Throwing<T, E> call) throws E {
        Observation observation = Observation.createNotStarted("platform.request", registry)
                .lowCardinalityKeyValue("platform", platform)
                .lowCardinalityKeyValue("method", method)
                .highCardinalityKeyValue("path", path);
        observation.start();
        try (Observation.Scope scope = observation.openScope()) {
            return call.call();
        } catch (Exception e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /** What a portal client's call looks like: it answers something and may fail its own way. */
    @FunctionalInterface
    public interface Throwing<T, E extends Exception> {
        T call() throws E;
    }
}
