package sk.drabikp.gigsynchub.bandsintown.csv;

import com.opencsv.CSVWriter;
import sk.drabikp.gigsynchub.bandsintown.portal.BitSession.EventEdit;
import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Gig;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bandsintown's bulk-upload CSV. The 28 template columns (starred = required) are what
 * the artist portal's template and our download export contain; the portal's own
 * export appends {@code Event Id} and {@code Status}, and uploading a row WITH an
 * Event Id updates that event in place instead of creating a new one. A row is filled by
 * column ({@link Column}); the file lists the columns in their order, a column a row leaves
 * out empty.
 *
 * <p>Start/end are the band's slot when the gig has one, else the event's (Bandzone gets the
 * whole event; Bandsintown is about when the artist plays).
 *
 * <p>"Do Not Announce = Y" makes Bandsintown treat the event as already announced, so
 * publishing it sends followers no notification.
 */
public final class BandsintownCsv {

    /** The file's columns, in their order: the template's 28, then the two of the portal's own export. */
    enum Column {
        ARTIST_NAME("Artist Name"),
        VENUE("Venue*"),
        COUNTRY("Country*"),
        ADDRESS("Address"),
        CITY("City*"),
        REGION("Region*"),
        POSTAL_CODE("Postal Code"),
        TIMEZONE("Timezone*"),
        START_DATE("Start Date* (yyyy-mm-dd)"),
        START_TIME("Start Time* (HH:MM)"),
        END_DATE("End Date"),
        END_TIME("End Time"),
        STREAMING_LINK("Streaming Link"),
        TICKET_LINK("Ticket Link"),
        TICKET_TYPE("Ticket Type"),
        TICKET_LINK_2("Ticket Link 2"),
        TICKET_TYPE_2("Ticket Type 2"),
        ON_SALE_DATE("On-Sale Date"),
        ON_SALE_TIME("On-Sale Time"),
        LINEUP("Lineup"),
        EVENT_NAME("Event Name"),
        EVENT_DISPLAY_FORMAT("Event Display Format"),
        DESCRIPTION("Description"),
        SCHEDULE_DATE("Schedule Date"),
        SCHEDULE_TIME("Schedule Time"),
        DO_NOT_ANNOUNCE("Do Not Announce"),
        SETLIST("Setlist"),
        EVENT_IMAGE("Event Image"),
        EVENT_ID("Event Id"),
        STATUS("Status");

        /** The template's columns: everything but the export's event id and status. */
        static final List<Column> TEMPLATE = List.copyOf(EnumSet.range(ARTIST_NAME, EVENT_IMAGE));
        /** Every column: the rows of an update carry the event id and status. */
        static final List<Column> WITH_EVENT_ID = List.of(values());

        final String header;

