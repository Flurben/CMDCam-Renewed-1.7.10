package team.creative.cmdcam.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The message is a translation key. */
@SideOnly(Side.CLIENT)
public class SceneException extends Exception {

    private static final long serialVersionUID = 1L;

    public SceneException(String key) {
        super(key);
    }

}
