package sk.drabikp.gigsynchub;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.IdClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant isolation, checked where it can be forgotten.
 *
 * <p>A band's rows are kept apart by the band being part of what every query asks for — in a derived
 * method's own name ({@code findByBandId…}) or in a predicate of its {@code @Query}. That is not a
 * convention one remembers: this test ENUMERATES every method of every repository over a band's
 * table and insists on it, so a query added next year that leaves the band out fails the build
 * rather than answering with another band's rows. A read that genuinely belongs to no band says so
 * with {@link AcrossBands} and gives its reason.
 *
 * <p>Why enumerate rather than sample: a test that lists the methods it checks proves only what
 * someone remembered to add to the list. This one starts from the classes on the classpath, so a new
 * repository method is covered the moment it exists — which is the whole difference between "a good
 * set of tests" and a sufficient one. {@code BandIsolationContract} then proves the isolation
 * behaves; this proves nothing escapes being asked.
 */
class BandScopedQueriesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("sk.drabikp.gigsynchub");

    /**
     * The tables that are NOT a band's data, and could not be scoped by band if we wanted: they are
     * how one finds out which bands there are and who is in them. An invitation is looked up by its
     * token before anyone knows the band; a membership is the answer to "which bands are mine"; a
     * user is nobody's band. The same four that {@code V6__row_level_security.sql} left out.
     */
    private static final Set<String> NOT_A_BANDS_DATA =
            Set.of("BandEntity", "MembershipEntity", "AppUserEntity", "InvitationEntity");

    /** {@code bandId} standing in a predicate — not merely mentioned, as an {@code order by} does. */
    private static final Pattern IN_A_PREDICATE =
            Pattern.compile("bandId\\s*(=|in\\b|is\\s+null)", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("every query over a band's table names the band, or says it is no band's")
    void every_query_over_a_bands_table_names_the_band_or_says_it_is_no_bands() {
        List<String> asked = new ArrayList<>();
        List<String> unscoped = new ArrayList<>();

        for (Class<?> repository : repositories()) {
            Class<?> entity = entityOf(repository);
            if (entity == null || NOT_A_BANDS_DATA.contains(entity.getSimpleName()) || !keepsABandsRows(entity)) {
                continue;
            }
            for (Method method : repository.getDeclaredMethods()) {
                asked.add(repository.getSimpleName() + "." + method.getName());
                if (!namesTheBand(method) && !method.isAnnotationPresent(AcrossBands.class)) {
                    unscoped.add(repository.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(asked)
                .as("this test found no band-scoped repository at all, so it would pass for the wrong "
                        + "reason — the scan or the detection of a band's table has broken")
                .isNotEmpty();
        assertThat(unscoped)
                .as("these touch a band's table without naming the band. Name it (findByBandId… or a "
                        + "bandId predicate in the @Query), or, if the query really is no band's, say "
                        + "so with @AcrossBands(\"why\")")
                .isEmpty();
    }

    @Test
    @DisplayName("a query that is no band's gives its reason")
    void a_query_that_is_no_bands_gives_its_reason() {
        List<String> mute = new ArrayList<>();
        for (Class<?> repository : repositories()) {
            for (Method method : repository.getDeclaredMethods()) {
                AcrossBands across = method.getAnnotation(AcrossBands.class);
                if (across != null && across.value().isBlank()) {
                    mute.add(repository.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertThat(mute).as("@AcrossBands without a reason tells the next reader nothing").isEmpty();
    }

    /** The band-scoped tables we know of, so the detector above cannot quietly stop finding them. */
    @Test
    @DisplayName("the tables that keep a band's rows are the ones we think they are")
    void the_tables_that_keep_a_bands_rows_are_the_ones_we_think_they_are() {
        List<String> found = repositories().stream()
                .map(BandScopedQueriesTest::entityOf)
                .filter(e -> e != null && !NOT_A_BANDS_DATA.contains(e.getSimpleName()) && keepsABandsRows(e))
                .map(Class::getSimpleName)
                .sorted()
                .toList();

        assertThat(found).contains("GigEntity", "PublishedGigEntity", "SyncTaskEntity", "BandSettingEntity",
                "CalendarEventEntity", "CalendarDecisionEntity", "CalendarLinkEntity", "CalendarRuleEntity",
                "PlatformAccountEntity", "BandCalendarEntity", "BandCheckEntity", "AuditEventEntity");
    }

    private static List<Class<?>> repositories() {
        List<Class<?>> found = new ArrayList<>();
        CLASSES.stream()
                .filter(c -> c.isInterface() && c.isAssignableTo(Repository.class))
                .forEach(c -> found.add(c.reflect()));
        return found;
    }

    private static Class<?> entityOf(Class<?> repository) {
        return ResolvableType.forClass(repository).as(Repository.class).getGeneric(0).resolve();
    }

    /**
     * Does a row of this table belong to one band? Either the entity carries {@code bandId} itself, or
     * the band is part of its identity — {@code @IdClass} (gig: the band and the gig's own id) or
     * {@code @EmbeddedId} (a publication: the band, the platform and the gig).
     */
    private static boolean keepsABandsRows(Class<?> entity) {
        if (hasABandId(entity)) {
            return true;
        }
        IdClass idClass = entity.getAnnotation(IdClass.class);
        if (idClass != null && hasABandId(idClass.value())) {
            return true;
        }
        return Arrays.stream(entity.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(EmbeddedId.class))
                .anyMatch(f -> hasABandId(f.getType()));
    }

    private static boolean hasABandId(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).anyMatch("bandId"::equals);
    }

    private static boolean namesTheBand(Method method) {
        if (method.getName().contains("BandId")) {
            return true;
        }
        Query query = method.getAnnotation(Query.class);
        return query != null && IN_A_PREDICATE.matcher(query.value()).find();
    }
}
