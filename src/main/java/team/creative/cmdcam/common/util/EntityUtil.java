package team.creative.cmdcam.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;

public final class EntityUtil {

    private static final float PLAYER_EYE_HEIGHT = 1.62F;

    private EntityUtil() {}

    /**
     * 1.7.10 has no consistent eye height: the local client player has its eye height as yOffset, so posY already is the position of the eyes. For all other players posY is the feet,
     * while their getEyeHeight() is not the real value on the client. Subtracting yOffset first gets the feet for all of them.
     */
    public static Vec3d eyePosition(Entity entity, float partialTicks) {
        double feetY = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks - entity.yOffset;
        float eyeHeight = entity instanceof EntityPlayer ? PLAYER_EYE_HEIGHT : entity.getEyeHeight();
        return new Vec3d(entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks, feetY + eyeHeight, entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks);
    }

    public static CamPoint createPoint(Entity entity, float partialTicks, double roll, double zoom) {
        Vec3d eyes = eyePosition(entity, partialTicks);
        float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
        float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
        return new CamPoint(eyes.x, eyes.y, eyes.z, yaw, pitch, roll, zoom);
    }

}
