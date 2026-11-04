package team.creative.cmdcam.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.command.CamCommandBase;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.command.SceneCommands;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SceneNBT;
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
        sendUsage(sender, "/cam save|load <name>", "store scenes, on the server if it has the mod");
        sendUsage(sender, "/cam list|delete <name>", "the scenes of this client");
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
                if (CMDCamClient.serverAvailable())
                    throw new CommandException("scenes.list_fail");
                send(sender, "scenes.list", ClientSceneStorage.names().size(), String.join(", ", ClientSceneStorage.names()));
                return;
            case "load":
                load(sender, args);
                return;
            case "save":
                save(sender, args);
                return;
            case "delete":
                if (args.length < 2)
                    throw new WrongUsageException("/cam delete <name>");
                if (CMDCamClient.serverAvailable())
                    throw new CommandException("scenes.list_fail");
                if (!ClientSceneStorage.remove(args[1]))
                    throw new CommandException("scene.remove_fail", args[1]);
                send(sender, "scene.remove", args[1]);
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

    private static void load(ICommandSender sender, String[] args) {
        if (args.length < 2)
            throw new WrongUsageException("/cam load <name>");
        if (CMDCamClient.serverAvailable()) {
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.GET, args[1], null));
            return;
        }
        CamScene scene = ClientSceneStorage.get(args[1]);
        if (scene == null)
            throw new CommandException("scene.load_fail", args[1]);
        CMDCamClient.set(scene);
        send(sender, "scene.load", args[1]);
    }

    private static void save(ICommandSender sender, String[] args) {
        if (args.length < 2)
            throw new WrongUsageException("/cam save <name>");
        CamScene scene;
        try {
            scene = CMDCamClient.createScene();
        } catch (SceneException e) {
            throw new CommandException(e.getMessage());
        }
        if (CMDCamClient.serverAvailable())
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.SET, args[1], SceneNBT.save(scene)));
        else {
            ClientSceneStorage.set(args[1], scene);
            send(sender, "scene.save", args[1]);
        }
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
        if (args.length == 2 && (args[0].equals("load") || args[0].equals("delete")) && !CMDCamClient.serverAvailable())
            return matching(args[1], new ArrayList<>(ClientSceneStorage.names()));
        return SceneCommands.tabComplete(args);
    }

}
