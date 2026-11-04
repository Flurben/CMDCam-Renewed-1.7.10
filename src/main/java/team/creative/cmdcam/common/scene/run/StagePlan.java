package team.creative.cmdcam.common.scene.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import team.creative.cmdcam.common.math.interpolation.CamInterpolation;
import team.creative.cmdcam.common.math.interpolation.CamPitchMode;
import team.creative.cmdcam.common.math.point.CamPoint;
import team.creative.cmdcam.common.math.point.CamPoints;

/** Splits a scene into the stages it plays: optional smooth start, first run, loops and a final run that slows down. */
public class StagePlan {

    public static class Entry {

        public final CamInterpolation interpolation;
        public final long duration;
        public final int loops;
        public final CamPoints points;
        public final boolean mainStart;

        Entry(CamInterpolation interpolation, long duration, int loops, CamPoints points, boolean mainStart) {
            this.interpolation = interpolation;
            this.duration = duration;
            this.loops = loops;
            this.points = points;
            this.mainStart = mainStart;
        }
    }

    /**
     * @param smoothStartFrom
     *            where the camera currently is (in the same space as the points) or null to start directly at the first point
     */
    public static List<Entry> create(List<CamPoint> scenePoints, CamInterpolation interpolation, CamPitchMode pitchMode, long duration, int loop, CamPoint smoothStartFrom) {
        if (scenePoints.isEmpty())
            return Collections.emptyList();

        List<CamPoint> pts = scenePoints;
        if (pts.size() == 1) { // A single point is a shot which does not move
            pts = new ArrayList<>(pts);
            pts.add(pts.get(0));
        }

        CamPoint first = pts.get(0);
        CamPoint last = pts.get(pts.size() - 1);

        List<Entry> stages = new ArrayList<>();

        if (smoothStartFrom != null) {
            CamPoints points = new CamPoints();
            points.add(smoothStartFrom);
            points.add(first);
            points.after(first);
            points.fixSpinning(CamPitchMode.FIX);
            // 10 blocks per second, at least one second so a pure rotation is not abrupt
            long smoothDuration = (long) Math.max(1000, Math.min(20000, points.estimateLength() * 100));
            stages.add(new Entry(CamInterpolation.HERMITE, smoothDuration, 0, points, false));
        }

        { // First sequence
            CamPoints points = new CamPoints(pts);
            if (loop != 0) {
                points.add(first);
                points.after(pts.get(1));
            }
            points.fixSpinning(pitchMode);
            stages.add(new Entry(interpolation, duration, 0, points, true));
        }

        if (loop != 0 && loop != 1) { // Actual loop
            CamPoints points = new CamPoints(pts);
            points.before(last);
            points.add(first);
            points.after(pts.get(1));
            points.fixSpinning(pitchMode);
            stages.add(new Entry(interpolation, duration, loop > 0 ? loop - 2 : -1, points, false));
        }

        if (loop > 0) { // End of the last loop
            CamPoints points = new CamPoints(pts);
            points.before(last);
            points.after(last); // For a slow stop
            points.fixSpinning(pitchMode);
            stages.add(new Entry(interpolation, duration, 0, points, false));
        }

        return stages;
    }

}
