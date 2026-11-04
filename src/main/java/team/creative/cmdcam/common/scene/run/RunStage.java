package team.creative.cmdcam.common.scene.run;

/** Duration and loop bookkeeping of one stage. */
public class RunStage {

    public final long duration;
    public final int loops;
    public int looped = 0;

    public RunStage(long duration, int loops) {
        this.duration = duration;
        this.loops = loops;
    }

    public boolean endless() {
        return loops < 0;
    }

    /** @return true if the stage has to play again, false if it is over */
    public boolean completeIteration() {
        if (endless() || looped < loops) {
            looped++;
            return true;
        }
        return false;
    }

}
