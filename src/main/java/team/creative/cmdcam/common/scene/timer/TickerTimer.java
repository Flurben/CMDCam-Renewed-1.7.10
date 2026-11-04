package team.creative.cmdcam.common.scene.timer;

/** Follows game ticks. Stays in sync with the world even if the game lags, for example while recording frame by frame. */
public class TickerTimer extends RunTimer {

    private static final int TICK_MILLIS = 50;

    private long ticks = 0;

    @Override
    public long position(boolean running, float partialTick) {
        return Math.max(0, (long) ((ticks - 1 + partialTick) * TICK_MILLIS));
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void tick(boolean running) {
        if (running)
            ticks++;
    }

    @Override
    public void stageCompleted(long stageDuration) {
        ticks = Math.max(0, ticks - stageDuration / TICK_MILLIS);
    }

}
