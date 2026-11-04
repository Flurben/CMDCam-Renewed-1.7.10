package team.creative.cmdcam.common.math.follow;

import team.creative.cmdcam.common.math.vec.VecNd;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;

/**
 * Eases a value towards a moving target. The step setting keeps its upstream meaning, "move 1/step of the way each frame", but it is measured at a reference frame rate of 60 fps
 * so the same scene follows at the same speed on every machine.
 */
public class CamFollow<T extends VecNd> {

    private static final double REFERENCE_FRAME_MILLIS = 1000D / 60D;

    private final CamAttribute<T> attribute;
    private final double div;
    private T current;

    public CamFollow(CamAttribute<T> attribute, double div, T initial) {
        this.attribute = attribute;
        this.div = Math.max(1, div);
        this.current = initial;
    }

    private void makeInBounds(T vec) {
        T minVec = attribute.getMin();
        T maxVec = attribute.getMax();
        for (int i = 0; i < vec.dimensions(); i++) {
            double value = vec.get(i);
            double min = minVec.get(i);
            double max = maxVec.get(i);
            if (value >= min && value <= max)
                continue;

            value -= min;
            value %= max - min;
            if (value < 0)
                value += max - min;
            vec.set(i, value + min);
        }
    }

    @SuppressWarnings("unchecked")
    public T follow(T target, double frameMillis) {
        T aimed = (T) target.copy();
        if (attribute.getMin() != null) {
            makeInBounds(aimed);
            makeInBounds(current);

            // Take the short way around
            T minVec = attribute.getMin();
            T maxVec = attribute.getMax();
            for (int i = 0; i < aimed.dimensions(); i++) {
                double range = maxVec.get(i) - minVec.get(i);
                double difference = aimed.get(i) - current.get(i);
                if (difference > range / 2)
                    aimed.set(i, aimed.get(i) - range);
                else if (difference < -range / 2)
                    aimed.set(i, aimed.get(i) + range);
            }
        }

        if (div == 1) {
            current = aimed;
            return (T) aimed.copy();
        }

        double alpha = 1 - Math.pow(1 - 1 / div, Math.max(0, frameMillis) / REFERENCE_FRAME_MILLIS);
        T vec = (T) current.copy();
        for (int i = 0; i < vec.dimensions(); i++)
            vec.set(i, current.get(i) + (aimed.get(i) - current.get(i)) * alpha);
        current = vec;
        return (T) vec.copy();
    }

}
