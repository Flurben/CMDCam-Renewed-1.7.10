package team.creative.cmdcam.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SceneNBT;

/** Saved scenes are stored once per save, so they work in all dimensions. */
public class CMDCamServer {

    private static final int PERMISSION_LEVEL = 4;
    private static final int MAX_POINTS = 4096;

    /** The player owning this singleplayer world, null on a dedicated server. */
    public static volatile EntityPlayerMP localOwner;

    public static void updateLocalOwner() {
        MinecraftServer server = MinecraftServer.getServer();
        localOwner = server != null && server.isSinglePlayer() ? server.getConfigurationManager().func_152612_a(server.getServerOwner()) : null;
    }

    public static CamSaveData data() {
        World world = MinecraftServer.getServer().worldServerForDimension(0);
        CamSaveData data = (CamSaveData) world.loadItemData(CamSaveData.class, CamSaveData.DATA_NAME);
        if (data == null) {
            data = new CamSaveData(CamSaveData.DATA_NAME);
            world.setItemData(CamSaveData.DATA_NAME, data);
        }
        return data;
    }

    public static void handlePacket(CamPacket packet, EntityPlayerMP player) {
        if (packet.action == CamPacket.Action.GET) {
            CamScene scene = data().get(packet.id);
            if (scene != null)
                CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.SET, packet.id, SceneNBT.save(scene)), player);
            else
                player.addChatMessage(new ChatComponentTranslation("scene.load_fail", packet.id));
            return;
        }

        if (packet.action == CamPacket.Action.SET) {
            if (!hasPermission(player)) {
                player.addChatMessage(new ChatComponentTranslation("scene.save_perm"));
                return;
            }
            if (packet.nbt == null || packet.id.isEmpty()) {
                player.addChatMessage(new ChatComponentTranslation("scene.save_fail"));
                return;
            }
            CamScene scene = SceneNBT.load(packet.nbt);
            if (scene.points.size() > MAX_POINTS) {
                player.addChatMessage(new ChatComponentTranslation("scene.save_fail"));
                return;
            }
            data().set(packet.id, scene);
            player.addChatMessage(new ChatComponentTranslation("scene.save", packet.id));
        }
    }

    private static boolean hasPermission(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        // The owner of a singleplayer world can save without cheats being enabled
        if (server.isSinglePlayer() && player.getCommandSenderName().equals(server.getServerOwner()))
            return true;
        return player.canCommandSenderUseCommand(PERMISSION_LEVEL, "cam-server");
    }

}
