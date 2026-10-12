package sk.drabikp.gigsynchub.bandzone.scrape;

import java.time.ZonedDateTime;
import java.util.List;

record GigSummary(
        String bzId,
        String title,
        ZonedDateTime start,
        ZonedDateTime end,
        String url,
        String city,
        String venue,
        List<String> bands,
        String entryFee,
        boolean isCancelled
) {
    public static final class GigSummaryBuilder {
        private String bzId;
        private String title;
        private ZonedDateTime start;
        private ZonedDateTime end;
        private String url;
        private String city;
        private String venue;
        private List<String> bands;
        private String entryFee;
        private boolean isCancelled;

        private GigSummaryBuilder() {
        }

        public static GigSummaryBuilder aGigSummary() {
            return new GigSummaryBuilder();
        }

        public GigSummaryBuilder setBzId(String bzId) {
            this.bzId = bzId;
            return this;
        }

        public GigSummaryBuilder setTitle(String title) {
            this.title = title;
            return this;
        }

        public GigSummaryBuilder setStart(ZonedDateTime start) {
            this.start = start;
            return this;
        }

        public GigSummaryBuilder setEnd(ZonedDateTime end) {
            this.end = end;
            return this;
        }

        public GigSummaryBuilder setUrl(String url) {
            this.url = url;
            return this;
        }

        public GigSummaryBuilder setCity(String city) {
            this.city = city;
            return this;
        }

        public GigSummaryBuilder setVenue(String venue) {
            this.venue = venue;
            return this;
        }

        public GigSummaryBuilder setBands(List<String> bands) {
            this.bands = bands;
            return this;
        }

        public GigSummaryBuilder setEntryFee(String entryFee) {
            this.entryFee = entryFee;
            return this;
        }

        public GigSummaryBuilder setIsCancelled(boolean isCancelled) {
            this.isCancelled = isCancelled;
            return this;
        }

        public GigSummary build() {
            return new GigSummary(bzId, title, start, end, url, city, venue, bands, entryFee, isCancelled);
        }
    }
}
