package sk.drabikp.bzscraper.check.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.application.port.in.StartPlatformCheckUseCase;
import sk.drabikp.bzscraper.gig.application.port.out.RowScope;
import sk.drabikp.bzscraper.gig.domain.BandId;

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
    private final RowScope rows;

    public BackgroundCheck(CheckPlatformsUseCase check, Executor executor) {
        this(check, executor, RowScope.NONE);
    }

    /** {@code rows}: the background check sees the band's rows only. */
    public BackgroundCheck(CheckPlatformsUseCase check, Executor executor, RowScope rows) {
        this.check = check;
        this.executor = executor;
        this.rows = rows;
    }

    @Override
    public void start(BandId band) {
        if (check.running(band)) {
            return;
        }
        executor.execute(() -> rows.as(band, () -> {
            try {
                check.check(band);
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.ERROR, "The platform check of band " + band + " failed", e);
            }
        }));
    }
}
