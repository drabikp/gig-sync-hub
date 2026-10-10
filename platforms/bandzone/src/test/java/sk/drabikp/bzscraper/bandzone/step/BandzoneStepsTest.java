package sk.drabikp.bzscraper.bandzone.step;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BandzoneStepsTest {

    private final BandzonePortalClient portalClient = mock(BandzonePortalClient.class);
    private final BandzoneSession session = mock(BandzoneSession.class);
    private final Gig gig = TestGigs.gig("Fest", "Klub 007");

    @Test
    void create_makes_the_concert_and_completes_it_through_the_edit_form() throws Exception {
        when(portalClient.openSession(BandId.FIRST)).thenReturn(session);
        when(session.createGig(gig)).thenReturn("563400");

        List<StepOutcome> outcomes = new BandzoneFormCreate(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, null, gig)));

        assertThat(outcomes).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(StepOutcome.Kind.DONE);
            assertThat(o.ref()).isEqualTo("563400");
        });
        verify(session).updateGig("563400", gig);
        verify(session).close();
    }

    @Test
    void a_created_concert_counts_as_created_even_when_completing_it_fails() throws Exception {
        when(portalClient.openSession(BandId.FIRST)).thenReturn(session);
        when(session.createGig(gig)).thenReturn("563400");
        doThrow(BandzonePortalException.temporary("poster upload timed out")).when(session).updateGig(any(), any());

        StepOutcome outcome = new BandzoneFormCreate(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, null, gig))).getFirst();

        assertThat(outcome.ref()).isEqualTo("563400");
        assertThat(outcome.note()).contains("not all details were saved", "Re-sync");
    }

    @Test
    void a_town_bandzone_doesnt_know_needs_the_user_and_a_timeout_is_tried_again() throws Exception {
        when(portalClient.openSession(BandId.FIRST)).thenReturn(session);
        when(session.createGig(gig))
                .thenThrow(BandzonePortalException.needsUser("Bandzone doesn't know the town X — correct the city"))
                .thenThrow(BandzonePortalException.temporary("timeout"));
        BandzoneFormCreate create = new BandzoneFormCreate(portalClient);

        assertThat(create.run(List.of(new SyncStep.Item(BandId.FIRST, null, gig))).getFirst().kind())
                .isEqualTo(StepOutcome.Kind.FAILED_FOR_GOOD);
        assertThat(create.run(List.of(new SyncStep.Item(BandId.FIRST, null, gig))).getFirst().kind()).isEqualTo(StepOutcome.Kind.FAILED);
    }

    @Test
    void cancel_and_remove_drive_the_delete_tab_and_a_concert_already_gone_counts_as_removed() throws Exception {
        when(portalClient.openSession(BandId.FIRST)).thenReturn(session);
        when(session.deleteGig("101")).thenReturn(true);
        when(session.deleteGig("102")).thenReturn(false);

        assertThat(new BandzoneCancel(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, "100", gig))))
                .extracting(StepOutcome::kind).containsExactly(StepOutcome.Kind.DONE);
        List<StepOutcome> removed = new BandzoneRemove(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, "101", null)));
        List<StepOutcome> gone = new BandzoneRemove(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, "102", null)));

        verify(session).cancelGig("100");
        assertThat(removed).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(StepOutcome.Kind.DONE);
            assertThat(o.note()).isNull();
        });
        assertThat(gone).singleElement().satisfies(o -> {
            assertThat(o.kind()).isEqualTo(StepOutcome.Kind.DONE);
            assertThat(o.note()).contains("already removed");
        });
    }

    @Test
    void a_created_concert_stays_created_even_when_closing_the_session_throws() throws Exception {
        when(portalClient.openSession(BandId.FIRST)).thenReturn(session);
        when(session.createGig(gig)).thenReturn("563400");
        doThrow(new IllegalStateException("browser gone")).when(session).close();

        StepOutcome outcome = new BandzoneFormCreate(portalClient).run(List.of(new SyncStep.Item(BandId.FIRST, null, gig))).getFirst();

        assertThat(outcome.kind()).isEqualTo(StepOutcome.Kind.DONE);
        assertThat(outcome.ref()).isEqualTo("563400");
    }
}
