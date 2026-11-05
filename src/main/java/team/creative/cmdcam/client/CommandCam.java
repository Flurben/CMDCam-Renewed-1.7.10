package team.creative.cmdcam.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import team.creative.cmdcam.client.gui.GuiPathBrowser;
import team.creative.cmdcam.client.gui.GuiPathSave;
import team.creative.cmdcam.client.path.LocalPaths;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.ServerPaths;
import team.creative.cmdcam.common.command.CamCommandBase;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.command.SceneCommands;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.path.PathNames;
import team.creative.cmdcam.common.target.CamTarget;

/** /cam: edits and plays the scene of this client. */
@SideOnly(Side.CLIENT)
public class CommandCam extends CamCommandBase implements CamCommandProcessor {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final List<String> CLIENT_COMMANDS = Arrays.asList("start", "stop", "pause", "resume", "show", "hide", "list", "load", "save", "delete", "follow_center");

    @Override
    public String getCommandName() {
        return "cam";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cam (without arguments lists all sub commands)";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private static void help(ICommandSender sender) {
        sendUsage(sender, "/cam add [x y z [yaw pitch [roll [fov]]]]", "add a point, ~ makes a value relative");
        sendUsage(sender, "/cam insert|set <index> [x y z ...]", "add a point before the index or replace it");
        sendUsage(sender, "/cam remove <index>", "remove a point");
        sendUsage(sender, "/cam clear", "remove all points");
        sendUsage(sender, "/cam goto <index>", "teleport to a point");
        sendUsage(sender, "/cam start [duration] [loops]", "play the scene, -1 loops forever");
        sendUsage(sender, "/cam stop|pause|resume", "control the playing scene");
        sendUsage(sender, "/cam duration|loops [value]", "get or set");
        sendUsage(sender, "/cam mode [default|outside]", "get or set");
        sendUsage(sender, "/cam interpolation [" + String.join("|", CamInterpolation.names()) + "]", "get or set");
        sendUsage(sender, "/cam target|follow [none|self|select|player <name>|entity <selector>|pos <x y z>]", "look at or move with a target");
        sendUsage(sender, "/cam pitch|yaw|pos step <value>", "how fast targets are followed, 1 is instantly");
        sendUsage(sender, "/cam follow_center [x y z ...]", "where a following scene is previewed");
        sendUsage(sender, "/cam smooth_start|distance_timing|hide_gui|return_start [true|false]", "get or set");
        sendUsage(sender, "/cam spinning_fix [no_fix|fix|fix_keep_direction]", "get or set");
        sendUsage(sender, "/cam timer [realtime|ticks]", "get or set, ticks stays in sync with the game when recording");
        sendUsage(sender, "/cam show|hide [all|<interpolation>]", "preview paths, nothing means the active one");
        sendUsage(sender, "/cam save [name]", "store the path in the cmdcam-paths folder and on the server if it has the mod, without a name it opens a screen");
        sendUsage(sender, "/cam load [name]", "load a path, without a name it opens a screen to manage all paths");
        sendUsage(sender, "/cam list | delete <name>", "the paths in the cmdcam-paths folder");
        sendUsage(sender, "/cam info", "everything about the current scene");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
            return;
        }

