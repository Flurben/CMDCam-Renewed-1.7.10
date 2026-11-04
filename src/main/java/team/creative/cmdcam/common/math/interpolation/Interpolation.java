package team.creative.cmdcam.common.math.interpolation;

import java.util.ArrayList;
import java.util.List;

import team.creative.cmdcam.common.math.vec.VecNd;

/** Maps a time in [0, 1] onto a list of timed values. */
public abstract class Interpolation<T extends VecNd> {

    protected final double[] times;
    protected final List<T> values;

    public Interpolation(double[] times, List<T> points) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least two points are needed!");
        if (times == null)
            times = uniformTimes(points.size());
        if (times.length != points.size())
            throw new IllegalArgumentException("Invalid times array!");
        this.times = times;
        this.values = new ArrayList<>(points);
    }

    public static double[] uniformTimes(int size) {
        double[] times = new double[size];
        for (int i = 0; i < size; i++)
            times[i] = i / (double) (size - 1);
        return times;
    }

    protected double getValue(int index, int dim) {
        return values.get(index).get(dim);
    }

    public int size() {
        return values.size();
    }

    @SuppressWarnings("unchecked")
    public T valueAt(double t) {
        if (t <= times[0])
            return (T) values.get(0).copy();
        int last = times.length - 1;
        if (t >= times[last])
            return (T) values.get(last).copy();

        int next = 1;
        while (times[next] < t)
            next++;
        int previous = next - 1;

        double pointDistance = times[next] - times[previous];
        double mu = pointDistance > 0 ? (t - times[previous]) / pointDistance : 0;

        T vec = (T) values.get(previous).copy();
        for (int dim = 0; dim < vec.dimensions(); dim++)
            vec.set(dim, valueAt(mu, previous, next, dim));
        return vec;
    }

    public abstract double valueAt(double mu, int pointIndex, int pointIndexNext, int dim);

    /** @return index 0 holds the total length, index i the length of the segment ending at point i */
    public double[] estimateDistance() {
        double[] data = new double[values.size()];
        for (int i = 1; i < values.size(); i++) {
            double distance = values.get(i).distance(values.get(i - 1));
            data[0] += distance;
            data[i] = distance;
        }
        return data;
    }

    /** Same layout as {@link #estimateDistance()}, measured by walking along the curve. */
    protected double[] estimateCurveDistance(int steps) {
        double[] data = new double[values.size()];
        for (int i = 1; i < values.size(); i++) {
            double start = times[i - 1];
            double duration = times[i] - start;
            double distance = 0;
            VecNd last = values.get(i - 1);
            for (int j = 1; j <= steps; j++) {
                VecNd vec = valueAt(start + duration * j / (steps + 1D));
                distance += vec.distance(last);
                last = vec;
            }
            distance += values.get(i).distance(last);
            data[0] += distance;
            data[i] = distance;
        }
        return data;
    }

}
