package team.creative.cmdcam.common.math.vec;

/** Minimal replacement for CreativeCore's vector types, so the mod has no library dependency. */
public abstract class VecNd {

    public abstract int dimensions();

    public abstract double get(int dim);

    public abstract void set(int dim, double value);

    public abstract VecNd copy();

    public void add(VecNd other) {
        for (int i = 0; i < dimensions(); i++)
            set(i, get(i) + other.get(i));
    }

    public void sub(VecNd other) {
        for (int i = 0; i < dimensions(); i++)
            set(i, get(i) - other.get(i));
    }

    public void scale(double factor) {
        for (int i = 0; i < dimensions(); i++)
            set(i, get(i) * factor);
    }

    public double lengthSquared() {
        double sum = 0;
        for (int i = 0; i < dimensions(); i++)
            sum += get(i) * get(i);
        return sum;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public void normalize() {
        double length = length();
        if (length > 0)
            scale(1 / length);
    }

    public double distance(VecNd other) {
        double sum = 0;
        for (int i = 0; i < dimensions(); i++) {
            double diff = get(i) - other.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

}
