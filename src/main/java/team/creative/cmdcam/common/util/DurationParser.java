package team.creative.cmdcam.common.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations such as 10s, 500ms, 1m30s or a plain 10 (seconds, as in the old 1.7.10 version). */
public final class DurationParser {

    public static final long SECOND = 1000;
    public static final long MINUTE = SECOND * 60;
    public static final long HOUR = MINUTE * 60;
    public static final long DAY = HOUR * 24;

    private static final Pattern PART = Pattern.compile("(\\d+)(ms|s|m|h|d)");
    private static final Pattern FULL = Pattern.compile("(\\d+(ms|s|m|h|d))+");

    private DurationParser() {}

    /** @return the duration in milliseconds or -1 if the input is not valid */
    public static long parse(String input) {
        if (input == null)
            return -1;
        input = input.trim().toLowerCase(Locale.ROOT);
        try {
            if (input.matches("\\d+"))
                return Math.multiplyExact(Long.parseLong(input), SECOND);
            if (!FULL.matcher(input).matches())
                return -1;
            long total = 0;
            Matcher matcher = PART.matcher(input);
            while (matcher.find())
                total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), factor(matcher.group(2))));
            return total;
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    public static long parse(String input, long defaultValue) {
        long duration = parse(input);
        return duration > 0 ? duration : defaultValue;
    }

    private static long factor(String unit) {
        switch (unit) {
            case "ms":
                return 1;
            case "s":
                return SECOND;
            case "m":
                return MINUTE;
            case "h":
                return HOUR;
            default:
                return DAY;
        }
    }

    public static String print(long duration) {
        if (duration <= 0)
            return "0ms";
        StringBuilder output = new StringBuilder();
        duration = appendUnit(output, duration, DAY, "d");
        duration = appendUnit(output, duration, HOUR, "h");
        duration = appendUnit(output, duration, MINUTE, "m");
        duration = appendUnit(output, duration, SECOND, "s");
        appendUnit(output, duration, 1, "ms");
        return output.toString();
    }

    private static long appendUnit(StringBuilder output, long duration, long factor, String unit) {
        long amount = duration / factor;
        if (amount > 0) {
            if (output.length() > 0)
                output.append(' ');
            output.append(amount).append(unit);
        }
        return duration - amount * factor;
    }

}
