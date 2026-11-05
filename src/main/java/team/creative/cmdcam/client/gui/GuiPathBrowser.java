package team.creative.cmdcam.client.gui;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.I18n;
import team.creative.cmdcam.client.path.PathActions;
import team.creative.cmdcam.client.path.PathEntry;
import team.creative.cmdcam.client.path.ServerPaths;

/** /cam load: everything that can be done with saved paths. */
@SideOnly(Side.CLIENT)
public class GuiPathBrowser extends GuiPathList {

    public static final String LOAD = "load";
    public static final String ADVANCED = "advanced";
    public static final String DUPLICATE = "duplicate";
    public static final String RENAME = "rename";
    public static final String DELETE = "delete";

    @Override
    protected int headerBottom() {
        return 14;
    }

    @Override
    protected String title() {
        return I18n.format("cmdcam.gui.browser.title");
    }

    @Override
    protected void drawHeader(int mouseX, int mouseY) {}

    @Override
    protected List<RowAction> actions(final PathEntry entry) {
        boolean hasPoints = entry.stats.points > 0;
        String noPoints = hasPoints ? null : I18n.format("cmdcam.gui.reason.no_points");
        String cannotEdit = entry.server && !ServerPaths.canEdit() ? I18n.format("cmdcam.gui.reason.no_permission") : null;
        String cannotMove = noPoints != null ? noPoints : entry.path.scene.canMove() ? null : I18n.format("cmdcam.gui.reason.follows");

        List<RowAction> actions = new ArrayList<>();
        actions.add(new RowAction(LOAD, I18n.format("cmdcam.gui.action.load"), noPoints, new Runnable() {
            @Override
            public void run() {
                PathActions.load(entry, null);
                mc.displayGuiScreen(null);
            }
        }));
        actions.add(new RowAction(ADVANCED, I18n.format("cmdcam.gui.action.advanced"), cannotMove, new Runnable() {
            @Override
            public void run() {
                mc.displayGuiScreen(new GuiPathAdvancedLoad(GuiPathBrowser.this, entry));
            }
        }));
        actions.add(new RowAction(DUPLICATE, I18n.format("cmdcam.gui.action.duplicate"), null, new Runnable() {
            @Override
            public void run() {
                String copy = PathActions.duplicate(entry);
                if (copy != null) {
                    status(I18n.format("cmdcam.gui.duplicated", copy), false);
                    reload();
                } else
                    status(I18n.format("path.io_fail"), true);
            }
        }));
        actions.add(new RowAction(RENAME, I18n.format("cmdcam.gui.action.rename"), cannotEdit, new Runnable() {
            @Override
            public void run() {
                mc.displayGuiScreen(new GuiPathRename(GuiPathBrowser.this, entry));
            }
        }));
        actions.add(new RowAction(DELETE, I18n.format("cmdcam.gui.action.delete"), cannotEdit, new Runnable() {
            @Override
            public void run() {
                String detail = entry.server ? "cmdcam.gui.delete.server" : entry.alsoOnServer ? "cmdcam.gui.delete.both" : "cmdcam.gui.delete.local";
                confirm(I18n.format("cmdcam.gui.delete.question", entry.path.name), I18n.format(detail), new Runnable() {
                    @Override
                    public void run() {
                        if (!PathActions.delete(entry))
                            status(I18n.format("path.io_fail"), true);
                        else if (!entry.server)
                            status(I18n.format("scene.remove", entry.path.name), false);
                        reload();
                    }
                });
            }
        }));
        return actions;
    }

}
