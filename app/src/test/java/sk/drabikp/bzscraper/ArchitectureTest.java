package sk.drabikp.bzscraper;

import com.tngtech.archunit.base.DescribedPredicate;
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
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The structure's rules (see CLAUDE.md): vertical slices — one module per feature, each with
 * domain / application / adapters (its REST API among them) — over the shared kernel {@code gig};
 * platforms plug in from outside; live updates and the app's web setup are modules of their own. Maven already forbids a module using one it doesn't
 * depend on; these rules keep each module's inside in shape and its outside narrow.
 */
@AnalyzeClasses(packages = "sk.drabikp.bzscraper", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "sk.drabikp.bzscraper";
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

    @ArchTest
    static final ArchRule domain_knows_only_the_jdk_and_other_domain_code = classes()
            .that().resideInAPackage(BASE + "..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", BASE + "..domain..");

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
     * queries across bands (the sync worker's due work) are named as such. The bands themselves
     * (accounts: the directory of every band) are the exception.
     */
    @ArchTest
    static final ArchRule a_bands_rows_are_read_and_written_by_band = noClasses()
            .that().resideInAPackage(BASE + "..adapter.out.persistence..")
            .and().resideOutsideOfPackage(ROOT + "accounts..")
            .should().callMethodWhere(DescribedPredicate.describe("a repository's every-row method",
                    (JavaMethodCall call) -> Set.of("findAll", "count", "deleteAll").contains(call.getTarget().getName())
                            && call.getTarget().getRawParameterTypes().isEmpty()
                            && call.getTarget().getOwner().isAssignableTo(Repository.class)));

    @ArchTest
    static final ArchRule the_kernels_persistence_and_wiring_are_its_own = classes()
            .that().resideInAnyPackage(ROOT + "gig.adapter..", ROOT + "gig.config..")
            .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + "gig..");

    @ArchTest
    static final ArchRule the_kernel_knows_no_feature = noClasses()
            .that().resideInAPackage(ROOT + "gig..")
            .should().dependOnClassesThat().resideInAnyPackage(packages(Stream.concat(FEATURES.stream(),
                    Stream.of("live", "web", "browser", "bandzone", "bandsintown"))));

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
                    Stream.of("web", "browser", "bandzone", "bandsintown"))));

    @ArchTest
    static final ArchRule nothing_uses_the_apps_web_setup = noClasses()
            .that().resideOutsideOfPackage(ROOT + "web..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + "web..");

    @ArchTest
    static final ArchRule the_features_know_no_platform = noClasses()
            .that().resideInAnyPackage(packages(FEATURES.stream()))
            .should().dependOnClassesThat().resideInAnyPackage(packages(Stream.of("browser", "bandzone",
                    "bandsintown")));

    @ArchTest
    static final ArchRule a_platform_knows_no_other_platform = all(PLATFORMS.stream()
            .map(p -> noClasses().that().resideInAPackage(ROOT + p + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(packages(PLATFORMS.stream()
                            .filter(other -> !other.equals(p)))))
            .toList());

    /**
     * Inside a platform: the steps, the importer and the endpoints use the portal client; the
     * client's Selenium implementation and its page objects are used by nothing else.
     */
    @ArchTest
    static final ArchRule a_platforms_browser_code_stays_behind_its_portal_client = all(PLATFORMS.stream()
            .flatMap(p -> Stream.of(
                    classes().that().resideInAPackage(ROOT + p + ".portal.selenium..")
                            .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + p + ".portal.selenium.."),
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
