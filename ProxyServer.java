import javax.swing.*;
import java.io.*;
import java.net.*;

public class ProxyServer {

    private final int port;
    private final RequestListener requestListener;

    public ProxyServer(int port) {
        this(port, null);
    }

    public ProxyServer(int port, RequestListener requestListener) {
        this.port = port;
        this.requestListener = requestListener;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ProxyServer listening on Port: " + port + "\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket, requestListener);
                Thread t = new Thread(handler);
                t.start();
            }
        }
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }

        ManagementConsole console = new ManagementConsole();
        SwingUtilities.invokeLater(() -> console.setVisible(true));

        try {
            new ProxyServer(port, console).start();
        } catch (IOException e) {
            System.err.println("Failed to start Proxy Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}