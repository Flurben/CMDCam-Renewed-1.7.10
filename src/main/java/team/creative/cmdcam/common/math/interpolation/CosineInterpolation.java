package team.creative.cmdcam.common.math.interpolation;

import java.util.List;

import team.creative.cmdcam.common.math.vec.VecNd;

public class CosineInterpolation<T extends VecNd> extends Interpolation<T> {

    public CosineInterpolation(double[] times, List<T> points) {
        super(times, points);
    }

    @Override
    public double valueAt(double mu, int pointIndex, int pointIndexNext, int dim) {
        double mu2 = (1 - Math.cos(mu * Math.PI)) / 2;
        return getValue(pointIndex, dim) * (1 - mu2) + getValue(pointIndexNext, dim) * mu2;
    }

}
