package team.creative.cmdcam.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The view entity of outside mode, never added to the world. 1.7.10 requires a living entity as the view entity. EntityRenderer places the camera at posY - (yOffset - 1.62), so with
 * this yOffset posY is exactly the camera's position, the same way it is for the local player.
 */
@SideOnly(Side.CLIENT)
public class CameraEntity extends EntityLivingBase {

    private static final float EYE_HEIGHT = 1.62F;

    public CameraEntity(World world) {
        super(world);
        this.yOffset = EYE_HEIGHT;
        this.noClip = true;
    }

    @Override
    public float getEyeHeight() {
        return EYE_HEIGHT;
    }

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[0];
    }

}
