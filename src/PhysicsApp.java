import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Lead Author(s):
 * @author Arthur Nguyen
 * 
 * Version/date: December 18, 2025
 * 
 * PhysicsApp
 * 
 * Builds the GUI (window, fields, buttons, canvas, status bar),
 * wires user actions to the engine/model, and manages save/export.
 * 
 * Flow:
 *  - User sets parameters → Run → engine steps continuously.
 *  - Pause/Step/Reset control the engine.
 *  - Save CSV writes the DataSet log to disk.
 */

public class PhysicsApp {
    // ===== GUI widgets =====
    private JFrame frame;
    private JTextField mField, kField, cField, x0Field, v0Field, dtField;
    private JLabel status;
    private JPanel canvas;
    private JComboBox<String> presetBox;

    // ===== Core objects =====
    private final DataSet<double[]> dataset = new DataSet<>();         // logged samples
    private final MassSpringSim sim = new MassSpringSim();             // concrete model
    private final SimEngine engine = new SimEngine(sim, dataset);      // timekeeper

    // Keep last good values so we can revert on invalid edits
    private final Map<String, Double> lastGood = new HashMap<>();

    /** Launch the app on the EDT (thread-safe for Swing). */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new PhysicsApp().initUI());
    }

    /** Build and show the entire user interface. */
    private void initUI() {
        // ---- Main window and layout
        frame = new JFrame("Mass–Spring Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ---- Toolbar (Run/Pause/Step/Reset/Save)
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton runBtn   = new JButton("Run ⏵");
        JButton pauseBtn = new JButton("Pause ⏸");
        JButton stepBtn  = new JButton("Step ⏭");
        JButton resetBtn = new JButton("Reset ⟲");
        JButton saveBtn  = new JButton("Save CSV ⤓");
        bar.add(runBtn); bar.add(pauseBtn); bar.add(stepBtn); bar.add(resetBtn); bar.addSeparator(); bar.add(saveBtn);
        frame.add(bar, BorderLayout.NORTH);

        // ---- Left panel: parameters (m, k, c, x0, v0, dt) + presets
        JPanel left = new JPanel(new GridBagLayout());
        left.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,4,4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        mField  = new JTextField("1.0", 8);
        kField  = new JTextField("20.0", 8);
        cField  = new JTextField("0.0", 8);
        x0Field = new JTextField("0.2", 8);
        v0Field = new JTextField("0.0", 8);
        dtField = new JTextField("0.016", 8);

        int r=0;
        addRow(left, gc, r++, "m (kg):",    mField);
        addRow(left, gc, r++, "k (N/m):",   kField);
        addRow(left, gc, r++, "c (N·s/m):", cField);
        addRow(left, gc, r++, "x0 (m):",    x0Field);
        addRow(left, gc, r++, "v0 (m/s):",  v0Field);
        addRow(left, gc, r++, "Δt (s):",    dtField);

        presetBox = new JComboBox<>(new String[]{"Undamped","Lightly Damped","Heavily Damped"});
        JButton applyPreset = new JButton("Apply Preset");
        JPanel presetRow = new JPanel(new BorderLayout(6,0));
        presetRow.add(presetBox, BorderLayout.CENTER);
        presetRow.add(applyPreset, BorderLayout.EAST);
        gc.gridx=0; gc.gridy=r++; gc.gridwidth=2;
        left.add(presetRow, gc);

        frame.add(left, BorderLayout.WEST);

        // ---- Canvas in the center (delegates drawing to sim.render)
        canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                sim.render((Graphics2D) g, getSize());
            }
        };
        canvas.setPreferredSize(new Dimension(800, 400));
        canvas.setBackground(Color.WHITE);
        frame.add(canvas, BorderLayout.CENTER);

        // ---- Status bar
        status = new JLabel("Ready.");
        status.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
        frame.add(status, BorderLayout.SOUTH);

        // ---- Button actions (what happens when clicked)
        runBtn.addActionListener(e -> {
            try { applyParams(); engine.start(); setStatus("Running…"); }
            catch (IllegalArgumentException ex) { showError(ex.getMessage()); }
        });
        pauseBtn.addActionListener(e -> { engine.pause(); setStatus("Paused."); });
        stepBtn.addActionListener(e -> {
            try {
                if (engine.isRunning()) return;          // only step when paused
                applyParams(); engine.stepOnce();         // one dt step
                canvas.repaint();                         // redraw to show change
                setStatus("Stepped once.");
            } catch (IllegalArgumentException ex) { showError(ex.getMessage()); }
        });
        resetBtn.addActionListener(e -> {
            try { applyParams(); engine.pause(); canvas.repaint(); setStatus("Reset."); }
            catch (IllegalArgumentException ex) { showError(ex.getMessage()); }
        });
        saveBtn.addActionListener(e -> doSave());

        // ---- Validate inputs on focus loss (revert to last good if bad)
        List<JTextField> fields = Arrays.asList(mField,kField,cField,x0Field,v0Field,dtField);
        for (JTextField tf : fields) {
            tf.addFocusListener(new FocusAdapter() {
                String old = tf.getText();
                @Override public void focusGained(FocusEvent e) { old = tf.getText(); }
                @Override public void focusLost (FocusEvent e) {
                    try { parseParams(); old = tf.getText(); setStatus("Ready."); }
                    catch (IllegalArgumentException ex) { tf.setText(old); showError(ex.getMessage()); }
                }
            });
        }

        // ---- Presets (quick parameter fills)
        applyPreset.addActionListener(e -> {
            String p = (String) presetBox.getSelectedItem();
            if ("Undamped".equals(p))            { cField.setText("0.0"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            else if ("Lightly Damped".equals(p)) { cField.setText("0.8"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            else if ("Heavily Damped".equals(p)) { cField.setText("5.0"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            setStatus("Preset applied.");
        });

        // ---- Initialize sim once with defaults
        try { applyParams(); } catch (IllegalArgumentException ex) { showError(ex.getMessage()); }

        // ---- Lightweight repaint timer (~30 FPS) → repaint only while running
        new javax.swing.Timer(33, e -> { if (engine.isRunning()) canvas.repaint(); }).start();

        // ---- Show window
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    // Helper to add a labeled field row to the left panel
    private void addRow(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx=0; gc.gridy=row; gc.gridwidth=1; panel.add(new JLabel(label), gc);
        gc.gridx=1; gc.gridy=row; panel.add(field, gc);
    }

    /**
     * Parse, validate, and apply parameters:
     * - set engine dt,
     * - reset the simulation (clears dataset and logs t=0).
     */
    private void applyParams() {
        Map<String, Double> p = parseParams();
        engine.setDt(p.get("dt"));
        engine.reset(p);
    }

    /** Read numbers from text fields, validate, remember last-good, and return a map. */
    private Map<String, Double> parseParams() {
        double m  = parsePositive(mField.getText().trim(), "`m` must be > 0");
        double k  = parsePositive(kField.getText().trim(), "`k` must be > 0");
        double c  = parseNonNegative(cField.getText().trim(), "`c` must be ≥ 0");
        double x0 = parseDouble(x0Field.getText().trim(), "`x0` must be a number");
        double v0 = parseDouble(v0Field.getText().trim(), "`v0` must be a number");
        double dt = parsePositive(dtField.getText().trim(), "`Δt` must be > 0");

        Map<String, Double> p = new HashMap<>();
        p.put("m", m); p.put("k", k); p.put("c", c); p.put("x0", x0); p.put("v0", v0); p.put("dt", dt);

        lastGood.putAll(p); // keep a safe copy for error recovery
        return p;
    }

    // Small parsing helpers with clear messages
    private static double parseDouble(String s, String err) {
        try { return Double.parseDouble(s); } catch (Exception e) { throw new IllegalArgumentException(err); }
    }
    private static double parsePositive(String s, String err) {
        double v = parseDouble(s, err);
        if (v <= 0) throw new IllegalArgumentException(err);
        return v;
    }
    private static double parseNonNegative(String s, String err) {
        double v = parseDouble(s, err);
        if (v < 0) throw new IllegalArgumentException(err);
        return v;
    }

    /** Show a save dialog and write the DataSet to CSV (or show an error). */
    private void doSave() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("mass_spring_" + timestamp() + ".csv"));
        int r = fc.showSaveDialog(frame);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                dataset.toCSV(f, new String[]{"t","x","v","a","KE","PE","E"});
                setStatus("Saved " + f.getName() + " (" + dataset.size() + " rows).");
            } catch (Exception ex) { showError("Failed to save: " + ex.getMessage()); }
        }
    }

    /** Filename-friendly timestamp for exported CSVs. */
    private static String timestamp() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return String.format("%04d-%02d-%02d_%02d-%02d-%02d",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
            now.getHour(), now.getMinute(), now.getSecond());
    }

    /** Pop an error dialog and reflect it in the status bar. */
    private void showError(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Invalid Input", JOptionPane.ERROR_MESSAGE);
        status.setText("Error: " + msg);
    }

    /** Convenience for the status bar text. */
    private void setStatus(String msg) { status.setText(msg); }
}