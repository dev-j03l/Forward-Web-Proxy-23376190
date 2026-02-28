import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final RequestListener requestListener;

    public ClientHandler(Socket clientSocket) {
        this(clientSocket, null);
    }

    public ClientHandler(Socket clientSocket, RequestListener requestListener) {
        this.clientSocket = clientSocket;
        this.requestListener = requestListener;
    }

    // As we implement runnable we need to override the default interface method.
    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        try (
                Socket socket = this.clientSocket;
                InputStream rawIn = socket.getInputStream();
                OutputStream rawOut = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.ISO_8859_1))) {

            // For first line of header: (e.g "GET http://example.com/ HTTP/1.1")
            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty())
                return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                System.out.println("Invalid Request Line");
                return;
            }

            String method = parts[0];
            String target = parts[1];
            String httpVer = parts[2];

            String line;
            HashMap<String, String> headers = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    break;

                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.put(key.toLowerCase(), value);
                }
            }

            // Extracting the host and port from the requestLine target.
            // There are two cases.
            // 1) HTTP - GET we are provided HOST, PORT (default = 80) and Path
            // 2) HTTPS - CONNECT example.com:443 only HOST and PORT
            String host = "";
            int port = -1;
            String path = "";

            // CONNECT: target is usually "host:port"
            if (method.equalsIgnoreCase("CONNECT")) {
                String[] hp = target.split(":", 2);
                host = hp[0];
                port = (hp.length == 2) ? Integer.parseInt(hp[1]) : 443;
                path = ""; // Not used for CONNECT requests
            } else {
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    boolean https = target.startsWith("https://");
                    String url = target.substring(https ? 8 : 7); // We strip the http(s):// from the target

                    int slashIndex = url.indexOf("/");
                    String hostPart = (slashIndex != -1) ? url.substring(0, slashIndex) : url;
                    path = (slashIndex != -1) ? url.substring(slashIndex) : "/";

                    String[] hp = hostPart.split(":", 2);
                    host = hp[0];
                    port = (hp.length == 2) ? Integer.parseInt(hp[1]) : (https ? 443 : 80);
                } else if (target.startsWith("/")) {
                    // origin-form: host comes from host header
                    path = target;
                    String hostHeader = headers.get("host");
                    if (hostHeader == null) {
                        System.out.println("No Host Header Present; cannot route request");
                        return;
                    }
                    hostHeader = hostHeader.trim();

                    String[] hp = hostHeader.split(":", 2);
                    host = hp[0];
                    port = (hp.length == 2) ? Integer.parseInt(hp[1]) : 80;
                } else {
                    // Some unrecognizable form
                    System.out.println("Unrecognized target form: " + target);
                    return;
                }
            }

            System.out.println("ROUTE => " + method + " " + host + ":" + port + " " + path);

            if (requestListener != null) {
                requestListener.onRequest(new RequestRecord(
                        Instant.now(), method, host, port, path,
                        clientSocket.getRemoteSocketAddress().toString()));
            }

            if (method.equalsIgnoreCase("CONNECT")) {
                // HTTPS tunnel: connect to origin, send 200 to client, then forward bytes both ways
                try (Socket serverSocket = new Socket(host, port)) {
                    serverSocket.setSoTimeout(0); // long-lived TLS connection
                    InputStream serverIn = serverSocket.getInputStream();
                    OutputStream serverOut = serverSocket.getOutputStream();

                    rawOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    rawOut.flush();

                    Thread clientToServer = new Thread(() -> {
                        byte[] buf = new byte[8192];
                        try {
                            int n;
                            while ((n = rawIn.read(buf)) != -1) {
                                serverOut.write(buf, 0, n);
                                serverOut.flush();
                            }
                            serverSocket.shutdownOutput();
                        } catch (IOException e) {
                            // Client or server closed; stop tunnel
                        }
                    }, "client->server");
                    Thread serverToClient = new Thread(() -> {
                        byte[] buf = new byte[8192];
                        try {
                            int n;
                            while ((n = serverIn.read(buf)) != -1) {
                                rawOut.write(buf, 0, n);
                                rawOut.flush();
                            }
                        } catch (IOException e) {
                            // Server or client closed; stop tunnel
                        }
                    }, "server->client");

                    clientToServer.start();
                    serverToClient.start();
                    try {
                        clientToServer.join();
                        serverToClient.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return;
            }

            try (Socket serverSocket = new Socket(host, port)) {
                serverSocket.setSoTimeout(15000);

                InputStream serverIn = serverSocket.getInputStream();
                OutputStream serverOut = serverSocket.getOutputStream();

                String outBoundRequestLine = method + " " + path + " " + httpVer + "\r\n";
                serverOut.write(outBoundRequestLine.getBytes(StandardCharsets.ISO_8859_1));

                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (key.equalsIgnoreCase("proxy-connection"))
                        continue;
                    if (key.equalsIgnoreCase("connection"))
                        continue;

                    if (key.equalsIgnoreCase("host")) {
                        serverOut.write(("Host: " + value + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    } else {
                        serverOut.write((key + ": " + value + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    }
                }

                if (!headers.containsKey("host")) {
                    serverOut.write(("Host: " + host + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                }

                serverOut.write(("Connection: close \r\n").getBytes(StandardCharsets.ISO_8859_1));
                serverOut.write(("\r\n").getBytes(StandardCharsets.ISO_8859_1));
                serverOut.flush();

                byte[] buffer = new byte[8192];
                int n;
                while ((n = serverIn.read(buffer)) != -1) {
                    rawOut.write(buffer, 0, n);
                }
                rawOut.flush();
            }

        } catch (IOException e) {
            System.err.println("[" + threadName + "] ClientHandler Error: " + e.getMessage());
        }
    }
}
