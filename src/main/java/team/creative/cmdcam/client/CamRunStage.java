package team.creative.cmdcam.client;

import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.world.World;
import team.creative.cmdcam.common.math.follow.CamFollow;
import team.creative.cmdcam.common.math.interpolation.Interpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec1d;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.math.vec.VecNd;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.scene.run.RunStage;
import team.creative.cmdcam.common.scene.run.StagePlan;

@SideOnly(Side.CLIENT)
public class CamRunStage extends RunStage {

    private final CamScene scene;
    private final Map<CamAttribute<?>, Interpolation<?>> attributes = new HashMap<>();
    public final boolean mainStart;

    private CamFollow<Vec1d> pitchFollow;
    private CamFollow<Vec1d> yawFollow;
    private CamFollow<Vec3d> posFollow;
    private boolean started = false;
    private Double lastYaw;
    private Double lastPitch;
    private Vec3d lastPos;

    public CamRunStage(CamScene scene, StagePlan.Entry entry) {
        super(entry.duration, entry.loops);
        this.scene = scene;
        this.mainStart = entry.mainStart;

        double[] times = entry.points.createTimes(scene.distanceBasedTiming, scene, entry.interpolation);
        for (CamAttribute<?> attribute : CamAttribute.PATH_ATTRIBUTES)
            attributes.put(attribute, entry.points.interpolate(times, scene, entry.interpolation, attribute));
    }

    public boolean hasStarted() {
        return started;
    }

    public void start(CamPoint currentView) {
        pitchFollow = null;
        yawFollow = null;
        posFollow = null;
        if (scene.lookTarget != null) {
            pitchFollow = new CamFollow<>(CamAttribute.PITCH, scene.pitchStep, CamAttribute.PITCH.get(currentView));
            yawFollow = new CamFollow<>(CamAttribute.YAW, scene.yawStep, CamAttribute.YAW.get(currentView));
        }
        if (scene.posTarget != null) {
            posFollow = new CamFollow<>(CamAttribute.POSITION, scene.posStep, CamAttribute.POSITION.get(currentView));
            lastPos = new Vec3d(currentView); // Without a target the points are only offsets, not a place to go to
        }
        started = true;
    }

    @SuppressWarnings("unchecked")
    private static <T extends VecNd> void apply(CamAttribute<T> attribute, Interpolation<?> interpolation, double progress, CamPoint point) {
        attribute.set(point, ((Interpolation<T>) interpolation).valueAt(progress));
    }

    public CamPoint calculatePoint(World world, long position, float partialTicks, double frameMillis) {
        double progress = duration > 0 ? Math.min(1, position / (double) duration) : 1;

        CamPoint point = new CamPoint();
        for (CamAttribute<?> attribute : CamAttribute.PATH_ATTRIBUTES)
            apply(attribute, attributes.get(attribute), progress, point);

        // Follow the position first, so the look direction is calculated from where the camera is going to be this frame
        if (posFollow != null) {
            Vec3d target = scene.posTarget.position(world, partialTicks);
            if (target != null) {
                Vec3d aimed = new Vec3d(point);
                aimed.add(target);
                lastPos = posFollow.follow(aimed, frameMillis);
            }
            if (lastPos != null) // The followed entity can be gone (died or unloaded), the camera then waits where it is
                point.set(lastPos);
        }

        if (pitchFollow != null) {
            Vec3d target = scene.lookTarget.position(world, partialTicks);
            if (target != null) {
                double dx = target.x - point.x;
                double dy = target.y - point.y;
                double dz = target.z - point.z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                lastPitch = pitchFollow.follow(new Vec1d(-Math.toDegrees(Math.atan2(dy, horizontal))), frameMillis).x;
                lastYaw = yawFollow.follow(new Vec1d(Math.toDegrees(Math.atan2(dz, dx)) - 90), frameMillis).x;
            }
            if (lastPitch != null) {
                point.rotationPitch = lastPitch;
                point.rotationYaw = lastYaw;
            }
        }

        return point;
    }

}
