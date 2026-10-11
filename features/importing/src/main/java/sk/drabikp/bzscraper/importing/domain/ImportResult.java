package sk.drabikp.bzscraper.importing.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/** What applying an import did. {@code linked} counts platform events tied to catalog gigs. */
@ValueObject
public record ImportResult(int added, int updated, int linked, int skipped) {
}
