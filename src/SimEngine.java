import javax.swing.*;
import java.util.Map;

/**
 * SimEngine
 * ---------
 * The "timekeeper" for the app.
 * - Drives a simulation forward at a fixed time step (dt) using a Swing Timer.
 * - Logs each step's snapshot to a DataSet (for CSV saving).
 * - Exposes controls the GUI calls: start, pause, stepOnce, reset.
 */
public class SimEngine {

    /** Contract the engine needs from any simulation model. */
    public interface SimModel {
        void reset(java.util.Map<String, Double> p);         // initialize params + state
        void step(double dt);                                // advance physics by dt seconds
        double[] snapshot();                                 // [t, x, v, a, KE, PE, E] for logging
        void render(java.awt.Graphics2D g2, java.awt.Dimension size); // draw current state
    }

    // ===== Engine State =====
    private final SimModel sim;           // the model currently driven by the engine
    private final DataSet<double[]> data; // the time-series "notebook" for logged results
    private final String[] header = {"t","x","v","a","KE","PE","E"}; // CSV header convenience

    private javax.swing.Timer timer;      // fires on the EDT ~every N ms; calls tick()
    private boolean running = false;      // whether the engine is advancing continuously
    private double dt = 0.016;            // physics time step in seconds (~60 Hz)

    /**
     * Wire up engine with a simulation model and a dataset to log into.
     * Creates a GUI-safe Swing timer to drive ticks.
     */
    public SimEngine(SimModel sim, DataSet<double[]> dataset) {
        this.sim = sim;
        this.data = dataset;
        this.timer = new javax.swing.Timer(16, e -> tick()); // ~60 calls/sec → stable stepping
    }

    /** Change the physics step size; clamped to a minimum (>0) to remain valid. */
    public void setDt(double dtSeconds) { this.dt = Math.max(1e-6, dtSeconds); }

    /**
     * Reset the simulation with new parameters from the GUI.
     * Clears any old logs and records the initial (t=0) snapshot.
     */
    public void reset(Map<String, Double> params) {
        sim.reset(params);
        data.clear();
        log(); // capture t=0 row
    }

    /** Begin continuous stepping (animation). */
    public void start() {
        if (!running) { running = true; timer.start(); }
    }

    /** Stop continuous stepping (freeze the state). */
    public void pause() {
        if (running) { timer.stop(); running = false; }
    }

    /**
     * Single-step when paused (advance exactly once by dt and log it).
     * The GUI can repaint right after to show the new state.
     */
    public void stepOnce() {
        if (running) return; // only step if paused
        sim.step(dt);
        log();
    }

    /** Timer callback: advance physics and log. Rendering is the GUI's job. */
    private void tick() { sim.step(dt); log(); }

    /** Append the latest snapshot to the dataset (time in one column, rest as a row). */
    private void log() {
        double[] s = sim.snapshot();            // [t, x, v, a, KE, PE, E]
        double[] row = new double[s.length - 1];// we'll store x..E in the row
        System.arraycopy(s, 1, row, 0, s.length - 1);
        data.add(s[0], row);                    // time column + row columns
    }

    // ===== Small accessors used by the GUI =====
    public DataSet<double[]> dataset() { return data; }
    public String[] headerWithT() { return header; }
    public boolean isRunning() { return running; }
    public double getDt() { return dt; }
    public SimModel simulation() { return sim; }
}