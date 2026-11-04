package team.creative.cmdcam.client;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;

@SideOnly(Side.CLIENT)
public class KeyHandler {

    public static final String CATEGORY = "key.categories.cmdcam";

    public static final KeyBinding zoomIn = new KeyBinding("key.zoomin", Keyboard.KEY_V, CATEGORY);
    public static final KeyBinding zoomCenter = new KeyBinding("key.centerzoom", Keyboard.KEY_B, CATEGORY);
    public static final KeyBinding zoomOut = new KeyBinding("key.zoomout", Keyboard.KEY_N, CATEGORY);

    public static final KeyBinding rollLeft = new KeyBinding("key.rollleft", Keyboard.KEY_G, CATEGORY);
    public static final KeyBinding rollCenter = new KeyBinding("key.rollcenter", Keyboard.KEY_H, CATEGORY);
    public static final KeyBinding rollRight = new KeyBinding("key.rollright", Keyboard.KEY_J, CATEGORY);

    public static final KeyBinding pointKey = new KeyBinding("key.point", Keyboard.KEY_P, CATEGORY);
    public static final KeyBinding startStop = new KeyBinding("key.startStop", Keyboard.KEY_U, CATEGORY);
    public static final KeyBinding clearPoint = new KeyBinding("key.clearPoint", Keyboard.KEY_DELETE, CATEGORY);

    public static void registerKeys() {
        for (KeyBinding key : new KeyBinding[] { zoomIn, zoomCenter, zoomOut, rollLeft, rollCenter, rollRight, pointKey, startStop, clearPoint })
            ClientRegistry.registerKeyBinding(key);
    }

}
