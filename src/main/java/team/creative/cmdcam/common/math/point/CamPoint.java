package team.creative.cmdcam.common.math.point;

import team.creative.cmdcam.common.math.vec.Vec3d;

/** A camera key frame. The position is the position of the eyes. */
public class CamPoint extends Vec3d {

    public static final double DEFAULT_FOV = 70;

    public double rotationYaw;
    public double rotationPitch;

    public double roll;
    public double zoom;

    public CamPoint() {}

    public CamPoint(double x, double y, double z, double rotationYaw, double rotationPitch, double roll, double zoom) {
        super(x, y, z);
        this.rotationYaw = rotationYaw;
        this.rotationPitch = rotationPitch;
        this.roll = roll;
        this.zoom = zoom;
    }

    public Vec3d calculateViewVector() {
        double pitch = Math.toRadians(rotationPitch);
        double yaw = Math.toRadians(-rotationYaw);
        return new Vec3d(Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    @Override
    public CamPoint copy() {
        return new CamPoint(x, y, z, rotationYaw, rotationPitch, roll, zoom);
    }

    @Override
    public String toString() {
        return "x:" + x + ",y:" + y + ",z:" + z + ",yaw:" + rotationYaw + ",pitch:" + rotationPitch + ",roll:" + roll + ",zoom:" + zoom;
    }

}
