package team.creative.cmdcam.client.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.client.path.LocalPaths;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.client.path.ServerPaths;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.util.DurationParser;

/** The list of saved paths both screens are built on: all in the folder and on the server, with filters, sorting, previews and statistics. */
@SideOnly(Side.CLIENT)
public abstract class GuiPathList extends GuiScreen implements GuiYesNoCallback {

    public enum Sort {
        SAVED,
        LOADED
    }

    public enum Source {
        ALL,
        LOCAL,
        SERVER
    }

    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 60;
    private static final int PREVIEW_SIZE = 52;
    private static final int SCROLLBAR = 6;
    private static final long STATUS_MILLIS = 8000;

    private static final int ID_SORT = 0;
    private static final int ID_CREATOR = 1;
    private static final int ID_SOURCE = 2;
    private static final int ID_FOLDER = 3;
    private static final int ID_DONE = 4;
    protected static final int ID_FIRST_FREE = 10;

    private final List<PathEntry> all = new ArrayList<>();
    private final List<PathEntry> visible = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private Sort sort = Sort.SAVED;
    private String creatorFilter;
    private Source source = Source.ALL;

    private GuiButton sortButton;
    private GuiButton creatorButton;
    private GuiButton sourceButton;

    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int scroll;
    private boolean draggingBar;

    private boolean requested;
    private int seenVersion;
    private long seenFolder;
    private String status;
    private boolean statusError;
    private long statusUntil;
    private Runnable afterConfirm;

    /** @return where the part above the list ends */
    protected abstract int headerBottom();

    protected abstract String title();

    protected abstract List<RowAction> actions(PathEntry entry);

    protected abstract void drawHeader(int mouseX, int mouseY);

    /** @return text to show instead of the usual line above the buttons, null for none */
    protected String hint() {
        return null;
    }

    // ---------------------------------------------------------------- set up and data

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        listLeft = MARGIN;
        listRight = width - MARGIN - SCROLLBAR;
        int toolbar = headerBottom() + 2;
        listTop = toolbar + 22;
        listBottom = height - 36;

        int buttonWidth = (width - 2 * MARGIN - 8) / 3;
        sortButton = new GuiButton(ID_SORT, MARGIN, toolbar, buttonWidth, 20, "");
        creatorButton = new GuiButton(ID_CREATOR, MARGIN + buttonWidth + 4, toolbar, buttonWidth, 20, "");
        sourceButton = new GuiButton(ID_SOURCE, MARGIN + 2 * (buttonWidth + 4), toolbar, buttonWidth, 20, "");
        buttonList.add(sortButton);
        buttonList.add(creatorButton);
        buttonList.add(sourceButton);
        buttonList.add(new GuiButton(ID_FOLDER, MARGIN, height - 24, 130, 20, I18n.format("cmdcam.gui.open_folder")));
        buttonList.add(new GuiButton(ID_DONE, width - MARGIN - 80, height - 24, 80, 20, I18n.format("gui.done")));