        Column(String header) {
            this.header = header;
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private BandsintownCsv() {
    }

    /** The plain template, as the user downloads it for a manual import. */
    public static String template(List<Gig> gigs, String artistName) {
        return write(Column.TEMPLATE, gigs.stream().map(gig -> notFormulas(row(gig, artistName))).toList());
    }

    /** Every value of a row as itself — for the file a person downloads and opens. */
    private static Map<Column, String> notFormulas(Map<Column, String> row) {
        Map<Column, String> shown = new LinkedHashMap<>(row);
        shown.replaceAll((column, value) -> notAFormula(value));
        return shown;
    }

    /** New events to upload; {@code announce=false} marks every row "Do Not Announce". */
    public static String newEvents(List<Gig> gigs, String artistName, boolean announce) {
        return write(Column.TEMPLATE, gigs.stream().map(gig -> {
            Map<Column, String> row = row(gig, artistName);
            row.put(Column.DO_NOT_ANNOUNCE, announce ? "" : "Y");
            return row;
        }).toList());
    }

    /**
     * Edits of existing, published events: one row per edit, in order, with its Bandsintown
     * event id. Never announced: an edit must not re-notify followers.
     */
    public static String updates(List<EventEdit> edits, String artistName) {
        return write(Column.WITH_EVENT_ID, edits.stream().map(edit -> {
            Map<Column, String> row = row(edit.gig(), artistName);
            row.put(Column.DO_NOT_ANNOUNCE, "Y");
            row.put(Column.EVENT_ID, edit.eventId());
            row.put(Column.STATUS, "PUBLISHED");
            return row;
        }).toList());
    }

    private static Map<Column, String> row(Gig gig, String artistName) {
        // Bandsintown lists when the ARTIST plays: the band's slot when the gig has one
        // (a festival over several days), else the event itself.
        ZonedDateTime start = gig.schedule().showStart();    // always present (invariant)
        ZonedDateTime end = gig.schedule().showEnd();
        String ticketLink = nullToEmpty(gig.ticketUrl());
        Map<Column, String> row = new EnumMap<>(Column.class);
        row.put(Column.ARTIST_NAME, nullToEmpty(artistName));
        row.put(Column.VENUE, gig.location().displayVenue());
        row.put(Column.COUNTRY, gig.location().countryName());
        // Bandsintown finds the place from these texts; with only a town's name it guessed the
        // wrong one of several (Hranice near Cheb, a Hořice near Brno) — street and postal code
        // tell them apart, and the region a village, whose name repeats more than a town's (and
        // Photon often has no postal code for it). The gig's own coordinates cannot be sent here at
        // all, so a wrong guess is put right afterwards, by the one-event form edit, which can
        // (HttpBitSession.placedWhereTheGigIs)
        row.put(Column.ADDRESS, addressPart(gig, Address::street));
        row.put(Column.CITY, gig.location().city());
        row.put(Column.REGION, addressPart(gig, Address::region));
        row.put(Column.POSTAL_CODE, addressPart(gig, Address::postalCode));
        row.put(Column.TIMEZONE, gig.location().timezone());
        row.put(Column.START_DATE, start.format(DATE));
        row.put(Column.START_TIME, start.format(TIME));
        row.put(Column.END_DATE, end != null ? end.format(DATE) : "");
        row.put(Column.END_TIME, end != null ? end.format(TIME) : "");
        row.put(Column.TICKET_LINK, ticketLink);
        row.put(Column.TICKET_TYPE, ticketLink.isBlank() ? "" : "Tickets");
        row.put(Column.LINEUP, String.join(",", gig.lineup()));
        row.put(Column.EVENT_NAME, gig.title());
        row.put(Column.DESCRIPTION, nullToEmpty(gig.description()));
        row.put(Column.EVENT_IMAGE, nullToEmpty(gig.posterImageUrl()));
        return row;
    }

    /** One part of the gig's address, or empty when it has none. */
    private static String addressPart(Gig gig, Function<Address, String> part) {
        Address address = gig.location().address();
        return address == null ? "" : nullToEmpty(part.apply(address));
    }

    /**
     * A value a spreadsheet would run rather than show. Excel and its like treat a cell starting
     * with {@code = + - @} or a control character as a formula, so a gig titled
     * {@code =HYPERLINK(...)} becomes one when a colleague opens the band's downloaded CSV. A
     * leading apostrophe is the standard answer and shows the text as it was typed.
     *
     * <p>Only on the DOWNLOAD path. The rows that go to Bandsintown's own bulk upload are read by
     * the portal and not by a spreadsheet, and an apostrophe in them would end up in the published
     * event's title.
     */
    static String notAFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

    /** The header and the rows, each value under its column; a column a row leaves out is empty. */
    private static String write(List<Column> columns, List<Map<Column, String>> rows) {
        StringWriter writer = new StringWriter();
        try (CSVWriter csv = new CSVWriter(writer)) {
            csv.writeNext(header(columns));
            rows.forEach(row -> csv.writeNext(columns.stream()
                    .map(column -> row.containsKey(column) ? row.get(column) : "")
                    .toArray(String[]::new)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    /** The header of {@code columns}, as the file writes it. */
    static String[] header(List<Column> columns) {
        return columns.stream().map(column -> column.header).toArray(String[]::new);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
