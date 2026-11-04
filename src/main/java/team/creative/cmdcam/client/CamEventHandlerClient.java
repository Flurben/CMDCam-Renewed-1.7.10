package team.creative.cmdcam.client;

import java.lang.reflect.Field;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.CMDCamConfig;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.target.CamTarget;

@SideOnly(Side.CLIENT)
public class CamEventHandlerClient {

    public interface TargetSelection {

        void accept(CamTarget target);
    }

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static final double ZOOM_STEP = 0.005;
    public static final float ROLL_STEP = 1.5F;
    public static final double MAX_FOV = 170;
    public static final double MIN_FOV = 0.1;
    public static final double FOV_RANGE_HALF = (MAX_FOV - MIN_FOV) / 2;

    public static boolean SHOW_ACTIVE_INTERPOLATION = false;

    private static double fov = 0;
    private static float roll = 0;
    private static double lastVanillaFov = CamPoint.DEFAULT_FOV;
    private static TargetSelection selectingTarget = null;

    private static Field camRollField;
    private static Field prevCamRollField;
    private static boolean rollFieldsFailed = false;

    private long lastFrame = System.nanoTime();
    private EntityLivingBase savedViewEntity;

    public static void startSelectionMode(TargetSelection selection) {
        selectingTarget = selection;
    }

    public static void resetRoll() {
        roll = 0;
    }

    public static float roll() {
        return roll;
    }

    public static void roll(float value) {
        roll = value;
    }

    public static void resetFOV() {
        fov = 0;
    }

    /** While playing it is the absolute FOV, otherwise it is added to the FOV of the game. */
    public static void fov(double value) {
        fov = value;
    }

    public static double fovVanilla() {
        return lastVanillaFov;
    }

    public static double fovExact() {
        if (CMDCamClient.isPlaying())
            return fov;
        return clampFov(lastVanillaFov + fov);
    }

    private static double clampFov(double value) {
        return Math.max(MIN_FOV, Math.min(MAX_FOV, value));
    }

    public static float modifyFov(float vanilla) {
        lastVanillaFov = vanilla;
        if (CMDCamClient.isPlaying())
            return (float) clampFov(fov);
        return fov == 0 ? vanilla : (float) clampFov(vanilla + fov);
    }

    /** Zooming gets slower towards the limits. */
    private static double calculatePointInCurve(double value) {
        value -= MIN_FOV;
        value /= FOV_RANGE_HALF;
        value = Math.max(0, Math.min(2, value));
        return Math.asin(value - 1) / Math.PI + 0.5;
    }

    private static double transformFov(double x) {
        if (x <= 0)
            return MIN_FOV;
        if (x >= 1)
            return MAX_FOV;
        return (Math.sin((x - 0.5) * Math.PI) + 1) * FOV_RANGE_HALF + MIN_FOV;
    }

    private static void applyRoll() {
        if (rollFieldsFailed)
            return;
        try {
            if (camRollField == null) {
                camRollField = ReflectionHelper.findField(EntityRenderer.class, "camRoll", "field_78495_O");
                prevCamRollField = ReflectionHelper.findField(EntityRenderer.class, "prevCamRoll", "field_78505_P");
            }
            camRollField.setFloat(mc.entityRenderer, roll);
            prevCamRollField.setFloat(mc.entityRenderer, roll);
        } catch (Exception e) {
            rollFieldsFailed = true;
            CMDCam.LOGGER.error("Camera roll is not available", e);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        ClientProxy.runTasks();
        if (mc.thePlayer != null && mc.theWorld != null && !mc.isGamePaused())
            CMDCamClient.gameTick();
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) // Used to run twice per frame, zooming and rolling at double the speed
            return;

        long now = System.nanoTime();
        double timeFactor = (now - lastFrame) / 50000000D; // In ticks
        lastFrame = now;

        CMDCamClient.partialTicks(event.renderTickTime);
        CMDCamClient.updateServerProtection();

        if (!(mc.mouseHelper instanceof CamMouseHelper))
            mc.mouseHelper = new CamMouseHelper(mc.mouseHelper);

        if (mc.thePlayer == null || mc.theWorld == null) {
            CMDCamClient.renderTick(event.renderTickTime);
            return;
        }

        if (CMDCamClient.isPlaying()) {
            CMDCamClient.renderTick(event.renderTickTime);

            // Jump pauses a scene if it would not control the player anyway
            if (CMDCamClient.isPlayerCamera() && mc.currentScreen == null && !CMDCamClient.run().scene.serverSynced())
                while (mc.gameSettings.keyBindJump.isPressed())
                    CMDCamClient.run().togglePause();
        } else if (mc.currentScreen == null && mc.inGameHasFocus && !mc.isGamePaused())
            handleCameraKeys(timeFactor);

        if (mc.currentScreen == null && mc.inGameHasFocus) {
            while (KeyHandler.startStop.isPressed()) {
                if (CMDCamClient.isPlaying())
                    CMDCamClient.stop();
                else
                    try {
                        CMDCamClient.start(CMDCamClient.createScene());
                    } catch (SceneException e) {
                        CommandCam.send(mc.thePlayer, e.getMessage());
                    }
            }

            while (KeyHandler.clearPoint.isPressed()) {
                CMDCamClient.getPoints().clear();
                CommandCam.send(mc.thePlayer, "scene.clear");
            }
        }

        applyRoll();
    }

