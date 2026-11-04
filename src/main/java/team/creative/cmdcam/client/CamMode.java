package team.creative.cmdcam.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.util.EntityUtil;

/** What moves along the path. */
@SideOnly(Side.CLIENT)
public abstract class CamMode {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    protected final CamScene scene;

    protected CamMode(CamScene scene) {
        this.scene = scene;
    }

    public abstract EntityLivingBase getCamera();

    public void started() {}

    public void finished() {
        CamEventHandlerClient.resetFOV();
        CamEventHandlerClient.resetRoll();
    }

    public CamPoint currentView(float partialTicks) {
        return EntityUtil.createPoint(getCamera(), partialTicks, CamEventHandlerClient.roll(), CamEventHandlerClient.fovExact());
    }

    public void process(CamPoint point) {
        CamEventHandlerClient.roll((float) point.roll);
        CamEventHandlerClient.fov(point.zoom);
        place(getCamera(), point);
    }

    /**
     * Puts an entity at the position of its eyes. Do not use setLocationAndAngles here, it adds yOffset to y, which is the reason the camera of the old version was above its points when
     * inside. The previous positions have to be the same as well, otherwise the view is interpolated between the old and the new position and the camera shakes.
     */
    public static void place(EntityLivingBase camera, CamPoint point) {
        float yaw = (float) point.rotationYaw;
        float pitch = (float) point.rotationPitch;

        camera.setPositionAndRotation(point.x, point.y, point.z, yaw, pitch);
        camera.prevPosX = camera.lastTickPosX = camera.posX;
        camera.prevPosY = camera.lastTickPosY = camera.posY;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
        camera.rotationYawHead = camera.prevRotationYawHead = camera.rotationYaw;
        camera.renderYawOffset = camera.prevRenderYawOffset = camera.rotationYaw;
        camera.motionX = camera.motionY = camera.motionZ = 0;
        camera.fallDistance = 0;
    }

    /** The player acts as the camera. */
    public static class Default extends CamMode {

        private boolean wasFlying;
        private CamPoint startView;

        public Default(CamScene scene) {
            super(scene);
        }

        @Override
        public EntityLivingBase getCamera() {
            return mc.thePlayer;
        }

        @Override
        public void started() {
            EntityClientPlayerMP player = mc.thePlayer;
            wasFlying = player.capabilities.isFlying;
            startView = CMDCamClient.createLocalPoint();
        }

        @Override
        public void process(CamPoint point) {
            EntityClientPlayerMP player = mc.thePlayer;
            player.capabilities.isFlying = true;
            player.hurtTime = 0; // The camera should not shake when something hits the player
            player.cameraYaw = player.prevCameraYaw = 0;
            player.cameraPitch = player.prevCameraPitch = 0;
            super.process(point);
        }

        @Override
        public void finished() {
            super.finished();
            EntityClientPlayerMP player = mc.thePlayer;
            if (player == null)
                return;
            player.fallDistance = 0;
            // Back to what it was before, the old version kept everybody flying and upstream only checks for creative mode
            player.capabilities.isFlying = wasFlying && player.capabilities.allowFlying;
            player.sendPlayerAbilities();
            if (scene.returnToStart && startView != null) {
                place(player, startView);
                CamEventHandlerClient.roll((float) startView.roll);
                CamEventHandlerClient.fov(startView.zoom - CamEventHandlerClient.fovVanilla());
            }
        }
    }

    /** The camera is a separate entity, the player can still be controlled and seen. */
    public static class Outside extends CamMode {

        private final CameraEntity camera;

        public Outside(CamScene scene) {
            super(scene);
            this.camera = new CameraEntity(mc.theWorld);
        }

        @Override
        public EntityLivingBase getCamera() {
            return camera;
        }

        @Override
        public void started() {
            place(camera, CMDCamClient.createLocalPoint());
        }

        @Override
        public void process(CamPoint point) {
            super.process(point);
            mc.renderViewEntity = camera;
        }

        @Override
        public void finished() {
            super.finished();
            mc.renderViewEntity = mc.thePlayer;
        }
    }

}