        switch (args[0]) {
            case "start":
                start(sender, args);
                return;
            case "stop":
                CMDCamClient.stop();
                return;
            case "pause":
                CMDCamClient.pause();
                return;
            case "resume":
                CMDCamClient.resume();
                return;
            case "show":
                setVisible(sender, args, true);
                return;
            case "hide":
                setVisible(sender, args, false);
                return;
            case "list":
                List<String> names = LocalPaths.names();
                send(sender, "scenes.list", names.size(), String.join(", ", names));
                return;
            case "load":
                load(args);
                return;
            case "save":
                save(args);
                return;
            case "delete":
                if (args.length < 2)
                    throw new WrongUsageException("/cam delete <name>");
                String name = nameArgument(args);
                if (!LocalPaths.delete(name))
                    throw new CommandException("scene.remove_fail", name);
                send(sender, "scene.remove", name);
                return;
            case "follow_center":
                CMDCamClient.setTargetMarker(followCenter(sender, tail(args, 1)));
                send(sender, "scene.follow.center");
                return;
            default:
                if (!SceneCommands.process(sender, this, null, args))
                    throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void start(ICommandSender sender, String[] args) {
        CamScene scene = getScene(sender, null);
        if (args.length >= 2)
            scene.duration = parseDurationArg(args[1]);
        if (args.length >= 3)
            scene.loop = Math.max(-1, parseInteger(args[2]));
        try {
            CMDCamClient.start(CMDCamClient.createScene());
        } catch (SceneException e) {
            throw new CommandException(e.getMessage());
        }
    }

    /** The marker is absolute, so the scene is not involved. */
    private CamPoint followCenter(ICommandSender sender, String[] args) {
        if (args.length == 0)
            return CMDCamClient.createLocalPoint();
        CamScene absolute = new CamScene();
        return SceneCommands.parsePoint(sender, this, absolute, args);
    }

    private static void setVisible(ICommandSender sender, String[] args, boolean visible) {
        String suffix = visible ? "show" : "hide";
        if (args.length < 2) {
            CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = visible;
            send(sender, "scene.interpolation." + suffix + "_active");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            for (CamInterpolation interpolation : CamInterpolation.all())
                interpolation.isRenderingEnabled = visible;
            if (!visible)
                CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = false;
            send(sender, "scene.interpolation." + suffix + "_all");
            return;
        }
        CamInterpolation interpolation = CamInterpolation.get(args[1]);
        if (interpolation == null)
            throw new CommandException("cam.interpolation.unknown", args[1]);
        interpolation.isRenderingEnabled = visible;
        send(sender, "scene.interpolation." + suffix, args[1]);
    }

    /** @return everything after the sub command, names can contain spaces */
    private static String nameArgument(String[] args) {
        return String.join(" ", tail(args, 1));
    }

    /** The chat closes itself after running a command, a screen opened right now would be closed with it. */
    private static void openLater(final GuiScreen screen) {
        ClientProxy.schedule(new Runnable() {
            @Override
            public void run() {
                if (mc.thePlayer != null)
                    mc.displayGuiScreen(screen);
            }
        });
    }

    private static void load(String[] args) {
        if (args.length < 2) {
            openLater(new GuiPathBrowser());
            return;
        }
        PathActions.loadByName(nameArgument(args));
    }

    private static void save(String[] args) {
        CamScene scene;
        try {
            scene = CMDCamClient.createScene();
        } catch (SceneException e) {
            throw new CommandException(e.getMessage());
        }
        if (args.length < 2) {
            openLater(new GuiPathSave(scene));
            return;
        }
        PathActions.save(nameArgument(args), scene);
    }

    @Override
    public CamScene getScene(ICommandSender sender, String name) {
        return CMDCamClient.getScene();
    }

    @Override
    public boolean canSelectTarget() {
        return true;
    }

    @Override
    public void selectTarget(ICommandSender sender, final String name, final boolean look) throws CommandException {
        final ICommandSender selector = sender;
        if (!look)
            checkFollowTarget(getScene(sender, name), true);
        CamEventHandlerClient.startSelectionMode(new CamEventHandlerClient.TargetSelection() {
            @Override
            public void accept(CamTarget target) {
                try {
                    setTarget(selector, name, target, look);
                } catch (CommandException e) {
                    send(selector, e.getMessage(), e.getErrorOjbects());
                }
            }
        });
    }

    @Override
    public boolean canCreatePoint(ICommandSender sender) {
        return mc.thePlayer != null;
    }

    @Override
    public CamPoint createPoint(ICommandSender sender) {
        return CMDCamClient.createLocalPoint();
    }

    @Override
    public void teleport(ICommandSender sender, CamScene scene, int index, String player) {
        CMDCamClient.teleportTo(scene.points.get(index));
    }

    @Override
    public void markDirty(ICommandSender sender) {}

    @Override
    public float partialTicks() {
        return CMDCamClient.partialTicks();
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>(CLIENT_COMMANDS);
            all.addAll(SceneCommands.SUB_COMMANDS);
            return matching(args[0], all);
        }
        if (args.length == 2 && (args[0].equals("show") || args[0].equals("hide"))) {
            List<String> options = new ArrayList<>(CamInterpolation.names());
            options.add("all");
            return matching(args[1], options);
        }
        if (args.length == 2 && args[0].equals("delete"))
            return matching(args[1], LocalPaths.names());
        if (args.length == 2 && args[0].equals("load")) {
            List<String> names = LocalPaths.names();
            for (String name : ServerPaths.names()) // As far as they are known, the list only comes when the screen is opened
                if (!PathNames.contains(names, name))
                    names.add(name);
            return matching(args[1], names);
        }
        return SceneCommands.tabComplete(args);
    }

}
