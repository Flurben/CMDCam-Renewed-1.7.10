package team.creative.cmdcam.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.scene.path.PathNames;

/** Saved scenes are stored once per save, so they work in all dimensions. */
public class CMDCamServer {

    /** Answering sends every saved path, a client should not be able to ask for that all the time. */
    private static final long LIST_INTERVAL_MILLIS = 1000;

    private static final Map<UUID, Long> LAST_LIST = new HashMap<>();

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
        switch (packet.action) {
            case GET:
                get(packet.id, player);
                break;
            case SET:
                set(packet, player);
                break;
            case LIST:
                Long last = LAST_LIST.get(player.getUniqueID());
                long now = System.currentTimeMillis();
                if (last == null || now - last >= LIST_INTERVAL_MILLIS || now < last) {
                    LAST_LIST.put(player.getUniqueID(), now);
                    sendList(player);
                }
                break;
            case RENAME:
                rename(packet, player);
                break;
            case DELETE:
                delete(packet.id, player);
                break;
            case LOADED:
                markLoaded(packet.id);
                break;
            default:
                break;
        }
    }

    private static void markLoaded(String name) {
        SavedPath path = data().getPath(name);
        if (path != null) {
            path.lastLoaded = System.currentTimeMillis();
            data().markDirty();
        }
    }

    private static void get(String name, EntityPlayerMP player) {
        SavedPath path = data().getPath(name);
        if (path == null) { // The client looks in its own folder next
            CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.LOAD_FAIL, name, null), player);
            return;
        }
        markLoaded(name);
        CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.SET, name, SceneNBT.save(path.scene)), player);
    }

    private static void set(CamPacket packet, EntityPlayerMP player) {
        if (!hasPermission(player)) {
            player.addChatMessage(new ChatComponentTranslation("scene.save_perm", packet.id));
            return;
        }
        if (packet.nbt == null || !PathNames.isValid(packet.id)) {
            player.addChatMessage(new ChatComponentTranslation("scene.save_fail"));
            return;
        }
        SavedPath existing = data().getPath(packet.id);
        SavedPath path = new SavedPath(packet.id, SceneNBT.load(packet.nbt));
        if (!path.isSane()) {
            player.addChatMessage(new ChatComponentTranslation("scene.save_fail"));
            return;
        }
        // Who saved it and when is up to the server, a client could claim anything
        path.creator = player.getCommandSenderName();
        path.creatorId = player.getUniqueID().toString();
        path.savedAt = System.currentTimeMillis();
        if (existing != null)
            path.lastLoaded = existing.lastLoaded;
        data().set(path);
        player.addChatMessage(new ChatComponentTranslation("scene.save_server", packet.id));
    }

    private static void rename(CamPacket packet, EntityPlayerMP player) {
        String newName = packet.nbt != null ? packet.nbt.getString("name") : "";
        if (!hasPermission(player))
            sendResult(player, false, "path.no_permission");
        else if (!PathNames.isValid(newName))
            sendResult(player, false, "path.invalid_name", newName);
        else if (data().getPath(packet.id) == null)
            sendResult(player, false, "scene.remove_fail", packet.id);
        else if (!packet.id.equals(newName) && data().getPath(newName) != null)
            sendResult(player, false, "scene.exists", newName);
        else {
            data().rename(packet.id, newName);
            sendResult(player, true, "path.renamed_server", packet.id, newName);
            sendList(player);
        }
    }

    private static void delete(String name, EntityPlayerMP player) {
        if (!hasPermission(player))
            sendResult(player, false, "path.no_permission");
        else if (!data().remove(name))
            sendResult(player, false, "scene.remove_fail", name);
        else {
            sendResult(player, true, "path.deleted_server", name);
            sendList(player);
        }
    }

    /** One packet for each path: together they could be larger than a single packet may be. */
    private static void sendList(EntityPlayerMP player) {
        for (SavedPath path : data().all())
            CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.LIST_ENTRY, path.name, path.save()), player);
        NBTTagCompound end = new NBTTagCompound();
        end.setBoolean("can_edit", hasPermission(player));
        CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.LIST_END, "", end), player);
    }

    private static void sendResult(EntityPlayerMP player, boolean success, String key, String... args) {
        CMDCam.NETWORK.sendTo(new CamPacket(CamPacket.Action.RESULT, key, CamPacket.resultData(success, args)), player);
    }

    /** Operators can do everything with the saved paths of a server, whoever made them and whatever their operator level is. */
    private static boolean hasPermission(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        // The owner of a singleplayer world can save without cheats being enabled
        if (server.isSinglePlayer() && player.getCommandSenderName().equals(server.getServerOwner()))
            return true;
        return server.getConfigurationManager().func_152596_g(player.getGameProfile());
    }

}
