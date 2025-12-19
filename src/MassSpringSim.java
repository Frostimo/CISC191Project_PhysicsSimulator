import java.awt.*;
import java.util.Map;
import java.util.function.DoublePredicate;

/**
 *  * Lead Author(s):
 * @author Arthur Nguyen
 * 
 * Version/date: December 18, 2025
 * 
 * MassSpringSim
 * 
 * A concrete 1D mass–spring (with optional damping) simulation.
 * Implements SimEngine.SimModel so SimEngine can drive it polymorphically.
 * Responsibilities:
 *  - Holds parameters (m, k, c) and state (x, v, time).
 *  - Knows how to step the physics forward.
 *  - Provides a snapshot for logging and overlay text.
 *  - Knows how to draw itself onto a Graphics2D canvas.
 */

public class MassSpringSim implements SimEngine.SimModel {

    // ===== Physics Parameters (set by reset(...)) =====
    private double m, k, c;      // mass, spring constant, damping (>=0)

    // ===== State (changes during stepping) =====
    private double x, v, time;   // displacement (m), velocity (m/s), time (s)

    // ===== Visual settings (drawing only) =====
    private final double pixelsPerMeter = 200.0; // scale: meters → pixels
    private final int leftMarginPx = 80;         // space from anchor to x=0 reference

    /** Initialize parameters + initial conditions; also resets the clock to t=0. */
    @Override
    public void reset(Map<String, Double> newParams) throws IllegalArgumentException {
        // Validate required positives
        m  = mustGet(newParams, "m",  d -> d > 0, "`m` must be > 0");
        k  = mustGet(newParams, "k",  d -> d > 0, "`k` must be > 0");

        // Damping can be zero; use 0 if missing
        c  = Math.max(0.0, newParams.getOrDefault("c", 0.0));

        // Initial conditions (defaults if not provided)
        x  = newParams.getOrDefault("x0", 0.1);
        v  = newParams.getOrDefault("v0", 0.0);

        // Reset time
        time = 0.0;
    }

    /**
     * Advance physics by dt seconds using semi-implicit Euler:
     *   a = -(c/m)*v - (k/m)*x
     *   v <- v + a*dt
     *   x <- x + v*dt   (using the updated v)
     */
    @Override
    public void step(double dt) {
        double a = -(c/m)*v - (k/m)*x;
        v += a * dt;
        x += v * dt;
        time += dt;
    }

    /** Return [time, x, v, a, KE, PE, E] for logging and on-screen text. */
    @Override
    public double[] snapshot() {
        double a  = -(c/m)*v - (k/m)*x;
        double KE = 0.5 * m * v * v;
        double PE = 0.5 * k * x * x;
        return new double[]{ time, x, v, a, KE, PE, KE + PE };
    }

    /**
     * Draw current state on the given canvas.
     * Purely visual—does not change physics state.
     */
    @Override
    public void render(Graphics2D g2, Dimension size) {
        // Background + antialiasing for smooth lines
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size.width, size.height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Center baseline for reference
        int cy = size.height / 2;
        g2.setColor(new Color(230, 230, 230));
        g2.fillRect(0, cy - 1, size.width, 2);

        // Fixed anchor at the left
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(20, cy - 30, 20, 60);

        // Compute current block position in pixels from displacement x (meters)
        int anchorX = 40, blockW = 60, blockH = 40;
        int blockX = (int)(anchorX + leftMarginPx + x * pixelsPerMeter);
        int span   = Math.max(10, blockX - anchorX);

        // Draw a zig-zag spring between anchor and block
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(90, 90, 90));
        int coils = 8, amp = 12, px = anchorX, py = cy;
        for (int i = 1; i <= coils * 2; i++) {
            int xi = anchorX + (i * span) / (coils * 2);
            int yi = cy + ((i % 2 == 0) ? -amp : amp);
            g2.drawLine(px, py, xi, yi);
            px = xi; py = yi;
        }
        g2.drawLine(px, py, blockX, cy);

        // Draw the block
        int by = cy - blockH / 2;
        g2.setColor(new Color(60, 120, 200));
        g2.fillRoundRect(blockX, by, blockW, blockH, 10, 10);
        g2.setColor(Color.BLACK);
        g2.drawRoundRect(blockX, by, blockW, blockH, 10, 10);

        // Small text overlay with t, x, v
        double[] s = snapshot();
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(20, 20, 20));
        g2.drawString(String.format("t=%.2fs  x=%.3fm  v=%.3fm/s", s[0], s[1], s[2]), 10, 18);
    }

    // ---- Helper: look up and validate a required double param ----
    private static double mustGet(Map<String, Double> m, String key,
                                  DoublePredicate ok, String err) {
        Double v = m.get(key);
        if (v == null || !ok.test(v)) throw new IllegalArgumentException(err);
        return v;
    }
}