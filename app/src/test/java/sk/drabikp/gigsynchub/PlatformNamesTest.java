package sk.drabikp.gigsynchub;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The features are platform-agnostic in their text too: no class, string or comment of a
 * feature names a platform (what a platform is like comes from its traits). ArchUnit sees only
 * dependencies between classes, so the sources are read here.
 */
class PlatformNamesTest {

    private static final Pattern PLATFORM = Pattern.compile("(?i)bandzone|bandsintown");

    @Test
    void no_feature_names_a_platform() throws IOException {
        Path features = repositoryRoot().resolve("features");
        List<String> named;
        try (Stream<Path> files = Files.walk(features)) {
            named = files.filter(f -> f.toString().endsWith(".java") && f.toString().contains("/src/main/"))
                    .flatMap(PlatformNamesTest::linesNamingAPlatform).toList();
        }
        assertThat(named).isEmpty();
    }

    private static Stream<String> linesNamingAPlatform(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return Stream.iterate(0, i -> i < lines.size(), i -> i + 1)
                    .filter(i -> PLATFORM.matcher(lines.get(i)).find())
                    .map(i -> file.getFileName() + ":" + (i + 1) + ": " + lines.get(i).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The tests run in app/; the features are beside it. */
    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("features"))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("the repository root (with features/)").isNotNull();
        return dir;
    }
}
