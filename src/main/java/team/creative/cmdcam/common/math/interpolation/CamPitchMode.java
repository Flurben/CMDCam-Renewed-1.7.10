package team.creative.cmdcam.common.math.interpolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How yaw values are prepared before interpolation. The name matches upstream, it is about the rotation between points (spinning). */
public enum CamPitchMode {

    NO_FIX,
    FIX,
    FIX_KEEP_DIRECTION;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the mode or null if there is none with this id */
    public static CamPitchMode of(String id) {
        for (CamPitchMode mode : values())
            if (mode.id().equals(id))
                return mode;
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (CamPitchMode mode : values())
            names.add(mode.id());
        return names;
    }

}
