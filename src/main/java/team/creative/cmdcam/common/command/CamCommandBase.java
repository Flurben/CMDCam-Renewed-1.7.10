package team.creative.cmdcam.common.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import team.creative.cmdcam.common.util.DurationParser;

public abstract class CamCommandBase extends CommandBase {

    public static void send(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation(key, args));
    }

    public static void sendUsage(ICommandSender sender, String command, String description) {
        sender.addChatMessage(new ChatComponentText("" + EnumChatFormatting.YELLOW + command + " " + EnumChatFormatting.GRAY + description));
    }

    public static int parseIndex(String value, int size) throws CommandException {
        int index;
        try {
            index = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException("scene.index", value);
        }
        if (index < 1 || index > size)
            throw new CommandException("scene.index", value);
        return index - 1;
    }

    public static int parseInteger(String value) throws CommandException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException("cam.invalid_number", value);
        }
    }

    public static long parseDurationArg(String value) throws CommandException {
        long duration = DurationParser.parse(value);
        if (duration <= 0)
            throw new CommandException("invalid_time_format");
        return duration;
    }

    public static double parseNumber(String value) throws CommandException {
        try {
            double number = Double.parseDouble(value);
            if (Double.isNaN(number) || Double.isInfinite(number))
                throw new CommandException("cam.invalid_number", value);
            return number;
        } catch (NumberFormatException e) {
            throw new CommandException("cam.invalid_number", value);
        }
    }

    /** Parses a number which can be relative to the base by starting with ~. */
    public static double parseRelative(double base, String value) throws CommandException {
        if (value.startsWith("~"))
            return value.length() == 1 ? base : base + parseNumber(value.substring(1));
        return parseNumber(value);
    }

    public static boolean parseBool(String value) throws CommandException {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on"))
            return true;
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off"))
            return false;
        throw new CommandException("cam.invalid_bool", value);
    }

    public static List<String> matching(String last, String... options) {
        return matching(last, Arrays.asList(options));
    }

    public static List<String> matching(String last, List<String> options) {
        List<String> result = new ArrayList<>();
        String prefix = last.toLowerCase(Locale.ROOT);
        for (String option : options)
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix))
                result.add(option);
        return result;
    }

    public static String[] tail(String[] args, int start) {
        return Arrays.copyOfRange(args, Math.min(start, args.length), args.length);
    }

}
