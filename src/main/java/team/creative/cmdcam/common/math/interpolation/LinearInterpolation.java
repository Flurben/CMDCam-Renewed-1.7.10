package team.creative.cmdcam.common.math.interpolation;

import java.util.List;

import team.creative.cmdcam.common.math.vec.VecNd;

public class LinearInterpolation<T extends VecNd> extends Interpolation<T> {

    public LinearInterpolation(double[] times, List<T> points) {
        super(times, points);
    }

    @Override
    public double valueAt(double mu, int pointIndex, int pointIndexNext, int dim) {
        return (getValue(pointIndexNext, dim) - getValue(pointIndex, dim)) * mu + getValue(pointIndex, dim);
    }

}
