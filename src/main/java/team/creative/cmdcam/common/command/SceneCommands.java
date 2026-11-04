package team.creative.cmdcam.common.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.target.CamTarget;
import team.creative.cmdcam.common.util.DurationParser;
import team.creative.cmdcam.common.util.EntityFinder;

/** The sub commands /cam and /cam-server get &lt;name&gt; have in common. */
public final class SceneCommands {

    public static final List<String> SUB_COMMANDS = Arrays.asList("clear", "add", "insert", "set", "remove", "duration", "loops", "goto", "mode", "target", "follow", "pitch", "yaw",
        "pos", "interpolation", "smooth_start", "spinning_fix", "distance_timing", "hide_gui", "return_start", "timer", "info");

    private SceneCommands() {}

    /** @return false if there is no sub command with this name */
    public static boolean process(ICommandSender sender, CamCommandProcessor processor, String name, String[] args) throws CommandException {
        String[] rest = CamCommandBase.tail(args, 1);
        CamScene scene;
        switch (args[0]) {
            case "clear":
                processor.getScene(sender, name).points.clear();
                processor.markDirty(sender);
                CamCommandBase.send(sender, "scene.clear");
                return true;
            case "add":
                scene = processor.getScene(sender, name);
                scene.points.add(parsePoint(sender, processor, scene, rest));
                processor.markDirty(sender);
                CamCommandBase.send(sender, "scene.add", scene.points.size());
                return true;
            case "insert":
            case "set":
            case "remove":
                pointAtIndex(sender, processor, name, args[0], rest);
                return true;
            case "duration":
                scene = processor.getScene(sender, name);
                if (rest.length == 0)
                    CamCommandBase.send(sender, "scene.output.duration", DurationParser.print(scene.duration));
                else {
                    scene.duration = CamCommandBase.parseDurationArg(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.duration", DurationParser.print(scene.duration));
                }
                return true;
            case "loops":
                loops(sender, processor, name, rest);
                return true;
            case "goto":
                scene = processor.getScene(sender, name);
                int indexArg = processor.requiresPlayer() ? 1 : 0;
                if (rest.length <= indexArg)
                    throw new CommandException("cam.usage.goto");
                processor.teleport(sender, scene, CamCommandBase.parseIndex(rest[indexArg], scene.points.size()), processor.requiresPlayer() ? rest[0] : null);
                return true;
            case "mode":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    if (!rest[0].equals(CamScene.MODE_DEFAULT) && !rest[0].equals(CamScene.MODE_OUTSIDE))
                        throw new CommandException("invalid_mode");
                    scene.setMode(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.mode", new ChatComponentTranslation("cam.mode." + scene.mode));
                } else
                    CamCommandBase.send(sender, "scene.output.mode", new ChatComponentTranslation("cam.mode." + scene.mode));
                return true;
            case "target":
                target(sender, processor, name, rest, true);
                return true;
            case "follow":
                target(sender, processor, name, rest, false);
                return true;
            case "pitch":
                followStep(sender, processor, name, rest, CamAttribute.PITCH);
                return true;
            case "yaw":
                followStep(sender, processor, name, rest, CamAttribute.YAW);
                return true;
            case "pos":
                followStep(sender, processor, name, rest, CamAttribute.POSITION);
                return true;
            case "interpolation":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    CamInterpolation interpolation = CamInterpolation.get(rest[0]);
                    if (interpolation == null)
                        throw new CommandException("cam.interpolation.unknown", rest[0]);
                    scene.interpolation = interpolation;
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.interpolation", new ChatComponentTranslation("cam.interpolation." + interpolation.id));
                } else
                    CamCommandBase.send(sender, "scene.output.interpolation", new ChatComponentTranslation("cam.interpolation." + scene.interpolation.id));
                return true;
            case "smooth_start":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    scene.smoothBeginning = CamCommandBase.parseBool(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.smooth_beginning", state(scene.smoothBeginning));
                } else
                    CamCommandBase.send(sender, "scene.output.smooth_beginning", state(scene.smoothBeginning));
                return true;
            case "spinning_fix":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    CamPitchMode mode = CamPitchMode.of(rest[0]);
                    if (mode == null)
                        throw new CommandException("invalid_mode");
                    scene.pitchMode = mode;
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.pitch_mode", new ChatComponentTranslation("cam.pitch_mode." + mode.id()));
                } else
                    CamCommandBase.send(sender, "scene.output.pitch_mode", new ChatComponentTranslation("cam.pitch_mode." + scene.pitchMode.id()));
                return true;
            case "distance_timing":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    scene.distanceBasedTiming = CamCommandBase.parseBool(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.distance_timing", state(scene.distanceBasedTiming));
                } else
                    CamCommandBase.send(sender, "scene.output.distance_timing", state(scene.distanceBasedTiming));
                return true;
            case "hide_gui":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    scene.hideGui = CamCommandBase.parseBool(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.hide_gui", state(scene.hideGui));
                } else
                    CamCommandBase.send(sender, "scene.output.hide_gui", state(scene.hideGui));
                return true;
            case "return_start":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    scene.returnToStart = CamCommandBase.parseBool(rest[0]);
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.return_start", state(scene.returnToStart));
                } else
                    CamCommandBase.send(sender, "scene.output.return_start", state(scene.returnToStart));
                return true;
            case "timer":
                scene = processor.getScene(sender, name);
                if (rest.length > 0) {
                    if (!rest[0].equals(CamScene.TIMER_REAL) && !rest[0].equals(CamScene.TIMER_TICKS))
                        throw new CommandException("invalid_mode");
                    scene.timer = rest[0];
                    processor.markDirty(sender);
                    CamCommandBase.send(sender, "scene.timer", scene.timer);
                } else
                    CamCommandBase.send(sender, "scene.output.timer", scene.timer);
                return true;
            case "info":
                info(sender, processor.getScene(sender, name));
                return true;
            default:
                return false;
        }
    }

    private static IChatComponent state(boolean value) {
        return new ChatComponentTranslation(value ? "cam.enabled" : "cam.disabled");
    }

    /** @param args nothing for the point of view of the sender, otherwise x y z [yaw pitch [roll [fov]]], everything can be relative with ~ */
    public static CamPoint parsePoint(ICommandSender sender, CamCommandProcessor processor, CamScene scene, String[] args) throws CommandException {
        CamPoint base = null;
        if (processor.canCreatePoint(sender)) {
            base = processor.createPoint(sender);
            processor.makeRelative(sender, scene, base);
        }

        if (args.length == 0) {
            if (base == null)
                throw new CommandException("cam.usage.point");
            return base;
        }

        if (args.length < 3 || args.length == 4 || args.length > 7)
            throw new CommandException("cam.usage.point");

        if (base == null) {
            ChunkCoordinates origin = sender.getPlayerCoordinates();
            base = new CamPoint(origin.posX, origin.posY, origin.posZ, 0, 0, 0, CamPoint.DEFAULT_FOV);
        }

        CamPoint point = new CamPoint(CamCommandBase.parseRelative(base.x, args[0]), CamCommandBase.parseRelative(base.y, args[1]), CamCommandBase.parseRelative(base.z, args[2]), 0,
                0, 0, CamPoint.DEFAULT_FOV);
        if (args.length >= 5) {
            point.rotationYaw = CamCommandBase.parseRelative(base.rotationYaw, args[3]);
            point.rotationPitch = CamCommandBase.parseRelative(base.rotationPitch, args[4]);
        }
        if (args.length >= 6)
            point.roll = CamCommandBase.parseRelative(base.roll, args[5]);
        if (args.length >= 7)
            point.zoom = CamCommandBase.parseRelative(base.zoom, args[6]);
        return point;
    }

    private static void pointAtIndex(ICommandSender sender, CamCommandProcessor processor, String name, String action, String[] args) throws CommandException {
        if (args.length == 0)
            throw new CommandException("cam.usage.index", action);
        CamScene scene = processor.getScene(sender, name);
        int index = CamCommandBase.parseIndex(args[0], scene.points.size());
        if (action.equals("remove")) {
            scene.points.remove(index);
            CamCommandBase.send(sender, "scene.remove_point", index + 1);
        } else {
            CamPoint point = parsePoint(sender, processor, scene, CamCommandBase.tail(args, 1));
            if (action.equals("insert")) {
                scene.points.add(index, point);
                CamCommandBase.send(sender, "scene.insert", index + 1);
            } else {
                scene.points.set(index, point);
                CamCommandBase.send(sender, "scene.set", index + 1);
            }
        }
        processor.markDirty(sender);
    }

    private static void loops(ICommandSender sender, CamCommandProcessor processor, String name, String[] args) throws CommandException {
        CamScene scene = processor.getScene(sender, name);
        if (args.length == 0) {
            if (scene.loop == 0)
                CamCommandBase.send(sender, "scene.output.no_loop");
            else if (scene.loop < 0)
                CamCommandBase.send(sender, "scene.output.endless");
            else
                CamCommandBase.send(sender, "scene.output.loops", scene.loop);
            return;
        }
        scene.loop = Math.max(-1, CamCommandBase.parseInteger(args[0]));
        processor.markDirty(sender);
        if (scene.loop == 0)
            CamCommandBase.send(sender, "scene.loop");
        else if (scene.loop < 0)
            CamCommandBase.send(sender, "scene.loops.endless");
        else
            CamCommandBase.send(sender, "scene.loops", scene.loop);
    }

    private static void followStep(ICommandSender sender, CamCommandProcessor processor, String name, String[] args, CamAttribute<?> attribute) throws CommandException {
        if (args.length < 2 || !args[0].equals("step"))
            throw new CommandException("cam.usage.step", attribute.name());
        double step = Math.max(1, CamCommandBase.parseNumber(args[1]));
        processor.getScene(sender, name).followStep(attribute, step);
        processor.markDirty(sender);
        CamCommandBase.send(sender, "scene.follow.div", attribute.name(), step);
    }

    private static void target(ICommandSender sender, CamCommandProcessor processor, String name, String[] args, boolean look) throws CommandException {
        CamScene scene = processor.getScene(sender, name);
        String prefix = look ? "scene.look.target." : "scene.follow.target.";
        if (args.length == 0) {
            CamTarget target = look ? scene.lookTarget : scene.posTarget;
            if (target == null)
                CamCommandBase.send(sender, look ? "scene.output.no_look" : "scene.output.no_follow");
            else
                CamCommandBase.send(sender, look ? "scene.output.look" : "scene.output.follow", target.print(sender.getEntityWorld()));
            return;
        }

        switch (args[0]) {
            case "none":
                processor.setTarget(sender, name, null, look);
                CamCommandBase.send(sender, prefix + "remove");
                break;
            case "self":
                processor.setTarget(sender, name, new CamTarget.SelfTarget(), look);
                CamCommandBase.send(sender, prefix + "self");
                break;
            case "player":
            case "entity":
                if (args.length < 2)
                    throw new CommandException("cam.usage.target", args[0]);
                Entity entity = EntityFinder.find(sender, args[1]);
                if (entity == null)
                    throw new CommandException("cam.target.not_found", args[1]);
                if (entity instanceof EntityPlayer) {
                    processor.setTarget(sender, name, new CamTarget.PlayerTarget(entity.getUniqueID()), look);
                    CamCommandBase.send(sender, prefix + "player", entity.getCommandSenderName());
                } else {
                    if (args[0].equals("player"))
                        throw new CommandException("cam.target.not_found", args[1]);
                    processor.setTarget(sender, name, new CamTarget.EntityTarget(entity.getUniqueID()), look);
                    CamCommandBase.send(sender, prefix + "entity", entity.getCommandSenderName());
                }
                break;
            case "pos":
                if (args.length < 4)
                    throw new CommandException("cam.usage.target", "pos");
                ChunkCoordinates origin = sender.getPlayerCoordinates();
                int x = (int) Math.floor(CamCommandBase.parseRelative(origin.posX, args[1]));
                int y = (int) Math.floor(CamCommandBase.parseRelative(origin.posY, args[2]));
                int z = (int) Math.floor(CamCommandBase.parseRelative(origin.posZ, args[3]));
                processor.setTarget(sender, name, new CamTarget.BlockTarget(x, y, z), look);
                CamCommandBase.send(sender, prefix + "pos", x + " " + y + " " + z);
                break;
            case "select":
                if (!processor.canSelectTarget())
                    throw new CommandException("cam.target.no_select");
                processor.selectTarget(sender, name, look);
                CamCommandBase.send(sender, prefix + "select");
                return;
            default:
                throw new CommandException("cam.usage.target", args[0]);
        }
        processor.markDirty(sender);
    }

    private static void info(ICommandSender sender, CamScene scene) {
        CamCommandBase.send(sender, "scene.info.points", scene.points.size());
        CamCommandBase.send(sender, "scene.output.duration", DurationParser.print(scene.duration));
        CamCommandBase.send(sender, "scene.output.mode", new ChatComponentTranslation("cam.mode." + scene.mode));
        CamCommandBase.send(sender, "scene.output.interpolation", new ChatComponentTranslation("cam.interpolation." + scene.interpolation.id));
        if (scene.loop == 0)
            CamCommandBase.send(sender, "scene.output.no_loop");
        else if (scene.loop < 0)
            CamCommandBase.send(sender, "scene.output.endless");
        else
            CamCommandBase.send(sender, "scene.output.loops", scene.loop);
        if (scene.lookTarget != null)
            CamCommandBase.send(sender, "scene.output.look", scene.lookTarget.print(sender.getEntityWorld()));
        if (scene.posTarget != null)
            CamCommandBase.send(sender, "scene.output.follow", scene.posTarget.print(sender.getEntityWorld()));
        for (int i = 0; i < scene.points.size(); i++) {
            CamPoint point = scene.points.get(i);
            CamCommandBase.send(sender, "scene.info.point", i + 1, String.format("%.2f", point.x), String.format("%.2f", point.y), String.format("%.2f", point.z), String.format("%.1f",
                point.rotationYaw), String.format("%.1f", point.rotationPitch), String.format("%.1f", point.roll), String.format("%.1f", point.zoom));
        }
    }

    /** @param args everything after the scene name, the last entry is the word being typed */
    public static List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return CamCommandBase.matching(args[0], SUB_COMMANDS);
        if (args.length == 2) {
            switch (args[0]) {
                case "mode":
                    return CamCommandBase.matching(args[1], CamScene.MODE_DEFAULT, CamScene.MODE_OUTSIDE);
                case "interpolation":
                    return CamCommandBase.matching(args[1], CamInterpolation.names());
                case "smooth_start":
                case "distance_timing":
                case "hide_gui":
                case "return_start":
                    return CamCommandBase.matching(args[1], "true", "false");
                case "spinning_fix":
                    return CamCommandBase.matching(args[1], CamPitchMode.names());
                case "timer":
                    return CamCommandBase.matching(args[1], CamScene.TIMER_REAL, CamScene.TIMER_TICKS);
                case "target":
                case "follow":
                    return CamCommandBase.matching(args[1], "none", "self", "select", "player", "entity", "pos");
                case "pitch":
                case "yaw":
                case "pos":
                    return CamCommandBase.matching(args[1], "step");
                case "duration":
                    return CamCommandBase.matching(args[1], "10s", "30s", "1m", "500ms");
                default:
                    break;
            }
        }
        return new ArrayList<>();
    }

}
