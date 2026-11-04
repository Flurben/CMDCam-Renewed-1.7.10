package team.creative.cmdcam.common.math.point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.interpolation.Interpolation;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.math.vec.VecNd;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;

/** One stage's working copy of the points. Modifying it never touches the scene's points. */
public class CamPoints {

    private CamPoint before;
    private final List<CamPoint> content = new ArrayList<>();
    private CamPoint after;

    public CamPoints() {}

    public CamPoints(Collection<CamPoint> collection) {
        for (CamPoint point : collection)
            content.add(point.copy());
    }

    public void add(CamPoint point) {
        content.add(point.copy());
    }

    public CamPoint get(int index) {
        return content.get(index);
    }

    public void before(CamPoint point) {
        before = point.copy();
    }

    public void after(CamPoint point) {
        after = point.copy();
    }

    public CamPoint before() {
        return before;
    }

    public CamPoint after() {
        return after;
    }

    public int size() {
        return content.size();
    }

    private List<CamPoint> allPoints() {
        List<CamPoint> all = new ArrayList<>(content.size() + 2);
        if (before != null)
            all.add(before);
        all.addAll(content);
        if (after != null)
            all.add(after);
        return all;
    }

    public static double wrapDegrees(double value) {
        value %= 360;
        if (value >= 180)
            value -= 360;
        if (value < -180)
            value += 360;
        return value;
    }

    public void fixSpinning(CamPitchMode mode) {
        if (mode == CamPitchMode.FIX)
            fixSpinning();
        else if (mode == CamPitchMode.FIX_KEEP_DIRECTION)
            fixSpinningKeepDirection();
    }

    /** Always turns the short way around. */
    public void fixSpinning() {
        double wrappedYaw = 180;
        double lastYaw = 0;
        for (CamPoint point : allPoints()) {
            double wrappedYawCurrent = wrapDegrees(point.rotationYaw) + 180;

            double rightDistance;
            double leftDistance;
            if (wrappedYaw > wrappedYawCurrent) {
                rightDistance = 360 - wrappedYaw + wrappedYawCurrent;
                leftDistance = wrappedYaw - wrappedYawCurrent;
            } else {
                rightDistance = wrappedYawCurrent - wrappedYaw;
                leftDistance = 360 - wrappedYawCurrent + wrappedYaw;
            }

            if (rightDistance < leftDistance)
                lastYaw += rightDistance;
            else
                lastYaw -= leftDistance;

            wrappedYaw = wrappedYawCurrent;
            point.rotationYaw = lastYaw;
        }
    }

    /** Removes full rotations but keeps the direction the player turned in. */
    public void fixSpinningKeepDirection() {
        double lastYaw = 0;
        double originalYaw = 0;
        for (CamPoint point : allPoints()) {
            lastYaw += (point.rotationYaw - originalYaw) % 360;
            originalYaw = point.rotationYaw;
            point.rotationYaw = lastYaw;
        }
    }

    public double estimateLength() {
        double distance = 0;
        for (int i = 0; i < content.size() - 1; i++)
            distance += content.get(i).distance(content.get(i + 1));
        return distance;
    }

    public <T extends VecNd> Interpolation<T> interpolate(double[] times, PathContext context, CamInterpolation interpolation, CamAttribute<T> attribute) {
        List<T> vecs = new ArrayList<>(content.size());
        for (CamPoint point : content)
            vecs.add(attribute.get(point));
        return interpolation.create(times, context, before != null ? attribute.get(before) : null, vecs, after != null ? attribute.get(after) : null, attribute);
    }

    public double[] createTimes(boolean distanceBasedTiming, PathContext context, CamInterpolation interpolation) {
        double[] times = Interpolation.uniformTimes(size());
        if (!distanceBasedTiming || size() <= 2)
            return times;

        Interpolation<Vec3d> inter = interpolate(times, context, interpolation, CamAttribute.POSITION);
        double[] data = inter.estimateDistance();
        if (data[0] <= 0) // All points are at the same position
            return times;

        double time = 0;
        for (int i = 1; i < data.length - 1; i++) {
            time += data[i] / data[0];
            times[i] = time;
        }
        return times;
    }

}
