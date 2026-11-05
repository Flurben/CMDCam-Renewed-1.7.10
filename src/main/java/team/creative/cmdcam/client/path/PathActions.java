package team.creative.cmdcam.client.path;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.lwjgl.Sys;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Util;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.client.CommandCam;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.scene.path.PathNames;

/** What can be done with a saved path, for the commands and the screens. */
@SideOnly(Side.CLIENT)
public final class PathActions {

    /** A client cannot send a packet of 32k or more, the id and the type of packet take some of it. */
    private static final int MAX_UPLOAD_BYTES = 32000;

    private static final Minecraft mc = Minecraft.getMinecraft();

    private PathActions() {}

    public static String localPlayer() {
        return mc.getSession().getUsername();
    }

    /** The name decides here, the id of the player differs between launchers and servers without online mode. The server checks on its own with what it knows. */
    public static boolean isOwnPath(SavedPath path) {
        return path.creator.isEmpty() || path.creator.equalsIgnoreCase(localPlayer());
    }

    /** Stores it in the folder and, if the server has the mod, also on the server. The creator is this player, the server checks on its own if this player may save there. */
    public static void save(String name, CamScene scene) throws CommandException {
        if (!PathNames.isValid(name))
            throw new CommandException("path.invalid_name", name);
        SavedPath existing = LocalPaths.find(name);
        if (existing != null && !isOwnPath(existing))
            throw new CommandException("path.exists_other", existing.name, existing.creator);

        // A name only differing in case is the same file on some systems, the old spelling stays with it
        SavedPath path = new SavedPath(existing != null ? existing.name : name, scene);
        path.creator = localPlayer();
        path.savedAt = System.currentTimeMillis();
        if (existing != null)
            path.lastLoaded = existing.lastLoaded;
        if (!LocalPaths.write(path))
            throw new CommandException("path.io_fail");
        CommandCam.send(mc.thePlayer, "path.saved", path.name);

        if (CMDCamClient.serverAvailable())
            upload(name, scene);
    }

    private static void upload(String name, CamScene scene) {
        NBTTagCompound nbt = SceneNBT.save(scene);
        if (compressedSize(nbt) > MAX_UPLOAD_BYTES) {
            CommandCam.send(mc.thePlayer, "path.too_large", name);
            return;
        }
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.SET, name, nbt));
    }

    private static int compressedSize(NBTTagCompound nbt) {
        try {
            return CompressedStreamTools.compress(nbt).length;
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Asks the server first if it has the mod, {@link #loadFromFolder(String)} is next if it does not have this name. */
    public static void loadByName(String name) throws CommandException {
        if (CMDCamClient.serverAvailable())
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.GET, name, null));
        else if (!loadFromFolder(name))
            throw new CommandException("scene.load_fail", name);
    }

    public static boolean loadFromFolder(String name) {
        SavedPath path = LocalPaths.find(name);
        if (path == null)
            return false;
        apply(path, null, false);
        CommandCam.send(mc.thePlayer, "scene.load", path.name);
        return true;
    }

    /** @param moveBy added to all positions of the path, null for none */
    public static void load(PathEntry entry, Vec3d moveBy) {
        apply(entry.path, moveBy, entry.server);
        if (entry.alsoOnServer)
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.LOADED, entry.path.name, null));
        if (moveBy != null) {
            Vec3d start = CMDCamClient.getScene().startPosition();
            CommandCam.send(mc.thePlayer, "path.loaded_moved", entry.path.name, format(start.x), format(start.y), format(start.z));
        } else
            CommandCam.send(mc.thePlayer, "scene.load", entry.path.name);
    }

    private static void apply(SavedPath path, Vec3d moveBy, boolean server) {
        CamScene scene = path.scene.copy();
        if (moveBy != null)
            scene.translate(moveBy.x, moveBy.y, moveBy.z);
        CMDCamClient.set(scene);
        if (server)
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.LOADED, path.name, null));
        else {
            path.lastLoaded = System.currentTimeMillis();
            if (!LocalPaths.write(path))
                CMDCam.LOGGER.warn("Could not remember when {} was loaded", path.name);
        }
    }

    public static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /**
     * The copy always ends up in the folder, also for a path on the server: that is how to get paths of other people. It belongs to whoever made the copy.
     *
     * @return the name of the copy, null if it could not be stored
     */
    public static String duplicate(PathEntry entry) {
        Collection<String> taken = LocalPaths.names();
        SavedPath copy = new SavedPath(PathNames.free(PathNames.sanitize(entry.path.name), taken), entry.path.scene.copy());
        copy.creator = localPlayer();
        copy.savedAt = System.currentTimeMillis();
        return LocalPaths.write(copy) ? copy.name : null;
    }

    /** @return the key of the text explaining what is wrong, null if it worked or the server was asked */
    public static String rename(PathEntry entry, String newName) {
        if (!PathNames.isValid(newName))
            return "path.invalid_name";
        if (entry.server) {
            if (!entry.path.name.equals(newName) && ServerPaths.find(newName) != null)
                return "scene.exists";
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("name", newName);
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.RENAME, entry.path.name, nbt));
            return null;
        }
        SavedPath other = LocalPaths.find(newName);
        if (other != null && !other.name.equalsIgnoreCase(entry.path.name))
            return "scene.exists";
        return LocalPaths.rename(entry.path.name, newName) ? null : "path.io_fail";
    }

    /** @return false if a local file could not be deleted */
    public static boolean delete(PathEntry entry) {
        if (entry.server) {
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.DELETE, entry.path.name, null));
            return true;
        }
        return LocalPaths.delete(entry.path.name);
    }

    /** Works like the button of the resource pack screen. @return false if the system could not be told to open it */
    public static boolean openFolder() {
        File folder = LocalPaths.folder();
        folder.mkdirs();
        String path = folder.getAbsolutePath();
        Util.EnumOS system = Util.getOSType();
        try {
            if (system == Util.EnumOS.WINDOWS)
                Runtime.getRuntime().exec(new String[] { "explorer.exe", path });
            else if (system == Util.EnumOS.OSX)
                Runtime.getRuntime().exec(new String[] { "/usr/bin/open", path });
            else
                Runtime.getRuntime().exec(new String[] { "xdg-open", path });
            return true;
        } catch (IOException | RuntimeException e) {
            CMDCam.LOGGER.warn("Could not open " + path + " directly", e);
        }
        try {
            java.awt.Desktop.getDesktop().open(folder);
            return true;
        } catch (Throwable e) { // Not all systems and Java versions have this, some do not even have the class
            CMDCam.LOGGER.warn("Could not open " + path + " through the desktop", e);
        }
        try {
            Sys.openURL(folder.toURI().toString());
            return true;
        } catch (Throwable e) {
            CMDCam.LOGGER.error("Could not open " + path, e);
            return false;
        }
    }

}
