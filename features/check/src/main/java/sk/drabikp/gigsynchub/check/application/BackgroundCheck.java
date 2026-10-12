package sk.drabikp.gigsynchub.check.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.gigsynchub.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.gigsynchub.check.application.port.in.StartPlatformCheckUseCase;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.concurrent.Executor;

/**
 * A check the user started: runs on the {@code executor} so the request doesn't wait for the
 * platforms; the check itself announces its start and end on the live updates.
 */
@Application
public class BackgroundCheck implements StartPlatformCheckUseCase {

    private static final System.Logger log = System.getLogger(BackgroundCheck.class.getName());

    private final CheckPlatformsUseCase check;
    private final Executor executor;

    public BackgroundCheck(CheckPlatformsUseCase check, Executor executor) {
        this.check = check;
        this.executor = executor;
    }

    @Override
    public void start(BandId band) {
        if (check.running(band)) {
            return;
        }
        executor.execute(() -> {
            try {
                check.check(band);
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.ERROR, "The platform check of band " + band + " failed", e);
            }
        });
    }
}
