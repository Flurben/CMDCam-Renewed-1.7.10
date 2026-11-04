package team.creative.cmdcam;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.world.World;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.packet.CamPacket;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    /** @return the eyes of the local player or null if there is none, like on a server */
    public Vec3d localPlayerEyes(World world, float partialTicks) {
        return null;
    }

    public void handleClientPacket(CamPacket packet) {}

}
