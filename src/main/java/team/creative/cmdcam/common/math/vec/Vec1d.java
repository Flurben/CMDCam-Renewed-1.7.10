package team.creative.cmdcam.common.math.vec;

public class Vec1d extends VecNd {

    public double x;

    public Vec1d(double x) {
        this.x = x;
    }

    @Override
    public int dimensions() {
        return 1;
    }

    @Override
    public double get(int dim) {
        return x;
    }

    @Override
    public void set(int dim, double value) {
        x = value;
    }

    @Override
    public Vec1d copy() {
        return new Vec1d(x);
    }

}
