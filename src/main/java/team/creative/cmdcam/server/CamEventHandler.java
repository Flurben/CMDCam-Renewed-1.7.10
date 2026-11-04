package team.creative.cmdcam.server;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.packet.CamPacket;

public class CamEventHandler {

    /** Tells the client saved scenes are handled by this server. */
    @SubscribeEvent
    public void onPlayerConnect(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP)
            CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.CONNECT), (EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CamPacket.runServerTasks();
            CMDCamServer.updateLocalOwner();
        }
    }

}