        if (!requested) {
            requested = true;
            if (CMDCamClient.serverAvailable())
                ServerPaths.request();
        }
        reload();
    }

    /** Reads everything again, so what happened in the folder or on the server shows. */
    public void reload() {
        all.clear();
        all.addAll(PathEntry.collect());
        seenVersion = ServerPaths.version();
        seenFolder = LocalPaths.folder().lastModified();
        if (creatorFilter != null && !creators().contains(creatorFilter))
            creatorFilter = null;
        applyFilters();
    }

    private void applyFilters() {
        visible.clear();
        for (PathEntry entry : all) {
            if (source == Source.ALL && entry.inFolder)
                continue; // Listed once, as the path in the folder which says it is on the server as well
            if ((source == Source.LOCAL && entry.server) || (source == Source.SERVER && !entry.server))
                continue;
            if (creatorFilter != null && !creatorFilter.equals(entry.path.creator))
                continue;
            visible.add(entry);
        }

        Collections.sort(visible, new Comparator<PathEntry>() {
            @Override
            public int compare(PathEntry first, PathEntry second) {
                SavedPath a = first.path;
                SavedPath b = second.path;
                int result = sort == Sort.LOADED ? Long.compare(b.lastLoaded, a.lastLoaded) : 0;
                if (result == 0)
                    result = Long.compare(b.savedAt, a.savedAt);
                return result != 0 ? result : a.name.compareToIgnoreCase(b.name);
            }
        });

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        sortButton.displayString = I18n.format("cmdcam.gui.sort." + sort.name().toLowerCase(Locale.ROOT));
        creatorButton.displayString = I18n.format("cmdcam.gui.creator", creatorFilter == null ? I18n.format("cmdcam.gui.all") : creatorName(creatorFilter));
        sourceButton.displayString = I18n.format("cmdcam.gui.source." + source.name().toLowerCase(Locale.ROOT));
        sourceButton.enabled = CMDCamClient.serverAvailable();
    }

    private TreeSet<String> creators() {
        TreeSet<String> creators = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PathEntry entry : all)
            creators.add(entry.path.creator);
        return creators;
    }

    static String points(int count) {
        return I18n.format(count == 1 ? "cmdcam.gui.point" : "cmdcam.gui.points", count);
    }

    private static String creatorName(String creator) {
        return creator.isEmpty() ? I18n.format("cmdcam.gui.unknown") : creator;
    }

    /** @return all entries, also those the filters hide right now */
    protected List<PathEntry> allEntries() {
        return all;
    }

    /** @return the lines of the list from top to bottom */
    public List<PathEntry> visibleEntries() {
        return visible;
    }

    public void status(String text, boolean error) {
        status = text;
        statusError = error;
        statusUntil = System.currentTimeMillis() + STATUS_MILLIS;
    }

    public Sort sort() {
        return sort;
    }

    public void sort(Sort value) {
        sort = value;
        applyFilters();
    }

    public void creatorFilter(String value) {
        creatorFilter = value;
        applyFilters();
    }

    public void source(Source value) {
        source = value;
        applyFilters();
    }

    /** Asks before doing something which cannot be undone. Afterwards this screen is back. */
    protected void confirm(String question, String detail, Runnable action) {
        afterConfirm = action;
        mc.displayGuiScreen(new GuiYesNo(this, question, detail, 0));
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        Runnable action = afterConfirm;
        afterConfirm = null;
        mc.displayGuiScreen(this);
        if (result && action != null)
            action.run();
    }

    @Override
    public void updateScreen() {
        if (mc.thePlayer == null) {
            mc.displayGuiScreen(null);
            return;
        }
        if (ServerPaths.version() != seenVersion || LocalPaths.folder().lastModified() != seenFolder)
            reload();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled)
            return;
        switch (button.id) {
            case ID_SORT:
                sort(sort == Sort.SAVED ? Sort.LOADED : Sort.SAVED);
                break;
            case ID_CREATOR:
                creatorFilter(nextCreator());
                break;
            case ID_SOURCE:
                source(Source.values()[(source.ordinal() + 1) % Source.values().length]);
                break;
            case ID_FOLDER:
                if (PathActions.openFolder())
                    status(I18n.format("cmdcam.gui.folder_opened"), false);
                else
                    status(I18n.format("cmdcam.gui.folder_failed", LocalPaths.folder().getAbsolutePath()), true);
                break;
            case ID_DONE:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    /** All, then each creator in turn. */
    private String nextCreator() {
        TreeSet<String> creators = creators();
        if (creatorFilter == null)
            return creators.isEmpty() ? null : creators.first();
        return creators.higher(creatorFilter);
    }

    private int maxScroll() {
        return Math.max(0, visible.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    private int rowTop(int index) {
        return listTop + index * ROW_HEIGHT - scroll;
    }

    private boolean inList(int mouseX, int mouseY) {
        return mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom;
    }

    /** @return the buttons of a line at their current place */
    public List<RowAction> placedActions(int index) {
        List<RowAction> actions = actions(visible.get(index));
        int left = listLeft + PREVIEW_SIZE + 6;
        int gap = 2;
        int available = listRight - left - 2;
        int each = Math.min(70, (available - gap * (actions.size() - 1)) / Math.max(1, actions.size()));
        int y = rowTop(index) + ROW_HEIGHT - RowAction.HEIGHT - 3;
        for (int i = 0; i < actions.size(); i++) {
            RowAction action = actions.get(i);
            action.xPosition = left + i * (each + gap);
            action.yPosition = y;
            action.width = each;
        }
        return actions;
    }

    /** Brings a line into view. */
    public void scrollTo(int index) {
        int top = index * ROW_HEIGHT;
        int view = listBottom - listTop;
        if (top < scroll)
            scroll = top;
        else if (top + ROW_HEIGHT > scroll + view)
            scroll = top + ROW_HEIGHT - view;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0)
            return;
        if (mouseX >= listRight && mouseX < listRight + SCROLLBAR && mouseY >= listTop && mouseY < listBottom && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return;
        }
        if (!inList(mouseX, mouseY))
            return;
        int index = (mouseY - listTop + scroll) / ROW_HEIGHT;
        if (index < 0 || index >= visible.size())
            return;
        for (RowAction action : placedActions(index))
            if (action.contains(mouseX, mouseY)) {
                if (action.enabled) {
                    mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1));
                    action.action.run();
                }
                return;
            }
    }

    private void dragBar(int mouseY) {
        double position = (mouseY - listTop) / (double) Math.max(1, listBottom - listTop);
        scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(position * maxScroll())));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingBar)
            dragBar(mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0)
            draggingBar = false;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (wheel > 0 ? 1 : -1) * ROW_HEIGHT / 2));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, title(), width / 2, 5, 0xFFFFFF);
        drawRect(listLeft, listTop, listRight + SCROLLBAR, listBottom, 0x80000000);

        boolean mouseInList = inList(mouseX, mouseY);
        int listMouseX = mouseInList ? mouseX : -1;
        int listMouseY = mouseInList ? mouseY : -1;

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(listLeft * scale, mc.displayHeight - listBottom * scale, (listRight - listLeft) * scale, (listBottom - listTop) * scale);
        for (int i = 0; i < visible.size(); i++) {
            int top = rowTop(i);
            if (top + ROW_HEIGHT > listTop && top < listBottom)
                drawRow(i, top, listMouseX, listMouseY);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (visible.isEmpty())
            drawCenteredString(fontRendererObj, I18n.format(all.isEmpty() ? "cmdcam.gui.empty" : "cmdcam.gui.filtered_empty"), (listLeft + listRight) / 2, (listTop + listBottom) / 2 - 4, 0xA0A0A0);
        drawScrollbar();
        drawStatus();
        drawHeader(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawTooltip(listMouseX, listMouseY);
    }

    private void drawScrollbar() {
        int max = maxScroll();
        if (max <= 0)
            return;
        int view = listBottom - listTop;
        int size = Math.max(16, view * view / (view + max));
        int top = listTop + (view - size) * scroll / max;
        drawRect(listRight, listTop, listRight + SCROLLBAR, listBottom, 0xFF000000);
        drawRect(listRight, top, listRight + SCROLLBAR, top + size, 0xFF808080);
        drawRect(listRight, top, listRight + SCROLLBAR - 1, top + size - 1, 0xFFC0C0C0);
    }

    private void drawStatus() {
        String text;
        int color = 0xA0A0A0;
        String hint = hint();
        if (status != null && System.currentTimeMillis() < statusUntil) {
            text = status;
            color = statusError ? 0xFF5555 : 0x55FF55;
        } else if (hint != null) {
            text = hint;
            color = 0xFFAA00;
        } else if (ServerPaths.waiting())
            text = I18n.format("cmdcam.gui.waiting");
        else if (ServerPaths.noAnswer())
            text = I18n.format("cmdcam.gui.no_answer");
        else if (LocalPaths.unreadable() > 0) {
            text = I18n.format("cmdcam.gui.unreadable", LocalPaths.unreadable());
            color = 0xFFAA00;
        } else
            text = I18n.format("cmdcam.gui.count", visible.size(), all.size());
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(text, width - 2 * MARGIN), width / 2, height - 34, color);
    }

    private void drawRow(int index, int top, int mouseX, int mouseY) {
        PathEntry entry = visible.get(index);
        SavedPath path = entry.path;
        CamScene scene = path.scene;

        if (mouseX >= 0 && mouseY >= top && mouseY < top + ROW_HEIGHT)
            drawRect(listLeft, top, listRight, top + ROW_HEIGHT, 0x30FFFFFF);
        drawRect(listLeft, top + ROW_HEIGHT - 1, listRight, top + ROW_HEIGHT, 0x60FFFFFF);

        int previewX = listLeft + 2;
        int previewY = top + 3;
        PathPreview.draw(entry.stats, scene.interpolation, scene.points, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, overPreview(mouseX, mouseY, previewX, previewY));

        int textX = previewX + PREVIEW_SIZE + 4;
        int textWidth = listRight - textX - 2;

        String origin = I18n.format(entry.alsoOnServer ? "cmdcam.gui.origin.both" : entry.server ? "cmdcam.gui.origin.server" : "cmdcam.gui.origin.local");
        int originWidth = fontRendererObj.getStringWidth(origin);
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(path.name, textWidth - originWidth - 6), textX, top + 3, 0xFFFFFF);
        fontRendererObj.drawStringWithShadow(origin, listRight - originWidth - 3, top + 3, entry.server ? 0x55FFFF : entry.alsoOnServer ? 0xFFFF55 : 0x55FF55);

        String stats = I18n.format("cmdcam.gui.row.stats", points(entry.stats.points), I18n.format("cam.interpolation.short." + scene.interpolation.id), DurationParser.print(scene.duration), PathActions.format(
            entry.stats.length), PathActions.format(entry.stats.speed));
        drawLine(stats, textX, top + 13, textWidth, 0xD0D0D0);

        Vec3d start = scene.startPosition();
        String where = start == null ? I18n.format("cmdcam.gui.row.no_points") : I18n.format(scene.canMove() ? "cmdcam.gui.row.start" : "cmdcam.gui.row.offset", PathActions.format(start.x), PathActions.format(
            start.y), PathActions.format(start.z));
        drawLine(where + "  " + I18n.format("cmdcam.gui.row.by", creatorName(path.creator)), textX, top + 23, textWidth, 0xD0D0D0);

        drawLine(I18n.format("cmdcam.gui.row.times", time(path.savedAt, "cmdcam.gui.unknown"), time(path.lastLoaded, "cmdcam.gui.never")), textX, top + 33, textWidth, 0xA0A0A0);

        for (RowAction action : placedActions(index))
            action.drawButton(mc, mouseX, mouseY);
    }

    private void drawLine(String text, int x, int y, int maxWidth, int color) {
        String trimmed = fontRendererObj.trimStringToWidth(text, maxWidth);
        fontRendererObj.drawStringWithShadow(trimmed, x, y, color);
    }

    private static boolean overPreview(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + PREVIEW_SIZE && mouseY >= y && mouseY < y + PREVIEW_SIZE;
    }

    private String time(long millis, String fallback) {
        return millis > 0 ? dateFormat.format(new Date(millis)) : I18n.format(fallback);
    }

    private void drawTooltip(int mouseX, int mouseY) {
        if (mouseX < 0)
            return;
        int index = (mouseY - listTop + scroll) / ROW_HEIGHT;
        if (index < 0 || index >= visible.size())
            return;
        for (RowAction action : placedActions(index))
            if (action.contains(mouseX, mouseY)) {
                if (action.reason != null)
                    tooltip(Collections.singletonList(action.reason), mouseX, mouseY);
                return;
            }
        if (overPreview(mouseX, mouseY, listLeft + 2, rowTop(index) + 3))
            tooltip(details(visible.get(index)), mouseX, mouseY);
    }

    private void tooltip(List<String> lines, int mouseX, int mouseY) {
        func_146283_a(lines, mouseX, mouseY);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** All statistics, there is no room for them in a line of the list. */
    public List<String> details(PathEntry entry) {
        CamScene scene = entry.path.scene;
        List<String> lines = new ArrayList<>();
        lines.add(entry.path.name);
        lines.add(I18n.format("cmdcam.gui.details.points", entry.stats.points));
        lines.add(I18n.format("cmdcam.gui.details.interpolation", I18n.format("cam.interpolation." + scene.interpolation.id)));
        lines.add(I18n.format("cmdcam.gui.details.mode", I18n.format("cam.mode." + scene.mode)));
        lines.add(I18n.format("cmdcam.gui.details.duration", DurationParser.print(scene.duration)));
        lines.add(I18n.format("cmdcam.gui.details.loops", scene.loop < 0 ? I18n.format("cmdcam.gui.endless") : String.valueOf(scene.loop)));
        lines.add(I18n.format("cmdcam.gui.details.length", PathActions.format(entry.stats.length)));
        lines.add(I18n.format("cmdcam.gui.details.speed", PathActions.format(entry.stats.speed)));
        if (entry.stats.min != null) {
            Vec3d min = entry.stats.min;
            Vec3d max = entry.stats.max;
            lines.add(I18n.format("cmdcam.gui.details.size", PathActions.format(max.x - min.x), PathActions.format(max.y - min.y), PathActions.format(max.z - min.z)));
        }
        Vec3d start = scene.startPosition();
        if (start != null) {
            lines.add(I18n.format("cmdcam.gui.details.start", PathActions.format(start.x), PathActions.format(start.y), PathActions.format(start.z)));
            Vec3d end = new Vec3d(scene.points.get(scene.points.size() - 1));
            end.add(start);
            end.sub(scene.points.get(0));
            lines.add(I18n.format("cmdcam.gui.details.end", PathActions.format(end.x), PathActions.format(end.y), PathActions.format(end.z)));
        }
        if (scene.lookTarget != null)
            lines.add(I18n.format("cmdcam.gui.details.look", scene.lookTarget.print(mc.theWorld).getUnformattedText()));
        if (scene.posTarget != null)
            lines.add(I18n.format("cmdcam.gui.details.follow", scene.posTarget.print(mc.theWorld).getUnformattedText()));
        lines.add(I18n.format("cmdcam.gui.details.creator", creatorName(entry.path.creator)));
        lines.add(I18n.format("cmdcam.gui.details.saved", time(entry.path.savedAt, "cmdcam.gui.unknown")));
        lines.add(I18n.format("cmdcam.gui.details.loaded", time(entry.path.lastLoaded, "cmdcam.gui.never")));
        return lines;
    }

}
