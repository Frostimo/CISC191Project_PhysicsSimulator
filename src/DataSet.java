import java.io.*;
import java.util.*;

/**
 * DataSet<T>
 * ----------
 * A tiny time-series "notebook":
 *  - times:   holds the time column (double)
 *  - rows:    holds the per-step data object (here we use double[] rows)
 * Provides clear(), add(time,row), size(), and toCSV(file, header).
 */
public class DataSet<T> {
    private final List<Double> times = new ArrayList<>();
    private final List<T> rows   = new ArrayList<>();

    /** Clear all logged samples (used on Reset). */
    public void clear() { times.clear(); rows.clear(); }

    /** Add a new sample: time value + row payload. */
    public void add(double t, T row) { times.add(t); rows.add(row); }

    /** Number of samples recorded. */
    public int size() { return rows.size(); }

    /**
     * Write CSV to disk with a header line (if provided).
     * If the row type is double[], each number becomes a column.
     */
    public void toCSV(File file, String[] header) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {

            // ---- optional header
            if (header != null && header.length > 0) {
                for (int i = 0; i < header.length; i++) {
                    if (i > 0) pw.print(',');
                    pw.print(header[i]);
                }
                pw.println();
            }

            // ---- rows (time, then row payload)
            for (int i = 0; i < rows.size(); i++) {
                pw.print(times.get(i)); // first column is time
                T row = rows.get(i);

                if (row instanceof double[]) {
                    // If row is a numeric array, write each element as a column
                    for (double v : (double[]) row) { pw.print(','); pw.print(v); }
                } else {
                    // Fallback: write the object's toString
                    pw.print(','); pw.print(String.valueOf(row));
                }
                pw.println();
            }
        }
    }
}
