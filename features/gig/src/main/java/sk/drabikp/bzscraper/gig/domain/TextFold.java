package sk.drabikp.bzscraper.gig.domain;

import org.jmolecules.ddd.annotation.Service;

import java.text.Normalizer;
import java.util.Locale;

/** How names and texts from different sources are compared: lower case, no accents, single spaces. */
@Service
public final class TextFold {

    private TextFold() {
    }

    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace(' ', ' ').replaceAll("\\s+", " ").strip();
    }
}
