package team.creative.cmdcam.client.gui;

import java.util.List;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.path.PathStats;

/** Draws a path into a small box: the curve in the color /cam show uses and its points, seen from above at an angle. It turns while the mouse is on it, so its shape can be told. */
@SideOnly(Side.CLIENT)
final class PathPreview {

    private static final int BORDER = 0xFF000000;
    private static final int BACKGROUND = 0xFF1C1C1C;
    private static final int POINT = 0xFFFFFFFF;
    private static final int START = 0xFF55FF55;
    private static final int MAX_MARKERS = 64;

    private static final double PITCH = Math.toRadians(35);
    private static final double SIN_PITCH = Math.sin(PITCH);
    private static final double COS_PITCH = Math.cos(PITCH);
    private static final double RESTING_YAW = Math.toRadians(45);

    private PathPreview() {}

    static void draw(PathStats stats, CamInterpolation interpolation, List<CamPoint> points, int x, int y, int width, int height, boolean turning) {
        Gui.drawRect(x, y, x + width, y + height, BORDER);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, BACKGROUND);
        if (stats.samples.isEmpty())
            return;

        // The size of the bounding sphere keeps the scale the same while turning
        double centerX = (stats.min.x + stats.max.x) / 2;
        double centerY = (stats.min.y + stats.max.y) / 2;
        double centerZ = (stats.min.z + stats.max.z) / 2;
        double radius = Math.max(0.001, stats.min.distance(stats.max) / 2);
        double scale = (Math.min(width, height) - 8) / (2 * radius);

        double yaw = turning ? RESTING_YAW + Minecraft.getSystemTime() / 1500D : RESTING_YAW;
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double screenX = x + width / 2D;
        double screenY = y + height / 2D;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glLineWidth(1.5F);
        // Pure blue, the color of linear paths, is hard to see on a dark background. Some white keeps the color recognizable
        GL11.glColor4f(lighten(interpolation.red), lighten(interpolation.green), lighten(interpolation.blue), 1);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3d sample : stats.samples) {
            double relativeX = sample.x - centerX;
            double relativeY = sample.y - centerY;
            double relativeZ = sample.z - centerZ;
            double turnedX = relativeX * cos - relativeZ * sin;
            double turnedZ = relativeX * sin + relativeZ * cos;
            GL11.glVertex2d(screenX + turnedX * scale, screenY + (turnedZ * SIN_PITCH - relativeY * COS_PITCH) * scale);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);

        int step = Math.max(1, points.size() / MAX_MARKERS);
        for (int i = points.size() - 1; i >= 0; i -= step == 1 || i == 0 ? 1 : Math.min(step, i)) { // The first point always gets its marker
            CamPoint point = points.get(i);
            double relativeX = point.x - centerX;
            double relativeY = point.y - centerY;
            double relativeZ = point.z - centerZ;
            double turnedX = relativeX * cos - relativeZ * sin;
            double turnedZ = relativeX * sin + relativeZ * cos;
            int markerX = (int) Math.round(screenX + turnedX * scale);
            int markerY = (int) Math.round(screenY + (turnedZ * SIN_PITCH - relativeY * COS_PITCH) * scale);
            Gui.drawRect(markerX - 1, markerY - 1, markerX + 1, markerY + 1, i == 0 ? START : POINT);
        }
    }

    private static float lighten(float channel) {
        return channel * 0.75F + 0.25F;
    }

}
