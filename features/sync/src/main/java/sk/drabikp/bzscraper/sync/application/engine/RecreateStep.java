package sk.drabikp.bzscraper.sync.application.engine;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.util.List;
import java.util.Optional;

/**
 * Reactivating where the platform can't un-cancel: the cancelled copy is removed (and its
 * record forgotten at once — from then on, no record means a later Publish creates it),
 * then the gig is created again; the new id is recorded when this is settled. The copy is
 * removed the first of the platform's ways that takes the gig.
 */
@Application
public final class RecreateStep implements SyncStep {

    private final List<SyncStep> removes;
    private final SyncStep create;
    private final Transactions transactions;
    private final PublishedGigStore publishedGigStore;

    RecreateStep(List<SyncStep> removes, SyncStep create, Transactions transactions,
                 PublishedGigStore publishedGigStore) {
        this.removes = List.copyOf(removes);
        this.create = create;
        this.transactions = transactions;
        this.publishedGigStore = publishedGigStore;
    }

    /** How the registry builds this step for a platform from its remove and create steps. */
    public static StepRegistry.Recreator recreator(Transactions transactions, PublishedGigStore publishedGigStore) {
        return (removes, create) -> new RecreateStep(removes, create, transactions, publishedGigStore);
    }

    @Override
    public Platform platform() {
        return create.platform();
    }

    @Override
    public StepType type() {
        return StepType.RECREATE;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return removing(gig).isPresent() ? create.refusal(gig) : removes.getFirst().refusal(gig);
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::recreate).toList();
    }

    private Optional<SyncStep> removing(Gig gig) {
        return removes.stream().filter(remove -> remove.refusal(gig).isEmpty()).findFirst();
    }

    private StepOutcome recreate(Item item) {
        SyncStep remove = removing(item.gig()).orElse(removes.getLast());
        StepOutcome removed = remove.run(List.of(item)).getFirst();
        if (removed.kind() != StepOutcome.Kind.DONE) {
            return removed.kind() == StepOutcome.Kind.REFUSED ? removed
                    : new StepOutcome(removed.kind(), "could not remove the cancelled copy: " + removed.note(), null);
        }
        transactions.inTransaction(() -> publishedGigStore.remove(item.band(), platform(), item.gig().id()));
        StepOutcome created = create.run(List.of(new Item(item.band(), null, item.gig()))).getFirst();
        if (created.kind() == StepOutcome.Kind.DONE && created.ref() != null) {
            return created;
        }
        return StepOutcome.failedForGood("cancelled copy removed, but re-creating failed (" + created.note()
                + ") — publish the gig again");
    }
}
