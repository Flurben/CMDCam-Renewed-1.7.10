package team.creative.cmdcam.common.scene.timer;

/** Follows the wall clock, which is the smoothest for watching and screen recording. */
public class RealTimeTimer extends RunTimer {

    public interface Clock {

        long millis();
    }

    private final Clock clock;
    private long lastResumed;
    private long timePlayed;
    private boolean paused;

    public RealTimeTimer() {
        this(new Clock() {
            @Override
            public long millis() {
                return System.currentTimeMillis();
            }
        });
    }

    public RealTimeTimer(Clock clock) {
        this.clock = clock;
        this.lastResumed = clock.millis();
    }

    @Override
    public long position(boolean running, float partialTick) {
        if (paused)
            return timePlayed;
        return timePlayed + (clock.millis() - lastResumed);
    }

    @Override
    public void pause() {
        if (paused)
            return;
        timePlayed += clock.millis() - lastResumed;
        paused = true;
    }

    @Override
    public void resume() {
        if (!paused)
            return;
        lastResumed = clock.millis();
        paused = false;
    }

    @Override
    public void tick(boolean running) {}

    @Override
    public void stageCompleted(long stageDuration) {
        long overshoot = Math.max(0, position(true, 0) - stageDuration);
        timePlayed = overshoot;
        lastResumed = clock.millis();
    }

}
