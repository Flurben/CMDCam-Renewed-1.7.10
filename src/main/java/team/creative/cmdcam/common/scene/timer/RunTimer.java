package team.creative.cmdcam.common.scene.timer;

public abstract class RunTimer {

    /** @return the position in the current stage in milliseconds */
    public abstract long position(boolean running, float partialTick);

    public abstract void pause();

    public abstract void resume();

    /** Called once per unpaused game tick. */
    public abstract void tick(boolean running);

    /** Called when the stage is over. Time past the end of the stage carries over, so long scenes do not drift. */
    public abstract void stageCompleted(long stageDuration);

}
