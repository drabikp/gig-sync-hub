package sk.drabikp.gigsynchub.gig.application.port.out;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This read or write is NOT one band's: it is the app's own work across every band, and it says so
 * here rather than being found out later.
 *
 * <p>Everything else that touches a band's table names the band — in a derived method's own name
 * ({@code findByBandId…}) or in a predicate of its {@code @Query}. {@code BandScopedQueriesTest}
 * enumerates every method of every repository over a band's table and insists on one or the other,
 * so a new query that quietly leaves the band out fails the build instead of answering with another
 * band's rows. A query that genuinely belongs to no band is not a mistake, but it is a decision, and
 * a decision is written down.
 *
 * <p>The reason is the whole point of the annotation. {@code @AcrossBands("the worker picking the
 * next due task")} tells the next reader why one query is allowed what the others are not; an empty
 * one would make the test pass and teach nobody anything.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AcrossBands {

    /** Why this one is no band's — in words, for whoever reads the next diff. */
    String value();
}
