package team.creative.cmdcam.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.MouseHelper;

/**
 * Ignores mouse movement while the player is the camera. The old version moved the cursor back to the center and released and grabbed it again each frame to get rid of the movement,
 * which made the cursor of the operating system flicker on the screen. Now the cursor just stays grabbed.
 */
@SideOnly(Side.CLIENT)
public class CamMouseHelper extends MouseHelper {

    private final MouseHelper parent;

    public CamMouseHelper(MouseHelper parent) {
        this.parent = parent;
    }

    @Override
    public void grabMouseCursor() {
        parent.grabMouseCursor();
    }

    @Override
    public void ungrabMouseCursor() {
        parent.ungrabMouseCursor();
    }

    @Override
    public void mouseXYChange() {
        parent.mouseXYChange(); // Has to read the movement, otherwise it is applied all at once when the scene ends
        if (CMDCamClient.isPlayerCamera()) {
            deltaX = 0;
            deltaY = 0;
        } else {
            deltaX = parent.deltaX;
            deltaY = parent.deltaY;
        }
    }

}
