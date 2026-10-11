package sk.drabikp.bzscraper;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesDddRules;

/**
 * What the jMolecules annotations claim about this model, checked rather than merely written. The
 * model says what each type is — {@code @AggregateRoot} with its {@code @Identity},
 * {@code @Entity}, {@code @ValueObject}, {@code @Repository}, {@code @Service} — and these are
 * jMolecules' own rules holding it to that.
 *
 * <p>The three types that carry no stereotype on purpose say so in their own javadoc: a read model
 * ({@code CatalogMatch}, {@code ImportProposal}, {@code ImportedGig}) HOLDS an aggregate so a
 * person can decide about it, which is exactly what {@link
 * JMoleculesDddRules#valueObjectsMustNotReferToIdentifiables()} refuses to call a value object —
 * and it is right to.
 *
 * <p><b>Not run here: {@code JMoleculesArchitectureRules.ensureHexagonal()}</b>, and one thing is
 * why. It reports, at every depth, that two secondary adapters ({@code JpaPlatformAccountStore},
 * {@code JpaBandCalendarStore}) use {@code SecretBox}, which lives in a {@code @PrimaryPort}
 * package. In a strict hexagon a secret box IS a secondary port: nobody asks the app to seal
 * anything, the persistence needs it in order to keep a secret. Here it sits among the use cases
 * because this repo's rule is "{@code port.in} to call, {@code port.out} to implement" and another
 * feature CALLS it (see CLAUDE.md, Structure). Two defensible readings of the same seam, so the
 * rule is named here with its finding rather than switched off quietly, and
 * {@link ArchitectureTest} keeps the boundary this app actually means.
 */
@AnalyzeClasses(packages = "sk.drabikp.bzscraper", importOptions = ImportOption.DoNotIncludeTests.class)
class JMoleculesModelTest {

    /** An entity is part of ONE aggregate — a {@code Membership} is the roster's, nobody else's. */
    @ArchTest
    static final ArchRule an_entity_belongs_to_one_aggregate =
            JMoleculesDddRules.entitiesShouldBeDeclaredForUseInSameAggregate();

    /**
     * One aggregate names another by its identity, never by holding it. Easy here — this mapping has
     * no associations at all — and this is what keeps it that way.
     */
    @ArchTest
    static final ArchRule aggregates_name_each_other_by_identity =
            JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation();

    /** Anything with a lifecycle says what identifies it ({@code @Identity}), composite keys included. */
    @ArchTest
    static final ArchRule every_aggregate_and_entity_says_what_identifies_it =
            JMoleculesDddRules.annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier();

    /** A value object is made of values: it never reaches into something with an identity. */
    @ArchTest
    static final ArchRule a_value_object_holds_values =
            JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables();
}
