package team.creative.cmdcam.ingametest;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.client.gui.GuiPathAdvancedLoad;
import team.creative.cmdcam.client.gui.GuiPathBrowser;
import team.creative.cmdcam.client.gui.GuiPathList;
import team.creative.cmdcam.client.gui.GuiPathRename;
import team.creative.cmdcam.client.gui.GuiPathSave;
import team.creative.cmdcam.client.gui.RowAction;
import team.creative.cmdcam.client.path.LocalPaths;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.client.path.ServerPaths;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.packet.CamPacket;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.SceneNBT;
import team.creative.cmdcam.common.target.CamTarget;
import team.creative.cmdcam.server.CMDCamServer;

/** Saved paths, the folder and the screens. Everything here is named t_..., other paths in the folder are left alone. */
final class PathTests {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int BUTTON_SORT = 0;
    private static final int BUTTON_CREATOR = 1;
    private static final int BUTTON_FOLDER = 3;
    private static final int BUTTON_SAVE = 10;
    private static final int BUTTON_MY_POSITION = 11;
    private static final int RENAME_DONE = 0;
    private static final int RENAME_CANCEL = 1;
    private static final int ADVANCED_LOAD = 0;
    private static final int ADVANCED_POSITION = 2;
    private static final int ADVANCED_RESET = 3;

    /** The server answers a list request once per second. */
    private static final long LIST_PAUSE_MILLIS = 1200;

    private final InGameTestMod t;
    private long lastList;

    PathTests(InGameTestMod t) {
        this.t = t;
    }

    // ---------------------------------------------------------------- helpers

    private void cleanUp() {
        t.clientDo(() -> {
            if (mc.currentScreen != null)
                mc.displayGuiScreen(null);
            for (SavedPath path : LocalPaths.all())
                if (path.name.toLowerCase().startsWith("t_"))
                    LocalPaths.delete(path.name);
        });
        t.server(() -> {
            for (SavedPath path : CMDCamServer.data().all())
                if (path.name.toLowerCase().startsWith("t_"))
                    CMDCamServer.data().remove(path.name);
            return null;
        });
    }

    private static CamScene lineScene(double offset) {
        CamScene scene = new CamScene();
        scene.interpolation = CamInterpolation.LINEAR;
        scene.duration = 10000;
        scene.points.add(new CamPoint(offset, 100, 0, 1, 2, 3, 70));
        scene.points.add(new CamPoint(offset + 30, 100, 0, 4, 5, 6, 71));
        scene.points.add(new CamPoint(offset + 30, 100, 30, 7, 8, 9, 72));
        return scene;
    }

    private static SavedPath path(String name, String creator, long saved, long loaded) {
        SavedPath path = new SavedPath(name, lineScene(0));
        path.creator = creator;
        path.savedAt = saved;
        path.lastLoaded = loaded;
        return path;
    }

    private void putLocal(SavedPath path) {
        t.check(t.client(() -> LocalPaths.write(path)), "could not write " + path.name);
    }

    private void putServer(SavedPath path) {
        t.server(() -> {
            CMDCamServer.data().set(path);
            return null;
        });
    }

    private SavedPath onServer(String name) {
        return t.server(() -> CMDCamServer.data().getPath(name));
    }

    private SavedPath inFolder(String name) {
        return t.client(() -> LocalPaths.find(name));
    }

