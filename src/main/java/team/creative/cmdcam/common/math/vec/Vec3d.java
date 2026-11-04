package team.creative.cmdcam.common.math.vec;

public class Vec3d extends VecNd {

    public double x;
    public double y;
    public double z;

    public Vec3d() {}

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Vec3d other) {
        this(other.x, other.y, other.z);
    }

    public void set(Vec3d other) {
        x = other.x;
        y = other.y;
        z = other.z;
    }

    @Override
    public int dimensions() {
        return 3;
    }

    @Override
    public double get(int dim) {
        switch (dim) {
            case 0:
                return x;
            case 1:
                return y;
            default:
                return z;
        }
    }

    @Override
    public void set(int dim, double value) {
        switch (dim) {
            case 0:
                x = value;
                break;
            case 1:
                y = value;
                break;
            default:
                z = value;
                break;
        }
    }

    @Override
    public Vec3d copy() {
        return new Vec3d(x, y, z);
    }

}
