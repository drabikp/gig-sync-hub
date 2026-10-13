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
 *
 * <p>Not only the Java. A feature's OTHER sources say as much about what it knows: its OpenAPI
 * spec, which is generated into Java the same rule would then have to cover, and the schema's own
 * migrations — and a migration's comment is where a platform's name slips in most naturally,
 * because the incident that made someone write the migration happened on one. (It had: V13's
 * header named a platform and nothing noticed, the walk being {@code .java} alone.)
 */
class PlatformNamesTest {

    private static final Pattern PLATFORM = Pattern.compile("(?i)bandzone|bandsintown");

    /** What a feature is written in: its code, the API it serves, and the schema it keeps. */
    private static final List<String> SOURCES = List.of(".java", ".yaml", ".sql");

    @Test
    void no_feature_names_a_platform() throws IOException {
        Path features = repositoryRoot().resolve("features");
        List<String> named;
        try (Stream<Path> files = Files.walk(features)) {
            named = files.filter(f -> f.toString().contains("/src/main/") && SOURCES.stream()
                            .anyMatch(f.toString()::endsWith))
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
