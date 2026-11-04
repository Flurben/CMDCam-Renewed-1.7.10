package team.creative.cmdcam.common.math.interpolation;

import java.util.List;

import team.creative.cmdcam.common.math.vec.VecNd;

/** Hermite spline with neutral bias and tension. */
public class HermiteInterpolation<T extends VecNd> extends CubicInterpolation<T> {

    public HermiteInterpolation(double[] times, T before, List<T> points, T after) {
        super(times, before, points, after);
    }

    @Override
    public double valueAt(double mu, int pointIndex, int pointIndexNext, int dim) {
        double v0 = getValue(pointIndex - 1, dim);
        double v1 = getValue(pointIndex, dim);
        double v2 = getValue(pointIndexNext, dim);
        double v3 = getValue(pointIndexNext + 1, dim);

        double mu2 = mu * mu;
        double mu3 = mu2 * mu;
        double m0 = (v1 - v0) / 2 + (v2 - v1) / 2;
        double m1 = (v2 - v1) / 2 + (v3 - v2) / 2;
        double a0 = 2 * mu3 - 3 * mu2 + 1;
        double a1 = mu3 - 2 * mu2 + mu;
        double a2 = mu3 - mu2;
        double a3 = -2 * mu3 + 3 * mu2;

        return a0 * v1 + a1 * m0 + a2 * m1 + a3 * v2;
    }

}
