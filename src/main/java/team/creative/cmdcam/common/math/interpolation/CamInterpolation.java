package team.creative.cmdcam.common.math.interpolation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import team.creative.cmdcam.common.math.point.PathContext;
import team.creative.cmdcam.common.math.vec.Vec1d;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.math.vec.VecNd;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;

/** The selectable interpolation types, ported from CMDCam 2.x. */
public abstract class CamInterpolation {

    private static final Map<String, CamInterpolation> REGISTRY = new LinkedHashMap<>();

    public static final CamInterpolation LINEAR = register(new CamInterpolation("linear", 0, 0, 1) {
        @Override
        public <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute) {
            return new LinearInterpolation<>(times, points);
        }
    });

    public static final CamInterpolation CUBIC = register(new CamInterpolation("cubic", 1, 0, 0) {
        @Override
        public <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute) {
            return new CubicInterpolation<>(times, before, points, after);
        }
    });

    public static final CamInterpolation HERMITE = register(new CamInterpolation("hermite", 1, 1, 1) {
        @Override
        public <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute) {
            return new HermiteInterpolation<>(times, before, points, after);
        }
    });

    public static final CamInterpolation COSINE = register(new CamInterpolation("cosine", 0, 1, 0) {
        @Override
        public <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute) {
            return new CosineInterpolation<>(times, points);
        }
    });

    public static final CamInterpolation CIRCULAR = register(new Circular("circular", true));
    public static final CamInterpolation INV_CIRCULAR = register(new Circular("invcircular", false));

    private static CamInterpolation register(CamInterpolation interpolation) {
        REGISTRY.put(interpolation.id, interpolation);
        return interpolation;
    }

    /** @return the interpolation with the given id or null if there is none */
    public static CamInterpolation get(String id) {
        return REGISTRY.get(id);
    }

    public static CamInterpolation getOrDefault(String id) {
        CamInterpolation interpolation = REGISTRY.get(id);
        return interpolation != null ? interpolation : HERMITE;
    }

    public static Collection<CamInterpolation> all() {
        return REGISTRY.values();
    }

    public static List<String> names() {
        return new ArrayList<>(REGISTRY.keySet());
    }

    public final String id;
    public final float red;
    public final float green;
    public final float blue;
    public boolean isRenderingEnabled = false;

    CamInterpolation(String id, float red, float green, float blue) {
        this.id = id;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public abstract <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute);

    private static class Circular extends CamInterpolation {

        private final boolean clockwise;

        Circular(String id, boolean clockwise) {
            super(id, 1, 1, 0);
            this.clockwise = clockwise;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends VecNd> Interpolation<T> create(double[] times, PathContext context, T before, List<T> points, T after, CamAttribute<T> attribute) {
            if (attribute == CamAttribute.POSITION) {
                Vec3d center = context.lookCenter();
                if (center != null)
                    return (Interpolation<T>) new CircularInterpolation(clockwise, times, (List<Vec3d>) points, context, center);
            }
            return new HermiteInterpolation<>(times, before, points, after);
        }
    }

    /**
     * Circles around the look target. The first point sets radius and start angle, the other points only contribute their height, at the angle they are placed at.
     */
    public static class CircularInterpolation extends Interpolation<Vec3d> {

        private final boolean clockwise;
        private final PathContext context;
        private final Vec3d sphereOrigin;
        private final double radius;
        private final HermiteInterpolation<Vec1d> yAxis;

        public CircularInterpolation(boolean clockwise, double[] times, List<Vec3d> points, PathContext context, Vec3d center) {
            super(times, points);
            this.clockwise = clockwise;
            this.context = context;

            Vec3d first = points.get(0);
            this.sphereOrigin = first.copy();
            this.sphereOrigin.sub(center);
            this.radius = this.sphereOrigin.length();

            List<Double> angleTimes = new ArrayList<>();
            List<Vec1d> heights = new ArrayList<>();
            angleTimes.add(0D);
            heights.add(new Vec1d(first.y));

            for (int i = 1; i < points.size(); i++) {
                Vec3d point = points.get(i);
                double offsetX = point.x - center.x;
                double offsetZ = point.z - center.z;

                // Only the horizontal plane may contribute, the height is interpolated on its own
                double dot = offsetX * sphereOrigin.x + offsetZ * sphereOrigin.z;
                double det = offsetX * sphereOrigin.z - offsetZ * sphereOrigin.x;
                double angle = Math.toDegrees(Math.atan2(det, dot));
                if (angle < 0)
                    angle += 360;

                double time = angle / 360;
                if (!clockwise)
                    time = 1 - time;
                if (time <= 0 || time >= 1) // A point at the start angle does not add anything
                    continue;

                int insert = 0;
                while (insert < angleTimes.size() && angleTimes.get(insert) < time)
                    insert++;
                if (insert < angleTimes.size() && angleTimes.get(insert) == time)
                    continue;
                angleTimes.add(insert, time);
                heights.add(insert, new Vec1d(point.y));
            }

            angleTimes.add(1D);
            heights.add(new Vec1d(first.y));

            double[] primitiveTimes = new double[angleTimes.size()];
            for (int i = 0; i < primitiveTimes.length; i++)
                primitiveTimes[i] = angleTimes.get(i);
            this.yAxis = new HermiteInterpolation<>(primitiveTimes, null, heights, null);
        }

        @Override
        public Vec3d valueAt(double t) {
            Vec3d center = context.lookCenter();
            if (center == null) // Target is gone, stay on the last known radius around the first point
                return values.get(0).copy();

            t = Math.max(0, Math.min(1, t));
            if (!clockwise)
                t = 1 - t;

            double angle = Math.toRadians(t * 360);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            Vec3d point = new Vec3d(sphereOrigin.x * cos + sphereOrigin.z * sin, yAxis.valueAt(t).x - center.y, -sphereOrigin.x * sin + sphereOrigin.z * cos);
            point.normalize();
            point.scale(radius);
            point.add(center);
            return point;
        }

        @Override
        public double valueAt(double mu, int pointIndex, int pointIndexNext, int dim) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] estimateDistance() {
            return estimateCurveDistance(8);
        }
    }

}
