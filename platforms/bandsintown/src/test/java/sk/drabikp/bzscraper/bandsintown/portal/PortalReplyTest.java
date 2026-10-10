package sk.drabikp.bzscraper.bandsintown.portal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortalReplyTest {

    private static PortalReply reply(long status, Map<String, Object> json) {
        return PortalReply.of(Map.of("method", "PATCH", "url", "/api/…", "status", status, "json", json, "text", "…"));
    }

    @Test
    void maps_draft_rows_to_gig_indexes() {
        PortalReply reply = reply(200, Map.of("status", "OK", "payload", Map.of("draft_events", List.of(
                Map.of("row", 2L, "id", 109006286L, "status", "DRAFT"),
                Map.of("row", 4L, "id", 109006288L, "status", "DRAFT")))));

        assertThat(reply.ok()).isTrue();
        assertThat(reply.draftIds(3)).containsExactly("109006286", null, "109006288");
        assertThat(reply.updatedIds(3)).containsExactly(null, null, null);
    }

    @Test
    void reads_updated_rows() {
        PortalReply reply = reply(200, Map.of("status", "OK", "payload", Map.of("updated_events",
                List.of(Map.of("row", 2L, "id", 109006286L, "status", "DRAFT")))));

        assertThat(reply.updatedIds(1)).containsExactly("109006286");
        assertThat(reply.draftIds(1)).containsExactly((String) null);
    }

    @Test
    void reads_a_single_event_and_the_event_list() {
        assertThat(reply(200, Map.of("status", "OK", "payload", Map.of("id", 109006286L, "status", "DELETED")))
                .event()).get().satisfies(e -> {
                    assertThat(e.id()).isEqualTo("109006286");
                    assertThat(e.deleted()).isTrue();
                });
        assertThat(reply(200, Map.of("status", "OK", "payload", List.of(Map.of("id", 1L)))).events()
                .stream().map(BitEvent::id).toList()).containsExactly("1");
    }

    @Test
    void an_error_reply_is_not_ok_and_is_described() {
        PortalReply reply = reply(401, Map.of("status", "ERROR"));

        assertThat(reply.ok()).isFalse();
        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.describe()).startsWith("HTTP 401");
    }

    @Test
    void reads_the_row_errors_bandsintown_refused_an_upload_with() {
        // live reply (2026-10-08) to an edit of an event in the past
        PortalReply refused = reply(200, Map.of("status", "ERROR",
                "error", Map.of("message", List.of(Map.of("row", 2L, "error", "INVALID_START_TIME")))));

        assertThat(refused.ok()).isFalse();
        assertThat(refused.rowErrors()).containsExactly("INVALID_START_TIME");
        assertThat(refused.rowErrorsByIndex(2)).containsExactly("INVALID_START_TIME", null);
        assertThat(reply(401, Map.of("status", "ERROR")).rowErrors()).isEmpty();
    }

    @Test
    void reads_a_paged_list() {
        PortalReply page = PortalReply.of(Map.of("method", "GET", "url", "/events?past=true", "status", 200L,
                "json", Map.of("payload", List.of()), "text", "[]", "next", "2"));

        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.write()).isFalse();
        assertThat(reply(200, Map.of()).hasNextPage()).isFalse();
    }
}
