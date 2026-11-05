package team.creative.cmdcam.common.scene.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** A path is stored in a file with its name, so a name has to work as a file name on every system. */
public final class PathNames {

    public static final int MAX_LENGTH = 64;

    private static final String ILLEGAL = "<>:\"/\\|?*§";
    private static final List<String> RESERVED = new ArrayList<>(Arrays.asList("CON", "PRN", "AUX", "NUL"));

    static {
        for (int i = 1; i <= 9; i++) {
            RESERVED.add("COM" + i);
            RESERVED.add("LPT" + i);
        }
    }

    private PathNames() {}

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH)
            return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || ILLEGAL.indexOf(c) >= 0)
                return false;
        }
        char first = name.charAt(0);
        char last = name.charAt(name.length() - 1);
        if (first == ' ' || last == ' ' || last == '.')
            return false;
        int dot = name.indexOf('.');
        return !RESERVED.contains((dot >= 0 ? name.substring(0, dot) : name).toUpperCase(Locale.ROOT));
    }

    /** @return a valid name which is as close as possible, for names coming from servers and older versions */
    public static String sanitize(String name) {
        StringBuilder result = new StringBuilder();
        if (name != null)
            for (int i = 0; i < name.length() && result.length() < MAX_LENGTH; i++) {
                char c = name.charAt(i);
                result.append(c < 32 || c == 127 || ILLEGAL.indexOf(c) >= 0 ? '_' : c);
            }
        String cleaned = result.toString().trim();
        while (cleaned.endsWith("."))
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.isEmpty())
            return "path";
        return isValid(cleaned) ? cleaned : "path " + cleaned;
    }

    /** @return the name itself if it is free, otherwise the first free "name copy", "name copy 2", ... File names ignore case on some systems, so this does as well */
    public static String free(String name, Collection<String> taken) {
        if (!contains(taken, name))
            return name;
        for (int i = 1;; i++) {
            String suffix = i == 1 ? " copy" : " copy " + i;
            String base = name.length() + suffix.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH - suffix.length()).trim() : name;
            if (!contains(taken, base + suffix))
                return base + suffix;
        }
    }

    public static boolean contains(Collection<String> names, String name) {
        for (String other : names)
            if (other.equalsIgnoreCase(name))
                return true;
        return false;
    }

}
