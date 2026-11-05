package team.creative.cmdcam.client.gui;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.path.RelativeCoordinate;

/** Three text fields for where a path should begin, in the format of Minecraft's commands. ~ is the position of the player, the same as for /cam add. */
@SideOnly(Side.CLIENT)
public final class LocationFields {

    private static final int VALID = 0xE0E0E0;
    private static final int INVALID = 0xFF5555;
    /** Where the world ends. */
    private static final double LIMIT = 30000000;

    private final Vec3d initial;
    private final String[] initialText;
    private final String[] text;
    private final GuiTextField[] fields = new GuiTextField[3];

    public LocationFields(Vec3d initial) {
        this.initial = initial;
        this.initialText = new String[] { format(initial.x), format(initial.y), format(initial.z) };
        this.text = initialText.clone();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** Can be called again if the screen changes its size, what was typed stays. */
    public void layout(FontRenderer font, int x, int y, int fieldWidth, int gap, int height) {
        boolean[] focused = new boolean[3];
        for (int i = 0; i < 3; i++) {
            if (fields[i] != null) {
                text[i] = fields[i].getText();
                focused[i] = fields[i].isFocused();
            }
            fields[i] = new GuiTextField(font, x + i * (fieldWidth + gap), y, fieldWidth, height);
            fields[i].setMaxStringLength(32);
            fields[i].setText(text[i]);
            fields[i].setFocused(focused[i]);
            fields[i].setCursorPositionZero();
        }
    }

    public void setRelativeToPlayer() {
        for (GuiTextField field : fields)
            field.setText("~");
    }

    public void reset() {
        for (int i = 0; i < 3; i++)
            fields[i].setText(initialText[i]);
    }

    /** Only for what the screen shows or a test types in. */
    public void set(String x, String y, String z) {
        fields[0].setText(x);
        fields[1].setText(y);
        fields[2].setText(z);
    }

    /** @return where the path should begin, null if something typed cannot be used */
    public Vec3d resolve() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return null;
        Vec3d base = CMDCamClient.createLocalPoint();
        Vec3d result = new Vec3d();
        boolean valid = true;
        for (int i = 0; i < 3; i++) {
            String value = fields[i].getText();
            if (value.equals(initialText[i])) // The text is rounded, what was not changed must not move at all
                result.set(i, initial.get(i));
            else
                try {
                    double coordinate = RelativeCoordinate.parse(base.get(i), value);
                    if (Math.abs(coordinate) > LIMIT)
                        throw new NumberFormatException(value);
                    result.set(i, coordinate);
                } catch (NumberFormatException e) {
                    fields[i].setTextColor(INVALID);
                    valid = false;
                    continue;
                }
            fields[i].setTextColor(VALID);
        }
        return valid ? result : null;
    }

    public void draw() {
        for (GuiTextField field : fields)
            field.drawTextBox();
    }

    public void update() {
        for (GuiTextField field : fields)
            field.updateCursorCounter();
    }

    public void mouseClicked(int x, int y, int button) {
        for (GuiTextField field : fields)
            field.mouseClicked(x, y, button);
    }

    /** @return true if one of the fields took the key */
    public boolean keyTyped(char character, int key) {
        for (int i = 0; i < 3; i++)
            if (fields[i].isFocused()) {
                if (key == Keyboard.KEY_TAB) {
                    fields[i].setFocused(false);
                    if (i < 2)
                        fields[i + 1].setFocused(true);
                    return true;
                }
                return fields[i].textboxKeyTyped(character, key);
            }
        return false;
    }

    public void focusFirst() {
        fields[0].setFocused(true);
    }

}
