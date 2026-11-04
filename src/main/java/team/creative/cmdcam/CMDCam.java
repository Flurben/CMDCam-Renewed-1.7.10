package team.creative.cmdcam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.server.CamEventHandler;
import team.creative.cmdcam.server.CommandCamServer;

@Mod(
        modid = CMDCam.MODID,
        name = "CMDCam Renewed",
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*", // Works with only the client or only the server having it installed
        guiFactory = "team.creative.cmdcam.client.CMDCamGuiFactory")
public class CMDCam {

    public static final String MODID = "cmdcam";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(clientSide = "team.creative.cmdcam.client.ClientProxy", serverSide = "team.creative.cmdcam.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CMDCamConfig.load(event.getSuggestedConfigurationFile());
        NETWORK.registerMessage(CamPacket.Handler.class, CamPacket.class, 0, Side.CLIENT);
        NETWORK.registerMessage(CamPacket.Handler.class, CamPacket.class, 1, Side.SERVER);
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        CamEventHandler handler = new CamEventHandler();
        FMLCommonHandler.instance().bus().register(handler);
        proxy.init(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandCamServer());
    }

}
