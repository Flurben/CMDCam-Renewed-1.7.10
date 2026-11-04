package team.creative.cmdcam.client;

import java.util.Set;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.CMDCamConfig;

/** The config button in the mod list. */
@SideOnly(Side.CLIENT)
public class CMDCamGuiFactory implements IModGuiFactory {

    public static class ConfigGui extends GuiConfig {

        public ConfigGui(GuiScreen parent) {
            super(parent, new ConfigElement<Object>(CMDCamConfig.config.getCategory(CMDCamConfig.CATEGORY)).getChildElements(), CMDCam.MODID, false, false, "CMDCam Renewed");
        }
    }

    @Override
    public void initialize(Minecraft minecraft) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

}
