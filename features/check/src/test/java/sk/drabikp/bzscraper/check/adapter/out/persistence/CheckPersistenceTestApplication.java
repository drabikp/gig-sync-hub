package sk.drabikp.bzscraper.check.adapter.out.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestComponent;

/** Boots only this module's persistence for its tests (the schema comes from the gig module's migrations). */
@SpringBootApplication
// A test application of another module is on the classpath of the modules that use its test-jar: packaged,
// the jar leaves it out; in a reactor build (./mvnw test) its test-classes folder is used whole. @TestComponent
// keeps the other modules' context scans from picking it up there.
@TestComponent
class CheckPersistenceTestApplication {
}
