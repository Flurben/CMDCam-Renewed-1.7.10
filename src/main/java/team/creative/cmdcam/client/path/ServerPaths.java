package team.creative.cmdcam.client.path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.SavedPath;

/** What the client knows about the paths on the server. It stays empty if the server does not have the mod. */
@SideOnly(Side.CLIENT)
public final class ServerPaths {

    private static final long ANSWER_TIMEOUT_MILLIS = 5000;
    private static final long MIN_INTERVAL_MILLIS = 1100;

    private static List<SavedPath> paths = new ArrayList<>();
    private static final Map<String, SavedPath> INCOMING = new LinkedHashMap<>();
    private static boolean canEdit;
    private static long requestedAt;
    private static boolean waiting;
    private static int version;

    private ServerPaths() {}

    /** The server ignores requests coming too quickly and changes are sent anyway, so the list from just now is as good as a new one. */
    public static void request() {
        long now = System.currentTimeMillis();
        if (waiting() || (requestedAt > 0 && now >= requestedAt && now - requestedAt < MIN_INTERVAL_MILLIS))
            return;
        waiting = true;
        requestedAt = now;
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.LIST));
    }

    public static void entry(String name, NBTTagCompound nbt) {
        if (nbt != null)
            INCOMING.put(name, SavedPath.load(name, nbt));
    }

    public static void finish(NBTTagCompound nbt) {
        paths = new ArrayList<>(INCOMING.values());
        INCOMING.clear();
        canEdit = nbt != null && nbt.getBoolean("can_edit");
        waiting = false;
        version++;
    }

    public static void reset() {
        paths = new ArrayList<>();
        INCOMING.clear();
        canEdit = false;
        waiting = false;
        requestedAt = 0;
        version++;
    }

    public static List<SavedPath> all() {
        return paths;
    }

    public static SavedPath find(String name) {
        for (SavedPath path : paths)
            if (path.name.equals(name))
                return path;
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (SavedPath path : paths)
            names.add(path.name);
        return names;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    public static boolean waiting() {
        return waiting && System.currentTimeMillis() - requestedAt < ANSWER_TIMEOUT_MILLIS;
    }

    /** @return true if an answer was expected and never came, so an older version of the mod is probably on the server */
    public static boolean noAnswer() {
        return waiting && !waiting();
    }

    /** Changes whenever the list changes. */
    public static int version() {
        return version;
    }

}
