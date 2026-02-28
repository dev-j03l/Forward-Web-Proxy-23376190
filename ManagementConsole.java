import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;

/**
 * Swing management console that displays each proxied request in a table.
 */
public class ManagementConsole extends JFrame implements RequestListener {

    private static final String[] COLUMNS = { "Time", "Method", "Host", "Path", "Client" };
    private static final int MAX_ROWS = 2000;

    private final DefaultTableModel tableModel;

    public ManagementConsole() {
        super("Proxy Management Console");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 500);
        setLocationRelativeTo(null);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);
        table.getColumnModel().getColumn(4).setPreferredWidth(140);

        JScrollPane scroll = new JScrollPane(table);
        add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clear());
        south.add(clearBtn);
        add(south, BorderLayout.SOUTH);
    }

    @Override
    public void onRequest(RequestRecord record) {
        SwingUtilities.invokeLater(() -> {
            while (tableModel.getRowCount() >= MAX_ROWS) {
                tableModel.removeRow(0);
            }
            String path = record.getPath();
            if (path.isEmpty()) {
                path = record.getMethod().equalsIgnoreCase("CONNECT") ? "(tunnel)" : "/";
            }
            tableModel.addRow(new Object[]{
                    record.getTimeString(),
                    record.getMethod(),
                    record.getHostPort(),
                    path,
                    record.getClientAddress()
            });
        });
    }

    private void clear() {
        tableModel.setRowCount(0);
    }
}
