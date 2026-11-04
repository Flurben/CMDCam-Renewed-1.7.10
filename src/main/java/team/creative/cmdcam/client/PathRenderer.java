package team.creative.cmdcam.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.Interpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;

/** Previews the points and paths in the world. */
@SideOnly(Side.CLIENT)
public final class PathRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int STEPS_PER_POINT = 20;

    private PathRenderer() {}

    private static boolean shows(CamInterpolation interpolation, CamScene scene) {
        return interpolation.isRenderingEnabled || (CamEventHandlerClient.SHOW_ACTIVE_INTERPOLATION && interpolation == scene.interpolation);
    }

    public static void render() {
        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        CamScene scene = CMDCamClient.getScene();
        CamPoint marker = CMDCamClient.getTargetMarker();

        List<CamInterpolation> visible = new ArrayList<>();
        for (CamInterpolation interpolation : CamInterpolation.all())
            if (shows(interpolation, scene))
                visible.add(interpolation);

        if (marker == null && (visible.isEmpty() || scene.points.isEmpty()))
            return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1);

            if (marker != null)
                renderMarker(marker);

            if (!visible.isEmpty() && !scene.points.isEmpty()) {
                Vec3d origin = CMDCamClient.getFollowOrigin();
                if (scene.posTarget == null || origin != null) {
                    double offsetX = origin != null ? origin.x : 0;
                    double offsetY = origin != null ? origin.y : 0;
                    double offsetZ = origin != null ? origin.z : 0;

                    for (int i = 0; i < scene.points.size(); i++) {
                        CamPoint point = scene.points.get(i);
                        renderPoint(i + 1, point.x + offsetX, point.y + offsetY, point.z + offsetZ);
                    }

                    scene.bind(mc.theWorld, CMDCamClient.partialTicks());
                    for (CamInterpolation interpolation : visible)
                        renderPath(interpolation, scene, offsetX, offsetY, offsetZ);
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderPath(CamInterpolation interpolation, CamScene scene, double offsetX, double offsetY, double offsetZ) {
        CamPoints points = new CamPoints(scene.points);
        if (points.size() < 2)
            return;

        double[] times = points.createTimes(scene.distanceBasedTiming, scene, interpolation);
        Interpolation<Vec3d> path = points.interpolate(times, scene, interpolation, CamAttribute.POSITION);

        int steps = STEPS_PER_POINT * (points.size() - 1);
        GL11.glColor4f(interpolation.red, interpolation.green, interpolation.blue, 1);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= steps; i++) {
            Vec3d pos = path.valueAt(i / (double) steps);
            GL11.glVertex3d(pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ);
        }
        GL11.glEnd();
    }

    private static void renderPoint(int number, double x, double y, double z) {
        GL11.glColor4f(1, 1, 1, 1);
        renderBox(x - 0.05, y - 0.05, z - 0.05, x + 0.05, y + 0.05, z + 0.05, GL11.GL_QUADS);
        renderLabel(String.valueOf(number), x, y + 0.2, z);
    }

    /** Shows the player size, eye height and view direction around the marker. */
    private static void renderMarker(CamPoint marker) {
        GL11.glColor4f(1, 1, 1, 1);
        renderBox(marker.x - 0.3, marker.y - 1.62, marker.z - 0.3, marker.x + 0.3, marker.y + 0.18, marker.z + 0.3, GL11.GL_LINE_LOOP);
        GL11.glColor4f(1, 0, 0, 1);
        renderBox(marker.x - 0.3, marker.y - 0.01, marker.z - 0.3, marker.x + 0.3, marker.y + 0.01, marker.z + 0.3, GL11.GL_LINE_LOOP);

        Vec3d view = marker.calculateViewVector();
        GL11.glColor4f(0, 0, 1, 1);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(marker.x, marker.y, marker.z);
        GL11.glVertex3d(marker.x + view.x * 2, marker.y + view.y * 2, marker.z + view.z * 2);
        GL11.glEnd();
    }

    private static void renderBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int mode) {
        double[][] faces = { { minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ }, { minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ }, {
                minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ }, { minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ }, { minX, minY, minZ,
                        minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ }, { maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ } };
        for (double[] face : faces) {
            GL11.glBegin(mode);
            for (int i = 0; i < 12; i += 3)
                GL11.glVertex3d(face[i], face[i + 1], face[i + 2]);
            GL11.glEnd();
        }
    }

    private static void renderLabel(String text, double x, double y, double z) {
        FontRenderer font = mc.fontRenderer;
        float scale = 0.016666668F * 1.6F;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0, 1, 0);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
        GL11.glScalef(-scale, -scale, scale);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -font.getStringWidth(text) / 2, 0, -1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

}
