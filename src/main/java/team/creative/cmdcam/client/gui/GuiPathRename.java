package team.creative.cmdcam.client.gui;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.common.scene.path.PathNames;

/** Asks for a new name and goes back to the list. */
@SideOnly(Side.CLIENT)
public class GuiPathRename extends GuiScreen {

    private static final int ID_DONE = 0;
    private static final int ID_CANCEL = 1;

    private final GuiPathList parent;
    private final PathEntry entry;
    private GuiTextField nameField;
    private GuiButton doneButton;
    private String error;

    public GuiPathRename(GuiPathList parent, PathEntry entry) {
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String text = nameField != null ? nameField.getText() : entry.path.name;
        nameField = new GuiTextField(fontRendererObj, width / 2 - 100, height / 2 - 20, 200, 20);
        nameField.setMaxStringLength(PathNames.MAX_LENGTH);
        nameField.setText(text);
        nameField.setFocused(true);
        nameField.setCanLoseFocus(false); // There is nothing else to type into, after a refused name it should simply go on
        buttonList.clear();
        doneButton = new GuiButton(ID_DONE, width / 2 - 100, height / 2 + 16, 98, 20, I18n.format("cmdcam.gui.action.rename"));
        buttonList.add(doneButton);
        buttonList.add(new GuiButton(ID_CANCEL, width / 2 + 2, height / 2 + 16, 98, 20, I18n.format("gui.cancel")));
        doneButton.enabled = PathNames.isValid(text);
    }

    public GuiTextField nameField() {
        return nameField;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        doneButton.enabled = PathNames.isValid(nameField.getText());
    }

    public void rename() {
        String name = nameField.getText();
        String problem = PathActions.rename(entry, name);
        if (problem != null) {
            error = I18n.format(problem, name);
            return;
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled)
            return;
        if (button.id == ID_DONE)
            rename();
        else if (button.id == ID_CANCEL)
            mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE)
            mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (doneButton.enabled)
                rename();
        } else {
            nameField.textboxKeyTyped(character, key);
            error = null;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        nameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("cmdcam.gui.rename.title", entry.path.name), width / 2, height / 2 - 40, 0xFFFFFF);
        nameField.drawTextBox();
        if (error != null)
            drawCenteredString(fontRendererObj, error, width / 2, height / 2 + 4, 0xFF5555);
        else if (!PathNames.isValid(nameField.getText()))
            drawCenteredString(fontRendererObj, I18n.format("cmdcam.gui.rename.invalid"), width / 2, height / 2 + 4, 0xFFAA00);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

}
