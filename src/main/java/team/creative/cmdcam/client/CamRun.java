package team.creative.cmdcam.client;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.StagePlan;
import team.creative.cmdcam.common.scene.timer.RealTimeTimer;
import team.creative.cmdcam.common.scene.timer.RunTimer;
import team.creative.cmdcam.common.scene.timer.TickerTimer;

/** A playing scene. */
@SideOnly(Side.CLIENT)
public class CamRun {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final CamScene scene;
    public final CamMode mode;
    private final World world;
    private final List<CamRunStage> stages = new ArrayList<>();
    private final RunTimer timer;

    private int currentStage = 0;
    private boolean userPaused = false;
    private boolean gamePaused = false;
    private boolean finished = false;
    private long lastFrame = System.nanoTime();

    public CamRun(World world, CamScene scene) {
        this.world = world;
        this.scene = scene;
        this.mode = scene.outside() ? new CamMode.Outside(scene) : new CamMode.Default(scene);
        this.timer = CamScene.TIMER_TICKS.equals(scene.timer) ? new TickerTimer() : new RealTimeTimer();

        CamPoint from = null;
        if (scene.smoothBeginning) {
            from = CMDCamClient.createLocalPoint();
            // Without a target the view of a following scene cannot be expressed, it starts at the first point instead
            if (scene.posTarget != null) {
                Vec3d target = scene.posTarget.position(world, CMDCamClient.partialTicks());
                if (target != null)
                    from.sub(target);
                else
                    from = null;
            }
        }

        for (StagePlan.Entry entry : StagePlan.create(scene.points, scene.interpolation, scene.pitchMode, scene.duration, scene.loop, from))
            stages.add(new CamRunStage(scene, entry));

        mode.started();
    }

    public World world() {
        return world;
    }

    public boolean done() {
        return finished || stages.isEmpty();
    }

    public void gameTick() {
        timer.tick(playing());
    }

    /** Called at the start of each frame. */
    public void renderTick(float partialTicks) {
        if (done())
            return;

        // The clock must not run on behind the pause menu, otherwise the camera jumps after resuming
        boolean paused = mc.isGamePaused();
        if (paused != gamePaused) {
            gamePaused = paused;
            if (!userPaused) {
                if (paused)
                    timer.pause();
                else
                    timer.resume();
            }
        }

        long now = System.nanoTime();
        double frameMillis = (now - lastFrame) / 1000000D;
        lastFrame = now;

        scene.bind(world, partialTicks);

        CamRunStage stage = stages.get(currentStage);
        if (!stage.hasStarted())
            stage.start(mode.currentView(partialTicks));

        long time = timer.position(playing(), partialTicks);
        if (time >= stage.duration) {
            timer.stageCompleted(stage.duration);
            if (!stage.completeIteration()) {
                currentStage++;
                if (currentStage >= stages.size()) {
                    finished = true;
                    return;
                }
                stage = stages.get(currentStage);
                stage.start(mode.currentView(partialTicks));
            }
            time = timer.position(playing(), partialTicks);
        }

        mode.process(stage.calculatePoint(world, time, partialTicks, playing() ? frameMillis : 0));
    }

    public boolean playing() {
        return !userPaused && !gamePaused;
    }

    public void pause() {
        if (userPaused)
            return;
        userPaused = true;
        if (!gamePaused)
            timer.pause();
    }

    public void resume() {
        if (!userPaused)
            return;
        userPaused = false;
        if (!gamePaused)
            timer.resume();
    }

    public void togglePause() {
        if (userPaused)
            resume();
        else
            pause();
    }

    public void finish() {
        finished = true;
        mode.finished();
    }

}
