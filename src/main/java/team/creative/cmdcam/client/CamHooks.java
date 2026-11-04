package team.creative.cmdcam.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/** Called by the code {@link team.creative.cmdcam.asm.CamTransformer} adds to EntityRenderer. */
@SideOnly(Side.CLIENT)
public final class CamHooks {

    private static EntityLivingBase savedCamera;

    private CamHooks() {}

    /** @param useFovSetting false for the hand, which should not be zoomed */
    public static float modifyFov(float vanilla, boolean useFovSetting) {
        if (!useFovSetting)
            return vanilla;
        return CamEventHandlerClient.modifyFov(vanilla);
    }

    public static void mouseOverBefore() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && CMDCamClient.isPlayingOutside()) {
            savedCamera = mc.renderViewEntity;
            mc.renderViewEntity = mc.thePlayer;
        }
    }

    public static void mouseOverAfter() {
        if (savedCamera != null) {
            Minecraft.getMinecraft().renderViewEntity = savedCamera;
            savedCamera = null;
        }
    }

}
