package team.creative.cmdcam.common.math.interpolation;

import java.util.List;

import team.creative.cmdcam.common.math.vec.VecNd;

public class CubicInterpolation<T extends VecNd> extends Interpolation<T> {

    public final T beginVec;
    public final T endVec;

    @SuppressWarnings("unchecked")
    public CubicInterpolation(double[] times, T before, List<T> points, T after) {
        super(times, points);
        if (before != null)
            beginVec = before;
        else { // Mirror the second point at the first one
            beginVec = (T) points.get(0).copy();
            beginVec.sub(points.get(1));
            beginVec.add(points.get(0));
        }
        if (after != null)
            endVec = after;
        else {
            endVec = (T) points.get(points.size() - 1).copy();
            endVec.sub(points.get(points.size() - 2));
            endVec.add(points.get(points.size() - 1));
        }
    }

    @Override
    protected double getValue(int index, int dim) {
        if (index < 0)
            return beginVec.get(dim);
        if (index >= values.size())
            return endVec.get(dim);
        return values.get(index).get(dim);
    }

    @Override
    public double[] estimateDistance() {
        return estimateCurveDistance(3);
    }

    @Override
    public double valueAt(double mu, int pointIndex, int pointIndexNext, int dim) {
        double v0 = getValue(pointIndex - 1, dim);
        double v1 = getValue(pointIndex, dim);
        double v2 = getValue(pointIndexNext, dim);
        double v3 = getValue(pointIndexNext + 1, dim);

        double mu2 = mu * mu;
        double a0 = v3 - v2 - v0 + v1;
        double a1 = v0 - v1 - a0;
        double a2 = v2 - v0;

        return a0 * mu * mu2 + a1 * mu2 + a2 * mu + v1;
    }

}
