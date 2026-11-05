package team.creative.cmdcam.client;

import java.util.ArrayDeque;
import java.util.Queue;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.CommonProxy;
import team.creative.cmdcam.client.gui.GuiPathList;
import team.creative.cmdcam.client.path.LocalPaths;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.ServerPaths;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.util.EntityUtil;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    /** Packet handlers and connection events are called on a network thread in 1.7.10, this hands them over to the game thread. */
    private static final Queue<Runnable> TASKS = new ArrayDeque<>();

    public static void schedule(Runnable task) {
        synchronized (TASKS) {
            TASKS.add(task);
        }
    }

    public static void runTasks() {
        while (true) {
            Runnable task;
            synchronized (TASKS) {
                task = TASKS.poll();
            }
            if (task == null)
                return;
            task.run();
        }
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        LocalPaths.init(mc.mcDataDir, event.getModConfigurationDirectory());
        int migrated = LocalPaths.migrateLegacyFile(mc.getSession().getUsername());
        if (migrated > 0)
            CMDCam.LOGGER.info("Moved {} saved scenes to the {} folder", migrated, LocalPaths.FOLDER_NAME);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        KeyHandler.registerKeys();
        CamEventHandlerClient handler = new CamEventHandlerClient();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);
        ClientCommandHandler.instance.registerCommand(new CommandCam());
    }

    @Override
    public Vec3d localPlayerEyes(World world, float partialTicks) {
        if (!world.isRemote)
            return null;
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null ? EntityUtil.eyePosition(mc.thePlayer, partialTicks) : null;
    }

    @Override
    public void handleClientPacket(final CamPacket packet) {
        schedule(new Runnable() {
            @Override
            public void run() {
                process(packet);
            }
        });
    }

    private static void process(CamPacket packet) {
        Minecraft mc = Minecraft.getMinecraft();
        switch (packet.action) {
            case CONNECT:
                CMDCamClient.setServerAvailability();
                break;
            case SET:
                if (packet.nbt != null) {
                    CMDCamClient.set(SceneNBT.load(packet.nbt));
                    CommandCam.send(mc.thePlayer, "scene.load", packet.id);
                }
                break;
            case LOAD_FAIL:
                if (!PathActions.loadFromFolder(packet.id))
                    CommandCam.send(mc.thePlayer, "scene.load_fail", packet.id);
                break;
            case LIST_ENTRY:
                ServerPaths.entry(packet.id, packet.nbt);
                break;
            case LIST_END:
                ServerPaths.finish(packet.nbt);
                break;
            case RESULT:
                if (packet.nbt != null) {
                    if (mc.currentScreen instanceof GuiPathList)
                        ((GuiPathList) mc.currentScreen).status(I18n.format(packet.id, CamPacket.resultArgs(packet.nbt)), !packet.nbt.getBoolean("success"));
                    else
                        CommandCam.send(mc.thePlayer, packet.id, CamPacket.resultArgs(packet.nbt));
                }
                break;
            case START:
                if (packet.nbt != null) {
                    CamScene scene = SceneNBT.load(packet.nbt);
                    scene.setServerSynced();
                    CMDCamClient.stopServer();
                    CMDCamClient.start(scene);
                }
                break;
            case STOP:
                CMDCamClient.stopServer();
                break;
            case PAUSE:
                CMDCamClient.pause();
                break;
            case RESUME:
                CMDCamClient.resume();
                break;
            case TELEPORT:
                if (packet.nbt != null)
                    CMDCamClient.teleportTo(SceneNBT.loadPoint(packet.nbt));
                break;
            default:
                break;
        }
    }

}
