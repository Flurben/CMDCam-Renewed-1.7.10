package team.creative.cmdcam.common.math.point;

import team.creative.cmdcam.common.math.vec.Vec3d;

/** What a path needs to know about the world around it. */
public interface PathContext {

    PathContext NONE = new PathContext() {
        @Override
        public Vec3d lookCenter() {
            return null;
        }
    };

    /**
     * @return the look target in the same space as the points, so relative to the follow target for a following scene. Null if there is no look target or it cannot be found.
     */
    Vec3d lookCenter();

}
