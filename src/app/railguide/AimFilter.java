package app.railguide;

/** Confirms direction independently of guide length and resets when the shot changes. */
public final class AimFilter {
    private Geometry.Segment previous;
    private Geometry.Segment filtered;
    private Geometry.Box table;
    private double cueX = Double.NaN;
    private double cueY;
    private boolean cueOnly;
    private int observations;
    private long observed;

    public void reset() {
        previous = null;
        filtered = null;
        table = null;
        observations = 0;
        observed = 0;
        cueX = Double.NaN;
    }

    public Geometry.Segment update(Detector.Result result, long now) {
        Geometry.Segment line = result.cueOnly ? result.cueLine : result.line;
        if (line == null || result.table == null || !Double.isFinite(line.length()) || line.length() < 1) {
            reset();
            return null;
        }

        double radius = Math.max(2, result.frameWidth * result.ballRadiusRatio);
        boolean same = previous != null && now >= observed && now - observed <= 750
                && cueOnly == result.cueOnly;
        if (same) {
            same = Math.hypot(line.x - previous.x, line.y - previous.y) < radius * .35
                    && angle(line, previous) < Math.toRadians(1.2);
            double horizontalShift = Math.max(Math.abs(table.l - result.table.l), Math.abs(table.r - result.table.r));
            double verticalShift = Math.max(Math.abs(table.t - result.table.t), Math.abs(table.b - result.table.b));
            same &= Math.max(horizontalShift, verticalShift) < radius * .5;
            if (result.approach != null && Double.isFinite(cueX)) {
                same &= Math.hypot(result.approach.x - cueX, result.approach.y - cueY) < radius * .35;
            }
        }

        observations = same ? observations + 1 : 1;
        if (same && filtered != null && angle(line, filtered) < Math.toRadians(.7)) {
            double dx = .65 * (line.u - line.x) / line.length()
                    + .35 * (filtered.u - filtered.x) / filtered.length();
            double dy = .65 * (line.v - line.y) / line.length()
                    + .35 * (filtered.v - filtered.y) / filtered.length();
            double magnitude = Math.hypot(dx, dy);
            filtered = new Geometry.Segment(line.x, line.y,
                    line.x + dx / magnitude * line.length(), line.y + dy / magnitude * line.length());
        } else {
            filtered = line;
        }

        previous = line;
        table = result.table;
        observed = now;
        cueOnly = result.cueOnly;
        cueX = result.approach == null ? Double.NaN : result.approach.x;
        cueY = result.approach == null ? 0 : result.approach.y;
        int required = result.estimated || result.cueEstimated ? 3 : 2;
        return observations >= required ? filtered : null;
    }

    public static double angle(Geometry.Segment a, Geometry.Segment b) {
        double cross = (a.u - a.x) * (b.v - b.y) - (a.v - a.y) * (b.u - b.x);
        double dot = (a.u - a.x) * (b.u - b.x) + (a.v - a.y) * (b.v - b.y);
        return Math.abs(Math.atan2(cross, dot));
    }
}
