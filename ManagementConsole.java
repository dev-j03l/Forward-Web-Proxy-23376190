// Swing UI: request log with timing/source, block list management, and cache view.
// Implements RequestListener; refreshes cache tab on a timer.

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManagementConsole extends JFrame implements RequestListener {

    private static final String[] COLUMNS = { "Time", "Method", "Host", "Path", "Client", "Status", "Time (ms)", "Source" };
    private static final String[] CACHE_COLUMNS = { "URL", "Size", "Expires", "Last access" };
    private static final int MAX_ROWS = 2000;
    private static final int CACHE_REFRESH_MS = 2000;
    private static final int SOURCE_COLUMN_INDEX = 7;

    private static final Font UI_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final int ROW_HEIGHT = 22;
    private static final Color ROW_ALT = new Color(0xf8f8f8);
    private static final Color GRID = new Color(0xdddddd);
    private static final Color SOURCE_CACHE_COLOR = new Color(0x0d6b0d);
    private static final Color SOURCE_ORIGIN_COLOR = new Color(0x0066aa);
    private static final Color SOURCE_BLOCKED_COLOR = new Color(0xaa2222);
    private static final Color SOURCE_TUNNEL_COLOR = new Color(0x666666);

    private final BlockList blockList;
    private final ResponseCache responseCache;
    private final DefaultTableModel tableModel;
    private final DefaultTableModel cacheTableModel;
    private final List<RequestRecord> requestRecords = new ArrayList<>();
    private final DefaultListModel<String> blockListModel = new DefaultListModel<>();
    private final JTable requestTable;

    public ManagementConsole(BlockList blockList) {
        this(blockList, null);
    }

    public ManagementConsole(BlockList blockList, ResponseCache responseCache) {
        super("Proxy Management Console");
        this.blockList = blockList;
        this.responseCache = responseCache;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 660);
        setLocationRelativeTo(null);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        setUIFont(UI_FONT);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        requestTable = new JTable(tableModel);
        requestTable.setFont(TABLE_FONT);
        requestTable.setRowHeight(ROW_HEIGHT);
        requestTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestTable.setAutoCreateRowSorter(true);
        requestTable.setShowGrid(true);
        requestTable.setGridColor(GRID);
        requestTable.getTableHeader().setFont(TABLE_FONT);
        requestTable.getTableHeader().setReorderingAllowed(false);
        setRequestTableColumnWidths(requestTable);
        requestTable.setDefaultRenderer(Object.class, new SourceColorRenderer());

        JScrollPane tableScroll = new JScrollPane(requestTable);
        tableScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        cacheTableModel = new DefaultTableModel(CACHE_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JPanel tablePanel = buildTablePanel(tableScroll);
        JPanel blockPanel = buildBlockPanel();
        JPanel cachePanel = buildCachePanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UI_FONT);
        tabs.addTab("Requests", tablePanel);
        tabs.addTab("Block list", blockPanel);
        tabs.addTab("Cache", cachePanel);
        add(tabs, BorderLayout.CENTER);

        refreshBlockListModel();
        if (responseCache != null) {
            refreshCacheModel();
            new Timer(CACHE_REFRESH_MS, e -> refreshCacheModel()).start();
        }
    }

    private static void setRequestTableColumnWidths(JTable table) {
        int[] widths = { 88, 58, 180, 220, 130, 72, 72, 72 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JPanel buildTablePanel(JScrollPane tableScroll) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBorder(new EmptyBorder(6, 8, 6, 8));
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clear());
        toolbar.add(clearBtn);
        JButton blockHostBtn = new JButton("Block selected host");
        blockHostBtn.addActionListener(e -> blockSelectedHost());
        toolbar.add(blockHostBtn);
        JButton blockPathBtn = new JButton("Block selected host + path");
        blockPathBtn.addActionListener(e -> blockSelectedHostPath());
        toolbar.add(blockPathBtn);
        toolbar.add(new JLabel("  — Time (ms) and Source prove cache speed: Cache = from cache, Origin = from server."));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBlockPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(8, 8, 8, 8), BorderFactory.createTitledBorder("Blocked URLs")));
        JList<String> blockJList = new JList<>(blockListModel);
        blockJList.setFont(TABLE_FONT);
        blockJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.setBorder(new EmptyBorder(0, 0, 6, 0));
        JTextField addField = new JTextField(28);
        addField.setFont(UI_FONT);
        addField.setToolTipText("URL or host, e.g. https://youtube.com/ or example.com/ads");
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            String rule = addField.getText().trim();
            if (!rule.isEmpty()) {
                String normalized = blockList.add(rule);
                if (normalized != null && !blockListModel.contains(normalized)) {
                    blockListModel.addElement(normalized);
                }
                addField.setText("");
            }
        });
        JButton removeBtn = new JButton("Remove selected");
        removeBtn.addActionListener(e -> {
            int i = blockJList.getSelectedIndex();
            if (i >= 0) {
                blockList.remove(blockListModel.get(i));
                blockListModel.remove(i);
            }
        });
        toolbar.add(new JLabel("Add:"));
        toolbar.add(addField);
        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        panel.add(new JScrollPane(blockJList), BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCachePanel() {
        JTable cacheTable = new JTable(cacheTableModel);
        cacheTable.setFont(TABLE_FONT);
        cacheTable.setRowHeight(ROW_HEIGHT);
        cacheTable.setAutoCreateRowSorter(true);
        cacheTable.setShowGrid(true);
        cacheTable.setGridColor(GRID);
        cacheTable.getTableHeader().setFont(TABLE_FONT);
        cacheTable.getColumnModel().getColumn(0).setPreferredWidth(380);
        cacheTable.getColumnModel().getColumn(1).setPreferredWidth(72);
        cacheTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        cacheTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        cacheTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                if (!selected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                }
                return c;
            }
        });
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshCacheModel());
        toolbar.add(refreshBtn);
        toolbar.add(new JLabel("  Only HTTP GET is cached; use http://… for sites like neverssl.com."));
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(cacheTable), BorderLayout.CENTER);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private final class SourceColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, col);
            if (!selected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
            }
            if (col == SOURCE_COLUMN_INDEX && value != null) {
                String s = value.toString();
                if (RequestRecord.SOURCE_CACHE.equals(s)) {
                    c.setForeground(SOURCE_CACHE_COLOR);
                } else if (RequestRecord.SOURCE_ORIGIN.equals(s)) {
                    c.setForeground(SOURCE_ORIGIN_COLOR);
                } else if (RequestRecord.SOURCE_BLOCKED.equals(s)) {
                    c.setForeground(SOURCE_BLOCKED_COLOR);
                } else if (RequestRecord.SOURCE_TUNNEL.equals(s)) {
                    c.setForeground(SOURCE_TUNNEL_COLOR);
                } else {
                    c.setForeground(Color.BLACK);
                }
            } else if (!selected) {
                c.setForeground(Color.BLACK);
            }
            return c;
        }
    }

    private void refreshCacheModel() {
        if (responseCache == null) {
            return;
        }
        cacheTableModel.setRowCount(0);
        long now = System.currentTimeMillis();
        for (ResponseCache.CacheEntryInfo info : responseCache.getSnapshot()) {
            String expires = info.getExpiryMillis() <= now ? "Expired" : formatDuration((info.getExpiryMillis() - now) / 1000);
            String size = formatSize(info.getBodySize());
            String lastAccess = formatDuration((now - info.getLastAccessMillis()) / 1000) + " ago";
            cacheTableModel.addRow(new Object[]{ info.getDisplayUrl(), size, expires, lastAccess });
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private static String formatSize(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private void refreshBlockListModel() {
        blockListModel.clear();
        for (String rule : blockList.getAll()) {
            blockListModel.addElement(rule);
        }
    }

    @Override
    public void onRequest(RequestRecord record) {
        SwingUtilities.invokeLater(() -> {
            while (tableModel.getRowCount() >= MAX_ROWS) {
                tableModel.removeRow(0);
                requestRecords.remove(0);
            }
            String path = record.getPath();
            if (path.isEmpty()) {
                path = record.getMethod().equalsIgnoreCase("CONNECT") ? "(tunnel)" : "/";
            }
            String status = record.isBlocked() ? "Blocked" : "Forwarded";
            String timeMs = record.getDurationMs() != null ? String.valueOf(record.getDurationMs()) : "—";
            String source = record.getSource() != null ? record.getSource() : "—";
            tableModel.addRow(new Object[]{
                    record.getTimeString(),
                    record.getMethod(),
                    record.getHostPort(),
                    path,
                    record.getClientAddress(),
                    status,
                    timeMs,
                    source
            });
            requestRecords.add(record);
        });
    }

    private static void setUIFont(Font font) {
        for (Object key : UIManager.getLookAndFeelDefaults().keySet()) {
            if (key.toString().endsWith(".font")) {
                UIManager.put(key, font);
            }
        }
    }

    private void blockSelectedHost() {
        RequestRecord r = getSelectedRecord();
        if (r == null) {
            return;
        }
        String rule = r.getHost().toLowerCase();
        if (!blockList.contains(rule)) {
            String normalized = blockList.add(rule);
            if (normalized != null && !blockListModel.contains(normalized)) {
                blockListModel.addElement(normalized);
            }
        }
    }

    private void blockSelectedHostPath() {
        RequestRecord r = getSelectedRecord();
        if (r == null) {
            return;
        }
        String path = r.getPath();
        if (path.isEmpty() || "(tunnel)".equals(path)) {
            JOptionPane.showMessageDialog(this, "CONNECT requests have no path; use \"Block selected host\" instead.");
            return;
        }
        String pathPart = path.startsWith("/") ? path.substring(1) : path;
        String rule = (r.getHost() + "/" + pathPart).toLowerCase();
        if (!blockList.contains(rule)) {
            String normalized = blockList.add(rule);
            if (normalized != null) {
                blockListModel.addElement(normalized);
            }
        }
    }

    private RequestRecord getSelectedRecord() {
        int viewRow = requestTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a request row first.");
            return null;
        }
        int modelRow = requestTable.convertRowIndexToModel(viewRow);
        if (modelRow >= requestRecords.size()) {
            return null;
        }
        return requestRecords.get(modelRow);
    }

    private void clear() {
        tableModel.setRowCount(0);
        requestRecords.clear();
    }
}
