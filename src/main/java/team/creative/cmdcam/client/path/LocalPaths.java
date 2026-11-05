package team.creative.cmdcam.client.path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.path.PathNames;

/**
 * The cmdcam-paths folder of the game: one file per path, named after the path. These belong to the client and not to a world, so they work in all worlds and on all servers. The
 * folder is read again whenever it is needed, people are invited to put files in there or take them out.
 */
@SideOnly(Side.CLIENT)
public final class LocalPaths {

    public static final String FOLDER_NAME = "cmdcam-paths";
    private static final String EXTENSION = ".nbt";
    private static final String LEGACY_FILE = CMDCam.MODID + "_scenes.dat";

    private static final List<SavedPath> PATHS = new ArrayList<>();
    private static File folder;
    private static File configFolder;
    private static int unreadable;

    private LocalPaths() {}

    public static void init(File gameFolder, File config) {
        folder = new File(gameFolder, FOLDER_NAME);
        configFolder = config;
        if (!folder.isDirectory() && !folder.mkdirs())
            CMDCam.LOGGER.error("Could not create {}", folder);
    }

    public static File folder() {
        return folder;
    }

    public static void refresh() {
        PATHS.clear();
        unreadable = 0;
        File[] files = folder.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            String fileName = file.getName();
            if (!file.isFile() || !fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                continue;
            SavedPath path = read(fileName.substring(0, fileName.length() - EXTENSION.length()), file);
            if (path != null)
                PATHS.add(path);
            else
                unreadable++;
        }
    }

    private static SavedPath read(String name, File file) {
        try (InputStream input = new FileInputStream(file)) {
            SavedPath path = SavedPath.load(name, CompressedStreamTools.readCompressed(input));
            if (path.isSane())
                return path;
            CMDCam.LOGGER.warn("Ignored {}, it contains invalid values", file);
        } catch (IOException | RuntimeException e) {
            CMDCam.LOGGER.warn("Could not read " + file, e);
        }
        return null;
    }

    /** @return the number of files in the folder which are not usable paths, they are left alone */
    public static int unreadable() {
        return unreadable;
    }

    public static List<SavedPath> all() {
        refresh();
        return new ArrayList<>(PATHS);
    }

    public static List<String> names() {
        refresh();
        List<String> names = new ArrayList<>();
        for (SavedPath path : PATHS)
            names.add(path.name);
        return names;
    }

    /** @return the path with this name, also if only the case differs: for some systems those are the same file */
    public static SavedPath find(String name) {
        refresh();
        SavedPath similar = null;
        for (SavedPath path : PATHS) {
            if (path.name.equals(name))
                return path;
            if (path.name.equalsIgnoreCase(name))
                similar = path;
        }
        return similar;
    }

    private static File fileOf(String name) {
        return new File(folder, name + EXTENSION);
    }

    /** Written to a separate file first, a crash in the middle cannot destroy what is there. */
    public static boolean write(SavedPath path) {
        if (!PathNames.isValid(path.name))
            return false;
        File temporary = new File(folder, path.name + EXTENSION + ".tmp");
        try {
            if (!folder.isDirectory() && !folder.mkdirs())
                throw new IOException("Could not create " + folder);
            try (OutputStream output = new FileOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(path.save(), output);
            }
            Files.move(temporary.toPath(), fileOf(path.name).toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException e) {
            CMDCam.LOGGER.error("Could not write " + path.name, e);
            temporary.delete();
            return false;
        }
    }

    public static boolean delete(String name) {
        SavedPath path = find(name);
        if (path == null)
            return false;
        try {
            Files.delete(fileOf(path.name).toPath());
            return true;
        } catch (IOException e) {
            CMDCam.LOGGER.error("Could not delete " + name, e);
            return false;
        }
    }

    /** @return false if there is no such path, the new name is not usable or belongs to another path */
    public static boolean rename(String name, String newName) {
        SavedPath path = find(name);
        if (path == null || !PathNames.isValid(newName))
            return false;
        SavedPath other = find(newName);
        if (other != null && !other.name.equalsIgnoreCase(path.name))
            return false;
        try {
            // A move to a name only differing in case does nothing on Windows, it considers both the same file. The way over another name works everywhere
            File between = new File(folder, path.name + EXTENSION + ".renaming");
            Files.move(fileOf(path.name).toPath(), between.toPath());
            Files.move(between.toPath(), fileOf(newName).toPath());
            return true;
        } catch (IOException e) {
            CMDCam.LOGGER.error("Could not rename " + name + " to " + newName, e);
            return false;
        }
    }

    /**
     * Before the folder existed the client kept its scenes in one file in the config folder. Only the file's name changes afterwards, nothing is deleted. What cannot be taken over
     * keeps the file where it is, so the next start tries again.
     *
     * @return the number of scenes taken over
     */
    @SuppressWarnings("unchecked")
    public static int migrateLegacyFile(String creator) {
        File legacy = new File(configFolder, LEGACY_FILE);
        if (!legacy.isFile())
            return 0;
        int moved = 0;
        boolean complete = true;
        try (InputStream input = new FileInputStream(legacy)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(input);
            List<String> taken = names();
            for (String key : (Set<String>) nbt.func_150296_c()) {
                SavedPath path = SavedPath.load(PathNames.free(PathNames.sanitize(key), taken), nbt.getCompoundTag(key));
                path.creator = creator;
                path.savedAt = legacy.lastModified();
                if (write(path)) {
                    taken.add(path.name);
                    moved++;
                } else
                    complete = false;
            }
        } catch (IOException | RuntimeException e) {
            CMDCam.LOGGER.error("Could not read the scenes of the older version from " + legacy, e);
            return moved;
        }
        if (complete && !legacy.renameTo(new File(configFolder, LEGACY_FILE + ".migrated")))
            CMDCam.LOGGER.warn("Could not rename {}, its scenes will be copied again on the next start", legacy);
        return moved;
    }

}
