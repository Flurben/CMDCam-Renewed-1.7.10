package team.creative.cmdcam.common.scene;

import net.minecraft.nbt.NBTTagCompound;
import team.creative.cmdcam.common.math.point.CamPoint;

/** A scene with a name and where it comes from. Stored in the same compound as the scene, so older data simply has no creator or times. */
public class SavedPath {

    public static final int MAX_POINTS = 4096;

    public String name;
    public final CamScene scene;
    /** Empty if it is not known, as for scenes saved by older versions. */
    public String creator = "";
    public String creatorId = "";
    public long savedAt;
    public long lastLoaded;

    public SavedPath(String name, CamScene scene) {
        this.name = name;
        this.scene = scene;
    }

    public NBTTagCompound save() {
        NBTTagCompound nbt = SceneNBT.save(scene);
        nbt.setString("creator", creator);
        nbt.setString("creator_id", creatorId);
        nbt.setLong("saved_at", savedAt);
        nbt.setLong("last_loaded", lastLoaded);
        return nbt;
    }

    public static SavedPath load(String name, NBTTagCompound nbt) {
        SavedPath path = new SavedPath(name, SceneNBT.load(nbt));
        path.creator = nbt.getString("creator");
        path.creatorId = nbt.getString("creator_id");
        path.savedAt = nbt.getLong("saved_at");
        path.lastLoaded = nbt.getLong("last_loaded");
        return path;
    }

    /** @return false for data no scene should ever contain, which can only come from a damaged file or a modified client */
    public boolean isSane() {
        if (scene.points.size() > MAX_POINTS)
            return false;
        for (CamPoint point : scene.points)
            if (!finite(point.x) || !finite(point.y) || !finite(point.z) || !finite(point.rotationYaw) || !finite(point.rotationPitch) || !finite(point.roll) || !finite(point.zoom))
                return false;
        return true;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

}
