package team.creative.cmdcam.common.scene.path;

import java.util.ArrayList;
import java.util.List;

import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.Interpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.math.point.PathContext;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;

/** What can be said about a path without playing it. The samples are the same curve /cam show draws. */
public final class PathStats {

    private static final int STEPS_PER_POINT = 20;
    private static final int MAX_STEPS = 1000;

    public final int points;
    public final long duration;
    /** Along the curve, not along straight lines between the points. */
    public final double length;
    /** Blocks per second during one run. */
    public final double speed;
    public final Vec3d min;
    public final Vec3d max;
    public final List<Vec3d> samples;

    private PathStats(int points, long duration, double length, Vec3d min, Vec3d max, List<Vec3d> samples) {
        this.points = points;
        this.duration = duration;
        this.length = length;
        this.speed = duration > 0 ? length / (duration / 1000D) : 0;
        this.min = min;
        this.max = max;
        this.samples = samples;
    }

    public static PathStats of(List<CamPoint> points, CamInterpolation interpolation, boolean distanceTiming, PathContext context, long duration) {
        List<Vec3d> samples = sample(points, interpolation, distanceTiming, context);

        double length = 0;
        Vec3d min = null;
        Vec3d max = null;
        for (int i = 0; i < samples.size(); i++) {
            Vec3d sample = samples.get(i);
            if (i > 0)
                length += sample.distance(samples.get(i - 1));
            if (min == null) {
                min = sample.copy();
                max = sample.copy();
            }
            for (int dim = 0; dim < 3; dim++) {
                min.set(dim, Math.min(min.get(dim), sample.get(dim)));
                max.set(dim, Math.max(max.get(dim), sample.get(dim)));
            }
        }
        return new PathStats(points.size(), duration, length, min, max, samples);
    }

    private static List<Vec3d> sample(List<CamPoint> points, CamInterpolation interpolation, boolean distanceTiming, PathContext context) {
        List<Vec3d> samples = new ArrayList<>();
        if (points.size() >= 2)
            try {
                CamPoints working = new CamPoints(points);
                double[] times = working.createTimes(distanceTiming, context, interpolation);
                Interpolation<Vec3d> path = working.interpolate(times, context, interpolation, CamAttribute.POSITION);
                int steps = Math.min(MAX_STEPS, STEPS_PER_POINT * (points.size() - 1));
                for (int i = 0; i <= steps; i++) {
                    Vec3d position = path.valueAt(i / (double) steps);
                    if (finite(position))
                        samples.add(position);
                }
            } catch (RuntimeException e) { // A file can contain anything, straight lines are enough to show it
                samples.clear();
            }
        if (samples.size() < 2) {
            samples.clear();
            for (CamPoint point : points)
                if (finite(point))
                    samples.add(new Vec3d(point));
        }
        return samples;
    }

    private static boolean finite(Vec3d vec) {
        return !Double.isNaN(vec.x + vec.y + vec.z) && !Double.isInfinite(vec.x + vec.y + vec.z);
    }

}
