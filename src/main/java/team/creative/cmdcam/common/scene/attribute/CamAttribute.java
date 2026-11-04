package team.creative.cmdcam.common.scene.attribute;

import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec1d;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.math.vec.VecNd;

/** One interpolated property of a {@link CamPoint}. */
public abstract class CamAttribute<T extends VecNd> {

    public static final CamAttribute<Vec3d> POSITION = new CamAttribute<Vec3d>("pos") {

        @Override
        public Vec3d get(CamPoint point) {
            return new Vec3d(point);
        }

        @Override
        public void set(CamPoint point, Vec3d vec) {
            point.set(vec);
        }
    };

    public static final CamAttribute<Vec1d> YAW = new CamAttribute<Vec1d>("yaw") {

        private final Vec1d min = new Vec1d(-270);
        private final Vec1d max = new Vec1d(90);

        @Override
        public Vec1d get(CamPoint point) {
            return new Vec1d(point.rotationYaw);
        }

        @Override
        public void set(CamPoint point, Vec1d vec) {
            point.rotationYaw = vec.x;
        }

        @Override
        public Vec1d getMin() {
            return min;
        }

        @Override
        public Vec1d getMax() {
            return max;
        }
    };

    public static final CamAttribute<Vec1d> PITCH = new CamAttribute<Vec1d>("pitch") {

        @Override
        public Vec1d get(CamPoint point) {
            return new Vec1d(point.rotationPitch);
        }

        @Override
        public void set(CamPoint point, Vec1d vec) {
            point.rotationPitch = vec.x;
        }
    };

    public static final CamAttribute<Vec1d> ZOOM = new CamAttribute<Vec1d>("zoom") {

        @Override
        public Vec1d get(CamPoint point) {
            return new Vec1d(point.zoom);
        }

        @Override
        public void set(CamPoint point, Vec1d vec) {
            point.zoom = vec.x;
        }
    };

    public static final CamAttribute<Vec1d> ROLL = new CamAttribute<Vec1d>("roll") {

        @Override
        public Vec1d get(CamPoint point) {
            return new Vec1d(point.roll);
        }

        @Override
        public void set(CamPoint point, Vec1d vec) {
            point.roll = vec.x;
        }
    };

    public static final CamAttribute<?>[] PATH_ATTRIBUTES = new CamAttribute<?>[] { POSITION, PITCH, YAW, ZOOM, ROLL };

    private final String name;

    private CamAttribute(String name) {
        this.name = name;
    }

    public abstract T get(CamPoint point);

    public abstract void set(CamPoint point, T vec);

    /** @return lower bound if this attribute wraps around, otherwise null */
    public T getMin() {
        return null;
    }

    public T getMax() {
        return null;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

}
