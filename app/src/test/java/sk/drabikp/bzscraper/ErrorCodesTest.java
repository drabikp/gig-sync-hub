package sk.drabikp.bzscraper;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.api.ProblemCodeValue;
import sk.drabikp.bzscraper.gig.application.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every feature's error codes ({@code <Feature>ErrorCode} enums) are the spec's {@link ProblemCodeValue}s —
 * no code the API could send that the spec (and so the page's translations) doesn't know, none listed
 * that nothing sends, and no code used by two features.
 */
class ErrorCodesTest {

    private static List<String> serverCodes() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("sk.drabikp.bzscraper").stream()
                .filter(c -> c.isEnum() && c.isAssignableTo(ErrorCode.class))
                .map(JavaClass::reflect)
                .flatMap(c -> Stream.of(c.getEnumConstants()))
                .map(constant -> ((ErrorCode) constant).code())
                .toList();
    }

    @Test
    void the_server_sends_exactly_the_codes_the_spec_lists_each_from_one_feature() {
        List<String> server = serverCodes();
        List<String> spec = Arrays.stream(ProblemCodeValue.values()).map(ProblemCodeValue::getValue).toList();

        assertThat(server).doesNotHaveDuplicates();
        assertThat(server).containsExactlyInAnyOrderElementsOf(spec);
    }
}
