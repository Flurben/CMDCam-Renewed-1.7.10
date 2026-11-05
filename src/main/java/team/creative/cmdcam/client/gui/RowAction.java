package team.creative.cmdcam.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;

/** A small button in a line of the list. These move with the list and are lower than the texture of a button, so they are drawn by the list and not by the screen. */
@SideOnly(Side.CLIENT)
public final class RowAction extends GuiButton {

    public static final int HEIGHT = 13;

    public final String key;
    /** Why it cannot be used, or null. */
    public final String reason;
    final Runnable action;

    RowAction(String key, String label, String reason, Runnable action) {
        super(0, 0, 0, 0, HEIGHT, label);
        this.key = key;
        this.reason = reason;
        this.action = action;
        this.enabled = reason == null;
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(buttonTextures);
        GL11.glColor4f(1, 1, 1, 1);
        field_146123_n = contains(mouseX, mouseY);
        int v = 46 + getHoverState(field_146123_n) * 20;
        int top = height / 2;
        int bottom = height - top;
        int left = width / 2;
        int right = width - left;
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        drawTexturedModalRect(xPosition, yPosition, 0, v, left, top);
        drawTexturedModalRect(xPosition + left, yPosition, 200 - right, v, right, top);
        drawTexturedModalRect(xPosition, yPosition + top, 0, v + 20 - bottom, left, bottom);
        drawTexturedModalRect(xPosition + left, yPosition + top, 200 - right, v + 20 - bottom, right, bottom);
        int color = !enabled ? 0xA0A0A0 : field_146123_n ? 0xFFFFA0 : 0xE0E0E0;
        drawCenteredString(mc.fontRenderer, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }

}