    private void handleCameraKeys(double timeFactor) {
        double vanillaFov = lastVanillaFov;
        double x = calculatePointInCurve(vanillaFov + fov);
        double multiplier = mc.thePlayer.isSneaking() ? 5 : 1;

        if (GameSettings.isKeyDown(KeyHandler.zoomIn))
            fov = transformFov(multiplier * timeFactor * -ZOOM_STEP + x) - vanillaFov;
        if (GameSettings.isKeyDown(KeyHandler.zoomOut))
            fov = transformFov(multiplier * timeFactor * ZOOM_STEP + x) - vanillaFov;
        if (GameSettings.isKeyDown(KeyHandler.zoomCenter))
            resetFOV();

        if (GameSettings.isKeyDown(KeyHandler.rollLeft))
            roll -= timeFactor * ROLL_STEP;
        if (GameSettings.isKeyDown(KeyHandler.rollRight))
            roll += timeFactor * ROLL_STEP;
        if (GameSettings.isKeyDown(KeyHandler.rollCenter))
            resetRoll();

        while (KeyHandler.pointKey.isPressed()) {
            // Same as /cam add, relative to the followed target
            CamPoint point = CMDCamClient.createLocalPoint();
            if (CMDCamClient.getScene().posTarget != null) {
                Vec3d origin = CMDCamClient.getScene().posTarget.position(mc.theWorld, CMDCamClient.partialTicks());
                if (origin == null)
                    origin = CMDCamClient.getTargetMarker();
                if (origin == null) {
                    CommandCam.send(mc.thePlayer, "scene.follow.no_marker");
                    continue;
                }
                point.sub(origin);
            }
            CMDCamClient.getPoints().add(point);
            CommandCam.send(mc.thePlayer, "scene.add", CMDCamClient.getPoints().size());
        }
    }

    /** Clicks would use items or break blocks where the camera currently is. */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (CMDCamClient.isPlayerCamera() && mc.currentScreen == null && (event.button != -1 || event.dwheel != 0))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void worldRender(RenderWorldLastEvent event) {
        if (!CMDCamClient.isPlaying())
            PathRenderer.render();
    }

    /** The hand belongs to the player, it should not float in front of a separate camera. */
    @SubscribeEvent
    public void renderHand(RenderHandEvent event) {
        if (CMDCamClient.isPlayingOutside())
            event.setCanceled(true);
    }
    
    /** Vanilla decides on some of the player's rendering by comparing it with the view entity. */
    @SubscribeEvent
    public void renderPlayerPre(RenderPlayerEvent.Pre event) {
        if (CMDCamClient.isPlayingOutside() && event.entityPlayer == mc.thePlayer) {
            savedViewEntity = mc.renderViewEntity;
            mc.renderViewEntity = mc.thePlayer;
        }
    }

    @SubscribeEvent
    public void renderPlayerPost(RenderPlayerEvent.Post event) {
        if (savedViewEntity != null) {
            mc.renderViewEntity = savedViewEntity;
            savedViewEntity = null;
        }
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (selectingTarget == null || !event.entityPlayer.worldObj.isRemote)
            return;
        TargetSelection selection = selectingTarget;
        selectingTarget = null;
        selection.accept(new CamTarget.EntityTarget(event.target.getUniqueID()));
        CommandCam.send(event.entityPlayer, "scene.look.target.entity", event.target.getCommandSenderName());
        event.setCanceled(true); // Selecting should not open anything
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (selectingTarget == null || !event.entityPlayer.worldObj.isRemote || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
            return;
        TargetSelection selection = selectingTarget;
        selectingTarget = null;
        selection.accept(new CamTarget.BlockTarget(event.x, event.y, event.z));
        CommandCam.send(event.entityPlayer, "scene.look.target.pos", event.x + " " + event.y + " " + event.z);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onConnect(ClientConnectedToServerEvent event) {
        ClientProxy.schedule(new Runnable() {
            @Override
            public void run() {
                CMDCamClient.reset();
            }
        });
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        ClientProxy.schedule(new Runnable() {
            @Override
            public void run() {
                CMDCamClient.stopServer();
                selectingTarget = null;
            }
        });
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (CMDCam.MODID.equals(event.modID))
            CMDCamConfig.sync();
    }

}
