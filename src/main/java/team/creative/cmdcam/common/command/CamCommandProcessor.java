package team.creative.cmdcam.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.target.CamTarget;

/** The difference between /cam, which edits the client's scene, and /cam-server, which edits a saved one. */
public interface CamCommandProcessor {

    /** @param name the scene name, null for /cam */
    CamScene getScene(ICommandSender sender, String name) throws CommandException;

    boolean canSelectTarget();

    void selectTarget(ICommandSender sender, String name, boolean look) throws CommandException;

    boolean canCreatePoint(ICommandSender sender);

    /** The point of view of the sender. Must only be called if {@link #canCreatePoint(ICommandSender)} is true. */
    CamPoint createPoint(ICommandSender sender);

    /** @param player a player selector, only used by /cam-server */
    void teleport(ICommandSender sender, CamScene scene, int index, String player) throws CommandException;

    void markDirty(ICommandSender sender);

    /** @return the frame's partial ticks, 1 on the server */
    float partialTicks();

    /** Whether goto needs a player argument. */
    boolean requiresPlayer();

    default void setTarget(ICommandSender sender, String name, CamTarget target, boolean look) throws CommandException {
        CamScene scene = getScene(sender, name);
        if (look)
            scene.lookTarget = target;
        else {
            checkFollowTarget(scene, target != null);
            scene.posTarget = target;
        }
    }

    /** Absolute and relative points cannot be mixed. */
    default void checkFollowTarget(CamScene scene, boolean shouldFollow) throws CommandException {
        if (scene.points.isEmpty())
            return;
        if (shouldFollow && scene.posTarget == null)
            throw new CommandException("scene.follow.absolute_fail");
        if (!shouldFollow && scene.posTarget != null)
            throw new CommandException("scene.follow.relative_fail");
    }

    default void makeRelative(ICommandSender sender, CamScene scene, CamPoint point) throws CommandException {
        if (scene.posTarget != null) {
            Vec3d vec = scene.posTarget.position(sender.getEntityWorld(), partialTicks());
            if (vec == null)
                throw new CommandException("scene.follow.not_found");
            point.sub(vec);
        }
    }

}
