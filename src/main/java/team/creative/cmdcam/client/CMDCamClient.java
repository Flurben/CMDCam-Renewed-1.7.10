package team.creative.cmdcam.client;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.target.CamTarget;
import team.creative.cmdcam.common.util.EntityUtil;

@SideOnly(Side.CLIENT)
public class CMDCamClient {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static CamScene scene;
    private static CamRun run;
    private static boolean serverAvailable = false;
    private static CamPoint targetMarker;
    private static float partialTicks = 1;

    private static boolean hideGuiCache;
    private static int thirdPersonCache;
    private static boolean smoothCameraCache;

    public static float partialTicks() {
        return partialTicks;
    }

    public static void partialTicks(float value) {
        partialTicks = value;
    }

    public static void setServerAvailability() {
        serverAvailable = true;
    }

    public static boolean serverAvailable() {
        return serverAvailable;
    }

    /** A new connection starts with a clean state. */
    public static void reset() {
        abort();
        serverAvailable = false;
        scene = CamScene.createDefault();
        targetMarker = null;
    }

    /** @return the scene the commands change */
    public static CamScene getScene() {
        if (scene == null)
            scene = CamScene.createDefault();
        return scene;
    }

    public static CamRun run() {
        return run;
    }

    public static boolean isPlaying() {
        return run != null;
    }

    public static boolean isPlayingOutside() {
        return run != null && run.scene.outside();
    }

    /** @return true if the player is what moves along the path */
    public static boolean isPlayerCamera() {
        return run != null && !run.scene.outside();
    }

    public static List<CamPoint> getPoints() {
        return getScene().points;
    }

    public static void set(CamScene loaded) {
        getScene().set(loaded);
    }

    /** The point of view of the player. In 1.7.10 the local player's posY is the position of the eyes. */
    public static CamPoint createLocalPoint() {
        return EntityUtil.createPoint(mc.thePlayer, partialTicks, CamEventHandlerClient.roll(), CamEventHandlerClient.fovExact());
    }

    /** @return a copy of the current scene which can be played */
    public static CamScene createScene() throws SceneException {
        CamScene current = getScene();
        if (current.points.isEmpty())
            throw new SceneException("scene.create_fail");
        return current.copy();
    }

    public static void start(CamScene toPlay) {
        if (mc.thePlayer == null || mc.theWorld == null || toPlay.points.isEmpty())
            return;
        if (run != null)
            abort();

        hideGuiCache = mc.gameSettings.hideGUI;
        thirdPersonCache = mc.gameSettings.thirdPersonView;
        smoothCameraCache = mc.gameSettings.smoothCamera;

        // The player cannot be the target of itself if it is the camera
        if (!toPlay.outside()) {
            if (toPlay.lookTarget instanceof CamTarget.SelfTarget)
                toPlay.lookTarget = null;
            if (toPlay.posTarget instanceof CamTarget.SelfTarget)
                toPlay.posTarget = null;
        }

        toPlay.bind(mc.theWorld, partialTicks);
        run = new CamRun(mc.theWorld, toPlay);
        if (run.done())
            abort();
    }

    public static void pause() {
        if (run != null)
            run.pause();
    }

    public static void resume() {
        if (run != null)
            run.resume();
    }

    /** Stopping a scene the server started is up to the server. */
    public static void stop() {
        if (run != null && !run.scene.serverSynced())
            abort();
    }

    public static void stopServer() {
        abort();
    }

    private static void abort() {
        if (run == null)
            return;
        CamRun finished = run;
        run = null;
        finished.finish();
        mc.gameSettings.hideGUI = hideGuiCache;
        mc.gameSettings.thirdPersonView = thirdPersonCache;
        mc.gameSettings.smoothCamera = smoothCameraCache;
    }

    public static void gameTick() {
        if (run != null)
            run.gameTick();
    }

    /** Plays the scene at the start of a frame, after that the view does not change until it is rendered. */
    public static void renderTick(float partial) {
        if (run == null)
            return;

        // Nothing the scene relies on is there anymore: it left the world (changed dimension or disconnected) or the player died
        if (mc.thePlayer == null || mc.theWorld != run.world() || (isPlayerCamera() && (mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0))) {
            abort();
            return;
        }

        if (run.scene.hideGui)
            mc.gameSettings.hideGUI = true;
        mc.gameSettings.thirdPersonView = 0;
        if (isPlayerCamera())
            mc.gameSettings.smoothCamera = false; // Would still turn the camera with its remaining movement

        run.renderTick(partial);
        if (run != null && run.done())
            abort();
    }

    public static void setTargetMarker(CamPoint marker) {
        targetMarker = marker;
    }

    /** @return where the preview of a following scene is shown: the marker or else the target itself. Null if there is neither */
    public static Vec3d getFollowOrigin() {
        CamScene current = getScene();
        if (current.posTarget == null)
            return null;
        if (targetMarker != null)
            return targetMarker;
        if (mc.theWorld == null)
            return null;
        return current.posTarget.position(mc.theWorld, partialTicks);
    }

    public static CamPoint getTargetMarker() {
        return getScene().posTarget != null ? targetMarker : null;
    }

    public static void teleportTo(CamPoint point) {
        if (mc.thePlayer == null)
            return;
        mc.thePlayer.capabilities.isFlying = mc.thePlayer.capabilities.allowFlying;
        CamEventHandlerClient.roll((float) point.roll);
        CamEventHandlerClient.fov(point.zoom - CamEventHandlerClient.fovVanilla());
        CamMode.place(mc.thePlayer, point);
    }

}
