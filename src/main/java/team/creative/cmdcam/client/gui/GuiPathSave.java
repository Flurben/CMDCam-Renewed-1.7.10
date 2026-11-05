package team.creative.cmdcam.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandException;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.client.path.ServerPaths;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.SavedPath;
import team.creative.cmdcam.common.scene.path.PathNames;

/** /cam save: a name and where the path begins, with the existing paths below to overwrite those of the same creator. */
@SideOnly(Side.CLIENT)
public class GuiPathSave extends GuiPathList {

    public static final String OVERWRITE = "overwrite";

    private static final int ID_SAVE = ID_FIRST_FREE;
    private static final int ID_POSITION = ID_FIRST_FREE + 1;
    private static final int LABEL_WIDTH = 32;
    private static final int RIGHT_BUTTON = 84;

    private final CamScene scene;
    private final LocationFields location;
    private GuiTextField nameField;
    private String typedName = "";
    private GuiButton saveButton;

    public GuiPathSave(CamScene scene) {
        this.scene = scene;
        this.location = scene.canMove() ? new LocationFields(scene.startPosition()) : null;
    }

    @Override
    protected int headerBottom() {
        return 58;
    }

    @Override
    protected String title() {
        return I18n.format("cmdcam.gui.save.title", GuiPathList.points(scene.points.size()), I18n.format("cam.interpolation.short." + scene.interpolation.id));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (nameField != null)
            typedName = nameField.getText();
        boolean first = nameField == null;
        int left = 8 + LABEL_WIDTH;
        int rightButtonX = width - 8 - RIGHT_BUTTON;
        int fieldsWidth = rightButtonX - left - 4;

        nameField = new GuiTextField(fontRendererObj, left, 16, fieldsWidth, 18);
        nameField.setMaxStringLength(PathNames.MAX_LENGTH);
        nameField.setText(typedName);
        nameField.setFocused(first || location == null);

        if (location != null)
            location.layout(fontRendererObj, left, 38, (fieldsWidth - 8) / 3, 4, 18);

        super.initGui();
        saveButton = new GuiButton(ID_SAVE, rightButtonX, 15, RIGHT_BUTTON, 20, "");
        buttonList.add(saveButton);
        GuiButton position = new GuiButton(ID_POSITION, rightButtonX, 37, RIGHT_BUTTON, 20, I18n.format("cmdcam.gui.my_position_short"));
        position.enabled = location != null;
        buttonList.add(position);
        updateSaveButton();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    public GuiTextField nameField() {
        return nameField;
    }

    public LocationFields location() {
        return location;
    }

    private PathEntry existing(boolean server) {
        String name = nameField.getText();
        for (PathEntry entry : allEntries())
            if (entry.server == server && (server ? entry.path.name.equals(name) : entry.path.name.equalsIgnoreCase(name)))
                return entry;
        return null;
    }

    /** @return why this cannot be saved, null if it can */
    private String problem() {
        String name = nameField.getText();
        if (name.isEmpty())
            return I18n.format("cmdcam.gui.save.need_name");
        if (!PathNames.isValid(name))
            return I18n.format("path.invalid_name", name);
        if (location != null && location.resolve() == null)
            return I18n.format("cmdcam.gui.invalid_location");
        PathEntry local = existing(false);
        if (local != null && !PathActions.isOwnPath(local.path))
            return I18n.format("path.exists_other", local.path.name, local.path.creator);
        return null;
    }

    /** Only the server's list decides for the server: without permission nothing happens to a path there. */
    private boolean overwrites() {
        return existing(false) != null || (existing(true) != null && ServerPaths.canEdit());
    }

    private void updateSaveButton() {
        if (saveButton == null || nameField == null)
            return;
        saveButton.enabled = problem() == null;
        saveButton.displayString = I18n.format(overwrites() ? "cmdcam.gui.action.overwrite" : "cmdcam.gui.action.save");
    }

    @Override
    protected String hint() {
        String name = nameField.getText();
        return name.isEmpty() ? null : problem();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        nameField.updateCursorCounter();
        if (location != null)
            location.update();
        updateSaveButton();
    }

    private void trySave() {
        if (problem() != null)
            return;
        if (!overwrites()) {
            save();
            return;
        }
        final String name = nameField.getText();
        PathEntry server = existing(true);
        String detail = server != null && ServerPaths.canEdit() && !PathActions.isOwnPath(server.path) ? I18n.format("cmdcam.gui.overwrite.other", server.path.creator) : I18n.format(
            "cmdcam.gui.overwrite.detail");
        confirm(I18n.format("cmdcam.gui.overwrite.question", name), detail, new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
    }

    private void save() {
        CamScene toSave = scene.copy();
        if (location != null) {
            Vec3d target = location.resolve();
            if (target == null)
                return;
            Vec3d start = scene.startPosition();
            toSave.translate(target.x - start.x, target.y - start.y, target.z - start.z);
        }
        try {
            PathActions.save(nameField.getText(), toSave);
            mc.displayGuiScreen(null);
        } catch (CommandException e) {
            status(I18n.format(e.getMessage(), e.getErrorOjbects()), true);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        super.actionPerformed(button);
        if (!button.enabled)
            return;
        if (button.id == ID_SAVE)
            trySave();
        else if (button.id == ID_POSITION && location != null)
            location.setRelativeToPlayer();
    }

    @Override
    protected List<RowAction> actions(final PathEntry entry) {
        SavedPath path = entry.path;
        String reason;
        if (entry.server) // Operators can overwrite all paths on the server
            reason = ServerPaths.canEdit() ? null : I18n.format("cmdcam.gui.reason.no_permission");
        else
            reason = PathActions.isOwnPath(path) ? null : I18n.format("cmdcam.gui.reason.other_creator", path.creator);
        return new ArrayList<>(Collections.singletonList(new RowAction(OVERWRITE, I18n.format("cmdcam.gui.action.overwrite"), reason, new Runnable() {
            @Override
            public void run() {
                nameField.setText(entry.path.name);
                trySave();
            }
        })));
    }

    @Override
    protected void drawHeader(int mouseX, int mouseY) {
        fontRendererObj.drawStringWithShadow(I18n.format("cmdcam.gui.name"), 8, 21, 0xE0E0E0);
        nameField.drawTextBox();
        fontRendererObj.drawStringWithShadow(I18n.format("cmdcam.gui.start"), 8, 43, 0xE0E0E0);
        if (location != null)
            location.draw();
        else
            fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(I18n.format("cmdcam.gui.reason.follows"), width - 2 * 8 - LABEL_WIDTH - RIGHT_BUTTON - 4), 8 + LABEL_WIDTH, 43, 0xA0A0A0);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (nameField.isFocused()) {
            if (key == Keyboard.KEY_TAB) {
                nameField.setFocused(false);
                if (location != null)
                    location.focusFirst();
                return;
            }
            if (nameField.textboxKeyTyped(character, key))
                return;
        } else if (location != null && location.keyTyped(character, key))
            return;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
            trySave();
        else
            super.keyTyped(character, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        nameField.mouseClicked(mouseX, mouseY, button);
        if (location != null)
            location.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

}
