package sk.drabikp.gigsynchub.gig.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.ArrayList;
import java.util.List;

/** Live updates kept in a list, each with the band it was for ({@code null} = everyone's). */
public final class TestLiveUpdates implements LiveUpdates {

    /** One update. */
    public record Update(BandId band, Topic topic) {
    }

    public final List<Update> updates = new ArrayList<>();

    @Override
    public void changed(BandId band, Topic topic) {
        updates.add(new Update(band, topic));
    }

    @Override
    public void changedEverywhere(Topic topic) {
        updates.add(new Update(null, topic));
    }

    /** The topics, in order, whoever they were for. */
    public List<Topic> topics() {
        return updates.stream().map(Update::topic).toList();
    }
}
