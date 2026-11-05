package team.creative.cmdcam.client.gui;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;

/** Loads a path somewhere else: the start goes where the fields say and all other points keep their distance to it. */
@SideOnly(Side.CLIENT)
public class GuiPathAdvancedLoad extends GuiScreen {

    private static final int ID_LOAD = 0;
    private static final int ID_CANCEL = 1;
    private static final int ID_POSITION = 2;
    private static final int ID_RESET = 3;
    private static final int PREVIEW_SIZE = 88;
    private static final int CONTENT_WIDTH = 300;

    private final GuiPathList parent;
    private final PathEntry entry;
    private final Vec3d savedStart;
    private final LocationFields location;
    private GuiButton loadButton;
    private int left;
    private int top;

    public GuiPathAdvancedLoad(GuiPathList parent, PathEntry entry) {
        this.parent = parent;
        this.entry = entry;
        this.savedStart = entry.path.scene.startPosition();
        this.location = new LocationFields(savedStart);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - CONTENT_WIDTH) / 2;
        top = Math.max(16, (height - 176) / 2);
        location.layout(fontRendererObj, left, top + PREVIEW_SIZE + 16, (CONTENT_WIDTH - 8) / 3, 4, 18);
        buttonList.clear();
        int half = (CONTENT_WIDTH - 4) / 2;
        int row = top + PREVIEW_SIZE + 38;
        buttonList.add(new GuiButton(ID_POSITION, left, row, half, 20, I18n.format("cmdcam.gui.my_position")));
        buttonList.add(new GuiButton(ID_RESET, left + half + 4, row, half, 20, I18n.format("cmdcam.gui.saved_start")));
        loadButton = new GuiButton(ID_LOAD, left, row + 24, half, 20, I18n.format("cmdcam.gui.action.load"));
        buttonList.add(loadButton);
        buttonList.add(new GuiButton(ID_CANCEL, left + half + 4, row + 24, half, 20, I18n.format("gui.cancel")));
    }

    public LocationFields location() {
        return location;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        location.update();
        loadButton.enabled = location.resolve() != null;
    }

    /** @return what has to be added to all positions, null if the fields contain something unusable */
    public Vec3d movement() {
        Vec3d target = location.resolve();
        if (target == null)
            return null;
        target.sub(savedStart);
        return target;
    }

    public void load() {
        Vec3d movement = movement();
        if (movement == null)
            return;
        PathActions.load(entry, movement);
        mc.displayGuiScreen(null);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled)
            return;
        switch (button.id) {
            case ID_LOAD:
                load();
                break;
            case ID_CANCEL:
                mc.displayGuiScreen(parent);
                break;
            case ID_POSITION:
                location.setRelativeToPlayer();
                break;
            case ID_RESET:
                location.reset();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE)
            mc.displayGuiScreen(parent);
        else if (location.keyTyped(character, key))
            return;
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
            load();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        location.mouseClicked(mouseX, mouseY, button);
    }

    private static String triple(Vec3d vec) {
        return PathActions.format(vec.x) + " " + PathActions.format(vec.y) + " " + PathActions.format(vec.z);
    }

    private void line(String text, int x, int y, int color) {
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(text, left + CONTENT_WIDTH - x), x, y, color);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("cmdcam.gui.advanced.title", entry.path.name), width / 2, Math.max(4, top - 12), 0xFFFFFF);

        CamScene scene = entry.path.scene;
        boolean overPreview = mouseX >= left && mouseX < left + PREVIEW_SIZE && mouseY >= top && mouseY < top + PREVIEW_SIZE;
        PathPreview.draw(entry.stats, scene.interpolation, scene.points, left, top, PREVIEW_SIZE, PREVIEW_SIZE, overPreview);

        int x = left + PREVIEW_SIZE + 6;
        int y = top + 2;
        line(I18n.format("cmdcam.gui.advanced.saved", triple(savedStart)), x, y, 0xD0D0D0);
        Vec3d target = location.resolve();
        if (target == null)
            line(I18n.format("cmdcam.gui.invalid_location"), x, y + 12, 0xFF5555);
        else {
            line(I18n.format("cmdcam.gui.advanced.new", triple(target)), x, y + 12, 0x55FF55);
            target.sub(savedStart);
            line(I18n.format("cmdcam.gui.advanced.moved", triple(target)), x, y + 24, 0xD0D0D0);
        }
        line(I18n.format("cmdcam.gui.advanced.help1"), x, y + 40, 0xA0A0A0);
        line(I18n.format("cmdcam.gui.advanced.help2"), x, y + 50, 0xA0A0A0);
        line(I18n.format("cmdcam.gui.advanced.help3"), x, y + 60, 0xA0A0A0);

        int fieldWidth = (CONTENT_WIDTH - 8) / 3;
        String[] labels = { "X", "Y", "Z" };
        for (int i = 0; i < 3; i++)
            fontRendererObj.drawStringWithShadow(labels[i], left + i * (fieldWidth + 4), top + PREVIEW_SIZE + 6, 0xA0A0A0);
        location.draw();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

}
