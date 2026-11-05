package team.creative.cmdcam.ingametest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.client.CamEventHandlerClient;
import team.creative.cmdcam.client.CamMouseHelper;
import team.creative.cmdcam.client.CameraEntity;
import team.creative.cmdcam.client.KeyHandler;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.target.CamTarget;

/**
 * Plays through the mod inside a real game and writes the results to cmdcam-ingametest.txt. Only compiled with -PingameTest and only active with -Dcmdcam.ingametest=true.
 * The script runs on its own thread, everything touching the game is handed to the client or server thread.
 */
@Mod(modid = "cmdcam_ingametest", name = "CMDCam In-Game Test", version = "1", dependencies = "required-after:cmdcam", acceptableRemoteVersions = "*")
public class InGameTestMod {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** A big modpack needs minutes to load and to create a world. */
    private static final boolean SLOW = Boolean.getBoolean("cmdcam.ingametest.slow");
    private static final long TIMEOUT_SECONDS = SLOW ? 1200 : 300;
    /** Once in a world anything taking this long is stuck, there is no reason to wait for the limits above. */
    private static final long TASK_TIMEOUT_SECONDS = SLOW ? 120 : 45;
    private static final long WATCHDOG_MILLIS = (SLOW ? 60 : 15) * 60 * 1000;
    private static final long LOADING_STALL_MILLIS = (SLOW ? 12 : 3) * 60 * 1000;
    private static final long STALL_MILLIS = (SLOW ? 3 : 1) * 60 * 1000;
    private static final long TEST_LIMIT_MILLIS = (SLOW ? 8 : 2) * 60 * 1000;
    private static final double SPAWN_X = 0.5, SPAWN_Y = 100, SPAWN_Z = 0.5;

    private final PathTests paths = new PathTests(this);

    private final Queue<FutureTask<?>> clientTasks = new ConcurrentLinkedQueue<>();
    private final Queue<FutureTask<?>> serverTasks = new ConcurrentLinkedQueue<>();
    private final List<int[]> heldKeys = new CopyOnWriteArrayList<>();

    private PrintWriter out;
    private int passed, failed;
    private final List<String> problems = new ArrayList<>();
    private String world;

    private volatile List<Frame> recording;
    private volatile String pendingShot;
    private volatile CountDownLatch shotDone;
    volatile boolean watchGl;
    private volatile long lastProgress;
    /** False while the game is loading or a world starts, these can take minutes and do not answer in the meantime. */
    private volatile boolean inWorld;
    private volatile boolean everInWorld;
    private volatile long testStarted;
    private volatile String testName;
    volatile int glErrors;
    volatile int matrixDepth;

    private Method fovMethod;
    private Field rollField;
    private ByteBuffer keyDownBuffer;