    private boolean waitOnServer(String name, boolean exists) {
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end) {
            if ((onServer(name) != null) == exists)
                return true;
            InGameTestMod.sleep(25);
        }
        return false;
    }

    /** Exactly what the chat does when pressing enter, which includes closing itself afterwards. */
    private void chat(String command) {
        t.clientDo(() -> {
            GuiChat chat = new GuiChat();
            mc.displayGuiScreen(chat);
            chat.func_146403_a(command);
            mc.displayGuiScreen(null);
        });
    }

    private <T extends GuiScreen> T waitScreen(Class<T> type) {
        if (!t.waitFor(3000, () -> type.isInstance(mc.currentScreen))) {
            t.check(false, "expected " + type.getSimpleName() + " but the screen is " + t.client(() -> mc.currentScreen));
            throw new IllegalStateException("cannot go on without " + type.getSimpleName());
        }
        return type.cast(t.client(() -> mc.currentScreen));
    }

    /** Opens the list with the command and waits for what the server has. */
    private GuiPathBrowser openBrowser() {
        pauseForList();
        chat("/cam load");
        GuiPathBrowser gui = waitScreen(GuiPathBrowser.class);
        t.check(t.waitFor(3000, () -> !ServerPaths.waiting()), "no answer from the server");
        t.clientDo(gui::reload);
        return gui;
    }

    private void pauseForList() {
        long wait = lastList + LIST_PAUSE_MILLIS - System.currentTimeMillis();
        if (wait > 0)
            InGameTestMod.sleep(wait);
        lastList = System.currentTimeMillis();
    }

    private static int index(GuiPathList gui, String name, boolean server) {
        List<PathEntry> entries = gui.visibleEntries();
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).path.name.equals(name) && entries.get(i).server == server)
                return i;
        return -1;
    }

    private static PathEntry entry(GuiPathList gui, String name, boolean server) {
        int index = index(gui, name, server);
        return index >= 0 ? gui.visibleEntries().get(index) : null;
    }

    private void click(GuiScreen gui, int x, int y) {
        try {
            Method method = ReflectionHelper.findMethod(GuiScreen.class, gui, new String[] { "mouseClicked", "func_73864_a" }, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(gui, x, y, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void press(GuiScreen gui, char character, int key) {
        try {
            Method method = ReflectionHelper.findMethod(GuiScreen.class, gui, new String[] { "keyTyped", "func_73869_a" }, char.class, int.class);
            method.setAccessible(true);
            method.invoke(gui, character, key);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void type(GuiScreen gui, String text) {
        t.clientDo(() -> {
            for (char character : text.toCharArray())
                press(gui, character, 0);
        });
    }

    /** Clicks where the button is on the screen, the screen decides what that means. */
    private void clickButton(GuiScreen gui, int id) {
        t.clientDo(() -> {
            List<GuiButton> buttons = ReflectionHelper.getPrivateValue(GuiScreen.class, gui, "buttonList", "field_146292_n");
            gui.updateScreen(); // As every tick does, buttons get enabled there
            for (GuiButton button : buttons)
                if (button.id == id) {
                    click(gui, button.xPosition + button.width / 2, button.yPosition + button.height / 2);
                    return;
                }
            throw new IllegalStateException("no button " + id);
        });
    }

    private boolean buttonEnabled(GuiScreen gui, int id) {
        return t.client(() -> {
            gui.updateScreen();
            List<GuiButton> buttons = ReflectionHelper.getPrivateValue(GuiScreen.class, gui, "buttonList", "field_146292_n");
            for (GuiButton button : buttons)
                if (button.id == id)
                    return button.enabled;
            throw new IllegalStateException("no button " + id);
        });
    }

    private RowAction rowAction(GuiPathList gui, String name, boolean server, String key) {
        return t.client(() -> {
            int index = index(gui, name, server);
            if (index < 0)
                return null;
            gui.scrollTo(index);
            for (RowAction action : gui.placedActions(index))
                if (action.key.equals(key))
                    return action;
            return null;
        });
    }

    private void clickRow(GuiPathList gui, String name, boolean server, String key) {
        RowAction action = rowAction(gui, name, server, key);
        if (action == null) {
            t.check(false, "no " + key + " for " + name + " (server " + server + ")");
            throw new IllegalStateException("cannot go on without " + key);
        }
        t.clientDo(() -> click(gui, action.xPosition + action.width / 2, action.yPosition + action.height / 2));
    }

    private void checkOrder(GuiPathList gui, String message, String... expected) {
        List<String> wanted = Arrays.asList(expected);
        List<String> actual = t.client(() -> {
            List<String> names = new ArrayList<>();
            for (PathEntry entry : gui.visibleEntries())
                if (wanted.contains(entry.path.name))
                    names.add(entry.path.name);
            return names;
        });
        t.check(wanted.equals(actual), message + ": expected " + wanted + " but was " + actual);
    }

    private void renderClean(String name) {
        int depth = t.matrixDepth;
        t.clientDo(() -> {
            t.glErrors = 0;
            t.watchGl = true;
        });
        InGameTestMod.sleep(400);
        t.shot(name);
        t.watchGl = false;
        t.check(t.glErrors == 0, name + ": " + t.glErrors + " frames with OpenGL errors");
        t.check(t.matrixDepth == depth, name + ": matrix stack changed from " + depth + " to " + t.matrixDepth);
    }

    private boolean connected() {
        return t.client(() -> mc.thePlayer != null && mc.getNetHandler() != null) && t.server(() -> !MinecraftServer.getServer().getConfigurationManager().playerEntityList.isEmpty());
    }

    private static void movedBy(InGameTestMod t, String name, CamScene moved, CamScene original, double x, double y, double z) {
        t.check(moved.points.size() == original.points.size(), name + ": number of points");
        for (int i = 0; i < Math.min(moved.points.size(), original.points.size()); i++) {
            CamPoint a = moved.points.get(i);
            CamPoint b = original.points.get(i);
            t.near(name + " point " + i + " x", b.x + x, a.x, 1E-6);
            t.near(name + " point " + i + " y", b.y + y, a.y, 1E-6);
            t.near(name + " point " + i + " z", b.z + z, a.z, 1E-6);
            t.near(name + " point " + i + " yaw", b.rotationYaw, a.rotationYaw, 0);
            t.near(name + " point " + i + " pitch", b.rotationPitch, a.rotationPitch, 0);
            t.near(name + " point " + i + " roll", b.roll, a.roll, 0);
            t.near(name + " point " + i + " zoom", b.zoom, a.zoom, 0);
        }
    }

    // ---------------------------------------------------------------- the tests

    void testStorage() throws Exception {
        cleanUp();
        String player = t.client(() -> mc.getSession().getUsername());
        File folder = new File(mc.mcDataDir, "cmdcam-paths");
        t.check(folder.isDirectory(), "no folder at " + folder);

        t.fillScene();
        long before = System.currentTimeMillis();
        t.cam("save t_store");
        t.check(new File(folder, "t_store.nbt").isFile(), "/cam save did not write a file");
        SavedPath stored = inFolder("t_store");
        t.assertSceneEquals(stored.scene);
        t.check(player.equals(stored.creator), "creator in the folder: " + stored.creator);
        t.check(stored.savedAt >= before, "time of saving: " + stored.savedAt);
        t.check(stored.lastLoaded == 0, "never loaded yet: " + stored.lastLoaded);

        // The server still gets it, and knows on its own who it was
        t.check(waitOnServer("t_store", true), "/cam save did not reach the server");
        SavedPath server = onServer("t_store");
        t.assertSceneEquals(server.scene);
        t.check(player.equals(server.creator), "creator on the server: " + server.creator);
        t.check(!server.creatorId.isEmpty(), "the server should know the id of the creator");
        t.check(server.savedAt >= before, "time of saving on the server: " + server.savedAt);

        t.cam("save t_with spaces");
        t.check(new File(folder, "t_with spaces.nbt").isFile(), "a name with spaces");
        t.cam("delete t_with spaces");
        t.check(!new File(folder, "t_with spaces.nbt").exists(), "/cam delete with spaces");

        // Nothing typed may get out of the folder
        for (String bad : new String[] { "../t_escape", "..\\t_escape", "t_a/b", "t_a:b", "t_a?", "t_a*", "con", "t_ends." })
            t.camRejected("save " + bad);
        t.check(!new File(mc.mcDataDir, "t_escape.nbt").exists(), "a name got out of the folder");
        t.camRejected("delete ../cmdcam-paths/t_store");
        t.check(new File(folder, "t_store.nbt").isFile(), "deleted through a path");

        // Renaming, including only changing the case which is special on Windows
        t.check(t.client(() -> LocalPaths.rename("t_store", "t_renamed")), "rename");
        t.check(!new File(folder, "t_store.nbt").exists() && new File(folder, "t_renamed.nbt").isFile(), "rename on disk");
        t.check(t.client(() -> LocalPaths.rename("t_renamed", "T_RENAMED")), "rename of the case");
        t.check(Arrays.asList(folder.list()).contains("T_RENAMED.nbt"), "the case did not change: " + Arrays.asList(folder.list()));
        putLocal(path("t_taken", "x", 1, 0));
        t.check(!t.client(() -> LocalPaths.rename("T_RENAMED", "T_taken")), "renamed over another path");
        t.check(new File(folder, "T_RENAMED.nbt").isFile(), "the path is gone after a refused rename");

        // Damaged files stay where they are and do not disturb anything
        byte[] garbage = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        File damaged = new File(folder, "t_damaged.nbt");
        Files.write(damaged.toPath(), garbage);
        SavedPath invalid = new SavedPath("t_invalid", lineScene(0));
        invalid.scene.points.get(1).x = Double.NaN;
        File invalidFile = new File(folder, "t_invalid.nbt");
        CompressedStreamTools.writeCompressed(invalid.save(), new java.io.FileOutputStream(invalidFile));
        int count = t.client(() -> {
            LocalPaths.refresh();
            return LocalPaths.unreadable();
        });
        t.check(count >= 2, "damaged files should be counted, have " + count);
        t.check(inFolder("t_taken") != null, "a damaged file hid the others");
        t.check(inFolder("t_damaged") == null && inFolder("t_invalid") == null, "a damaged file was loaded");
        t.check(damaged.isFile() && invalidFile.isFile(), "damaged files were removed");
        t.check(damaged.delete() && invalidFile.delete(), "cleaning up");

        // Scenes of the previous version, in one file in the config folder
        File config = new File(mc.mcDataDir, "config");
        File legacy = new File(config, "cmdcam_scenes.dat");
        File migrated = new File(config, "cmdcam_scenes.dat.migrated");
        migrated.delete();
        t.check(!legacy.exists(), "this instance should not have the old file anymore: " + legacy);
        NBTTagCompound old = new NBTTagCompound();
        old.setTag("t_legacy", SceneNBT.save(lineScene(0)));
        old.setTag("t/legacy2", SceneNBT.save(lineScene(5)));
        old.setTag("T_TAKEN", SceneNBT.save(lineScene(9)));
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(legacy)) {
            CompressedStreamTools.writeCompressed(old, output);
        }
        t.check(t.client(() -> LocalPaths.migrateLegacyFile("Somebody")) == 3, "not all old scenes were moved");
        SavedPath moved = inFolder("t_legacy");
        t.check(moved != null && "Somebody".equals(moved.creator) && moved.savedAt > 0, "the old scene lacks its details");
        t.check(inFolder("t_legacy2") != null, "an old name which is no file name");
        t.check(inFolder("T_TAKEN copy") != null, "an old scene whose name is taken");
        t.check(inFolder("t_taken").scene.points.get(0).x == 0, "an old scene replaced a path");
        t.check(!legacy.exists() && migrated.isFile(), "the old file was not put aside");
        t.check(t.client(() -> LocalPaths.migrateLegacyFile("Somebody")) == 0, "moved twice");
        migrated.delete();
        cleanUp();
    }

    void testBetweenWorlds() {
        cleanUp();
        t.fillScene();
        t.cam("save t_transfer");
        t.check(waitOnServer("t_transfer", true), "not saved in the first world");
        t.leave();
        t.startWorld("cmdcamtest2" + System.currentTimeMillis());
        try {
            t.check(t.waitFor(3000, CMDCamClient::serverAvailable), "the server of the second world has the mod");
            t.check(onServer("t_transfer") == null, "the second world should not have it on its server");
            t.clientDo(() -> CMDCamClient.getScene().points.clear());
            t.cam("load t_transfer");
            t.check(t.waitFor(5000, () -> !CMDCamClient.getScene().points.isEmpty()), "the path did not come along to the other world");
            t.assertSceneEquals(t.scene());
            t.check(t.waitFor(2000, () -> LocalPaths.find("t_transfer").lastLoaded > 0), "the time of loading was not stored");

            t.clientDo(() -> CMDCamClient.getScene().points.clear());
            t.cam("load t_does_not_exist");
            InGameTestMod.sleep(400);
            t.check(t.scene().points.isEmpty(), "loaded something which does not exist");
        } finally {
            t.leave();
            t.rejoin();
        }
        cleanUp();
    }

    void testList() throws Exception {
        cleanUp();
        long now = System.currentTimeMillis();
        putLocal(path("t_old", "Alice", now - 40000, now - 5000));
        putLocal(path("t_new", "Bob", now - 20000, 0));
        putLocal(path("t_mid", "Alice", now - 30000, now - 1000));
        putServer(path("t_srv", "Other", now - 10000, 0));
        putServer(path("t_nobody", "", 0, 0)); // Saved by the previous version

        chat("/cam load");
        GuiPathBrowser gui = waitScreen(GuiPathBrowser.class);
        lastList = System.currentTimeMillis();
        InGameTestMod.sleep(300);
        t.check(t.client(() -> mc.currentScreen == gui), "the chat closed the list");
        t.check(t.waitFor(3000, () -> index(gui, "t_srv", true) >= 0), "the list of the server did not arrive");

        checkOrder(gui, "the newest should be on top", "t_srv", "t_new", "t_mid", "t_old", "t_nobody");
        t.check(t.client(() -> gui.sort() == GuiPathList.Sort.SAVED), "sorted by saving at first");

        clickButton(gui, BUTTON_SORT);
        checkOrder(gui, "sorted by last loaded", "t_mid", "t_old", "t_srv", "t_new", "t_nobody");
        clickButton(gui, BUTTON_SORT);
        checkOrder(gui, "back to last saved", "t_srv", "t_new", "t_mid", "t_old", "t_nobody");

        t.clientDo(() -> gui.creatorFilter("Alice"));
        checkOrder(gui, "only Alice", "t_mid", "t_old");
        t.check(t.client(() -> index(gui, "t_new", false)) < 0, "Bob is not filtered");
        t.clientDo(() -> gui.creatorFilter(""));
        checkOrder(gui, "without a known creator", "t_nobody");
        t.clientDo(() -> gui.creatorFilter(null));
        int total = t.client(() -> gui.visibleEntries().size());
        boolean[] seen = new boolean[3];
        for (int i = 0; i < 20; i++) { // The button goes through all and comes back
            clickButton(gui, BUTTON_CREATOR);
            int shown = t.client(() -> gui.visibleEntries().size());
            if (index(gui, "t_srv", true) >= 0 && index(gui, "t_old", false) < 0 && shown < total)
                seen[0] = true;
            if (index(gui, "t_old", false) >= 0 && index(gui, "t_srv", true) < 0)
                seen[1] = true;
            if (shown == total) {
                seen[2] = true;
                break;
            }
        }
        t.check(seen[0] && seen[1] && seen[2], "the creator button did not go through all: " + Arrays.toString(seen));

        t.clientDo(() -> gui.source(GuiPathList.Source.SERVER));
        checkOrder(gui, "only the server", "t_srv", "t_nobody");
        t.clientDo(() -> gui.source(GuiPathList.Source.LOCAL));
        checkOrder(gui, "only the folder", "t_new", "t_mid", "t_old");
        t.clientDo(() -> gui.source(GuiPathList.Source.ALL));

        // Statistics
        PathEntry entry = entry(gui, "t_new", false);
        t.check(entry.stats.points == 3, "points: " + entry.stats.points);
        t.near("length", 60, entry.stats.length, 1E-6);
        t.near("speed", 6, entry.stats.speed, 1E-6);
        t.near("size x", 30, entry.stats.max.x - entry.stats.min.x, 1E-6);
        t.check(t.client(() -> entry.path.scene.startPosition().x) == 0, "start");
        String details = t.client(() -> String.join("\n", gui.details(entry)));
        for (String expected : new String[] { "Points: 3", "Linear", "Duration", "Length: 60.00", "Average speed: 6.00", "Size: 30.00 x 0.00 x 30.00", "Start: 0.00 100.00 0.00", "End: 30.00 100.00 30.00", "Creator: Bob", "Saved:",
                "Last loaded: Never" })
            t.check(details.contains(expected), "details lack " + expected + ":\n" + details);

        // A file put into the folder from outside shows without doing anything
        Files.copy(new File(LocalPaths.folder(), "t_new.nbt").toPath(), new File(LocalPaths.folder(), "t_dropped.nbt").toPath());
        t.check(t.waitFor(2000, () -> index(gui, "t_dropped", false) >= 0), "a new file in the folder did not show up");
        t.check(new File(LocalPaths.folder(), "t_dropped.nbt").delete(), "cleaning up");
        t.check(t.waitFor(2000, () -> index(gui, "t_dropped", false) < 0), "a removed file stayed in the list");

        // Paths of all shapes should be visible
        CamScene circle = lineScene(0);
        circle.interpolation = CamInterpolation.CIRCULAR;
        circle.lookTarget = new CamTarget.BlockTarget(15, 100, 15);
        SavedPath circlePath = new SavedPath("t_circle", circle);
        circlePath.creator = "Alice";
        circlePath.savedAt = now;
        putLocal(circlePath);
        CamScene hermite = lineScene(0);
        hermite.points.add(new CamPoint(-20, 130, 10, 0, 0, 0, 70));
        hermite.interpolation = CamInterpolation.HERMITE;
        hermite.mode = CamScene.MODE_OUTSIDE;
        SavedPath hermitePath = new SavedPath("t_a very long name which takes a lot of room in the line 123", hermite);
        hermitePath.creator = "A_Very_Long_Name_16";
        hermitePath.savedAt = now - 1;
        putLocal(hermitePath);
        SavedPath single = new SavedPath("t_single", new CamScene());
        single.scene.points.add(new CamPoint(1000000, 3, -1000000, 0, 0, 0, 70));
        single.savedAt = now - 2;
        putLocal(single);
        SavedPath none = new SavedPath("t_no points", new CamScene());
        none.savedAt = now - 3;
        putLocal(none);
        t.clientDo(gui::reload);
        renderClean("paths-list");
        t.check(t.client(() -> {
            RowAction load = null;
            for (RowAction action : gui.placedActions(index(gui, "t_no points", false)))
                if (action.key.equals(GuiPathBrowser.LOAD))
                    load = action;
            return load != null && !load.enabled && load.reason != null;
        }), "a path without points cannot be loaded");
        t.clientDo(() -> gui.scrollTo(gui.visibleEntries().size() - 1));
        renderClean("paths-list-scrolled");

        // Closing does not leave anything behind
        t.clientDo(() -> press(gui, (char) 0, Keyboard.KEY_ESCAPE));
        t.check(t.client(() -> mc.currentScreen == null), "escape does not close the list");
        cleanUp();
    }

    void testRowActions() throws Exception {
        cleanUp();
        long now = System.currentTimeMillis();
        putLocal(path("t_a", "Alice", now - 5000, 0));
        SavedPath other = path("t_srv", "Other", now - 8000, 0);
        other.creatorId = UUID.randomUUID().toString();
        other.scene.points.get(0).x = 77;
        putServer(other);

        // Load: exactly as saved
        clickRow(openBrowser(), "t_a", false, GuiPathBrowser.LOAD);
        t.check(t.client(() -> mc.currentScreen == null), "load should close the list");
        movedBy(t, "load", t.scene(), lineScene(0), 0, 0, 0);
        t.check(inFolder("t_a").lastLoaded >= now, "the time of loading was not stored in the file");
        t.check(inFolder("t_a").savedAt == now - 5000, "loading changed the time of saving");

        clickRow(openBrowser(), "t_srv", true, GuiPathBrowser.LOAD);
        t.near("loaded from the server", 77, t.scene().points.get(0).x, 0);
        InGameTestMod.sleep(200);
        t.check(onServer("t_srv").lastLoaded >= now, "the server did not store the time of loading");
        t.check(inFolder("t_srv") == null, "loading should not copy anything to the folder");

        // Duplicate: the way to get paths of others
        String player = t.client(() -> mc.getSession().getUsername());
        GuiPathBrowser gui = openBrowser();
        clickRow(gui, "t_srv", true, GuiPathBrowser.DUPLICATE);
        SavedPath copy = inFolder("t_srv");
        t.check(copy != null, "the copy of a path on the server is not in the folder");
        if (copy != null) {
            t.check(player.equals(copy.creator), "the copy should belong to who made the copy: " + copy.creator);
            t.check(copy.savedAt >= now, "the time of saving of the copy: " + copy.savedAt);
            t.check(copy.lastLoaded == 0, "the copy was never loaded");
            t.near("the content of the copy", 77, copy.scene.points.get(0).x, 0);
        }
        t.check(onServer("t_srv") != null, "the original is gone");
        t.check(t.client(() -> mc.currentScreen == gui), "duplicate should stay in the list");
        t.check("Other".equals(onServer("t_srv").creator), "copying changed the creator on the server");
        // By someone else on the server and by this player in the folder: these are two paths now
        PathEntry mine = t.client(() -> entry(gui, "t_srv", false));
        t.check(mine != null && !mine.alsoOnServer, "the copy has another creator than the one on the server");
        t.check(t.client(() -> entry(gui, "t_srv", true)) != null, "the one on the server should stay listed");
        clickRow(gui, "t_srv", true, GuiPathBrowser.DUPLICATE);
        t.check(inFolder("t_srv copy") != null, "a second copy should get its own name");
        t.check(player.equals(inFolder("t_srv copy").creator), "creator of the second copy");
        clickRow(gui, "t_a", false, GuiPathBrowser.DUPLICATE);
        t.check(inFolder("t_a copy") != null, "copy of a path in the folder");
        t.check(inFolder("t_a") != null, "the original in the folder is gone");

        // Rename
        clickRow(gui, "t_a", false, GuiPathBrowser.RENAME);
        GuiPathRename rename = waitScreen(GuiPathRename.class);
        t.check("t_a".equals(t.client(() -> rename.nameField().getText())), "the rename screen starts with the name");
        t.clientDo(() -> rename.nameField().setText("t_x/y"));
        t.check(!buttonEnabled(rename, RENAME_DONE), "a bad name should not be possible");
        t.clientDo(() -> rename.nameField().setText("t_srv"));
        clickButton(rename, RENAME_DONE);
        t.check(t.client(() -> mc.currentScreen == rename), "renaming to an existing name should stay on the screen");
        t.check(inFolder("t_a") != null && inFolder("t_srv").scene.points.get(0).x == 77, "renaming to an existing name changed something");
        t.clientDo(() -> rename.nameField().setText(""));
        type(rename, "t_b");
        clickButton(rename, RENAME_DONE);
        t.check(t.client(() -> mc.currentScreen != rename), "the rename screen should be gone");
        GuiPathBrowser again = waitScreen(GuiPathBrowser.class);
        t.check(again == gui, "back to the list");
        t.check(inFolder("t_b") != null && !new File(LocalPaths.folder(), "t_a.nbt").exists(), "not renamed");
        t.check(t.client(() -> index(again, "t_b", false)) >= 0, "the list does not show the new name");

        clickRow(gui, "t_b", false, GuiPathBrowser.RENAME);
        GuiPathRename cancelled = waitScreen(GuiPathRename.class);
        t.clientDo(() -> cancelled.nameField().setText("t_c"));
        clickButton(cancelled, RENAME_CANCEL);
        waitScreen(GuiPathBrowser.class);
        t.check(inFolder("t_c") == null && inFolder("t_b") != null, "cancel renamed it");

        // On the server
        clickRow(gui, "t_srv", true, GuiPathBrowser.RENAME);
        GuiPathRename serverRename = waitScreen(GuiPathRename.class);
        t.clientDo(() -> serverRename.nameField().setText("t_srv2"));
        clickButton(serverRename, RENAME_DONE);
        waitScreen(GuiPathBrowser.class);
        t.check(waitOnServer("t_srv2", true), "not renamed on the server");
        t.check(onServer("t_srv") == null, "the old name stayed on the server");
        t.check("Other".equals(onServer("t_srv2").creator), "renaming changed the creator");
        t.check(t.waitFor(2000, () -> index(again, "t_srv2", true) >= 0 && index(again, "t_srv", true) < 0), "the list of the server was not updated");
        t.check(inFolder("t_srv") != null, "renaming on the server changed the folder");

        // What a modified client could send: the server has to say no on its own
        putServer(path("t_srv3", "Other", now, 0));
        NBTTagCompound taken = new NBTTagCompound();
        taken.setString("name", "t_srv3");
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.RENAME, "t_srv2", taken));
        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("name", "../t_bad");
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.RENAME, "t_srv2", bad));
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.RENAME, "t_missing", taken));
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.RENAME, "t_srv2", null));
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.DELETE, "t_missing", null));
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.LOADED, "t_missing", null));
        InGameTestMod.sleep(400);
        t.check(onServer("t_srv2") != null && onServer("t_srv3") != null && onServer("../t_bad") == null, "the server accepted a bad rename");

        // Delete asks first
        clickRow(gui, "t_b", false, GuiPathBrowser.DELETE);
        GuiYesNo question = waitScreen(GuiYesNo.class);
        t.clientDo(() -> gui.confirmClicked(false, 0));
        t.check(t.client(() -> mc.currentScreen == gui), "no should go back");
        t.check(inFolder("t_b") != null, "no deleted it");
        clickRow(gui, "t_b", false, GuiPathBrowser.DELETE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> gui.confirmClicked(true, 0));
        t.check(t.client(() -> mc.currentScreen == gui), "yes should go back");
        t.check(inFolder("t_b") == null, "not deleted");
        t.check(!new File(LocalPaths.folder(), "t_b.nbt").exists(), "the file is still there");
        t.check(t.client(() -> index(gui, "t_b", false)) < 0, "the list still shows it");

        clickRow(gui, "t_srv2", true, GuiPathBrowser.DELETE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> gui.confirmClicked(true, 0));
        t.check(waitOnServer("t_srv2", false), "not deleted on the server");
        t.check(t.waitFor(2000, () -> index(gui, "t_srv2", true) < 0), "the list of the server still shows it");
        t.check(inFolder("t_srv") != null, "deleting on the server touched the folder");
        t.clientDo(() -> mc.displayGuiScreen(null));

        // Saving stores both, the same path is one line until one of them changes
        t.addLine();
        t.cam("save t_twin");
        t.check(waitOnServer("t_twin", true), "not on the server");
        GuiPathBrowser list = openBrowser();
        t.check(entry(list, "t_twin", false) != null && entry(list, "t_twin", false).alsoOnServer, "the line should say it is on the server as well");
        t.check(entry(list, "t_twin", true) == null, "the same path is listed twice");
        t.clientDo(() -> list.source(GuiPathList.Source.SERVER));
        t.check(entry(list, "t_twin", true) != null, "the copy on the server should be reachable");
        t.clientDo(() -> list.source(GuiPathList.Source.ALL));
        clickRow(list, "t_twin", false, GuiPathBrowser.DELETE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> list.confirmClicked(true, 0));
        t.check(inFolder("t_twin") == null, "the folder still has it");
        t.check(onServer("t_twin") != null, "deleting in the folder removed it on the server");
        t.check(t.client(() -> entry(list, "t_twin", true)) != null, "now the one on the server should be listed");

        // Without the permission. Only the client's side: this test has no second player
        t.clientDo(() -> {
            for (SavedPath path : new ArrayList<>(ServerPaths.all()))
                ServerPaths.entry(path.name, path.save());
            ServerPaths.finish(new NBTTagCompound());
            list.reload();
        });
        RowAction noRename = rowAction(list, "t_twin", true, GuiPathBrowser.RENAME);
        RowAction noDelete = rowAction(list, "t_twin", true, GuiPathBrowser.DELETE);
        RowAction canCopy = rowAction(list, "t_twin", true, GuiPathBrowser.DUPLICATE);
        t.check(noRename != null && !noRename.enabled && noRename.reason != null, "rename without the permission");
        t.check(noDelete != null && !noDelete.enabled && noDelete.reason != null, "delete without the permission");
        t.check(canCopy != null && canCopy.enabled, "anyone can copy a path to the folder");
        clickRow(list, "t_twin", true, GuiPathBrowser.DELETE);
        t.check(t.client(() -> mc.currentScreen == list), "a disabled button did something");
        t.clientDo(() -> mc.displayGuiScreen(null));
        cleanUp();
    }

    void testAdvancedLoad() throws Exception {
        cleanUp();
        CamScene original = new CamScene();
        original.interpolation = CamInterpolation.HERMITE;
        original.points.add(new CamPoint(10, 100, 10, 11, 12, 13, 50));
        original.points.add(new CamPoint(20, 105, 15, 21, 22, 23, 60));
        original.points.add(new CamPoint(30, 100, 40, 31, 32, 33, 70));
        original.lookTarget = new CamTarget.BlockTarget(5, 90, 5);
        SavedPath path = new SavedPath("t_adv", original);
        path.creator = "Alice";
        path.savedAt = System.currentTimeMillis();
        putLocal(path);

        // Specific coordinates
        GuiPathBrowser gui = openBrowser();
        clickRow(gui, "t_adv", false, GuiPathBrowser.ADVANCED);
        GuiPathAdvancedLoad advanced = waitScreen(GuiPathAdvancedLoad.class);
        Vec3d none = t.client(advanced::movement);
        t.check(none != null && none.x == 0 && none.y == 0 && none.z == 0, "without typing anything it should not move");
        renderClean("paths-advanced");
        t.clientDo(() -> advanced.location().set("100", "70", "-50"));
        clickButton(advanced, ADVANCED_LOAD);
        t.check(t.client(() -> mc.currentScreen == null), "load should close the screens");
        movedBy(t, "specific coordinates", t.scene(), original, 90, -30, -60);
        CamTarget.BlockTarget look = (CamTarget.BlockTarget) t.scene().lookTarget;
        t.check(look.x == 95 && look.y == 60 && look.z == -55, "the block looked at should move along: " + look.x + " " + look.y + " " + look.z);
        t.near("the speed stays", t.scene().duration, original.duration, 0);
        movedBy(t, "the file stays as it was", inFolder("t_adv").scene, original, 0, 0, 0);
        t.check(inFolder("t_adv").lastLoaded > 0, "the time of loading was not stored");

        // The current position, which is what ~ means everywhere
        gui = openBrowser();
        clickRow(gui, "t_adv", false, GuiPathBrowser.ADVANCED);
        GuiPathAdvancedLoad here = waitScreen(GuiPathAdvancedLoad.class);
        clickButton(here, ADVANCED_POSITION);
        double[] eyes = t.client(() -> {
            CamPoint point = CMDCamClient.createLocalPoint();
            return new double[] { point.x, point.y, point.z };
        });
        clickButton(here, ADVANCED_LOAD);
        movedBy(t, "at the current position", t.scene(), original, eyes[0] - 10, eyes[1] - 100, eyes[2] - 10);

        // With an offset, mixed with a specific coordinate
        gui = openBrowser();
        clickRow(gui, "t_adv", false, GuiPathBrowser.ADVANCED);
        GuiPathAdvancedLoad offset = waitScreen(GuiPathAdvancedLoad.class);
        t.clientDo(() -> offset.location().set("~1", "64", "~-3.5"));
        clickButton(offset, ADVANCED_LOAD);
        movedBy(t, "with an offset", t.scene(), original, eyes[0] + 1 - 10, 64 - 100, eyes[2] - 3.5 - 10);

        // Text which cannot be used
        gui = openBrowser();
        clickRow(gui, "t_adv", false, GuiPathBrowser.ADVANCED);
        GuiPathAdvancedLoad invalid = waitScreen(GuiPathAdvancedLoad.class);
        for (String bad : new String[] { "", "abc", "~x", "~~", "1e99", "NaN", "Infinity", "30000001" }) {
            t.clientDo(() -> invalid.location().set(bad, "1", "1"));
            t.check(t.client(invalid::movement) == null, bad + " should not be usable");
            t.check(!buttonEnabled(invalid, ADVANCED_LOAD), bad + " should disable load");
            clickButton(invalid, ADVANCED_LOAD);
            t.check(t.client(() -> mc.currentScreen == invalid), bad + " should not load");
        }
        clickButton(invalid, ADVANCED_RESET);
        t.check(t.client(invalid::movement).x == 0, "back to the saved start");
        t.clientDo(() -> press(invalid, (char) 0, Keyboard.KEY_ESCAPE));
        t.check(t.client(() -> mc.currentScreen == invalid) == false, "escape should go back to the list");
        waitScreen(GuiPathBrowser.class);
        t.clientDo(() -> mc.displayGuiScreen(null));

        // Following a block: its points are relative to it, so the block moves
        CamScene follow = new CamScene();
        follow.points.add(new CamPoint(1, 2, 3, 0, 0, 0, 70));
        follow.points.add(new CamPoint(4, 5, 6, 0, 0, 0, 70));
        follow.posTarget = new CamTarget.BlockTarget(10, 60, 10);
        SavedPath followPath = new SavedPath("t_follow_block", follow);
        followPath.savedAt = System.currentTimeMillis();
        putLocal(followPath);
        // Following something moving: there is no location to choose
        CamScene moving = new CamScene();
        moving.points.add(new CamPoint(1, 2, 3, 0, 0, 0, 70));
        moving.posTarget = new CamTarget.EntityTarget(UUID.randomUUID());
        SavedPath movingPath = new SavedPath("t_follow_entity", moving);
        movingPath.savedAt = System.currentTimeMillis();
        putLocal(movingPath);

        gui = openBrowser();
        RowAction disabled = rowAction(gui, "t_follow_entity", false, GuiPathBrowser.ADVANCED);
        t.check(disabled != null && !disabled.enabled && disabled.reason != null, "advanced load of a path following an entity");
        clickRow(gui, "t_follow_block", false, GuiPathBrowser.ADVANCED);
        GuiPathAdvancedLoad block = waitScreen(GuiPathAdvancedLoad.class);
        t.clientDo(() -> block.location().set("100.25", "70", "50"));
        clickButton(block, ADVANCED_LOAD);
        Vec3d start = t.client(() -> CMDCamClient.getScene().startPosition());
        t.near("start of a path following a block x", 100.25, start.x, 1E-6);
        t.near("start of a path following a block y", 70, start.y, 1E-6);
        t.near("start of a path following a block z", 50, start.z, 1E-6);
        CamPoint first = t.scene().points.get(0);
        CamPoint second = t.scene().points.get(1);
        t.near("the points keep their distance x", 3, second.x - first.x, 1E-6);
        t.near("the points keep their distance y", 3, second.y - first.y, 1E-6);
        t.near("the points keep their distance z", 3, second.z - first.z, 1E-6);
        cleanUp();
    }

    void testSaveScreen() throws Exception {
        cleanUp();
        String player = t.client(() -> mc.getSession().getUsername());

        // Without points there is nothing to save
        chat("/cam save");
        InGameTestMod.sleep(300);
        t.check(t.client(() -> mc.currentScreen == null), "the save screen should not open without points");

        t.addLine();
        CamScene current = t.scene().copy();
        chat("/cam save");
        GuiPathSave gui = waitScreen(GuiPathSave.class);
        InGameTestMod.sleep(200);
        t.check(t.client(() -> mc.currentScreen == gui), "the chat closed the save screen");
        Vec3d initial = t.client(() -> gui.location().resolve());
        t.check(initial.x == 0 && initial.y == 100 && initial.z == 0, "the start should be the current one: " + initial.x + " " + initial.y + " " + initial.z);
        t.check(!buttonEnabled(gui, BUTTON_SAVE), "cannot save without a name");

        // Typing goes to the name field
        type(gui, "t_saved");
        t.check("t_saved".equals(t.client(() -> gui.nameField().getText())), "typed: " + t.client(() -> gui.nameField().getText()));
        t.check(buttonEnabled(gui, BUTTON_SAVE), "should be able to save now");
        renderClean("paths-save");
        long before = System.currentTimeMillis();
        clickButton(gui, BUTTON_SAVE);
        t.check(t.client(() -> mc.currentScreen == null), "saving should close the screen");
        SavedPath saved = inFolder("t_saved");
        t.check(saved != null && player.equals(saved.creator) && saved.savedAt >= before, "not in the folder as expected");
        movedBy(t, "saved as it is", saved.scene, current, 0, 0, 0);
        t.check(waitOnServer("t_saved", true), "not on the server");
        movedBy(t, "saved on the server", onServer("t_saved").scene, current, 0, 0, 0);

        // A different start: the whole path is saved there, what is being edited stays
        chat("/cam save");
        GuiPathSave moved = waitScreen(GuiPathSave.class);
        type(moved, "t_moved");
        clickButton(moved, BUTTON_MY_POSITION);
        double[] eyes = t.client(() -> {
            CamPoint point = CMDCamClient.createLocalPoint();
            return new double[] { point.x, point.y, point.z };
        });
        clickButton(moved, BUTTON_SAVE);
        movedBy(t, "saved with a new start", inFolder("t_moved").scene, current, eyes[0], eyes[1] - 100, eyes[2]);
        movedBy(t, "what is being edited", t.scene(), current, 0, 0, 0);

        chat("/cam save");
        GuiPathSave typed = waitScreen(GuiPathSave.class);
        type(typed, "t_typed");
        t.clientDo(() -> typed.location().set("~5", "12", "~"));
        clickButton(typed, BUTTON_SAVE);
        movedBy(t, "saved with typed coordinates", inFolder("t_typed").scene, current, eyes[0] + 5, 12 - 100, eyes[2]);

        chat("/cam save");
        GuiPathSave invalid = waitScreen(GuiPathSave.class);
        type(invalid, "t_invalid");
        t.clientDo(() -> invalid.location().set("x", "12", "~"));
        t.check(!buttonEnabled(invalid, BUTTON_SAVE), "bad coordinates should not be saved");
        t.clientDo(() -> invalid.location().set("1", "12", "1"));
        t.clientDo(() -> invalid.nameField().setText("t_a/b"));
        t.check(!buttonEnabled(invalid, BUTTON_SAVE), "a bad name should not be saved");
        t.clientDo(() -> mc.displayGuiScreen(null));

        // Paths of the same creator can be overwritten after asking
        t.clientDo(() -> {
            LocalPaths.find("t_saved").lastLoaded = 0;
            SavedPath stored = LocalPaths.find("t_saved");
            stored.lastLoaded = 12345;
            LocalPaths.write(stored);
        });
        t.clientDo(() -> CMDCamClient.getScene().points.get(0).x = 5);
        pauseForList();
        chat("/cam save");
        GuiPathSave over = waitScreen(GuiPathSave.class);
        t.check(t.waitFor(3000, () -> !ServerPaths.waiting()), "no answer from the server");
        clickRow(over, "t_saved", false, GuiPathSave.OVERWRITE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> over.confirmClicked(false, 0));
        t.check(t.client(() -> mc.currentScreen == over), "no should go back");
        t.check("t_saved".equals(t.client(() -> over.nameField().getText())), "the name of the path to overwrite");
        t.near("no does not overwrite", 0, inFolder("t_saved").scene.points.get(0).x, 0);
        clickButton(over, BUTTON_SAVE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> over.confirmClicked(true, 0));
        t.check(t.client(() -> mc.currentScreen == null), "saving should close the screen");
        t.near("overwritten in the folder", 5, inFolder("t_saved").scene.points.get(0).x, 0);
        t.check(inFolder("t_saved").lastLoaded == 12345, "overwriting forgot when it was last loaded");
        InGameTestMod.sleep(300);
        t.near("overwritten on the server", 5, onServer("t_saved").scene.points.get(0).x, 0);

        // Those of others cannot in the folder. On the server an operator can do everything
        long now = System.currentTimeMillis();
        SavedPath foreignLocal = path("t_foreign", "Someone", now, 0);
        putLocal(foreignLocal);
        SavedPath foreignServer = path("t_foreign_srv", "Someone", now, 0);
        foreignServer.creatorId = UUID.randomUUID().toString();
        putServer(foreignServer);
        pauseForList();
        chat("/cam save");
        GuiPathSave blocked = waitScreen(GuiPathSave.class);
        t.check(t.waitFor(3000, () -> index(blocked, "t_foreign_srv", true) >= 0), "the list of the server did not arrive");
        RowAction foreignRow = rowAction(blocked, "t_foreign", false, GuiPathSave.OVERWRITE);
        RowAction foreignServerRow = rowAction(blocked, "t_foreign_srv", true, GuiPathSave.OVERWRITE);
        RowAction ownRow = rowAction(blocked, "t_saved", false, GuiPathSave.OVERWRITE);
        t.check(foreignRow != null && !foreignRow.enabled && foreignRow.reason != null, "overwrite for a path of someone else in the folder");
        t.check(foreignServerRow != null && foreignServerRow.enabled, "an operator can overwrite a path of someone else on the server");
        t.check(ownRow != null && ownRow.enabled, "overwrite for an own path");
        for (String name : new String[] { "t_foreign", "T_FOREIGN" }) {
            t.clientDo(() -> blocked.nameField().setText(name));
            t.check(!buttonEnabled(blocked, BUTTON_SAVE), name + " belongs to someone else");
        }
        t.clientDo(() -> blocked.nameField().setText("t_foreign_srv"));
        t.check(buttonEnabled(blocked, BUTTON_SAVE), "an operator can use the name of a path on the server");
        clickButton(blocked, BUTTON_SAVE);
        waitScreen(GuiYesNo.class);
        t.clientDo(() -> blocked.confirmClicked(true, 0));
        InGameTestMod.sleep(300);
        t.near("an operator replaced the path of someone else on the server", 5, onServer("t_foreign_srv").scene.points.get(0).x, 0);
        t.check(player.equals(onServer("t_foreign_srv").creator), "the path on the server is now by who saved it: " + onServer("t_foreign_srv").creator);
        t.check(inFolder("t_foreign_srv") != null && player.equals(inFolder("t_foreign_srv").creator), "the copy in the folder");

        // The same for the command, and on the server also for what a modified client could send
        t.camRejected("save t_foreign");
        t.near("the path of someone else in the folder", 0, inFolder("t_foreign").scene.points.get(0).x, 0);
        SavedPath second = path("t_foreign_cmd", "Someone", now, 0);
        second.creatorId = UUID.randomUUID().toString();
        putServer(second);
        t.cam("save t_foreign_cmd");
        InGameTestMod.sleep(300);
        t.near("/cam save of an operator on the server", 5, onServer("t_foreign_cmd").scene.points.get(0).x, 0);

        for (String bad : new String[] { "", "../t_x", "t_a/b", "con", "t_ends." })
            CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.SET, bad, SceneNBT.save(current)));
        CamScene notANumber = lineScene(0);
        notANumber.points.get(0).x = Double.NaN;
        CMDCam.NETWORK.sendToServer(new CamPacket(CamPacket.Action.SET, "t_nan", SceneNBT.save(notANumber)));
        InGameTestMod.sleep(300);
        t.check(onServer("t_nan") == null, "the server accepted an invalid value");
        t.check(onServer("") == null && onServer("../t_x") == null && onServer("t_a/b") == null, "the server accepted a bad name");
        t.check(connected(), "bad packets should not disconnect");
        cleanUp();
    }

    void testLarge() throws Exception {
        cleanUp();
        java.util.Random random = new java.util.Random(1);
        t.clientDo(() -> {
            CamScene scene = CMDCamClient.getScene();
            for (int i = 0; i < 3000; i++)
                scene.points.add(new CamPoint(random.nextDouble() * 1000, random.nextDouble() * 100, random.nextDouble() * 1000, random.nextDouble() * 360, 0, 0, 70));
        });
        t.cam("save t_large");
        InGameTestMod.sleep(500);
        t.check(connected(), "saving a large path ended the connection");
        t.check(inFolder("t_large") != null && inFolder("t_large").scene.points.size() == 3000, "the large path should be in the folder");
        t.check(onServer("t_large") == null, "this should be too large for the client to send, adjust this test if it no longer is");

        CamScene medium = new CamScene();
        for (int i = 0; i < 200; i++)
            medium.points.add(new CamPoint(random.nextDouble() * 1000, random.nextDouble() * 100, random.nextDouble() * 1000, random.nextDouble() * 360, 0, 0, 70));
        t.clientDo(() -> CMDCamClient.getScene().set(medium));
        t.cam("save t_medium");
        t.check(waitOnServer("t_medium", true), "200 points should fit into a packet");

        // Everything has to get to the client as well, also if there is more than a packet can hold
        CamScene huge = new CamScene();
        huge.smoothBeginning = false; // Flying to the start of the path can take up to 20 seconds
        for (int i = 0; i < SavedPath.MAX_POINTS; i++)
            huge.points.add(new CamPoint(random.nextDouble() * 1000, random.nextDouble() * 100, random.nextDouble() * 1000, random.nextDouble() * 360, random.nextDouble(), random.nextDouble(), 70));
        for (int i = 0; i < 8; i++) {
            SavedPath path = new SavedPath("t_huge" + i, huge);
            path.creator = "Other";
            path.savedAt = System.currentTimeMillis();
            putServer(path);
        }
        GuiPathBrowser gui = openBrowser();
        t.check(t.waitFor(5000, () -> index(gui, "t_huge7", true) >= 0), "large paths did not arrive from the server");
        t.check(connected(), "the list of large paths ended the connection");
        clickRow(gui, "t_huge3", true, GuiPathBrowser.LOAD);
        t.check(t.scene().points.size() == SavedPath.MAX_POINTS, "points: " + t.scene().points.size());

        // The same for the other ways a scene comes from the server
        t.clientDo(() -> CMDCamClient.getScene().points.clear());
        t.cam("load t_huge5");
        t.check(t.waitFor(5000, () -> CMDCamClient.getScene().points.size() == SavedPath.MAX_POINTS), "/cam load of a large path from the server");
        t.check(connected(), "/cam load of a large path ended the connection");
        t.serverCommand("/cam-server start t_huge2 @a 1s");
        t.check(t.waitFor(3000, CMDCamClient::isPlaying), "/cam-server start of a large path did not start it");
        t.check(t.waitFor(5000, () -> !CMDCamClient.isPlaying()), "the large path did not end");
        t.check(connected(), "/cam-server start of a large path ended the connection");

        CamScene tooMany = new CamScene();
        for (int i = 0; i < SavedPath.MAX_POINTS + 1; i++)
            tooMany.points.add(new CamPoint(i, 0, 0, 0, 0, 0, 70));
        SavedPath rejected = new SavedPath("t_too_many", tooMany);
        File file = new File(LocalPaths.folder(), "t_too_many.nbt");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(rejected.save(), output);
        }
        t.check(inFolder("t_too_many") == null, "a file with too many points was read");
        t.check(file.delete(), "cleaning up");
        cleanUp();
    }

    void testFolderButton() {
        cleanUp();
        if (!Boolean.getBoolean("cmdcam.ingametest.openfolder")) {
            t.info("skipped, this run does not open windows");
            return;
        }
        GuiPathBrowser gui = openBrowser();
        clickButton(gui, BUTTON_FOLDER);
        t.info("clicked the folder button, " + LocalPaths.folder().getAbsolutePath());
        t.check(t.client(() -> mc.currentScreen == gui), "the button should stay on the list");
        t.clientDo(() -> mc.displayGuiScreen(null));
    }

}
