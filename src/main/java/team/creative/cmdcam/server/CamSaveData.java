package team.creative.cmdcam.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;

public class CamSaveData extends WorldSavedData {

    public static final String DATA_NAME = CMDCam.MODID + "_Scenes";

    private final Map<String, SavedPath> paths = new LinkedHashMap<>();

    /** Called through reflection by MapStorage. */
    public CamSaveData(String name) {
        super(name);
    }

    public CamScene get(String key) {
        SavedPath path = paths.get(key);
        return path != null ? path.scene : null;
    }

    public SavedPath getPath(String key) {
        return paths.get(key);
    }

    public List<SavedPath> all() {
        return new ArrayList<>(paths.values());
    }

    public void set(SavedPath path) {
        paths.put(path.name, path);
        markDirty();
    }

    /** @return false if there is no path with the old name or the new name is taken */
    public boolean rename(String key, String newKey) {
        SavedPath path = paths.get(key);
        if (path == null || (paths.containsKey(newKey) && !key.equals(newKey)))
            return false;
        paths.remove(key);
        path.name = newKey;
        paths.put(newKey, path);
        markDirty();
        return true;
    }

    public boolean remove(String key) {
        boolean removed = paths.remove(key) != null;
        if (removed)
            markDirty();
        return removed;
    }

    public Collection<String> names() {
        return new TreeSet<>(paths.keySet());
    }

    public void clear() {
        paths.clear();
        markDirty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound nbt) {
        paths.clear();
        for (String key : (Set<String>) nbt.func_150296_c())
            paths.put(key, SavedPath.load(key, nbt.getCompoundTag(key)));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        for (Map.Entry<String, SavedPath> entry : paths.entrySet())
            nbt.setTag(entry.getKey(), entry.getValue().save());
    }

}