    private static final class Frame {
        long nanos;
        double x, y, z, renderY, yaw, pitch, roll, fov, handFov;
        boolean playing, hideGui, paused;
        int third;
        Entity view;
        int mouseDx, mouseDy;
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (!Boolean.getBoolean("cmdcam.ingametest"))
            return;
        // How a launcher starts the game is hard to change between two runs, so a run can be chosen with this file
        File options = new File(mc.mcDataDir, "cmdcam-ingametest.properties");
        if (options.isFile())
            try (java.io.InputStream input = new java.io.FileInputStream(options)) {
                System.getProperties().load(input);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        FMLCommonHandler.instance().bus().register(this);
        Thread script = new Thread(this::runScript, "CMDCam in-game test");
        script.setDaemon(true);
        script.start();
        Thread watchdog = new Thread(() -> {
            long begin = System.currentTimeMillis();
            lastProgress = begin;
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                long stall = inWorld || everInWorld ? STALL_MILLIS : LOADING_STALL_MILLIS; // Only the first world can take long, the game has loaded by then
                boolean testTooLong = inWorld && testStarted > 0 && now - testStarted > TEST_LIMIT_MILLIS;
                if (now - lastProgress > stall || now - begin > WATCHDOG_MILLIS || testTooLong) {
                    line("ABORT  " + (testTooLong ? "test " + testName + " runs for " + (now - testStarted) / 1000 + " s" : "no progress for " + (now - lastProgress) / 1000 + " s")
                            + ", screen " + mc.currentScreen + ", state of all threads:");
                    for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                        line("THREAD " + entry.getKey().getName() + " " + entry.getKey().getState());
                        StackTraceElement[] trace = entry.getValue();
                        for (int i = 0; i < Math.min(40, trace.length); i++)
                            line("         at " + trace[i]);
                    }
                    FMLCommonHandler.instance().exitJava(3, true);
                }
            }
        }, "CMDCam test watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ---------------------------------------------------------------- hand-over to the game threads

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END)
            drain(clientTasks);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
            drain(serverTasks);
    }

    /** Runs early and only for the keys of this test: Minecraft reads these keys from the keyboard state directly. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void renderStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && keyDownBuffer != null)
            for (int[] key : heldKeys)
                keyDownBuffer.put(key[0], (byte) 1);
    }

    @SubscribeEvent
    public void renderEnd(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (watchGl && GL11.glGetError() != GL11.GL_NO_ERROR)
            glErrors++;
        // Tasks also run in the client tick, where the depth is different. Always compare the same place in a frame
        matrixDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        List<Frame> frames = recording;
        if (frames != null && mc.thePlayer != null)
            frames.add(capture());
        String shot = pendingShot;
        if (shot != null) {
            pendingShot = null;
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, "cmdcam-" + shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            shotDone.countDown();
        }
        drain(clientTasks);
    }

    private static void drain(Queue<FutureTask<?>> tasks) {
        FutureTask<?> task;
        while ((task = tasks.poll()) != null)
            task.run();
    }

    private <T> T on(Queue<FutureTask<?>> queue, Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        queue.add(task);
        try {
            return task.get(inWorld ? TASK_TIMEOUT_SECONDS : TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("game thread did not respond: " + e, e);
        }
    }

    <T> T client(Callable<T> callable) {
        return on(clientTasks, callable);
    }

    void clientDo(Runnable runnable) {
        client(() -> {
            runnable.run();
            return null;
        });
    }

    <T> T server(Callable<T> callable) {
        MinecraftServer running = MinecraftServer.getServer();
        if (running == null || !running.isServerRunning()) // Would not get an answer, no need to wait for that
            throw new RuntimeException("the server is not running");
        return on(serverTasks, callable);
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    boolean waitFor(long millis, Callable<Boolean> condition) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (client(condition))
                return true;
            sleep(25);
        }
        return false;
    }

    // ---------------------------------------------------------------- reporting

    private synchronized void line(String text) {
        lastProgress = System.currentTimeMillis();
        System.out.println("[cmdcam-test] " + text);
        if (out != null) {
            out.println(text);
            out.flush();
        }
    }

    void info(String text) {
        line("INFO   " + text);
    }

    void check(boolean condition, String message) {
        if (!condition)
            problems.add(message);
    }

    void near(String name, double expected, double actual, double tolerance) {
        if (!(Math.abs(expected - actual) <= tolerance))
            problems.add(name + ": expected " + expected + " +-" + tolerance + " but was " + actual);
    }

    private interface Body {

        void run() throws Exception;
    }

    private void test(String name, Body body) {
        String only = System.getProperty("cmdcam.ingametest.only");
        if (only != null) { // Parts of names, separated by commas
            boolean selected = false;
            for (String part : only.split(","))
                selected |= name.contains(part);
            if (!selected)
                return;
        }
        problems.clear();
        long start = System.currentTimeMillis();
        testName = name;
        testStarted = start;
        line("START  " + name);
        try {
            reset();
            body.run();
        } catch (Throwable e) {
            StringBuilder trace = new StringBuilder(e.toString());
            StackTraceElement[] elements = e.getStackTrace();
            for (int i = 0; i < Math.min(6, elements.length); i++)
                trace.append(" | ").append(elements[i]);
            problems.add("EXCEPTION " + trace);
        }
        testStarted = 0;
        // Every following test would run into its time limits
        boolean lostWorld = !client(() -> mc.thePlayer != null && mc.theWorld != null);
        if (lostWorld)
            problems.add("the game is not in the world anymore, screen " + client(() -> mc.currentScreen));
        long took = System.currentTimeMillis() - start;
        if (problems.isEmpty()) {
            passed++;
            line("PASS   " + name + " (" + took + " ms)");
        } else {
            failed++;
            line("FAIL   " + name + " (" + took + " ms)");
            int shown = 0;
            for (String problem : problems) {
                if (shown++ == 15) {
                    line("         ... and " + (problems.size() - 15) + " more");
                    break;
                }
                line("         " + problem);
            }
        }
        if (lostWorld) {
            line("ABORT  lost the world in " + name);
            FMLCommonHandler.instance().exitJava(2, true);
        }
    }

    // ---------------------------------------------------------------- game helpers

    private static ICommand command(String name) {
        return (ICommand) ClientCommandHandler.instance.getCommands().get(name);
    }

    /** Runs on the client thread and returns the unexpected exception, CommandExceptions are normal answers for bad input. */
    void cam(String command) {
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split(command)));
    }

    private static String[] split(String command) {
        return command.isEmpty() ? new String[0] : command.split(" ");
    }

    void camRejected(String command) {
        Boolean rejected = client(() -> {
            try {
                command("cam").processCommand(mc.thePlayer, split(command));
                return false;
            } catch (CommandException e) {
                return true;
            }
        });
        check(rejected, "/cam " + command + " should have been rejected");
    }

    void serverCommand(String command) {
        server(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            ICommandSender sender = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            server.getCommandManager().executeCommand(sender, command);
            return null;
        });
    }

    private void camServer(String command) {
        server(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            ICommandSender sender = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            ((ICommand) server.getCommandManager().getCommands().get("cam-server")).processCommand(sender, split(command));
            return null;
        });
    }

    private void camServerRejected(String command) {
        Boolean rejected = server(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            ICommandSender sender = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            try {
                ((ICommand) server.getCommandManager().getCommands().get("cam-server")).processCommand(sender, split(command));
                return false;
            } catch (CommandException e) {
                return true;
            }
        });
        check(rejected, "/cam-server " + command + " should have been rejected");
    }

    CamScene scene() {
        return client(CMDCamClient::getScene);
    }

    void reset() {
        clientDo(() -> {
            if (CMDCamClient.isPlaying())
                CMDCamClient.stopServer();
            if (mc.currentScreen != null)
                mc.displayGuiScreen(null);
            CamScene fresh = CamScene.createDefault();
            fresh.smoothBeginning = false;
            CMDCamClient.getScene().set(fresh);
            CMDCamClient.getScene().points.clear();
            CMDCamClient.setTargetMarker(null);
            CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION = false;
            for (CamInterpolation interpolation : CamInterpolation.all())
                interpolation.isRenderingEnabled = false;
            CamEventHandlerClient.resetFOV();
            CamEventHandlerClient.resetRoll();
            if (mc.thePlayer != null) {
                CMDCamClient.protectServerMove(); // This test moves the player back as well
                mc.gameSettings.hideGUI = false;
                mc.gameSettings.thirdPersonView = 0;
                mc.gameSettings.smoothCamera = false;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.inGameHasFocus = true;
                mc.thePlayer.capabilities.isFlying = true;
                mc.thePlayer.setPositionAndRotation(SPAWN_X, SPAWN_Y, SPAWN_Z, 0, 0);
                mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
            }
        });
        heldKeys.clear();
        sleep(120); // A few frames, so the position reached the server and nothing from the previous test is left
    }

    void addPoint(double x, double y, double z, double yaw, double pitch, double roll, double fov) {
        clientDo(() -> CMDCamClient.getScene().points.add(new CamPoint(x, y, z, yaw, pitch, roll, fov)));
    }

    void addLine() {
        addPoint(0, 100, 0, 0, 0, 0, 70);
        addPoint(30, 100, 0, 0, 0, 0, 70);
        addPoint(30, 100, 30, 0, 0, 0, 70);
    }

    private Frame capture() {
        Frame frame = new Frame();
        frame.nanos = System.nanoTime();
        frame.playing = CMDCamClient.isPlaying();
        Entity view = mc.renderViewEntity;
        frame.view = view;
        frame.x = view.posX;
        frame.y = view.posY;
        frame.z = view.posZ;
        frame.renderY = RenderManager.renderPosY;
        frame.yaw = view.rotationYaw;
        frame.pitch = view.rotationPitch;
        frame.hideGui = mc.gameSettings.hideGUI;
        frame.third = mc.gameSettings.thirdPersonView;
        frame.paused = mc.isGamePaused();
        frame.roll = roll();
        frame.fov = fov(true);
        frame.handFov = fov(false);
        mc.mouseHelper.mouseXYChange();
        frame.mouseDx = mc.mouseHelper.deltaX;
        frame.mouseDy = mc.mouseHelper.deltaY;
        return frame;
    }

    private float fov(boolean useSetting) {
        try {
            return (Float) fovMethod.invoke(mc.entityRenderer, 1F, useSetting);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private float roll() {
        try {
            return rollField.getFloat(mc.entityRenderer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Starts a scene with this command and records frames until it is over. */
    private List<Frame> play(String command, long limitMillis) {
        List<Frame> frames = new CopyOnWriteArrayList<>();
        clientDo(() -> {
            recording = frames;
            command("cam").processCommand(mc.thePlayer, split(command));
        });
        waitFor(limitMillis, () -> !CMDCamClient.isPlaying());
        sleep(60);
        recording = null;
        return frames;
    }

    private static List<Frame> playing(List<Frame> frames) {
        List<Frame> result = new ArrayList<>();
        for (Frame frame : frames)
            if (frame.playing)
                result.add(frame);
        return result;
    }

    private static double seconds(List<Frame> frames) {
        return frames.size() < 2 ? 0 : (frames.get(frames.size() - 1).nanos - frames.get(0).nanos) / 1E9;
    }

    private void checkFinite(List<Frame> frames, String name) {
        for (Frame frame : frames)
            if (!finite(frame.x) || !finite(frame.y) || !finite(frame.z) || !finite(frame.yaw) || !finite(frame.pitch) || !finite(frame.roll) || !finite(frame.fov)) {
                problems.add(name + ": invalid value in a frame");
                return;
            }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double maxStep(List<Frame> frames) {
        double max = 0;
        for (int i = 1; i < frames.size(); i++) {
            Frame a = frames.get(i - 1);
            Frame b = frames.get(i);
            max = Math.max(max, Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)));
        }
        return max;
    }

    void shot(String name) {
        shotDone = new CountDownLatch(1);
        pendingShot = name;
        try {
            check(shotDone.await(30, TimeUnit.SECONDS), "screenshot " + name + " was not taken");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void hold(KeyBinding key, long millis) {
        int[] entry = { key.getKeyCode() };
        heldKeys.add(entry);
        sleep(millis);
        heldKeys.remove(entry);
        sleep(50);
    }

    private void press(KeyBinding key) {
        clientDo(() -> KeyBinding.onTick(key.getKeyCode()));
    }

    private void createWorld() {
        world = "cmdcamtest" + System.currentTimeMillis();
        startWorld(world);
        serverCommand("/gamerule doMobSpawning false");
        serverCommand("/gamerule doDaylightCycle false");
        serverCommand("/time set 6000");
        serverCommand("/weather clear 100000");
    }

    /** Creates a new world and joins it. */
    void startWorld(String name) {
        inWorld = false;
        clientDo(() -> {
            mc.gameSettings.pauseOnLostFocus = false;
            WorldSettings settings = new WorldSettings(12345L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
            settings.enableCommands();
            mc.launchIntegratedServer(name, name, settings);
        });
        joined();
    }

    void joined() {
        if (!waitFor(SLOW ? TIMEOUT_SECONDS * 1000 : 240000, () -> mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null && MinecraftServer.getServer() != null && !MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList.isEmpty()))
            throw new RuntimeException("could not join the world");
        clientDo(() -> mc.inGameHasFocus = true);
        sleep(1000);
        inWorld = true;
        everInWorld = true;
    }

    void leave() {
        clientDo(() -> {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        });
        sleep(500);
    }

    void rejoin() {
        inWorld = false;
        clientDo(() -> mc.launchIntegratedServer(world, world, null));
        joined();
    }

    // ---------------------------------------------------------------- the tests

    private void runScript() {
        try {
            File file = new File(mc.mcDataDir, "cmdcam-ingametest.txt");
            out = new PrintWriter(new FileOutputStream(file, false), true);
            line("BEGIN  java " + System.getProperty("java.version") + " lwjgl " + org.lwjgl.Sys.getVersion());

            // Tasks only run once loading has finished. Modpacks replace the main menu with their own class
            waitFor(TIMEOUT_SECONDS * 1000, () -> mc.currentScreen != null);
            line("REACHED main menu");

            fovMethod = ReflectionHelper.findMethod(EntityRenderer.class, mc.entityRenderer, new String[] { "getFOVModifier", "func_78481_a" }, float.class, boolean.class);
            fovMethod.setAccessible(true);
            rollField = ReflectionHelper.findField(EntityRenderer.class, "camRoll", "field_78495_O");
            try {
                Field buffer = Keyboard.class.getDeclaredField("keyDownBuffer");
                buffer.setAccessible(true);
                keyDownBuffer = (ByteBuffer) buffer.get(null);
            } catch (Throwable e) {
                info("the keyboard state cannot be simulated here, held key tests are skipped: " + e);
            }

            createWorld();
            line("REACHED world");

            test("linear path stays exactly on its points", this::testLinearAccuracy);
            test("all interpolations", this::testInterpolations);
            test("timer in ticks", this::testTickTimer);
            test("loop counts", this::testLoops);
            test("endless loop and stop", this::testEndlessLoop);
            test("zoom and roll while playing", this::testZoomRoll);
            test("spinning fix and scene not modified", this::testSpinning);
            test("smooth start", this::testSmoothStart);
            test("distance timing", this::testDistanceTiming);
            test("keys add, start, stop, clear", this::testKeys);
            test("held keys zoom and roll", this::testHeldKeys);
            test("pause and resume", this::testPause);
            test("pause menu holds the scene", this::testPauseMenu);
            test("settings restored", this::testRestoredSettings);
            test("outside mode", this::testOutside);
            test("look at block", this::testLookBlock);
            test("look at and follow entity, entity removed", this::testEntityTargets);
            test("relative points", this::testRelativePoints);
            test("goto", this::testGoto);
            test("target selection clicks", this::testSelection);
            test("fast path over large distance", this::testFastPath);
            test("far jumps do not freeze the server", this::testFarJumps);
            test("death while playing", this::testDeath);
            test("dimension change while playing", this::testDimension);
            test("save and load through the server", this::testSaveLoad);
            test("paths: stored in the folder of the game", paths::testStorage);
            test("paths: move between worlds", paths::testBetweenWorlds);
            test("paths: list opens from chat, sorts and filters", paths::testList);
            test("paths: load, duplicate, rename, delete in the list", paths::testRowActions);
            test("paths: advanced load moves the whole path", paths::testAdvancedLoad);
            test("paths: save screen", paths::testSaveScreen);
            test("paths: too large for one packet", paths::testLarge);
            test("paths: folder button", paths::testFolderButton);
            test("cam-server commands", this::testServerCommands);
            test("server scene playback", this::testServerPlayback);
            test("bad command input", this::testFuzzClient);
            test("bad server command input", this::testFuzzServer);
            test("tab completion", this::testTabCompletion);
            test("edge case scenes", this::testEdgeScenes);
            test("rapid start and stop", this::testRapid);
            test("large scene", this::testLargeScene);
            test("path preview rendering", this::testPreview);
            test("screenshots", this::testScreenshots);
            test("disconnect while playing", this::testDisconnect);

            line("DONE   passed=" + passed + " failed=" + failed);
            try {
                clientDo(() -> {
                    if (mc.theWorld != null) {
                        mc.theWorld.sendQuittingDisconnectingPacket();
                        mc.loadWorld(null);
                    }
                    mc.displayGuiScreen(new GuiMainMenu());
                });
                sleep(1500);
            } finally {
                clientDo(mc::shutdown);
                // GTNH asks for confirmation in a dialog before it quits. Nothing is left to do, so nothing may hold the game open
                sleep(20000);
                line("INFO   the game did not close by itself, ending it");
                FMLCommonHandler.instance().exitJava(0, true);
            }
        } catch (Throwable e) {
            line("ABORT  " + e);
            e.printStackTrace();
            if (out != null)
                e.printStackTrace(out);
            FMLCommonHandler.instance().exitJava(2, true);
        }
    }

    /** Shortest distance of a point to the line through the corners, in the plane y = 100. */
    private static double offPath(Frame frame, double[][] corners) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < corners.length; i++) {
            double[] a = corners[i - 1];
            double[] b = corners[i];
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double t = Math.max(0, Math.min(1, ((frame.x - a[0]) * dx + (frame.z - a[1]) * dz) / (dx * dx + dz * dz)));
            double ex = a[0] + dx * t - frame.x, ez = a[1] + dz * t - frame.z;
            best = Math.min(best, Math.sqrt(ex * ex + ez * ez));
        }
        return best;
    }

    private void testLinearAccuracy() {
        addLine();
        cam("interpolation linear");
        List<Frame> all = play("start 3s", 8000);
        List<Frame> frames = playing(all);
        info("linear: " + frames.size() + " frames in " + seconds(frames) + " s");
        check(frames.size() > 10, "only " + frames.size() + " frames");
        near("duration", 3, seconds(frames), 0.3);
        checkFinite(frames, "linear");
        double[][] corners = { { 0, 0 }, { 30, 0 }, { 30, 30 } };
        double maxOff = 0, maxY = 0, maxRender = 0, maxMouse = 0;
        for (Frame frame : frames) {
            maxOff = Math.max(maxOff, offPath(frame, corners));
            maxY = Math.max(maxY, Math.abs(frame.y - 100));
            maxRender = Math.max(maxRender, Math.abs(frame.renderY - 100));
            maxMouse = Math.max(maxMouse, Math.abs(frame.mouseDx) + Math.abs(frame.mouseDy));
            check(frame.view == client(() -> mc.thePlayer), "view entity is not the player in default mode");
            check(frame.hideGui, "HUD is shown while hide_gui is on");
        }
        near("distance to the path", 0, maxOff, 0.001);
        near("height of the player (upstream #13)", 0, maxY, 0.001);
        near("height of the rendered camera (upstream #13)", 0, maxRender, 0.001);
        near("mouse movement while playing (upstream #51)", 0, maxMouse, 0);
        near("speed", 30 * 2 / 3D / 60, maxStep(frames) / Math.max(1, 1), 3);
        Frame last = frames.get(frames.size() - 1);
        near("last frame close to the end x", 30, last.x, 2);
        near("last frame close to the end z", 30, last.z, 2);
        check(client(() -> mc.mouseHelper instanceof CamMouseHelper), "mouse helper is not installed");
        check(!client(CMDCamClient::isPlaying), "scene did not end");
    }

    private void testInterpolations() {
        double[][] points = { { 0, 100, 0 }, { 20, 110, 5 }, { 25, 100, 25 }, { -5, 105, 30 } };
        for (String name : new String[] { "linear", "cubic", "hermite", "cosine", "circular", "invcircular" }) {
            clientDo(() -> CMDCamClient.getScene().points.clear());
            for (double[] point : points)
                addPoint(point[0], point[1], point[2], 0, 0, 0, 70);
            cam("interpolation " + name);
            if (name.contains("circular"))
                cam("target pos 10 100 10");
            List<Frame> frames = playing(play("start 1s", 6000));
            cam("target none");
            checkFinite(frames, name);
            near(name + " duration", 1, seconds(frames), 0.25);
            check(frames.size() > 3, name + ": too few frames");
            // At a low frame rate the first frame shows the camera a good part of a step after the start
            near(name + " starts at the first point", 0, frames.get(0).x, name.contains("circular") ? 15 : Math.max(4, maxStep(frames) * 1.5));
            check(maxStep(frames) < 25, name + ": camera jumped " + maxStep(frames));
            Frame last = frames.get(frames.size() - 1);
            if (!name.contains("circular"))
                near(name + " ends at the last point", -5, last.x, Math.max(8, maxStep(frames) * 1.5)); // The last frame can be a long way before the end if the game hangs for a moment
            info(name + ": " + frames.size() + " frames, largest step " + maxStep(frames));
        }
    }

    private void testTickTimer() {
        addLine();
        cam("interpolation linear");
        cam("timer ticks");
        List<Frame> frames = playing(play("start 2s", 7000));
        near("duration in ticks", 2, seconds(frames), 0.4);
        Frame last = frames.get(frames.size() - 1);
        near("ends at x", 30, last.x, 3);
        near("ends at z", 30, last.z, 3);
        checkFinite(frames, "ticks");
    }

    private void testLoops() {
        for (int loops : new int[] { 0, 1, 2 }) {
            reset();
            addLine();
            cam("interpolation linear");
            List<Frame> frames = playing(play("start 1s " + loops, 8000));
            near(loops + " loops last", loops + 1, seconds(frames), 0.4);
            int returns = 0;
            for (int i = 1; i < frames.size(); i++)
                if (frames.get(i).x < frames.get(i - 1).x - 1)
                    returns++;
            info(loops + " loops: " + seconds(frames) + " s, went back " + returns + " times");
            check(!client(CMDCamClient::isPlaying), "still playing after " + loops + " loops");
        }
    }

    private void testEndlessLoop() {
        addLine();
        cam("interpolation linear");
        List<Frame> frames = new CopyOnWriteArrayList<>();
        clientDo(() -> {
            recording = frames;
            command("cam").processCommand(mc.thePlayer, split("start 500ms -1"));
        });
        sleep(4000);
        check(client(CMDCamClient::isPlaying), "endless loop ended by itself");
        recording = null;
        double maxX = 0;
        int returns = 0;
        for (int i = 1; i < frames.size(); i++) {
            maxX = Math.max(maxX, frames.get(i).x);
            if (frames.get(i).x < frames.get(i - 1).x - 1)
                returns++;
        }
        check(returns >= 3, "only went back to the start " + returns + " times in 4 s, upstream gets stuck at the end");
        checkFinite(frames, "endless");
        cam("stop");
        sleep(100);
        check(!client(CMDCamClient::isPlaying), "stop did not end the endless loop");
    }

    private void testZoomRoll() {
        float optionBefore = client(() -> mc.gameSettings.fovSetting);
        float vanillaBefore = client(() -> fov(true));
        addPoint(0, 100, 0, 0, 0, 0, 30);
        addPoint(10, 100, 0, 0, 0, 45, 90);
        cam("interpolation linear");
        List<Frame> frames = playing(play("start 2s", 6000));
        checkFinite(frames, "zoom");
        double minFov = 999, maxFov = 0, maxRoll = 0;
        for (Frame frame : frames) {
            minFov = Math.min(minFov, frame.fov);
            maxFov = Math.max(maxFov, frame.fov);
            maxRoll = Math.max(maxRoll, frame.roll);
            near("hand FOV is not zoomed", 70, frame.handFov, 15);
        }
        near("zoom at the start", 30, minFov, 4);
        near("zoom at the end", 90, maxFov, 4);
        near("roll at the end (camRoll field)", 45, maxRoll, 3);
        sleep(100);
        near("roll is reset afterwards", 0, client(this::roll), 0.001);
        near("FOV is back afterwards", vanillaBefore, client(() -> fov(true)), 0.5);
        near("FOV option is untouched (upstream #67)", optionBefore, client(() -> mc.gameSettings.fovSetting), 0);
    }

    private void testSpinning() {
        addPoint(0, 100, 0, 170, 0, 0, 70);
        addPoint(0, 100, 10, -170, 0, 0, 70);
        cam("interpolation linear");
        cam("spinning_fix fix");
        List<Frame> frames = playing(play("start 1s", 5000));
        double range = 0;
        for (Frame frame : frames)
            range = Math.max(range, Math.abs(frame.yaw - frames.get(0).yaw));
        check(range < 30, "fix took the long way round, turned " + range);
        near("the scene's point is not changed by playing", -170, scene().points.get(1).rotationYaw, 0.001);

        cam("spinning_fix no_fix");
        frames = playing(play("start 1s", 5000));
        range = 0;
        for (Frame frame : frames)
            range = Math.max(range, Math.abs(frame.yaw - frames.get(0).yaw));
        check(range > 200, "no_fix should turn the long way, turned " + range);
    }

    private void testSmoothStart() {
        addPoint(50, 100, 0, 0, 0, 0, 70);
        addPoint(60, 100, 0, 0, 0, 0, 70);
        cam("smooth_start true");
        List<Frame> frames = playing(play("start 1s", 15000));
        checkFinite(frames, "smooth");
        near("begins where the camera is", SPAWN_X, frames.get(0).x, 4);
        check(maxStep(frames) < 15, "smooth start jumped " + maxStep(frames));
        near("ends at the last point", 60, frames.get(frames.size() - 1).x, 2);
        check(seconds(frames) > 1.5, "the smooth part is missing, took " + seconds(frames));
    }

    private void testDistanceTiming() {
        addPoint(0, 100, 0, 0, 0, 0, 70);
        addPoint(2, 100, 0, 0, 0, 0, 70);
        addPoint(40, 100, 0, 0, 0, 0, 70);
        cam("interpolation linear");
        cam("distance_timing true");
        List<Frame> frames = playing(play("start 2s", 6000));
        double start = frames.get(0).nanos;
        double whenFirst = -1;
        for (Frame frame : frames)
            if (frame.x >= 2) {
                whenFirst = (frame.nanos - start) / 1E9;
                break;
            }
        check(whenFirst >= 0 && whenFirst < 0.35, "the short segment should be quick but took " + whenFirst);
    }

    /** Only one binding per key receives presses in 1.7.10, the one registered last. A conflict silently disables the other one. */
    private void reportKeyConflicts() {
        clientDo(() -> {
            net.minecraft.util.IntHashMap hash = ReflectionHelper.getPrivateValue(KeyBinding.class, null, "hash", "field_74514_b");
            KeyBinding[] ours = { KeyHandler.zoomIn, KeyHandler.zoomCenter, KeyHandler.zoomOut, KeyHandler.rollLeft, KeyHandler.rollCenter, KeyHandler.rollRight, KeyHandler.pointKey,
                    KeyHandler.startStop, KeyHandler.clearPoint };
            for (KeyBinding key : ours) {
                StringBuilder others = new StringBuilder();
                for (KeyBinding other : mc.gameSettings.keyBindings)
                    if (other != key && other.getKeyCode() == key.getKeyCode())
                        others.append(' ').append(other.getKeyDescription());
                Object receiver = hash.lookup(key.getKeyCode());
                boolean receives = receiver == key;
                if (!receives || others.length() > 0)
                    info("KEY " + key.getKeyDescription() + " on " + org.lwjgl.input.Keyboard.getKeyName(key.getKeyCode()) + (receives ? " receives presses" : " DOES NOT receive presses")
                            + ", shared with:" + others);
            }
        });
    }

    private void focus() {
        clientDo(() -> {
            String state = "focus " + mc.inGameHasFocus + ", screen " + mc.currentScreen + ", paused " + mc.isGamePaused() + ", active " + org.lwjgl.opengl.Display.isActive();
            if (!mc.inGameHasFocus || mc.currentScreen != null)
                info("input state before a press: " + state);
            if (mc.currentScreen != null)
                mc.displayGuiScreen(null);
            mc.inGameHasFocus = true;
        });
    }

    /** The conflicts are reported, the test itself should not depend on what other mods do with the same keys. */
    private void unbindConflicts() {
        clientDo(() -> {
            KeyBinding[] ours = { KeyHandler.zoomIn, KeyHandler.zoomCenter, KeyHandler.zoomOut, KeyHandler.rollLeft, KeyHandler.rollCenter, KeyHandler.rollRight, KeyHandler.pointKey,
                    KeyHandler.startStop, KeyHandler.clearPoint };
            for (KeyBinding key : ours)
                for (KeyBinding other : mc.gameSettings.keyBindings)
                    if (other != key && other.getKeyCode() == key.getKeyCode())
                        other.setKeyCode(0);
            KeyBinding.resetKeyBindingArrayAndHash(); // Now each of ours receives its key
        });
    }

    private void testKeys() {
        reportKeyConflicts();
        unbindConflicts();
        focus();
        press(KeyHandler.pointKey);
        sleep(150);
        check(scene().points.size() == 1, "P did not add a point, have " + scene().points.size());
        if (!scene().points.isEmpty())
            near("point is at the camera", SPAWN_Y, scene().points.get(0).y, 0.5);
        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X + 10, SPAWN_Y, SPAWN_Z, 0, 0));
        sleep(100);
        focus();
        press(KeyHandler.pointKey);
        press(KeyHandler.pointKey);
        sleep(150);
        check(scene().points.size() == 3, "P pressed twice in one frame, have " + scene().points.size());

        focus();
        clientDo(() -> {
            CMDCamClient.getScene().duration = 30000;
            KeyBinding.onTick(KeyHandler.startStop.getKeyCode());
        });
        sleep(300);
        check(client(CMDCamClient::isPlaying), "U did not start the scene");
        press(KeyHandler.startStop);
        sleep(300);
        check(!client(CMDCamClient::isPlaying), "U did not stop the scene");

        press(KeyHandler.clearPoint);
        sleep(150);
        check(scene().points.isEmpty(), "Delete did not clear the points");
        press(KeyHandler.startStop);
        sleep(200);
        check(!client(CMDCamClient::isPlaying), "U started a scene without points");

        addLine();
        clientDo(() -> {
            CMDCamClient.getScene().duration = 30000;
            KeyBinding.onTick(KeyHandler.startStop.getKeyCode());
        });
        sleep(300);
        press(client(() -> mc.gameSettings.keyBindJump));
        sleep(200);
        check(client(() -> CMDCamClient.isPlaying() && !CMDCamClient.run().playing()), "jump did not pause");
        press(client(() -> mc.gameSettings.keyBindJump));
        sleep(200);
        check(client(() -> CMDCamClient.isPlaying() && CMDCamClient.run().playing()), "jump did not resume");
    }

    private void testHeldKeys() {
        if (keyDownBuffer == null) {
            info("skipped, the keyboard cannot be simulated");
            return;
        }
        float vanilla = client(() -> fov(true));
        float option = client(() -> mc.gameSettings.fovSetting);
        hold(KeyHandler.zoomIn, 500);
        float zoomedIn = client(() -> fov(true));
        check(zoomedIn < vanilla - 1, "zoom in did nothing: " + vanilla + " -> " + zoomedIn);
        hold(KeyHandler.zoomCenter, 100);
        near("zoom reset", vanilla, client(() -> fov(true)), 0.5);
        hold(KeyHandler.zoomOut, 500);
        float zoomedOut = client(() -> fov(true));
        check(zoomedOut > vanilla + 1, "zoom out did nothing: " + vanilla + " -> " + zoomedOut);
        near("zooming does not change the option", option, client(() -> mc.gameSettings.fovSetting), 0);

        press(KeyHandler.pointKey);
        sleep(150);
        check(!scene().points.isEmpty() && Math.abs(scene().points.get(0).zoom - zoomedOut) < 3, "the point did not store the zoom");
        hold(KeyHandler.zoomCenter, 100);

        hold(KeyHandler.rollRight, 500);
        float rollRight = client(this::roll);
        check(rollRight > 1, "roll right did nothing: " + rollRight);
        // Speed must depend on time, 500 ms at 1.5 per tick is about 15 degrees. Double handling would show as about 30
        near("roll speed", 15, rollRight, 7);
        hold(KeyHandler.rollCenter, 100);
        near("roll reset", 0, client(this::roll), 0.001);
        hold(KeyHandler.rollLeft, 300);
        check(client(this::roll) < -1, "roll left did nothing");
        hold(KeyHandler.rollCenter, 100);
    }

    private void testPause() {
        addLine();
        cam("interpolation linear");
        List<Frame> frames = new CopyOnWriteArrayList<>();
        clientDo(() -> {
            recording = frames;
            command("cam").processCommand(mc.thePlayer, split("start 3s"));
        });
        sleep(900);
        cam("pause");
        sleep(100);
        double[] held = client(() -> new double[] { mc.thePlayer.posX, mc.thePlayer.posZ });
        sleep(1500);
        double[] after = client(() -> new double[] { mc.thePlayer.posX, mc.thePlayer.posZ });
        near("paused x", held[0], after[0], 0.001);
        near("paused z", held[1], after[1], 0.001);
        check(client(CMDCamClient::isPlaying), "pause ended the scene");
        cam("resume");
        sleep(150);
        double[] resumed = client(() -> new double[] { mc.thePlayer.posX, mc.thePlayer.posZ });
        near("no jump after resume", held[0] + held[1], resumed[0] + resumed[1], 6);
        waitFor(6000, () -> !CMDCamClient.isPlaying());
        recording = null;
        near("paused time does not count", 4.5, seconds(playing(frames)), 0.5);
        cam("pause");
        cam("resume");
    }

    private void testPauseMenu() {
        addLine();
        cam("interpolation linear");
        cam("start 3s");
        sleep(800);
        clientDo(() -> mc.displayGuiScreen(new GuiIngameMenu()));
        sleep(200);
        check(client(mc::isGamePaused), "the game did not pause in singleplayer");
        double held = client(() -> mc.thePlayer.posX + mc.thePlayer.posZ);
        sleep(1500);
        near("scene holds in the pause menu", held, client(() -> mc.thePlayer.posX + mc.thePlayer.posZ), 0.001);
        clientDo(() -> mc.displayGuiScreen(null));
        sleep(100);
        double resumed = client(() -> mc.thePlayer.posX + mc.thePlayer.posZ);
        near("no jump when leaving the menu", held, resumed, 6);
        check(client(CMDCamClient::isPlaying), "the scene ended behind the menu");
        waitFor(5000, () -> !CMDCamClient.isPlaying());
    }

    private void testRestoredSettings() {
        addLine();
        // Upstream #60: the HUD has to be how it was before, also if it already was hidden
        clientDo(() -> mc.gameSettings.hideGUI = true);
        cam("hide_gui false");
        play("start 300ms", 3000);
        check(client(() -> mc.gameSettings.hideGUI), "the hidden HUD was shown afterwards");
        clientDo(() -> mc.gameSettings.hideGUI = false);
        List<Frame> frames = playing(play("start 300ms", 3000));
        for (Frame frame : frames)
            check(!frame.hideGui, "hide_gui false still hid the HUD");
        cam("hide_gui true");
        frames = playing(play("start 300ms", 3000));
        for (Frame frame : frames)
            check(frame.hideGui, "hide_gui true did not hide the HUD");
        check(!client(() -> mc.gameSettings.hideGUI), "the HUD stayed hidden");

        clientDo(() -> mc.gameSettings.thirdPersonView = 2);
        frames = playing(play("start 300ms", 3000));
        for (Frame frame : frames)
            check(frame.third == 0, "not in first person while playing");
        check(client(() -> mc.gameSettings.thirdPersonView) == 2, "third person was not restored");
        clientDo(() -> mc.gameSettings.thirdPersonView = 0);

        clientDo(() -> mc.gameSettings.smoothCamera = true);
        play("start 300ms", 3000);
        check(client(() -> mc.gameSettings.smoothCamera), "smooth camera was not restored");
        clientDo(() -> mc.gameSettings.smoothCamera = false);

        clientDo(() -> mc.thePlayer.capabilities.isFlying = false);
        play("start 300ms", 3000);
        check(!client(() -> mc.thePlayer.capabilities.isFlying), "flying was not restored to off");
        clientDo(() -> mc.thePlayer.capabilities.isFlying = true);
        play("start 300ms", 3000);
        check(client(() -> mc.thePlayer.capabilities.isFlying), "flying was not restored to on");

        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X, SPAWN_Y, SPAWN_Z, 33, 12));
        sleep(100);
        cam("return_start true");
        play("start 300ms", 3000);
        sleep(100);
        near("return_start x", SPAWN_X, client(() -> mc.thePlayer.posX), 0.2);
        near("return_start z", SPAWN_Z, client(() -> mc.thePlayer.posZ), 0.2);
        near("return_start yaw", 33, client(() -> (double) mc.thePlayer.rotationYaw), 0.5);
        cam("return_start false");
        play("start 300ms", 3000);
        sleep(100);
        near("without return_start it stays at the end", 30, client(() -> mc.thePlayer.posX), 3);
    }

    private void testOutside() {
        addLine();
        cam("interpolation linear");
        cam("mode outside");
        List<Frame> all = new CopyOnWriteArrayList<>();
        double[] playerBefore = client(() -> new double[] { mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ });
        clientDo(() -> {
            recording = all;
            command("cam").processCommand(mc.thePlayer, split("start 2s"));
        });
        sleep(600);
        check(client(() -> mc.renderViewEntity instanceof CameraEntity), "view entity is not the camera");
        check(client(() -> MinecraftForge.EVENT_BUS.post(new RenderHandEvent(mc.renderGlobal, 1, 0))), "hand is not hidden in outside mode");
        check(client(() -> !mc.theWorld.loadedEntityList.contains(mc.renderViewEntity)), "the camera was added to the world");
        boolean[] hook = client(() -> {
            Entity before = mc.renderViewEntity;
            mc.entityRenderer.getMouseOver(1);
            return new boolean[] { mc.renderViewEntity == before };
        });
        check(hook[0], "getMouseOver did not restore the view entity");
        waitFor(6000, () -> !CMDCamClient.isPlaying());
        sleep(60);
        recording = null;
        List<Frame> frames = playing(all);
        checkFinite(frames, "outside");
        double maxOff = 0, maxRender = 0;
        for (Frame frame : frames) {
            if (!(frame.view instanceof CameraEntity))
                continue;
            maxOff = Math.max(maxOff, Math.abs(frame.y - 100));
            maxRender = Math.max(maxRender, Math.abs(frame.renderY - 100));
        }
        near("camera height (upstream #64)", 0, maxOff, 0.001);
        near("rendered camera height (upstream #64)", 0, maxRender, 0.001);
        near("duration", 2, seconds(frames), 0.3);
        near("player stayed x", playerBefore[0], client(() -> mc.thePlayer.posX), 0.5);
        near("player stayed z", playerBefore[2], client(() -> mc.thePlayer.posZ), 0.5);
        check(client(() -> mc.renderViewEntity == mc.thePlayer), "view entity was not restored");
        check(!client(() -> MinecraftForge.EVENT_BUS.post(new RenderHandEvent(mc.renderGlobal, 1, 0))), "hand stayed hidden");
    }

    private void testLookBlock() {
        addPoint(0, 100, 0, 0, 0, 0, 70);
        addPoint(20, 100, 0, 0, 0, 0, 70);
        addPoint(20, 100, 20, 0, 0, 0, 70);
        cam("interpolation linear");
        cam("target pos 10 80 40");
        cam("pitch step 1");
        cam("yaw step 1");
        List<Frame> frames = playing(play("start 2s", 6000));
        checkFinite(frames, "look");
        double worst = 0;
        // Blocks are aimed at in their center
        for (Frame frame : frames) {
            double dx = 10.5 - frame.x, dy = 80.5 - frame.y, dz = 40.5 - frame.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double yaw = Math.toRadians(frame.yaw), pitch = Math.toRadians(frame.pitch);
            double vx = -Math.sin(yaw) * Math.cos(pitch), vy = -Math.sin(pitch), vz = Math.cos(yaw) * Math.cos(pitch);
            double angle = Math.toDegrees(Math.acos(Math.min(1, (vx * dx + vy * dy + vz * dz) / length)));
            worst = Math.max(worst, angle);
        }
        info("look at block: worst angle " + worst);
        near("aims at the block", 0, worst, 3);
    }

    private Entity pig() {
        serverCommand("/summon Pig 20 5 20"); // Just above the ground of the flat world, a long fall would kill it
        waitFor(5000, () -> {
            for (Object entity : mc.theWorld.loadedEntityList)
                if (entity instanceof net.minecraft.entity.passive.EntityPig)
                    return true;
            return false;
        });
        return client(() -> {
            for (Object entity : mc.theWorld.loadedEntityList)
                if (entity instanceof net.minecraft.entity.passive.EntityPig)
                    return (Entity) entity;
            return null;
        });
    }

    /** The /kill of 1.7.10 has no arguments, it always kills the sender. */
    private void killPigs() {
        server(() -> {
            for (Object entity : MinecraftServer.getServer().worldServerForDimension(0).loadedEntityList)
                if (entity instanceof net.minecraft.entity.passive.EntityPig)
                    ((Entity) entity).setDead();
            return null;
        });
    }

    /** Time for a task to get through the server thread. */
    private double serverMillis() {
        long start = System.nanoTime();
        server(() -> null);
        return (System.nanoTime() - start) / 1E6;
    }

    private void testFarJumps() {
        // Each of these used to freeze the server for good, the block scan grows with the area of the move
        addPoint(200000, 100, 200000, 0, 0, 0, 70);
        addPoint(-200000, 100, 200000, 0, 0, 0, 70);
        cam("goto 1");
        sleep(300);
        double goTo = serverMillis();
        info("server after a goto of 280000 blocks: " + Math.round(goTo) + " ms");
        check(goTo < 5000, "the server hung for " + goTo + " ms after /cam goto");
        near("arrived", 200000, client(() -> mc.thePlayer.posX), 1);

        cam("interpolation linear");
        List<Frame> frames = playing(play("start 500ms", 4000));
        checkFinite(frames, "far path");
        double path = serverMillis();
        info("server after a path of 400000 blocks: " + Math.round(path) + " ms");
        check(path < 5000, "the server hung for " + path + " ms after the path");

        clientDo(() -> mc.thePlayer.setPositionAndRotation(0, 100, 0, 0, 0));
        cam("smooth_start false");
        cam("return_start true");
        play("start 300ms", 3000);
        sleep(300);
        double back = serverMillis();
        info("server after return_start from far away: " + Math.round(back) + " ms");
        check(back < 5000, "the server hung for " + back + " ms after return_start");
        near("returned", 0, client(() -> mc.thePlayer.posX), 1);

        Boolean noClip = server(() -> team.creative.cmdcam.server.CMDCamServer.localOwner != null && team.creative.cmdcam.server.CMDCamServer.localOwner.noClip);
        check(noClip, "protection should still be on right after a scene");
        sleep(2500);
        noClip = server(() -> team.creative.cmdcam.server.CMDCamServer.localOwner.noClip);
        check(!noClip, "the player stays without collision after the scene");
    }

    private void testEntityTargets() {
        killPigs();
        Entity pig = pig();
        check(pig != null, "no pig");
        if (pig == null)
            return;
        addPoint(0, 110, 0, 0, 0, 0, 70);
        addPoint(10, 110, 0, 0, 0, 0, 70);
        cam("target entity @e[type=Pig,c=1]");
        check(scene().lookTarget instanceof CamTarget.EntityTarget, "selector did not find the pig for looking");
        cam("target entity #" + pig.getEntityId());
        cam("target entity " + pig.getUniqueID());
        camRejected("target entity #99999999");
        camRejected("target player @e[type=Pig,c=1]");
        camRejected("target entity Nobody_Here");
        cam("info");
        check(scene().lookTarget instanceof CamTarget.EntityTarget, "the target was lost");
        cam("pitch step 1");
        cam("yaw step 1");

        clientDo(() -> {
            recording = new CopyOnWriteArrayList<>();
            command("cam").processCommand(mc.thePlayer, split("start 3s"));
        });
        sleep(800);
        killPigs();
        sleep(1200);
        check(client(CMDCamClient::isPlaying), "the scene stopped when the target died");
        waitFor(4000, () -> !CMDCamClient.isPlaying());
        List<Frame> frames = recording;
        recording = null;
        checkFinite(frames, "entity removed");
        cam("info");

        // Following
        reset();
        Entity followed = pig();
        check(followed != null && waitFor(15000, () -> followed.onGround), "the pig did not land");
        cam("follow entity @e[type=Pig,c=1]");
        check(scene().posTarget instanceof CamTarget.EntityTarget, "the follow target was not set");
        addPoint(0, 5, 0, 0, 0, 0, 70);
        addPoint(5, 5, 0, 0, 0, 0, 70);
        cam("interpolation linear");
        cam("pos step 1");
        cam("mode outside");
        frames = playing(play("start 1s", 5000));
        checkFinite(frames, "follow");
        if (followed != null && !frames.isEmpty()) {
            Frame frame = frames.get(frames.size() / 2);
            double pigY = client(() -> followed.posY);
            near("follows the pig", pigY + 5, frame.y, 2);
        }
        // The target is already gone when the scene starts: the points are offsets, the camera must not go to them
        killPigs();
        sleep(500);
        double waitingY = client(() -> mc.thePlayer.posY);
        frames = playing(play("start 500ms", 4000));
        checkFinite(frames, "follow without target");
        check(!frames.isEmpty(), "the scene without its target did not play");
        if (!frames.isEmpty())
            near("the camera waits without a target", waitingY, frames.get(frames.size() / 2).y, 3);
        pig();

        // Relative points cannot simply become absolute, and the other way round
        camRejected("follow none");
        cam("clear");
        cam("follow none");
        addPoint(1, 100, 1, 0, 0, 0, 70);
        camRejected("follow entity @e[type=Pig,c=1]");
        killPigs();

        // The player as target only makes sense with a separate camera
        reset();
        addPoint(10, 105, 0, 0, 0, 0, 70);
        addPoint(10, 105, 10, 0, 0, 0, 70);
        cam("mode outside");
        cam("target self");
        cam("pitch step 1");
        cam("yaw step 1");
        frames = playing(play("start 1s", 5000));
        checkFinite(frames, "self target");
        check(!frames.isEmpty() && Math.abs(frames.get(frames.size() / 2).pitch) > 1 && frames.get(frames.size() / 2).pitch < 90, "the camera does not look at the player");
        check(scene().lookTarget instanceof CamTarget.SelfTarget, "playing changed the target of the scene");
        cam("mode default");
        frames = playing(play("start 500ms", 4000));
        checkFinite(frames, "self target in default mode");
    }

    private void testRelativePoints() {
        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X, SPAWN_Y, SPAWN_Z, 40, 10));
        sleep(100);
        cam("add");
        cam("add ~5 ~ ~-5");
        cam("add ~ ~10 ~ ~20 ~-5");
        cam("add 1 2 3 4 5 6 7");
        List<CamPoint> points = scene().points;
        check(points.size() == 4, "have " + points.size() + " points");
        near("own position", SPAWN_X, points.get(0).x, 0.2);
        near("own height is the eyes", SPAWN_Y, points.get(0).y, 0.01);
        near("yaw", 40, points.get(0).rotationYaw, 0.2);
        near("relative x", SPAWN_X + 5, points.get(1).x, 0.2);
        near("relative z", SPAWN_Z - 5, points.get(1).z, 0.2);
        near("relative y", SPAWN_Y + 10, points.get(2).y, 0.2);
        near("relative yaw (upstream #111)", 60, points.get(2).rotationYaw, 0.2);
        near("relative pitch", 5, points.get(2).rotationPitch, 0.2);
        near("absolute zoom", 7, points.get(3).zoom, 0.001);
        near("absolute roll", 6, points.get(3).roll, 0.001);
        camRejected("add 1 2");
        camRejected("add 1 2 3 4");
        camRejected("add 1 2 3 4 5 6 7 8");
        cam("insert 1 9 9 9");
        near("insert", 9, scene().points.get(0).x, 0.001);
        cam("set 1 8 8 8");
        near("set", 8, scene().points.get(0).x, 0.001);
        cam("remove 1");
        check(scene().points.size() == 4, "remove");
        camRejected("remove 99");
        camRejected("remove 0");
        camRejected("insert 99 1 1 1");
        cam("clear");
        check(scene().points.isEmpty(), "clear");
        camRejected("start");
    }

    private void testGoto() {
        addPoint(40, 120, 40, 90, 20, 15, 50);
        cam("goto 1");
        sleep(100);
        double[] position = client(() -> new double[] { mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch });
        near("x", 40, position[0], 0.5);
        near("y", 120, position[1], 0.5);
        near("z", 40, position[2], 0.5);
        near("yaw", 90, position[3], 0.5);
        near("pitch", 20, position[4], 0.5);
        near("roll", 15, client(this::roll), 0.5);
        near("zoom", 50, client(() -> fov(true)), 2);
        camRejected("goto 2");
        camRejected("goto x");
        camRejected("goto");
    }

    private void testSelection() {
        cam("target select");
        boolean[] handled = client(() -> {
            PlayerInteractEvent event = ForgeEventFactory.onPlayerInteract(mc.thePlayer, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 3, 3, 3, 1, mc.theWorld);
            return new boolean[] { event.isCanceled() };
        });
        check(handled[0], "selecting a block should cancel the click (upstream #69)");
        CamTarget target = scene().lookTarget;
        check(target instanceof CamTarget.BlockTarget, "the block was not selected");
        boolean normal = client(() -> ForgeEventFactory.onPlayerInteract(mc.thePlayer, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 3, 3, 3, 1, mc.theWorld).isCanceled());
        check(!normal, "clicks after selecting are still taken");

        killPigs();
        Entity pig = pig();
        cam("target select");
        Boolean canceled = client(() -> {
            EntityInteractEvent event = new EntityInteractEvent(mc.thePlayer, pig);
            MinecraftForge.EVENT_BUS.post(event);
            return event.isCanceled();
        });
        check(canceled, "selecting an entity should cancel the click");
        check(scene().lookTarget instanceof CamTarget.EntityTarget, "the entity was not selected");
        killPigs();

        cam("follow select");
        client(() -> ForgeEventFactory.onPlayerInteract(mc.thePlayer, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, 3, 3, 3, 1, mc.theWorld));
        check(scene().posTarget == null, "left clicks should not select");
        clientDo(() -> ForgeEventFactory.onPlayerInteract(mc.thePlayer, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 3, 3, 3, 1, mc.theWorld));
        check(scene().posTarget instanceof CamTarget.BlockTarget, "the follow target was not selected");
    }

    private void testFastPath() {
        addPoint(0, 100, 0, 0, 0, 0, 70);
        addPoint(3000, 100, 0, 0, 0, 0, 70);
        cam("interpolation linear");
        List<Frame> frames = playing(play("start 2s", 8000));
        checkFinite(frames, "fast");
        double maxY = 0;
        for (Frame frame : frames)
            maxY = Math.max(maxY, Math.abs(frame.y - 100));
        near("height over unloaded chunks", 0, maxY, 0.001);
        near("reached the end", 3000, client(() -> mc.thePlayer.posX), 200);
        check(client(() -> mc.thePlayer != null && mc.theWorld != null), "lost the connection");
        sleep(500);
        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X, SPAWN_Y, SPAWN_Z, 0, 0));
        sleep(1000);
    }

    private void testDeath() {
        for (String mode : new String[] { "default", "outside" }) {
            reset();
            addLine();
            cam("mode " + mode);
            clientDo(() -> {
                CMDCamClient.getScene().duration = 20000;
                command("cam").processCommand(mc.thePlayer, split("start"));
            });
            sleep(500);
            serverCommand("/kill");
            sleep(1500);
            if (mode.equals("default"))
                check(!client(CMDCamClient::isPlaying), "the scene goes on after death (upstream #102)");
            clientDo(() -> {
                if (CMDCamClient.isPlaying())
                    CMDCamClient.stopServer();
                mc.thePlayer.respawnPlayer();
                mc.displayGuiScreen(null);
            });
            waitFor(10000, () -> mc.thePlayer != null && mc.thePlayer.getHealth() > 0);
            sleep(800);
            check(client(() -> mc.renderViewEntity == mc.thePlayer), mode + ": the view entity is wrong after respawn");
        }
        reset();
        addLine();
        List<Frame> frames = playing(play("start 300ms", 3000));
        check(!frames.isEmpty(), "cannot play after respawn");
    }

    private void testDimension() {
        for (String mode : new String[] { "default", "outside" }) {
            // travelToDimension builds a real portal around the player, who would otherwise use it again by itself
            server(() -> {
                ((EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0)).timeUntilPortal = 1000000;
                return null;
            });
            reset();
            addLine();
            cam("mode " + mode);
            int startDimension = client(() -> mc.theWorld.provider.dimensionId);
            info(mode + " starts in dimension " + startDimension);
            check(startDimension == 0, mode + ": the test has to start in the overworld, is in " + startDimension);
            clientDo(() -> {
                CMDCamClient.getScene().duration = 20000;
                command("cam").processCommand(mc.thePlayer, split("start"));
            });
            sleep(500);
            server(() -> {
                ((EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0)).travelToDimension(-1);
                return null;
            });
            check(waitFor(60000, () -> mc.theWorld != null && mc.theWorld.provider.dimensionId == -1 && mc.currentScreen == null), mode + ": did not arrive in the Nether");
            sleep(800);
            check(!client(CMDCamClient::isPlaying), mode + ": the scene goes on in the other dimension");
            check(client(() -> mc.renderViewEntity == mc.thePlayer), mode + ": the view entity is wrong in the other dimension");
            server(() -> {
                ((EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0)).travelToDimension(0);
                return null;
            });
            check(waitFor(60000, () -> mc.theWorld != null && mc.theWorld.provider.dimensionId == 0 && mc.currentScreen == null), mode + ": did not come back");
            sleep(800);
        }
    }

    void assertSceneEquals(CamScene loaded) {
        check(loaded.points.size() == 2, "points: " + loaded.points.size());
        if (loaded.points.size() == 2) {
            CamPoint point = loaded.points.get(1);
            near("x", 4, point.x, 1E-6);
            near("y", 5, point.y, 1E-6);
            near("z", 6, point.z, 1E-6);
            near("yaw", 7, point.rotationYaw, 1E-6);
            near("pitch", 8, point.rotationPitch, 1E-6);
            near("roll", 9, point.roll, 1E-6);
            near("zoom", 10, point.zoom, 1E-6);
        }
        check(loaded.duration == 12345, "duration: " + loaded.duration);
        check(loaded.loop == 3, "loops: " + loaded.loop);
        check(loaded.outside(), "mode");
        check(loaded.interpolation == CamInterpolation.COSINE, "interpolation: " + loaded.interpolation.id);
        check(loaded.lookTarget instanceof CamTarget.BlockTarget, "look target");
        check(!loaded.smoothBeginning, "smooth start");
        check(loaded.distanceBasedTiming, "distance timing");
        check(!loaded.hideGui, "hide_gui");
        check(loaded.returnToStart, "return_start");
        check(CamScene.TIMER_TICKS.equals(loaded.timer), "timer: " + loaded.timer);
    }

    void fillScene() {
        addPoint(1, 2, 3, 0, 0, 0, 70);
        addPoint(4, 5, 6, 7, 8, 9, 10);
        cam("duration 12345ms");
        cam("loops 3");
        cam("mode outside");
        cam("interpolation cosine");
        cam("target pos 1 2 3");
        cam("distance_timing true");
        cam("hide_gui false");
        cam("return_start true");
        cam("timer ticks");
    }

    private void testSaveLoad() {
        check(client(CMDCamClient::serverAvailable), "the client does not know the server has the mod");
        fillScene();
        String name = "scene" + System.currentTimeMillis();
        cam("save " + name);
        sleep(600);
        reset();
        check(scene().points.isEmpty(), "reset");
        cam("load " + name);
        check(waitFor(5000, () -> !CMDCamClient.getScene().points.isEmpty()), "the scene did not come back from the server");
        assertSceneEquals(scene());
        cam("load does_not_exist");
        sleep(300);
        assertSceneEquals(scene());
        cam("list");
        camRejected("delete does_not_exist");

        reset();
        camRejected("save empty");

        leave();
        rejoin();
        check(client(CMDCamClient::serverAvailable), "not available after rejoining");
        check(scene().points.isEmpty(), "the scene should be reset when connecting");
        clientDo(() -> mc.thePlayer.capabilities.isFlying = true);
        cam("load " + name);
        check(waitFor(5000, () -> !CMDCamClient.getScene().points.isEmpty()), "the saved scene was lost after rejoining the world");
        assertSceneEquals(scene());
        cam("delete " + name);
    }

    private void testServerCommands() {
        camServer("clear");
        camServer("");
        camServer("create a");
        camServerRejected("create a");
        camServer("get a add 1 100 1");
        camServer("get a add 5 100 1 10 10 5 60");
        camServer("get a");
        camServer("get a duration 2s");
        camServer("get a duration");
        camServer("get a loops 1");
        camServer("get a loops");
        camServer("get a mode outside");
        camServer("get a mode default");
        camServer("get a interpolation linear");
        camServer("get a smooth_start false");
        camServer("get a target pos 1 2 3");
        camServer("get a target");
        camServer("get a target none");
        camServer("get a follow");
        camServer("get a pitch step 2");
        camServer("get a info");
        camServerRejected("get missing info");
        camServerRejected("get a nonsense");
        camServerRejected("get a mode nope");
        camServerRejected("start missing @a");
        camServerRejected("start");
        camServerRejected("start a");
        camServerRejected("remove missing");
        camServerRejected("remove");
        camServerRejected("create");
        camServerRejected("nonsense");
        camServerRejected("get a target select");
        camServer("list");
        camServer("get a goto @p 1");
        sleep(300);
        near("server goto", 1, client(() -> mc.thePlayer.posX), 0.5);
        camServerRejected("get a goto @p 9");
        camServerRejected("get a goto");
        camServer("remove a");
        camServer("clear");
        camServer("create b");
        camServerRejected("start b @a"); // no points
        camServer("clear");
    }

    private void testServerPlayback() {
        camServer("clear");
        camServer("create show");
        camServer("get show add 0 100 0");
        camServer("get show add 30 100 0");
        camServer("get show add 30 100 30");
        camServer("get show smooth_start false");
        camServer("get show interpolation linear");
        camServer("get show duration 10s");

        List<Frame> frames = new CopyOnWriteArrayList<>();
        recording = frames;
        camServer("start show @a 2s");
        check(waitFor(3000, CMDCamClient::isPlaying), "start did not reach the client");
        check(client(() -> CMDCamClient.isPlaying() && CMDCamClient.run().scene.serverSynced()), "not marked as started by the server");
        cam("stop");
        sleep(100);
        check(client(CMDCamClient::isPlaying), "the client could stop a scene the server started");
        waitFor(6000, () -> !CMDCamClient.isPlaying());
        recording = null;
        near("the duration argument counts", 2, seconds(playing(frames)), 0.4);
        Boolean unchanged = server(() -> team.creative.cmdcam.server.CMDCamServer.data().get("show").duration == 10000);
        check(unchanged, "starting with a duration changed the saved scene");

        camServer("start show @a");
        check(waitFor(3000, CMDCamClient::isPlaying), "second start");
        sleep(300);
        camServer("pause @a");
        sleep(200);
        double held = client(() -> mc.thePlayer.posX + mc.thePlayer.posZ);
        sleep(500);
        near("server pause", held, client(() -> mc.thePlayer.posX + mc.thePlayer.posZ), 0.001);
        camServer("resume @a");
        sleep(300);
        check(Math.abs(client(() -> mc.thePlayer.posX + mc.thePlayer.posZ) - held) > 0.05, "server resume");
        camServer("stop @a");
        check(waitFor(3000, () -> !CMDCamClient.isPlaying()), "server stop");
        camServer("start show " + client(() -> mc.thePlayer.getCommandSenderName()) + " 300ms 1");
        check(waitFor(3000, CMDCamClient::isPlaying), "start by player name");
        waitFor(5000, () -> !CMDCamClient.isPlaying());
        camServerRejected("start show Nobody_Here");

        // The way players use it
        clientDo(() -> mc.thePlayer.sendChatMessage("/cam-server start show @a 300ms"));
        check(waitFor(3000, CMDCamClient::isPlaying), "through the chat");
        waitFor(3000, () -> !CMDCamClient.isPlaying());
        camServer("clear");
    }

    private static final String[] BAD = { "", "x", "-1", "0", "1", "99999999999999999999", "1e400", "NaN", "Infinity", "-Infinity", "~", "~~", "~x", "@e", "@e[", "@e[type=", "@e[r=abc]",
            "@a[name=]", "@r[c=-5]", "#", "#x", "#-1", "1s", "-5s", "99999999999d", "true", "default", "%s%s%s", "§c", "null" };

    private static final String[] CLIENT_ONLY_SAFE = { "add", "insert", "set", "remove", "duration", "loops", "goto", "mode", "target", "follow", "pitch", "yaw", "pos",
            "interpolation", "smooth_start", "spinning_fix", "distance_timing", "hide_gui", "return_start", "timer", "info", "show", "hide", "load", "follow_center", "clear", "resume",
            "pause", "stop", "list", "delete", "nonsense", "start", "save" };

    private void testFuzzClient() {
        addLine();
        List<String> unexpected = client(() -> {
            List<String> errors = new ArrayList<>();
            ICommand cam = command("cam");
            for (String sub : CLIENT_ONLY_SAFE) {
                for (String first : BAD) {
                    for (String[] args : new String[][] { { sub, first }, { sub, "1", first }, { sub, first, first, first }, { sub, "step", first }, { sub, "entity", first }, { sub,
                            "player", first }, { sub, "pos", first, first, first }, { sub, "1", "2", "3", first, first, first, first }, }) {
                        if (sub.equals("save") || sub.equals("delete") || sub.equals("load"))
                            continue; // Would write scenes to the server, covered in another test
                        try {
                            cam.processCommand(mc.thePlayer, args);
                        } catch (CommandException e) {
                            // Bad input answered correctly
                        } catch (Throwable e) {
                            errors.add(String.join(" ", args) + " -> " + e);
                        }
                        if (CMDCamClient.isPlaying())
                            CMDCamClient.stopServer();
                        if (CMDCamClient.getScene().points.size() > 50)
                            CMDCamClient.getScene().points.clear();
                    }
                }
            }
            return errors;
        });
        for (String error : unexpected)
            check(false, error);
        info("client commands tried with bad input, " + unexpected.size() + " unexpected exceptions");

        boolean finite = client(() -> {
            for (CamPoint point : CMDCamClient.getScene().points)
                if (!finite(point.x) || !finite(point.y) || !finite(point.z) || !finite(point.rotationYaw) || !finite(point.rotationPitch) || !finite(point.roll) || !finite(point.zoom))
                    return false;
            return true;
        });
        check(finite, "bad input created a point with an invalid number");
        check(client(() -> CMDCamClient.getScene().duration > 0), "bad input created an invalid duration: " + scene().duration);
        reset();
        camRejected("add NaN 100 0");
        camRejected("add 0 Infinity 0");
        camRejected("add 0 100 0 NaN 0");
        camRejected("duration -5s");
        camRejected("duration 0");
        camRejected("start 0");
        camRejected("pitch step NaN");
        camRejected("target pos NaN 1 1");
    }

    private void testFuzzServer() {
        camServer("clear");
        camServer("create fuzz");
        camServer("get fuzz add 0 100 0");
        camServer("get fuzz add 5 100 0");
        List<String> unexpected = server(() -> {
            List<String> errors = new ArrayList<>();
            MinecraftServer server = MinecraftServer.getServer();
            ICommandSender sender = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            ICommand command = (ICommand) server.getCommandManager().getCommands().get("cam-server");
            for (String sub : new String[] { "start", "stop", "pause", "resume", "get", "list", "remove", "create", "nonsense" })
                for (String bad : BAD) {
                    if (bad.isEmpty() && sub.equals("create"))
                        continue;
                    for (String[] args : new String[][] { { sub, bad }, { sub, "fuzz", bad }, { sub, "fuzz", "@a", bad }, { sub, "fuzz", "@a", "1s", bad }, })
                        try {
                            if (sub.equals("remove") && args[1].equals("fuzz"))
                                continue;
                            command.processCommand(sender, args);
                        } catch (CommandException e) {
                            // Bad input answered correctly
                        } catch (Throwable e) {
                            errors.add(String.join(" ", args) + " -> " + e);
                        }
                }
            for (String setting : new String[] { "add", "insert", "set", "remove", "duration", "loops", "goto", "mode", "target", "follow", "pitch", "yaw", "pos", "interpolation",
                    "smooth_start", "spinning_fix", "distance_timing", "hide_gui", "return_start", "timer", "info" })
                for (String bad : BAD)
                    for (String[] args : new String[][] { { "get", "fuzz", setting, bad }, { "get", "fuzz", setting, "1", bad }, { "get", "fuzz", setting, "@a", bad }, { "get", "fuzz",
                            setting, "entity", bad }, { "get", "fuzz", setting, "pos", bad, bad, bad } })
                        try {
                            command.processCommand(sender, args);
                        } catch (CommandException e) {
                            // Bad input answered correctly
                        } catch (Throwable e) {
                            errors.add(String.join(" ", args) + " -> " + e);
                        }
            return errors;
        });
        for (String error : unexpected)
            check(false, error);
        sleep(1000);
        camServer("stop @a");
        sleep(300);
        clientDo(() -> {
            if (CMDCamClient.isPlaying())
                CMDCamClient.stopServer();
        });
        camServer("clear");
    }

    @SuppressWarnings("unchecked")
    private void testTabCompletion() {
        List<String> unexpected = client(() -> {
            List<String> errors = new ArrayList<>();
            ICommand cam = command("cam");
            String[][] cases = { { "" }, { "st" }, { "mode", "" }, { "interpolation", "" }, { "target", "" }, { "follow", "e" }, { "show", "" }, { "hide", "" }, { "load", "" }, { "delete",
                    "" }, { "timer", "" }, { "spinning_fix", "" }, { "add", "", "", "", "", "", "", "", "", "", "" }, { "x", "y", "z" }, { "hide_gui", "" }, { "duration", "" }, { "pitch",
                            "" } };
            for (String[] args : cases)
                try {
                    cam.addTabCompletionOptions(mc.thePlayer, args);
                } catch (Throwable e) {
                    errors.add(String.join(" ", args) + " -> " + e);
                }
            List<String> start = cam.addTabCompletionOptions(mc.thePlayer, new String[] { "st" });
            if (start == null || !start.contains("start") || !start.contains("stop"))
                errors.add("st does not complete to start and stop: " + start);
            List<String> modes = cam.addTabCompletionOptions(mc.thePlayer, new String[] { "mode", "" });
            if (modes == null || !modes.contains("outside"))
                errors.add("mode does not complete: " + modes);
            return errors;
        });
        for (String error : unexpected)
            check(false, error);
        List<String> serverErrors = server(() -> {
            List<String> errors = new ArrayList<>();
            MinecraftServer server = MinecraftServer.getServer();
            ICommandSender sender = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            ICommand command = (ICommand) server.getCommandManager().getCommands().get("cam-server");
            for (String[] args : new String[][] { { "" }, { "st" }, { "start", "" }, { "get", "" }, { "get", "x", "" }, { "get", "x", "mode", "" }, { "remove", "" }, { "a", "b", "c", "d" } })
                try {
                    command.addTabCompletionOptions(sender, args);
                } catch (Throwable e) {
                    errors.add(String.join(" ", args) + " -> " + e);
                }
            return errors;
        });
        for (String error : serverErrors)
            check(false, error);
    }

    private void testEdgeScenes() {
        addPoint(5, 100, 5, 0, 0, 0, 70);
        List<Frame> frames = playing(play("start 300ms", 3000));
        checkFinite(frames, "single point");
        check(!frames.isEmpty(), "a single point did not play");
        near("single point stays", 5, frames.get(frames.size() - 1).x, 0.001);

        for (String name : new String[] { "linear", "cubic", "hermite", "cosine", "circular", "invcircular" }) {
            reset();
            cam("interpolation " + name);
            for (int i = 0; i < 3; i++)
                addPoint(5, 100, 5, 0, 0, 0, 70);
            cam("distance_timing true");
            if (name.contains("circular"))
                cam("target pos 5 100 5"); // The center is where the points are
            frames = playing(play("start 200ms", 3000));
            checkFinite(frames, name + " with all points at one spot");
            cam("target none");
        }

        reset();
        addPoint(29999000, 250, 29999000, 0, 90, 0, 170);
        addPoint(29999100, 250, -29999000, 720, -90, 3600, 0.1);
        frames = playing(play("start 300ms", 3000));
        checkFinite(frames, "extreme values");
        reset();
        sleep(1000);

        addLine();
        frames = playing(play("start 1ms", 3000));
        checkFinite(frames, "1 ms");
        check(!client(CMDCamClient::isPlaying), "1 ms did not end");
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 100d")));
        sleep(200);
        check(client(CMDCamClient::isPlaying), "100 days");
        cam("stop");
        sleep(100);
        check(!client(CMDCamClient::isPlaying), "stop 100 days");

        cam("stop");
        cam("pause");
        cam("resume");

        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 2s")));
        sleep(100);
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 2s"))); // Start while playing
        sleep(200);
        check(client(CMDCamClient::isPlaying), "start while playing");
        cam("clear"); // Changing the scene does not touch what is playing
        cam("add");
        cam("interpolation cubic");
        sleep(200);
        check(client(CMDCamClient::isPlaying), "editing while playing");
        check(client(() -> CMDCamClient.run().scene != CMDCamClient.getScene()), "the playing scene is not a copy");
        cam("stop");
    }

    private void testRapid() {
        addLine();
        for (String mode : new String[] { "default", "outside" }) {
            cam("mode " + mode);
            for (int i = 0; i < 100; i++) {
                clientDo(() -> {
                    command("cam").processCommand(mc.thePlayer, split("start 1s"));
                    CMDCamClient.stop();
                    command("cam").processCommand(mc.thePlayer, split("start 1s"));
                });
                sleep(10);
                cam("stop");
            }
            check(!client(CMDCamClient::isPlaying), mode + ": still playing");
            check(client(() -> mc.renderViewEntity == mc.thePlayer), mode + ": view entity not restored");
            check(!client(() -> mc.gameSettings.hideGUI), mode + ": the HUD stayed hidden");
            near(mode + ": roll", 0, client(this::roll), 0.001);
        }
    }

    private void testLargeScene() {
        clientDo(() -> {
            List<CamPoint> points = CMDCamClient.getScene().points;
            for (int i = 0; i < 1000; i++)
                points.add(new CamPoint(Math.sin(i * 0.1) * 40, 100 + Math.sin(i * 0.03) * 10, Math.cos(i * 0.1) * 40, i, 0, 0, 70));
        });
        for (String name : new String[] { "linear", "hermite", "cubic", "cosine", "circular" }) {
            cam("interpolation " + name);
            cam("distance_timing true");
            if (name.equals("circular"))
                cam("target pos 0 100 0");
            long start = System.nanoTime();
            clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 2s")));
            double startMillis = (System.nanoTime() - start) / 1E6;
            List<Frame> frames = new CopyOnWriteArrayList<>();
            recording = frames;
            waitFor(8000, () -> !CMDCamClient.isPlaying());
            recording = null;
            checkFinite(frames, name);
            double longest = 0;
            for (int i = 1; i < frames.size(); i++)
                longest = Math.max(longest, (frames.get(i).nanos - frames.get(i - 1).nanos) / 1E6);
            info("1000 points " + name + ": start " + Math.round(startMillis) + " ms, " + frames.size() + " frames, longest frame " + Math.round(longest) + " ms");
            check(startMillis < 2000, name + ": starting 1000 points took " + startMillis + " ms");
            check(longest < 500, name + ": a frame took " + longest + " ms");
            cam("target none");
        }
    }

    private void testPreview() {
        addPoint(0, 100, 6, 0, 0, 0, 70);
        addPoint(5, 102, 12, 0, 0, 0, 70);
        addPoint(-5, 104, 14, 0, 0, 0, 70);
        addPoint(0, 100, 18, 0, 0, 0, 70);
        sleep(200);
        int depth = matrixDepth;
        clientDo(() -> {
            GL11.glGetError();
            glErrors = 0;
            watchGl = true;
        });
        cam("show all");
        cam("target pos 0 100 12");
        sleep(1500);
        shot("preview-all");
        cam("hide all");
        cam("show");
        cam("show");
        sleep(400);
        cam("hide");
        cam("show hermite");
        sleep(400);
        cam("hide hermite");
        camRejected("show nope");
        // A marker and the preview relative to it
        cam("target none");
        cam("clear");
        cam("follow self");
        cam("follow_center");
        cam("add ~2 ~ ~5");
        cam("add ~-2 ~ ~5");
        cam("show all");
        sleep(800);
        shot("preview-marker");
        cam("hide all");
        cam("clear");
        cam("follow none");
        watchGl = false;
        check(glErrors == 0, "OpenGL errors in " + glErrors + " frames");
        sleep(200);
        near("matrix stack is balanced", depth, matrixDepth, 0);

        reset();
        clientDo(() -> {
            for (int i = 0; i < 1000; i++)
                CMDCamClient.getScene().points.add(new CamPoint(Math.sin(i * 0.1) * 30, 100 + i * 0.01, Math.cos(i * 0.1) * 30, 0, 0, 0, 70));
        });
        List<Frame> before = new CopyOnWriteArrayList<>();
        recording = before;
        sleep(1500);
        recording = null;
        cam("show all");
        List<Frame> after = new CopyOnWriteArrayList<>();
        recording = after;
        sleep(1500);
        recording = null;
        cam("hide all");
        info("frames in 1.5 s without preview " + before.size() + ", with a preview of 1000 points in all interpolations " + after.size());
        check(after.size() >= 5, "the preview of 1000 points makes the game unusable: " + after.size() + " frames in 1.5 s");
    }

    private void testScreenshots() {
        clientDo(() -> {
            mc.thePlayer.capabilities.isFlying = true;
            mc.thePlayer.setPositionAndRotation(SPAWN_X, 10, SPAWN_Z, 0, 0);
        });
        sleep(1500);
        shot("normal-player-view");

        // Default mode, HUD hidden
        addPoint(0.5, 10, 0.5, 0, 25, 0, 70);
        addPoint(0.5, 10, 0.6, 0, 25, 0, 70);
        cam("smooth_start false");
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 30s")));
        sleep(800);
        shot("default-mode-hud-hidden");
        cam("stop");

        // Outside mode looking at the player: the character has to be visible, the hand must not be
        reset();
        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X, 10, SPAWN_Z, 180, 0));
        sleep(800);
        addPoint(0.5, 10, 4.5, 180, 0, 0, 70);
        addPoint(0.5, 10, 4.6, 180, 0, 0, 70);
        cam("mode outside");
        cam("hide_gui false");
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 30s")));
        sleep(800);
        shot("outside-mode-sees-player");
        cam("stop");

        // Roll and zoom
        reset();
        clientDo(() -> mc.thePlayer.setPositionAndRotation(SPAWN_X, 10, SPAWN_Z, 0, 0));
        addPoint(0.5, 10, 0.5, 0, 25, 30, 30);
        addPoint(0.5, 10, 0.6, 0, 25, 30, 30);
        clientDo(() -> command("cam").processCommand(mc.thePlayer, split("start 30s")));
        sleep(800);
        shot("default-mode-roll-30-zoom-30");
        cam("stop");
    }

    private void testDisconnect() {
        for (String mode : new String[] { "default", "outside" }) {
            reset();
            addLine();
            cam("mode " + mode);
            clientDo(() -> {
                CMDCamClient.getScene().duration = 20000;
                command("cam").processCommand(mc.thePlayer, split("start"));
            });
            sleep(500);
            clientDo(() -> {
                recording = null;
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
            });
            sleep(800);
            check(!client(CMDCamClient::isPlaying), mode + ": still playing in the main menu");
            check(client(() -> !mc.gameSettings.hideGUI), mode + ": the HUD setting was not restored");
            rejoin();
            clientDo(() -> mc.thePlayer.capabilities.isFlying = true);
            check(client(() -> mc.renderViewEntity == mc.thePlayer), mode + ": view entity after rejoining");
            reset();
            addLine();
            List<Frame> frames = playing(play("start 300ms", 3000));
            check(!frames.isEmpty(), mode + ": cannot play after rejoining");
        }
    }

}
