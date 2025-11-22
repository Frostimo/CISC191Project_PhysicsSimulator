import java.awt.*;
import java.util.Map;
import java.util.function.DoublePredicate;

/**
 * MassSpringSim
 * 
 * This class is the "world" of the app. It stores the physics parameters,
 * the current state (position/velocity/time), knows how to advance the
 * state by a small time step, provides a snapshot for logging/overlays,
 * and knows how to draw itself onto a Graphics2D canvas.
 */
public class MassSpringSim implements SimEngine.SimModel{
    // PHYSICS PARAMETERS
    private double m, k, c;          // m = mass, k = spring constant, c = damping >= 0

    // SIMULATION STATE
    private double x, v, time;       // x = displacement (m), v = velocity (m/s), time (s)

    // This is for later on when I add the animation part (added later)
    private final double pixelsPerMeter = 200.0; // scale: how many screen pixels equal 1 meter
    private final int leftMarginPx = 80;         // space between anchor and x=0 reference

    /**
     * reset(newParams)
     * 
     * Reads the parameters from a map passed in by the GUI/controller (added later):
     * m>0, k>0, c>=0; initial conditions x0 and v0.
     * Also resets time back to zero.
     */
    public void reset(Map<String, Double> newParams) {
        // Validate and extract m and k (must be positive)
        m  = mustGet(newParams, "m",  d -> d > 0, "`m` must be > 0");
        k  = mustGet(newParams, "k",  d -> d > 0, "`k` must be > 0");
        // Damping is allowed to be zero
        c  = Math.max(0.0, newParams.getOrDefault("c", 0.0));
        // Initial displacement/velocity (defaults if not supplied)
        x  = newParams.getOrDefault("x0", 0.1);
        v  = newParams.getOrDefault("v0", 0.0);
        // Start the clock over (clock added in GUI later to show how much time passed)
        time = 0.0;
    }

    /**
     * step(dt)
     * 
     * Advances the physical values by a small time step dt using
     * semi-implicit Euler integration:
     *   a = -(c/m) v - (k/m) x
     *   v <- v + a*dt
     *   x <- x + v*dt   (using the new v)
     */
    public void step(double dt) {
        double a = -(c/m) * v - (k/m) * x; // acceleration from damping and spring force
        v += a * dt;                     // first update velocity
        x += v * dt;                     // then position using updated velocity
        time += dt;                    // advance the clock
    }

    /**
     * snapshot()
     * 
     * Returns a small array of commonly needed values for logging 
     * and overlays: [time, x, v, a, KE, PE, E]. The GUI will have
     * a place that shows the current values (added later).
     */
    public double[] snapshot() {
        double a  = -(c/m) * v - (k/m) * x;
        double KE = 0.5 * m * v * v;         // kinetic energy
        double PE = 0.5 * k * x * x;         // spring potential energy
        return new double[]{ time, x, v, a, KE, PE, KE+PE };
    }
    
    public void render(Graphics2D g2, Dimension size) {} // this is for the physics renderer later on
    
    // A way to read and validate a double from the params map. Currently only used for newParams.
    private static double mustGet(Map<String, Double> m, String key, DoublePredicate ok, String err){
        Double v = m.get(key);
        
        // Checks if the value doesn't exist or the input test fails
        if (v==null || !ok.test(v)) throw new IllegalArgumentException(err);
        return v;
    }
}
