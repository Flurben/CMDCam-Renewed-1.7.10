package team.creative.cmdcam.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SceneNBT;

/** Scenes saved by the client when the server does not have the mod. Upstream forgets them when the game closes, here they are kept in a file. */
@SideOnly(Side.CLIENT)
public final class ClientSceneStorage {

    private static final Map<String, CamScene> SCENES = new TreeMap<>();
    private static File file;

    private ClientSceneStorage() {}

    public static void init(File configDirectory) {
        file = new File(configDirectory, CMDCam.MODID + "_scenes.dat");
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        SCENES.clear();
        if (file == null || !file.exists())
            return;
        try (InputStream input = new FileInputStream(file)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(input);
            for (String key : (Set<String>) nbt.func_150296_c())
                SCENES.put(key, SceneNBT.load(nbt.getCompoundTag(key)));
        } catch (IOException | RuntimeException e) {
            CMDCam.LOGGER.error("Could not load saved scenes", e);
        }
    }

    private static void save() {
        if (file == null)
            return;
        NBTTagCompound nbt = new NBTTagCompound();
        for (Map.Entry<String, CamScene> entry : SCENES.entrySet())
            nbt.setTag(entry.getKey(), SceneNBT.save(entry.getValue()));

        // Written to a separate file first, a crash in the middle cannot destroy the existing scenes
        File temporary = new File(file.getPath() + ".tmp");
        try {
            try (OutputStream output = new FileOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(nbt, output);
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CMDCam.LOGGER.error("Could not save scenes", e);
        }
    }

    public static CamScene get(String name) {
        return SCENES.get(name);
    }

    public static void set(String name, CamScene scene) {
        SCENES.put(name, scene);
        save();
    }

    public static boolean remove(String name) {
        boolean removed = SCENES.remove(name) != null;
        if (removed)
            save();
        return removed;
    }

    public static Collection<String> names() {
        return SCENES.keySet();
    }

}
