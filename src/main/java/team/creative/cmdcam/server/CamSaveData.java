package team.creative.cmdcam.server;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SceneNBT;

public class CamSaveData extends WorldSavedData {

    public static final String DATA_NAME = CMDCam.MODID + "_Scenes";

    private final Map<String, CamScene> scenes = new LinkedHashMap<>();

    /** Called through reflection by MapStorage. */
    public CamSaveData(String name) {
        super(name);
    }

    public CamScene get(String key) {
        return scenes.get(key);
    }

    public void set(String key, CamScene scene) {
        scenes.put(key, scene);
        markDirty();
    }

    public boolean remove(String key) {
        boolean removed = scenes.remove(key) != null;
        if (removed)
            markDirty();
        return removed;
    }

    public Collection<String> names() {
        return new TreeSet<>(scenes.keySet());
    }

    public void clear() {
        scenes.clear();
        markDirty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound nbt) {
        scenes.clear();
        for (String key : (Set<String>) nbt.func_150296_c())
            scenes.put(key, SceneNBT.load(nbt.getCompoundTag(key)));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        for (Map.Entry<String, CamScene> entry : scenes.entrySet())
            nbt.setTag(entry.getKey(), SceneNBT.save(entry.getValue()));
    }

}
