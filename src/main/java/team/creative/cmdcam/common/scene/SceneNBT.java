package team.creative.cmdcam.common.scene;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.target.CamTarget;

/** Saves scenes to NBT, for world saves, the client's scene file and packets. Tag names match upstream. */
public final class SceneNBT {

    private static final int COMPOUND_TAG = 10;

    private SceneNBT() {}

    public static NBTTagCompound savePoint(CamPoint point) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble("x", point.x);
        nbt.setDouble("y", point.y);
        nbt.setDouble("z", point.z);
        nbt.setDouble("rotationYaw", point.rotationYaw);
        nbt.setDouble("rotationPitch", point.rotationPitch);
        nbt.setDouble("roll", point.roll);
        nbt.setDouble("zoom", point.zoom);
        return nbt;
    }

    public static CamPoint loadPoint(NBTTagCompound nbt) {
        return new CamPoint(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"), nbt.getDouble("rotationYaw"), nbt.getDouble("rotationPitch"), nbt.getDouble("roll"), nbt
                .getDouble("zoom"));
    }

    public static NBTTagCompound save(CamScene scene) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("duration", scene.duration);
        nbt.setInteger("loop", scene.loop);
        nbt.setString("mode", scene.mode);
        nbt.setString("inter", scene.interpolation.id);

        if (scene.lookTarget != null)
            nbt.setTag("look_target", scene.lookTarget.save());
        nbt.setDouble("pitch_step", scene.pitchStep);
        nbt.setDouble("yaw_step", scene.yawStep);

        if (scene.posTarget != null)
            nbt.setTag("pos_target", scene.posTarget.save());
        nbt.setDouble("pos_step", scene.posStep);

        NBTTagList list = new NBTTagList();
        for (CamPoint point : scene.points)
            list.appendTag(savePoint(point));
        nbt.setTag("points", list);

        nbt.setBoolean("smooth_start", scene.smoothBeginning);
        nbt.setInteger("pitch_mode", scene.pitchMode.ordinal());
        nbt.setBoolean("d_timing", scene.distanceBasedTiming);
        nbt.setBoolean("hide_gui", scene.hideGui);
        nbt.setBoolean("return_start", scene.returnToStart);
        nbt.setString("timer", scene.timer);
        return nbt;
    }

    /** Unknown values fall back to their defaults, data from a save or a packet can never crash the game. */
    public static CamScene load(NBTTagCompound nbt) {
        CamScene scene = new CamScene();
        scene.duration = Math.max(1, nbt.getLong("duration"));
        scene.loop = nbt.getInteger("loop");
        scene.setMode(nbt.getString("mode"));
        scene.interpolation = CamInterpolation.getOrDefault(nbt.getString("inter"));

        scene.lookTarget = nbt.hasKey("look_target") ? CamTarget.load(nbt.getCompoundTag("look_target")) : null;
        if (nbt.hasKey("pitch_step"))
            scene.pitchStep = Math.max(1, nbt.getDouble("pitch_step"));
        if (nbt.hasKey("yaw_step"))
            scene.yawStep = Math.max(1, nbt.getDouble("yaw_step"));

        scene.posTarget = nbt.hasKey("pos_target") ? CamTarget.load(nbt.getCompoundTag("pos_target")) : null;
        if (nbt.hasKey("pos_step"))
            scene.posStep = Math.max(1, nbt.getDouble("pos_step"));

        NBTTagList list = nbt.getTagList("points", COMPOUND_TAG);
        for (int i = 0; i < list.tagCount(); i++)
            scene.points.add(loadPoint(list.getCompoundTagAt(i)));

        scene.smoothBeginning = nbt.getBoolean("smooth_start");
        int pitchMode = nbt.getInteger("pitch_mode");
        if (pitchMode >= 0 && pitchMode < CamPitchMode.values().length)
            scene.pitchMode = CamPitchMode.values()[pitchMode];
        scene.distanceBasedTiming = nbt.getBoolean("d_timing");
        if (nbt.hasKey("hide_gui"))
            scene.hideGui = nbt.getBoolean("hide_gui");
        scene.returnToStart = nbt.getBoolean("return_start");
        scene.timer = CamScene.TIMER_TICKS.equals(nbt.getString("timer")) ? CamScene.TIMER_TICKS : CamScene.TIMER_REAL;
        return scene;
    }

}
