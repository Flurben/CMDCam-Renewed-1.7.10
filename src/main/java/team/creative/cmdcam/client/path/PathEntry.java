package team.creative.cmdcam.client.path;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.scene.path.PathStats;

/** A line in the list of paths. */
@SideOnly(Side.CLIENT)
public final class PathEntry {

    public final SavedPath path;
    public final boolean server;
    /** For a path in the folder: the server has the same one, as saving stores both. */
    public boolean alsoOnServer;
    /** For a path on the server: the same is in the folder, so it only has to be listed once. */
    public boolean inFolder;
    public final PathStats stats;

    private PathEntry(SavedPath path, boolean server) {
        this.path = path;
        this.server = server;
        CamScene scene = path.scene;
        scene.bind(Minecraft.getMinecraft().theWorld, CMDCamClient.partialTicks()); // What the circular path needs to find its center
        this.stats = PathStats.of(scene.points, scene.interpolation, scene.distanceBasedTiming, scene, scene.duration);
    }

    public static List<PathEntry> collect() {
        List<PathEntry> entries = new ArrayList<>();
        for (SavedPath path : LocalPaths.all())
            entries.add(new PathEntry(path, false));
        int localCount = entries.size();
        for (SavedPath path : ServerPaths.all()) {
            PathEntry entry = new PathEntry(path, true);
            for (int i = 0; i < localCount; i++) {
                PathEntry local = entries.get(i);
                if (local.path.name.equalsIgnoreCase(path.name) && local.path.creator.equalsIgnoreCase(path.creator) && SceneNBT.save(local.path.scene).equals(SceneNBT.save(path.scene))) {
                    local.alsoOnServer = true;
                    entry.inFolder = true;
                    break;
                }
            }
            entries.add(entry);
        }
        return entries;
    }

}
