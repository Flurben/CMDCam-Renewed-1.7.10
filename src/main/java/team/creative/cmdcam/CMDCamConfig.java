package team.creative.cmdcam;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class CMDCamConfig {

    public static final String CATEGORY = "defaults";

    public static Configuration config;

    public static String defaultDuration = "10s";
    public static String defaultMode = "default";
    public static String defaultInterpolation = "hermite";
    public static boolean defaultSmoothStart = true;
    public static boolean defaultHideGui = true;
    public static boolean defaultReturnToStart = false;

    public static void load(File file) {
        config = new Configuration(file);
        config.load();
        sync();
    }

    public static void sync() {
        defaultDuration = config.getString("defaultDuration", CATEGORY, defaultDuration, "Duration of a new scene, for example 10s, 500ms or 1m30s");
        defaultMode = config.getString("defaultMode", CATEGORY, defaultMode, "Mode of a new scene", new String[] { "default", "outside" });
        defaultInterpolation = config.getString("defaultInterpolation", CATEGORY, defaultInterpolation, "Interpolation of a new scene", new String[] { "linear", "cubic", "hermite",
                "cosine", "circular", "invcircular" });
        defaultSmoothStart = config.getBoolean("defaultSmoothStart", CATEGORY, defaultSmoothStart, "Move smoothly from the current view to the first point");
        defaultHideGui = config.getBoolean("defaultHideGui", CATEGORY, defaultHideGui, "Hide the HUD while a scene is playing");
        defaultReturnToStart = config.getBoolean("defaultReturnToStart", CATEGORY, defaultReturnToStart, "Put the player back to where they were when a default mode scene ends");
        if (config.hasChanged())
            config.save();
    }

}
