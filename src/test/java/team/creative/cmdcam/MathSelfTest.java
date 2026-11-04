package team.creative.cmdcam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import team.creative.cmdcam.common.math.follow.CamFollow;
import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.interpolation.Interpolation;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;
import team.creative.cmdcam.common.math.point.PathContext;
import team.creative.cmdcam.common.math.vec.Vec1d;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.scene.attribute.CamAttribute;
import team.creative.cmdcam.common.scene.run.RunStage;
import team.creative.cmdcam.common.scene.run.StagePlan;
import team.creative.cmdcam.common.scene.timer.RealTimeTimer;
import team.creative.cmdcam.common.scene.timer.TickerTimer;
import team.creative.cmdcam.common.util.DurationParser;

/** Tests everything which works without Minecraft. Runs with a plain JDK, see README. */
public class MathSelfTest {

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("FAILED: " + name);
        }
    }

    private static void near(String name, double expected, double actual) {
        check(name + " expected " + expected + " got " + actual, Math.abs(expected - actual) < 1E-6);
    }

    private static CamPoint p(double x, double y, double z, double yaw) {
        return new CamPoint(x, y, z, yaw, 0, 0, 70);
    }

    private static List<CamPoint> line() {
        return Arrays.asList(p(0, 0, 0, 0), p(10, 0, 0, 0), p(20, 0, 0, 0), p(100, 0, 0, 0));
    }

    static class ManualClock implements RealTimeTimer.Clock {

        long now = 1000;

        @Override
        public long millis() {
            return now;
        }
    }

    public static void main(String[] args) {
        interpolationsHitTheirPoints();
        distanceTiming();
        circular();
        spinning();
        stageCounts();
        timers();
        follow();
        durations();

        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0)
            System.exit(1);
    }

    private static void interpolationsHitTheirPoints() {
        for (CamInterpolation interpolation : Arrays.asList(CamInterpolation.LINEAR, CamInterpolation.CUBIC, CamInterpolation.HERMITE, CamInterpolation.COSINE)) {
            CamPoints points = new CamPoints(line());
            double[] times = points.createTimes(false, PathContext.NONE, interpolation);
            Interpolation<Vec3d> path = points.interpolate(times, PathContext.NONE, interpolation, CamAttribute.POSITION);
            for (int i = 0; i < line().size(); i++)
                near(interpolation.id + " hits point " + i, line().get(i).x, path.valueAt(times[i]).x);
            near(interpolation.id + " stays at the end after 1", 100, path.valueAt(1.5).x);
            near(interpolation.id + " stays at the start before 0", 0, path.valueAt(-1).x);
        }

        // Reading a value must not change the scene's points
        List<CamPoint> scene = line();
        CamPoints copy = new CamPoints(scene);
        copy.fixSpinning();
        copy.get(0).x = 55;
        near("points are copied", 0, scene.get(0).x);
    }

    private static void distanceTiming() {
        for (CamInterpolation interpolation : Arrays.asList(CamInterpolation.LINEAR, CamInterpolation.HERMITE, CamInterpolation.CUBIC)) {
            CamPoints points = new CamPoints(line());
            double[] times = points.createTimes(true, PathContext.NONE, interpolation);
            near(interpolation.id + " timing starts at 0", 0, times[0]);
            near(interpolation.id + " timing ends at 1", 1, times[3]);
            check(interpolation.id + " timing is increasing " + Arrays.toString(times), times[0] < times[1] && times[1] < times[2] && times[2] < times[3]);
            // The last segment is 8 times as long, it has to take most of the time. Failed when the length estimate sampled one spot only
            check(interpolation.id + " long segment takes longer " + Arrays.toString(times), times[3] - times[2] > 4 * (times[2] - times[1]));
        }
        near("linear distance timing is exact", 0.1, new CamPoints(line()).createTimes(true, PathContext.NONE, CamInterpolation.LINEAR)[1]);

        double[] same = new CamPoints(Arrays.asList(p(1, 1, 1, 0), p(1, 1, 1, 0), p(1, 1, 1, 0))).createTimes(true, PathContext.NONE, CamInterpolation.HERMITE);
        near("points at the same place fall back to even timing", 0.5, same[1]);
    }

    private static void circular() {
        final Vec3d center = new Vec3d(0, 0, 0);
        PathContext context = new PathContext() {
            @Override
            public Vec3d lookCenter() {
                return center.copy();
            }
        };

        for (CamInterpolation interpolation : Arrays.asList(CamInterpolation.CIRCULAR, CamInterpolation.INV_CIRCULAR)) {
            // Start is above the plane of the center, which used to skew all angles
            List<CamPoint> pts = Arrays.asList(p(10, 5, 0, 0), p(0, 5, 10, 0), p(-10, 5, 0, 0));
            CamPoints points = new CamPoints(pts);
            Interpolation<Vec3d> path = points.interpolate(points.createTimes(false, context, interpolation), context, interpolation, CamAttribute.POSITION);
            Vec3d start = path.valueAt(0);
            Vec3d end = path.valueAt(1);
            near(interpolation.id + " starts at first point x", 10, start.x);
            near(interpolation.id + " starts at first point y", 5, start.y);
            near(interpolation.id + " closes the circle x", 10, end.x);
            double radius = Math.sqrt(125);
            for (double t = 0; t <= 1; t += 0.05) {
                Vec3d point = path.valueAt(t);
                near(interpolation.id + " keeps the radius", radius, point.length());
            }
            // A quarter of the way it has to be at +z or -z depending on the direction, never back at x
            Vec3d quarter = path.valueAt(0.25);
            near(interpolation.id + " quarter x", 0, quarter.x);
            check(interpolation.id + " quarter z " + quarter.z, Math.abs(quarter.z) > 5);
        }

        Vec3d clockwiseQuarter = new CamPoints(line()).interpolate(Interpolation.uniformTimes(4), context, CamInterpolation.CIRCULAR, CamAttribute.POSITION).valueAt(0.25);
        Vec3d otherQuarter = new CamPoints(line()).interpolate(Interpolation.uniformTimes(4), context, CamInterpolation.INV_CIRCULAR, CamAttribute.POSITION).valueAt(0.25);
        check("directions are opposite", Math.signum(clockwiseQuarter.z) == -Math.signum(otherQuarter.z));

        // Heights are sorted by angle, the second point lies behind the third one here
        List<CamPoint> unsorted = Arrays.asList(p(10, 0, 0, 0), p(-10, 8, 0, 0), p(0, 2, 10, 0), p(0, 4, -10, 0));
        for (CamInterpolation interpolation : Arrays.asList(CamInterpolation.CIRCULAR, CamInterpolation.INV_CIRCULAR)) {
            CamPoints points = new CamPoints(unsorted);
            Interpolation<Vec3d> path = points.interpolate(Interpolation.uniformTimes(4), context, interpolation, CamAttribute.POSITION);
            for (double t = 0; t <= 1; t += 0.01)
                check(interpolation.id + " no invalid value", !Double.isNaN(path.valueAt(t).y));
        }

        // Without a target there is nothing to circle
        CamPoints plain = new CamPoints(line());
        near("circular without target falls back", 100, plain.interpolate(Interpolation.uniformTimes(4), PathContext.NONE, CamInterpolation.CIRCULAR, CamAttribute.POSITION).valueAt(1).x);

        // Rotation etc. never circle, even if the target exists
        CamPoints yaw = new CamPoints(Arrays.asList(p(1, 0, 0, 10), p(2, 0, 0, 20), p(3, 0, 0, 30)));
        near("other attributes are interpolated", 20, yaw.interpolate(Interpolation.uniformTimes(3), context, CamInterpolation.CIRCULAR, CamAttribute.YAW).valueAt(0.5).x);
    }

    private static void spinning() {
        CamPoints points = new CamPoints(Arrays.asList(p(0, 0, 0, 170), p(0, 0, 0, -170)));
        points.fixSpinning(CamPitchMode.FIX);
        near("fix takes the short way", 20, Math.abs(points.get(1).rotationYaw - points.get(0).rotationYaw));

        points = new CamPoints(Arrays.asList(p(0, 0, 0, 0), p(0, 0, 0, 720 + 30)));
        points.fixSpinning(CamPitchMode.FIX_KEEP_DIRECTION);
        near("keep direction removes full turns", 30, points.get(1).rotationYaw);

        points = new CamPoints(Arrays.asList(p(0, 0, 0, 0), p(0, 0, 0, 720)));
        points.fixSpinning(CamPitchMode.NO_FIX);
        near("no fix keeps it", 720, points.get(1).rotationYaw);
    }

    /** @return how often the path plays */
    private static int plays(int loop, boolean smooth) {
        CamPoint from = smooth ? p(5, 5, 5, 0) : null;
        List<StagePlan.Entry> entries = StagePlan.create(line(), CamInterpolation.HERMITE, CamPitchMode.FIX_KEEP_DIRECTION, 1000, loop, from);
        int plays = 0;
        for (StagePlan.Entry entry : entries) {
            if (smooth && entry == entries.get(0))
                continue; // The smooth start is not a run of the path
            RunStage stage = new RunStage(entry.duration, entry.loops);
            do {
                plays++;
                if (plays > 1000)
                    return plays;
            } while (stage.completeIteration());
        }
        return plays;
    }

    private static void stageCounts() {
        check("no loop plays once", plays(0, false) == 1);
        check("1 loop plays twice", plays(1, false) == 2);
        check("2 loops play three times", plays(2, false) == 3);
        check("5 loops play six times", plays(5, false) == 6);
        check("endless loops never end", plays(-1, false) > 1000);
        check("smooth start with loops does not change the count", plays(2, true) == 3);

        List<StagePlan.Entry> smooth = StagePlan.create(line(), CamInterpolation.LINEAR, CamPitchMode.FIX, 1000, 0, p(5, 5, 5, 0));
        check("smooth start adds a stage", smooth.size() == 2 && !smooth.get(0).mainStart && smooth.get(1).mainStart);
        near("smooth start begins at the camera", 5, smooth.get(0).points.get(0).x);
        near("smooth start ends at the first point", 0, smooth.get(0).points.get(1).x);
        check("smooth start is not too short", smooth.get(0).duration >= 1000);

        // Regression of upstream #74: With a smooth start the loop must still go through the whole path
        List<StagePlan.Entry> loop = StagePlan.create(line(), CamInterpolation.LINEAR, CamPitchMode.FIX, 1000, 2, p(5, 5, 5, 0));
        for (int i = 1; i < loop.size(); i++)
            check("loop stage contains all points", loop.get(i).points.size() >= 4);

        List<StagePlan.Entry> single = StagePlan.create(Arrays.asList(p(1, 2, 3, 0)), CamInterpolation.HERMITE, CamPitchMode.FIX, 1000, 0, null);
        check("a single point plays without crashing", single.size() == 1 && single.get(0).points.size() == 2);
        single.get(0).points.interpolate(Interpolation.uniformTimes(2), PathContext.NONE, CamInterpolation.HERMITE, CamAttribute.POSITION).valueAt(0.5);
        StagePlan.create(Arrays.asList(p(1, 2, 3, 0)), CamInterpolation.HERMITE, CamPitchMode.FIX, 1000, -1, p(0, 0, 0, 0));
        check("no points means no stages", StagePlan.create(new ArrayList<CamPoint>(), CamInterpolation.HERMITE, CamPitchMode.FIX, 1000, 0, null).isEmpty());
    }

    private static void timers() {
        ManualClock clock = new ManualClock();
        RealTimeTimer timer = new RealTimeTimer(clock);
        clock.now += 300;
        near("timer runs", 300, timer.position(true, 0));
        timer.pause();
        clock.now += 5000;
        near("timer holds while paused", 300, timer.position(false, 0));
        timer.pause();
        near("pausing twice changes nothing", 300, timer.position(false, 0));
        timer.resume();
        timer.resume();
        clock.now += 200;
        near("timer goes on after resume", 500, timer.position(true, 0));

        // Used to keep the time from before a pause in the next stage, cutting it short
        clock.now += 750;
        timer.stageCompleted(1000);
        near("next stage carries over the rest", 250, timer.position(true, 0));
        clock.now += 100;
        near("next stage runs", 350, timer.position(true, 0));

        TickerTimer ticks = new TickerTimer();
        check("tick timer never is negative", ticks.position(true, 0) == 0);
        for (int i = 0; i < 21; i++)
            ticks.tick(true);
        near("tick timer", 1000, ticks.position(true, 0));
        ticks.tick(false);
        near("tick timer holds when not running", 1000, ticks.position(false, 0));
        near("tick timer partial", 1025, ticks.position(true, 0.5F));
        ticks.stageCompleted(1000);
        near("tick timer stage", 0, ticks.position(true, 0));
    }

    private static void follow() {
        CamFollow<Vec1d> instant = new CamFollow<>(CamAttribute.PITCH, 1, new Vec1d(0));
        near("step 1 is instant", 50, instant.follow(new Vec1d(50), 16).x);

        // Reaching the same place after the same time, independent of the frame rate
        CamFollow<Vec1d> slow = new CamFollow<>(CamAttribute.PITCH, 10, new Vec1d(0));
        CamFollow<Vec1d> fast = new CamFollow<>(CamAttribute.PITCH, 10, new Vec1d(0));
        double slowValue = 0;
        double fastValue = 0;
        for (int i = 0; i < 30; i++)
            slowValue = slow.follow(new Vec1d(100), 1000D / 30).x;
        for (int i = 0; i < 240; i++)
            fastValue = fast.follow(new Vec1d(100), 1000D / 240).x;
        near("independent of frame rate", slowValue, fastValue);
        check("moved closer", slowValue > 50 && slowValue < 100);

        CamFollow<Vec1d> sixty = new CamFollow<>(CamAttribute.PITCH, 10, new Vec1d(0));
        near("one frame at 60 fps moves a tenth of the way", 10, sixty.follow(new Vec1d(100), 1000D / 60).x);

        CamFollow<Vec1d> paused = new CamFollow<>(CamAttribute.PITCH, 10, new Vec1d(5));
        near("no time, no movement", 5, paused.follow(new Vec1d(100), 0).x);

        // 170 to -170 degree is a turn of 20 degree, not 340
        CamFollow<Vec1d> yaw = new CamFollow<>(CamAttribute.YAW, 1, new Vec1d(80));
        double wrapped = yaw.follow(new Vec1d(-260), 16).x;
        check("yaw takes the short way " + wrapped, Math.abs(CamPoints.wrapDegrees(wrapped - 100)) < 1E-6);
        CamFollow<Vec1d> yawSteps = new CamFollow<>(CamAttribute.YAW, 2, new Vec1d(85));
        double step = yawSteps.follow(new Vec1d(-265), 1000D / 60).x;
        check("yaw step does not go the long way " + step, Math.abs(CamPoints.wrapDegrees(step - 90)) < 1E-6);

        CamFollow<Vec3d> pos = new CamFollow<>(CamAttribute.POSITION, 2, new Vec3d(0, 0, 0));
        Vec3d aim = new Vec3d(10, 20, 30);
        Vec3d first = pos.follow(aim, 1000D / 60);
        near("position follows", 5, first.x);
        first.x = 1000;
        near("result can be changed by the caller", 7.5, pos.follow(aim, 1000D / 60).x);
        near("target is not changed", 10, aim.x);
    }

    private static void durations() {
        check("seconds", DurationParser.parse("10s") == 10000);
        check("millis", DurationParser.parse("500ms") == 500);
        check("minutes", DurationParser.parse("2m") == 120000);
        check("hours", DurationParser.parse("1h") == 3600000);
        check("days", DurationParser.parse("1d") == 86400000);
        check("old number without unit is seconds", DurationParser.parse("5") == 5000);
        check("combined", DurationParser.parse("1m30s") == 90000);
        check("upper case", DurationParser.parse("10S") == 10000);
        check("invalid", DurationParser.parse("abc") == -1);
        check("invalid unit", DurationParser.parse("10x") == -1);
        check("empty", DurationParser.parse("") == -1);
        check("negative", DurationParser.parse("-5s") == -1);
        check("too large", DurationParser.parse("999999999999999d") == -1);
        check("default", DurationParser.parse("nope", 42) == 42);
        check("print", DurationParser.print(90500).equals("1m 30s 500ms"));
        check("print of 0 does not crash", DurationParser.print(0).equals("0ms"));
        check("print seconds", DurationParser.print(10000).equals("10s"));
    }

}
