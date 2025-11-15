import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * PhysicsApp
 * 
 * The GUI: builds the window, fields, buttons, drawing canvas, and status bar.
 * Wires user actions (Run/Pause/Step/Reset/Save) to the SimEngine and the model.
 */
public class PhysicsApp {
    // GUI Widgets
    private JFrame frame;
    private JTextField mField, kField, cField, x0Field, v0Field, dtField;
    private JLabel status;
    private JPanel canvas; // Delegated area for the simulation (added later)
    private JComboBox<String> presetBox;

    // Core Objects
    private final MassSpringSim sim = new MassSpringSim();      // the physics "world" (added later)

    // Keeps the latest valid parameters so we can revert on bad input
    private final Map<String, Double> lastGood = new HashMap<>();

    public static void main(String[] args) {
    	new PhysicsApp().initUI();
    }

    /**
     * initUI()
     * 
     * Builds the whole interface:
     *  - toolbar (buttons) (added later)
     *  - left param panel (text fields + presets)
     *  - canvas (center) that draws the simulation (added later)
     *  - status bar (bottom)
     * Also wires listeners, applies defaults, and starts a repaint timer.
     */
    private void initUI() {
        // ---- MAIN WINDOW
        frame = new JFrame("Mass–Spring Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ---- TOP TOOLBAR (buttons)
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton runBtn   = new JButton("Run ⏵");
        JButton pauseBtn = new JButton("Pause ⏸");
        JButton stepBtn  = new JButton("Step ⏭");
        JButton resetBtn = new JButton("Reset ⟲");
        JButton saveBtn  = new JButton("Save CSV ⤓");
        bar.add(runBtn); bar.add(pauseBtn); bar.add(stepBtn); bar.add(resetBtn); bar.addSeparator(); bar.add(saveBtn);
        frame.add(bar, BorderLayout.NORTH);

        // ---- LEFT: PARAMETER PANEL (labels + fields)
        JPanel left = new JPanel(new GridBagLayout());
        left.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,4,4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        // default values shown to the user
        mField  = new JTextField("1.0", 8);
        kField  = new JTextField("20.0", 8);
        cField  = new JTextField("0.0", 8);
        x0Field = new JTextField("0.2", 8);
        v0Field = new JTextField("0.0", 8);
        dtField = new JTextField("0.016", 8);

        int r=0;
        addRow(left, gc, r++, "m (kg):",   mField);
        addRow(left, gc, r++, "k (N/m):",  kField);
        addRow(left, gc, r++, "c (N·s/m):",cField);
        addRow(left, gc, r++, "x0 (m):",   x0Field);
        addRow(left, gc, r++, "v0 (m/s):", v0Field);
        addRow(left, gc, r++, "Δt (s):",   dtField);

        // Preset row (combobox + button)
        presetBox = new JComboBox<>(new String[]{"Undamped","Lightly Damped","Heavily Damped"});
        JButton applyPreset = new JButton("Apply Preset");
        JPanel presetRow = new JPanel(new BorderLayout(6,0));
        presetRow.add(presetBox, BorderLayout.CENTER);
        presetRow.add(applyPreset, BorderLayout.EAST);
        gc.gridx=0; gc.gridy=r++; gc.gridwidth=2;
        left.add(presetRow, gc);

        frame.add(left, BorderLayout.WEST);

        // ---- BOTTOM: STATUS BAR
        status = new JLabel("Ready.");
        status.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
        frame.add(status, BorderLayout.SOUTH);

        // ---- VALIDATE INPUTS when a field loses focus; revert to last good on error
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

        // PRESETS: quick parameter shortcuts
        applyPreset.addActionListener(e -> {
            String p = (String) presetBox.getSelectedItem();
            if ("Undamped".equals(p))            { cField.setText("0.0"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            else if ("Lightly Damped".equals(p)) { cField.setText("0.8"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            else if ("Heavily Damped".equals(p)) { cField.setText("5.0"); x0Field.setText("0.2"); v0Field.setText("0.0"); }
            setStatus("Preset applied.");
        });

        // ---- SIZE AND SHOW THE WINDOW
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    // Utility for laying out a labeled row in the left panel
    private void addRow(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx=0; gc.gridy=row; gc.gridwidth=1; panel.add(new JLabel(label), gc);
        gc.gridx=1; gc.gridy=row; panel.add(field, gc);
    }

    /**
     * parseParams()
     * -------------
     * Reads all text fields, converts to numbers, validates ranges,
     * stores a "last good" copy for error recovery, and returns the map.
     */
    private Map<String, Double> parseParams() {
        double m  = parsePositive(mField.getText().trim(), "`m` must be > 0");
        double k  = parsePositive(kField.getText().trim(), "`k` must be > 0");
        double c  = parseNonNegative(cField.getText().trim(), "`c` must be ≥ 0");
        double x0 = parseDouble(x0Field.getText().trim(), "`x0` must be a number");
        double v0 = parseDouble(v0Field.getText().trim(), "`v0` must be a number");
        double dt = parsePositive(dtField.getText().trim(), "`Δt` must be > 0");

        Map<String, Double> p = new HashMap<>();
        p.put("m", m); p.put("k", k); p.put("c", c); p.put("x0", x0); p.put("v0", v0); p.put("dt", dt);

        // Remember the last known good set so we can revert fields if user types bad input later
        lastGood.putAll(p);
        return p;
    }

    // ---- small parsing helpers with friendly error messages
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

    

    // Safe filename-friendly timestamp
    private static String timestamp() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return String.format("%04d-%02d-%02d_%02d-%02d-%02d",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
            now.getHour(), now.getMinute(), now.getSecond());
    }

    // Show an error dialog and also update the status bar
    private void showError(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Invalid Input", JOptionPane.ERROR_MESSAGE);
        status.setText("Error: " + msg);
    }

    // Convenience for status bar
    private void setStatus(String msg) { status.setText(msg); }
}