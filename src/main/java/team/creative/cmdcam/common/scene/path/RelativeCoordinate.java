package team.creative.cmdcam.common.scene.path;

/** Coordinates like in Minecraft's commands: 12.5 is absolute, ~ is the base and ~-3 is relative to the base. */
public final class RelativeCoordinate {

    private RelativeCoordinate() {}

    /** @throws NumberFormatException if it is not a usable number */
    public static double parse(double base, String text) {
        String value = text.trim();
        if (value.equals("~"))
            return base;
        boolean relative = value.startsWith("~");
        double number = Double.parseDouble(relative ? value.substring(1) : value);
        if (Double.isNaN(number) || Double.isInfinite(number))
            throw new NumberFormatException(text);
        return relative ? base + number : number;
    }

}
