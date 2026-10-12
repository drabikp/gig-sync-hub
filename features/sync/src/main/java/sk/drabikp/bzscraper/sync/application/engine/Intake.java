package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;

/**
 * What a step is to do for one run — or why it isn't this step's to do ({@link RunIntake}, asked
 * by the {@link WorkflowEngine} just before it uses a step). It is an answer and nothing more:
 * the engine writes down what follows from it, because what follows is the run's place in its
 * workflow and the engine is what knows the workflow.
 */
sealed interface Intake {

    /** This step is to do this: the band's gig and the platform's id for its copy. */
    record Take(SyncStep.Item item) implements Intake {
    }

    /** There is nothing left to do — the gig was deleted, cancelled or published meanwhile. */
    record Moot(String why) implements Intake {
    }

    /** Not this step's to do: it doesn't take this gig, so the next way is to be tried. */
    record PassedOn(String why) implements Intake {
    }

    /** Nothing here can do it; the user is told this much and decides. */
    record ByHand(String message) implements Intake {
    }
}
