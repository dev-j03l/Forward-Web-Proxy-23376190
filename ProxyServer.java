// Main entry point: listens for TCP connections and hands each client to a ClientHandler thread.
// Optionally uses a management console, block list, and response cache.

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer {

    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private final RequestListener requestListener;
    private final BlockList blockList;
    private final ResponseCache responseCache;

    public ProxyServer(int port) {
        this(port, null, null, null);
    }

    public ProxyServer(int port, RequestListener requestListener) {
        this(port, requestListener, null, null);
    }

    public ProxyServer(int port, RequestListener requestListener, BlockList blockList) {
        this(port, requestListener, blockList, null);
    }

    public ProxyServer(int port, RequestListener requestListener, BlockList blockList, ResponseCache responseCache) {
        this.port = port;
        this.requestListener = requestListener;
        this.blockList = blockList;
        this.responseCache = responseCache != null ? responseCache : new ResponseCache();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ProxyServer listening on Port: " + port + "\n");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, requestListener, blockList, responseCache);
                new Thread(handler).start();
            }
        }
    }

    public static void main(String[] args) {
        int port = args.length == 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        BlockList blockList = new BlockList();
        ResponseCache responseCache = new ResponseCache();
        ManagementConsole console = new ManagementConsole(blockList, responseCache);
        SwingUtilities.invokeLater(() -> console.setVisible(true));
        try {
            new ProxyServer(port, console, blockList, responseCache).start();
        } catch (IOException e) {
            System.err.println("Failed to start Proxy Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
