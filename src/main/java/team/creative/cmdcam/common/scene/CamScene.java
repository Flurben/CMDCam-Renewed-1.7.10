package team.creative.cmdcam.common.scene;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;
import team.creative.cmdcam.CMDCamConfig;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.PathContext;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.target.CamTarget;
import team.creative.cmdcam.common.util.DurationParser;

/** The settings and points of a camera path. Only holds data, playing it is done by the client's CamRun. */
public class CamScene implements PathContext {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_OUTSIDE = "outside";

    public static final String TIMER_REAL = "realtime";
    public static final String TIMER_TICKS = "ticks";

    public static final double DEFAULT_LOOK_STEP = 10;
    public static final double DEFAULT_POS_STEP = 2;

    public static CamScene createDefault() {
        CamScene scene = new CamScene();
        scene.duration = DurationParser.parse(CMDCamConfig.defaultDuration, 10000);
        scene.setMode(CMDCamConfig.defaultMode);
        scene.interpolation = CamInterpolation.getOrDefault(CMDCamConfig.defaultInterpolation);
        scene.smoothBeginning = CMDCamConfig.defaultSmoothStart;
        scene.hideGui = CMDCamConfig.defaultHideGui;
        scene.returnToStart = CMDCamConfig.defaultReturnToStart;
        return scene;
    }

    public long duration = 10000;
    public int loop = 0;

    public String mode = MODE_DEFAULT;
    public CamInterpolation interpolation = CamInterpolation.HERMITE;

    public CamTarget lookTarget;
    public double pitchStep = DEFAULT_LOOK_STEP;
    public double yawStep = DEFAULT_LOOK_STEP;

    /** Makes the points relative to this target. */
    public CamTarget posTarget;
    public double posStep = DEFAULT_POS_STEP;

    public List<CamPoint> points = new ArrayList<>();

    public boolean smoothBeginning = true;
    public CamPitchMode pitchMode = CamPitchMode.FIX_KEEP_DIRECTION;
    public boolean distanceBasedTiming = false;

    public boolean hideGui = true;
    public boolean returnToStart = false;
    public String timer = TIMER_REAL;

    private boolean serverSynced = false;

    private World world;
    private float partialTicks;

    public void setMode(String mode) {
        this.mode = MODE_OUTSIDE.equals(mode) ? MODE_OUTSIDE : MODE_DEFAULT;
    }

    public boolean outside() {
        return MODE_OUTSIDE.equals(mode);
    }

    public void setServerSynced() {
        serverSynced = true;
    }

    public boolean serverSynced() {
        return serverSynced;
    }

    /** Targets are looked up in this world by {@link #lookCenter()}. */
    public void bind(World world, float partialTicks) {
        this.world = world;
        this.partialTicks = partialTicks;
    }

    @Override
    public Vec3d lookCenter() {
        if (lookTarget == null || world == null)
            return null;
        Vec3d center = lookTarget.position(world, partialTicks);
        if (center == null)
            return null;
        if (posTarget != null) {
            Vec3d follow = posTarget.position(world, partialTicks);
            if (follow == null)
                return null;
            center.sub(follow); // The points of a following scene are relative to the followed target
        }
        return center;
    }

    public double followStep(CamAttribute<?> attribute) {
        if (attribute == CamAttribute.POSITION)
            return posStep;
        if (attribute == CamAttribute.PITCH)
            return pitchStep;
        return yawStep;
    }

    public void followStep(CamAttribute<?> attribute, double step) {
        if (attribute == CamAttribute.POSITION)
            posStep = step;
        else if (attribute == CamAttribute.PITCH)
            pitchStep = step;
        else
            yawStep = step;
    }

    public void set(CamScene scene) {
        duration = scene.duration;
        loop = scene.loop;
        mode = scene.mode;
        interpolation = scene.interpolation;
        lookTarget = scene.lookTarget;
        pitchStep = scene.pitchStep;
        yawStep = scene.yawStep;
        posTarget = scene.posTarget;
        posStep = scene.posStep;
        points = new ArrayList<>(scene.points.size());
        for (CamPoint point : scene.points)
            points.add(point.copy());
        smoothBeginning = scene.smoothBeginning;
        pitchMode = scene.pitchMode;
        distanceBasedTiming = scene.distanceBasedTiming;
        hideGui = scene.hideGui;
        returnToStart = scene.returnToStart;
        timer = scene.timer;
        serverSynced = scene.serverSynced;
        world = scene.world;
        partialTicks = scene.partialTicks;
    }

    public CamScene copy() {
        CamScene scene = new CamScene();
        scene.set(this);
        return scene;
    }

}
