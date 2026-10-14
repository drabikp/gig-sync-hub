package sk.drabikp.gigsynchub;

import jakarta.persistence.EntityManager;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The structure's rules (see CLAUDE.md): vertical slices — one module per feature, each with
 * domain / application / adapters (its REST API among them) — over the shared kernel {@code gig};
 * platforms plug in from outside; live updates and the app's web setup are modules of their own. Maven already forbids a module using one it doesn't
 * depend on; these rules keep each module's inside in shape and its outside narrow.
 */
@AnalyzeClasses(packages = "sk.drabikp.gigsynchub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "sk.drabikp.gigsynchub";
    private static final String ROOT = BASE + ".";
    private static final List<String> FEATURES = List.of("accounts", "places", "sync", "catalog", "importing",
            "check", "calendar");
    private static final List<String> PLATFORMS = List.of("bandzone", "bandsintown");

    private static String[] packages(Stream<String> modules) {
        return modules.map(m -> ROOT + m + "..").toArray(String[]::new);
    }

    private static ArchRule all(List<? extends ArchRule> rules) {
        CompositeArchRule all = CompositeArchRule.of(rules.getFirst());
        for (ArchRule rule : rules.subList(1, rules.size())) {
            all = all.and(rule);
        }
        return all;
    }

    /**
     * The one third-party thing the domain may name is jMolecules: annotations with no runtime and
     * no dependencies, which say what each type IS rather than giving it anything to do. Everything
     * else a domain type could reach for would be a technology, and that is what this rule is about.
     */
    @ArchTest
    static final ArchRule domain_knows_only_the_jdk_and_other_domain_code = classes()
            .that().resideInAPackage(BASE + "..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + "..domain..",
                    "org.jmolecules.ddd.annotation..");

    @ArchTest
    static final ArchRule application_code_uses_no_framework_and_no_adapter = noClasses()
            .that().resideInAPackage(BASE + "..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "com.vaadin..",
                    "org.openqa..", "org.hibernate..", "org.slf4j..", BASE + "..adapter..", BASE + "..config..",
                    ROOT + "live..", ROOT + "web..");

    /**
     * Another module sees of a feature only what it offers: its domain and its ports (use cases
     * to call, ports to implement). Its services, adapters, pages and wiring stay its own.
     */
    @ArchTest
    static final ArchRule a_feature_is_used_only_through_its_domain_and_ports = all(FEATURES.stream()
            .map(f -> classes().that().resideInAPackage(ROOT + f + "..")
                    .and().resideOutsideOfPackages(ROOT + f + ".domain..", ROOT + f + ".application.port..")
                    .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + f + "..")
                    .as("the inside of " + f + " (not its domain or ports) is used only by " + f))
            .toList());

    /**
     * Everything a band has is read and written by band: no persistence adapter asks its Spring Data
     * repository for every row of a table ({@code findAll()}, {@code count()}, {@code deleteAll()}) —
     * queries across bands (the sync worker's due work) are named as such.
     *
     * <p>The one exception is the directory of bands itself — there is no band to scope "which bands
     * exist" by. It is named by the repository it is allowed on and not, as it was until October 2026,
     * by exempting the whole {@code accounts} module: that module also keeps the memberships, the
     * users, the audit log and every band's sealed platform secrets, so the exemption covered far
     * more than the one query that needed it.
     */
    @ArchTest
    static final ArchRule a_bands_rows_are_read_and_written_by_band = noClasses()
            .that().resideInAPackage(BASE + "..adapter.out.persistence..")
            .should().callMethodWhere(DescribedPredicate.describe("a repository's every-row method",
                    (JavaMethodCall call) -> Set.of("findAll", "count", "deleteAll").contains(call.getTarget().getName())
                            && call.getTarget().getRawParameterTypes().isEmpty()
                            && call.getTarget().getOwner().isAssignableTo(Repository.class)
                            && !call.getTarget().getOwner().getSimpleName().equals("BandJpaRepository")));

    /**
     * No query is written in one database's own SQL. Every query is JPQL (or a derived method name),
     * so the dialect writes the SQL and the schema is not tied to PostgreSQL by the queries on top of
     * it — the last native one, the roster's {@code for update}, became a {@code LockModeType} in
     * October 2026, which is why this rule can go in with NO exceptions at all. Raw JDBC is a separate
     * matter and still used where it has to be (the Flyway callbacks, which run before there is a
     * session, and the connection pool's own setup); what is banned here is hand-written SQL standing
     * in for a query.
     */
    @ArchTest
    static final ArchRule no_query_is_written_in_one_databases_own_sql = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage(BASE + "..")
            .should().beAnnotatedWith(DescribedPredicate.describe("@Query(nativeQuery = true)",
                    (JavaAnnotation<?> at) -> at.getRawType().isAssignableTo(Query.class)
                            && Boolean.TRUE.equals(at.get("nativeQuery").orElse(Boolean.FALSE))));

    @ArchTest
    static final ArchRule nothing_asks_the_entity_manager_for_a_native_query = noClasses()
            .that().resideInAPackage(BASE + "..")
            .should().callMethodWhere(DescribedPredicate.describe("createNativeQuery",
                    (JavaMethodCall call) -> call.getTarget().getName().equals("createNativeQuery")));

    /**
     * A query built by hand is outside everything that checks queries. {@code BandScopedQueriesTest}
     * enumerates Spring Data repositories, and a class that assembles JPQL for the entity manager is
     * not one, so neither the band check nor the native-SQL rules above ever look at it — the gig
     * list, the most-run query in the app, sat in exactly that blind spot.
     *
     * <p>{@code GigPages} is the one place allowed to, because a keyset page with optional filters
     * cannot be a derived method; its {@code FROM} is a fixed fragment that names the band and every
     * value is bound, and {@code GigPagingContract#a_page_holds_only_the_asking_bands_gigs} is what
     * says so about its behaviour. A second such class has to come here and be named, and bring its
     * own behavioural test with it.
     */
    @ArchTest
    static final ArchRule only_the_gig_list_builds_a_query_by_hand = noClasses()
            .that().resideInAPackage(BASE + "..")
            .and().haveSimpleNameNotEndingWith("GigPages")
            .should().callMethodWhere(DescribedPredicate.describe("EntityManager.createQuery / getCriteriaBuilder",
                    (JavaMethodCall call) -> Set.of("createQuery", "getCriteriaBuilder")
                            .contains(call.getTarget().getName())
                            && call.getTarget().getOwner().isAssignableTo(EntityManager.class)));

    @ArchTest
    static final ArchRule the_kernels_persistence_and_wiring_are_its_own = classes()
            .that().resideInAnyPackage(ROOT + "gig.adapter..", ROOT + "gig.config..")
            .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + "gig..");

    @ArchTest
    static final ArchRule the_kernel_knows_no_feature = noClasses()
            .that().resideInAPackage(ROOT + "gig..")
            .should().dependOnClassesThat().resideInAnyPackage(packages(Stream.concat(FEATURES.stream(),
                    Stream.of("live", "web", "portal", "bandzone", "bandsintown"))));

    /**
     * Live updates are server-sent events only inside the live module: everyone else tells the
     * {@code LiveUpdates} port, and the live module knows nothing but that port.
     */
    @ArchTest
    static final ArchRule live_updates_are_used_only_through_their_port = noClasses()
            .that().resideOutsideOfPackage(ROOT + "live..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + "live..");

    @ArchTest
    static final ArchRule the_live_module_knows_only_the_kernel = noClasses()
            .that().resideInAPackage(ROOT + "live..")
            .should().dependOnClassesThat().resideInAnyPackage(packages(Stream.concat(FEATURES.stream(),
                    Stream.of("web", "portal", "bandzone", "bandsintown"))));

    @ArchTest
    static final ArchRule nothing_uses_the_apps_web_setup = noClasses()
            .that().resideOutsideOfPackage(ROOT + "web..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + "web..");

    @ArchTest
    static final ArchRule the_features_know_no_platform = noClasses()
            .that().resideInAnyPackage(packages(FEATURES.stream()))
            .should().dependOnClassesThat().resideInAnyPackage(packages(Stream.of("portal", "bandzone",
                    "bandsintown")));

    @ArchTest
    static final ArchRule a_platform_knows_no_other_platform = all(PLATFORMS.stream()
            .map(p -> noClasses().that().resideInAPackage(ROOT + p + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(packages(PLATFORMS.stream()
                            .filter(other -> !other.equals(p)))))
            .toList());

    /**
     * Inside a platform: the steps, the importer and the endpoints use the portal client; how the client
     * actually talks to the site — its requests, its reading of the answers — is used by nothing else.
     */
    @ArchTest
    static final ArchRule how_a_platform_talks_to_its_site_stays_behind_its_portal_client = all(PLATFORMS.stream()
            .flatMap(p -> Stream.of(
                    classes().that().resideInAPackage(ROOT + p + ".portal.http..")
                            .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + p + ".portal.http.."),
                    noClasses().that().resideInAPackage(ROOT + p + ".portal..")
                            .should().dependOnClassesThat().resideInAnyPackage(ROOT + p + ".step..",
                                    ROOT + p + ".importing..", ROOT + p + ".scrape..", ROOT + p + ".rest..")))
            .toList());

    @ArchTest
    static final ArchRule modules_have_no_cycles = slices()
            .matching(ROOT + "(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule packages_have_no_cycles = slices()
            .matching(ROOT + "(**)").should().beFreeOfCycles();

    /**
     * The API is written as OpenAPI specs (src/main/openapi); each controller implements the
     * interface its module generates from its spec — so what it serves is what the spec says. The
     * live updates' stream is the one exception: a generated interface can't declare it.
     */
    @ArchTest
    static final ArchRule every_controller_serves_its_spec = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().doNotHaveFullyQualifiedName(ROOT + "live.LiveController")
            .should(implementAGeneratedApi())
            .andShould().haveSimpleNameEndingWith("Controller");

    /** A controller only serves requests: what it needs done is in the use cases, mappings and views. */
    @ArchTest
    static final ArchRule controllers_have_no_private_methods = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().notBePrivate();

    private static ArchCondition<JavaClass> implementAGeneratedApi() {
        return new ArchCondition<>("implement an API interface generated from its spec (an ..api package)") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                boolean generated = controller.getRawInterfaces().stream()
                        .anyMatch(i -> i.getPackageName().endsWith(".api") && i.getSimpleName().endsWith("Api"));
                events.add(new SimpleConditionEvent(controller, generated,
                        controller.getName() + (generated ? " implements" : " doesn't implement") + " a generated API"));
            }
        };
    }
}
