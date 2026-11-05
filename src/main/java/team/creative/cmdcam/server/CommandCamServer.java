package team.creative.cmdcam.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerSelector;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.command.CamCommandBase;
import team.creative.cmdcam.common.command.CamCommandProcessor;
import team.creative.cmdcam.common.command.SceneCommands;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.util.EntityUtil;

/** /cam-server: scenes saved in the world which can be played for other players, also from command blocks. */
public class CommandCamServer extends CamCommandBase implements CamCommandProcessor {

    private static final List<String> SUB_COMMANDS = Arrays.asList("start", "stop", "pause", "resume", "get", "list", "clear", "remove", "create");

    @Override
    public String getCommandName() {
        return "cam-server";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cam-server <start|stop|pause|resume|get|list|clear|remove|create>";
    }

    /** Level 2, so command blocks are allowed to use it. */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, "/cam-server start <scene> <players> [duration] [loops]", "play a saved scene for players");
            sendUsage(sender, "/cam-server stop|pause|resume <players>", "control a playing scene");
            sendUsage(sender, "/cam-server get <scene> <setting> ...", "edit a saved scene, the settings are the same as for /cam");
            sendUsage(sender, "/cam-server create|remove <scene>", "create or delete a saved scene");
            sendUsage(sender, "/cam-server list|clear", "list or delete all saved scenes");
            return;
        }

        switch (args[0]) {
            case "start":
                start(sender, args);
                break;
            case "stop":
                sendToPlayers(sender, args, CamPacket.Action.STOP);
                break;
            case "pause":
                sendToPlayers(sender, args, CamPacket.Action.PAUSE);
                break;
            case "resume":
                sendToPlayers(sender, args, CamPacket.Action.RESUME);
                break;
            case "get":
                if (args.length < 3) {
                    if (args.length == 2) { // Without a setting it reports the scene
                        SceneCommands.process(sender, this, args[1], new String[] { "info" });
                        return;
                    }
                    throw new WrongUsageException("/cam-server get <scene> <setting> ...");
                }
                if (!SceneCommands.process(sender, this, args[1], tail(args, 2)))
                    throw new WrongUsageException("/cam-server get <scene> <setting> ...");
                break;
            case "list":
                Collection<String> names = CMDCamServer.data().names();
                send(sender, "scenes.list", names.size(), String.join(", ", names));
                break;
            case "clear":
                CMDCamServer.data().clear();
                send(sender, "scenes.clear");
                break;
            case "remove":
                if (args.length < 2)
                    throw new WrongUsageException("/cam-server remove <scene>");
                if (CMDCamServer.data().remove(args[1]))
                    send(sender, "scene.remove", args[1]);
                else
                    throw new CommandException("scene.remove_fail", args[1]);
                break;
            case "create":
                if (args.length < 2)
                    throw new WrongUsageException("/cam-server create <scene>");
                if (CMDCamServer.data().get(args[1]) != null)
                    throw new CommandException("scene.exists", args[1]);
                SavedPath created = new SavedPath(args[1], CamScene.createDefault());
                created.creator = sender.getCommandSenderName();
                if (sender instanceof EntityPlayerMP)
                    created.creatorId = ((EntityPlayerMP) sender).getUniqueID().toString();
                created.savedAt = System.currentTimeMillis();
                CMDCamServer.data().set(created);
                send(sender, "scene.create", args[1]);
                break;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void start(ICommandSender sender, String[] args) {
        if (args.length < 3)
            throw new WrongUsageException("/cam-server start <scene> <players> [duration] [loops]");
        CamScene scene = getScene(sender, args[1]);
        if (scene.points.isEmpty())
            throw new CommandException("scene.create_fail");

        // The arguments only apply to this run, playing a scene must not change what is saved
        scene = scene.copy();
        if (args.length >= 4)
            scene.duration = parseDurationArg(args[3]);
        if (args.length >= 5)
            scene.loop = Math.max(-1, parseInteger(args[4]));

        CamPacket packet = new CamPacket(CamPacket.Action.START, args[1], SceneNBT.save(scene));
        for (EntityPlayerMP player : players(sender, args[2]))
            CMDCam.NETWORK.sendTo(packet, player);
    }

    private void sendToPlayers(ICommandSender sender, String[] args, CamPacket.Action action) {
        if (args.length < 2)
            throw new WrongUsageException("/cam-server " + args[0] + " <players>");
        CamPacket packet = new CamPacket(action);
        for (EntityPlayerMP player : players(sender, args[1]))
            CMDCam.NETWORK.sendTo(packet, player);
    }

    private static List<EntityPlayerMP> players(ICommandSender sender, String selector) {
        List<EntityPlayerMP> players = new ArrayList<>();
        if (PlayerSelector.hasArguments(selector) || selector.startsWith("@")) {
            try {
                EntityPlayerMP[] matched = PlayerSelector.matchPlayers(sender, selector);
                if (matched != null)
                    players.addAll(Arrays.asList(matched));
            } catch (IllegalArgumentException e) { // Vanilla does not check its arguments, for example @r[c=-5]
                throw new CommandException("cam.target.not_found", selector);
            }
        } else
            players.add(getPlayer(sender, selector));
        return players;
    }

    @Override
    public CamScene getScene(ICommandSender sender, String name) throws CommandException {
        CamScene scene = CMDCamServer.data().get(name);
        if (scene == null)
            throw new CommandException("scene.load_fail", name);
        return scene;
    }

    @Override
    public boolean canSelectTarget() {
        return false;
    }

    @Override
    public void selectTarget(ICommandSender sender, String name, boolean look) {}

    @Override
    public boolean canCreatePoint(ICommandSender sender) {
        return sender instanceof Entity;
    }

    @Override
    public CamPoint createPoint(ICommandSender sender) {
        return EntityUtil.createPoint((Entity) sender, 1, 0, CamPoint.DEFAULT_FOV);
    }

    @Override
    public void teleport(ICommandSender sender, CamScene scene, int index, String player) throws CommandException {
        CamPacket packet = new CamPacket(CamPacket.Action.TELEPORT, "", SceneNBT.savePoint(scene.points.get(index)));
        for (EntityPlayerMP target : players(sender, player))
            CMDCam.NETWORK.sendTo(packet, target);
    }

    @Override
    public void markDirty(ICommandSender sender) {
        CMDCamServer.data().markDirty();
    }

    @Override
    public float partialTicks() {
        return 1;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1)
            return matching(args[0], SUB_COMMANDS);
        if (args.length == 2 && (args[0].equals("start") || args[0].equals("get") || args[0].equals("remove")))
            return matching(args[1], new ArrayList<>(CMDCamServer.data().names()));
        if (args.length >= 3 && args[0].equals("get"))
            return SceneCommands.tabComplete(tail(args, 2));
        return null;
    }

}
