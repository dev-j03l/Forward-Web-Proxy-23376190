// Handles one client connection: parses request, checks block list and cache, forwards or tunnels.
// Reports each completed request (with duration and source) to the RequestListener.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private static final int CONNECT_DEFAULT_PORT = 443;
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int ORIGIN_SO_TIMEOUT_MS = 15000;
    private static final int TUNNEL_BUFFER_SIZE = 8192;
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final int HTTPS_PREFIX_LEN = 8;
    private static final int HTTP_PREFIX_LEN = 7;
    private static final String CONNECTION_ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n";
    private static final String FORBIDDEN_BODY = "Blocked by proxy.";
    private static final String STATUS_CODE_200 = "200";

    private final Socket clientSocket;
    private final RequestListener requestListener;
    private final BlockList blockList;
    private final ResponseCache responseCache;

    public ClientHandler(Socket clientSocket) {
        this(clientSocket, null, null, null);
    }

    public ClientHandler(Socket clientSocket, RequestListener requestListener) {
        this(clientSocket, requestListener, null, null);
    }

    public ClientHandler(Socket clientSocket, RequestListener requestListener, BlockList blockList) {
        this(clientSocket, requestListener, blockList, null);
    }

    public ClientHandler(Socket clientSocket, RequestListener requestListener, BlockList blockList, ResponseCache responseCache) {
        this.clientSocket = clientSocket;
        this.requestListener = requestListener;
        this.blockList = blockList;
        this.responseCache = responseCache;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        try (Socket socket = this.clientSocket;
             InputStream rawIn = socket.getInputStream();
             OutputStream rawOut = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.ISO_8859_1))) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 3) {
                System.out.println("Invalid Request Line");
                return;
            }
            String method = parts[0];
            String target = parts[1];
            String httpVer = parts[2];

            Map<String, String> headers = readHeaders(reader);
            String host = "";
            int port = -1;
            String path = "";

            if (method.equalsIgnoreCase("CONNECT")) {
                String[] hp = target.split(":", 2);
                host = hp[0];
                port = hp.length == 2 ? Integer.parseInt(hp[1]) : CONNECT_DEFAULT_PORT;
            } else {
                if (target.startsWith(HTTPS_PREFIX) || target.startsWith(HTTP_PREFIX)) {
                    boolean https = target.startsWith(HTTPS_PREFIX);
                    int prefixLen = https ? HTTPS_PREFIX_LEN : HTTP_PREFIX_LEN;
                    String url = target.substring(prefixLen);
                    int slashIndex = url.indexOf('/');
                    String hostPart = slashIndex != -1 ? url.substring(0, slashIndex) : url;
                    path = slashIndex != -1 ? url.substring(slashIndex) : "/";
                    String[] hp = hostPart.split(":", 2);
                    host = hp[0];
                    port = hp.length == 2 ? Integer.parseInt(hp[1]) : (https ? CONNECT_DEFAULT_PORT : HTTP_DEFAULT_PORT);
                } else if (target.startsWith("/")) {
                    path = target;
                    String hostHeader = headers.get("host");
                    if (hostHeader == null) {
                        System.out.println("No Host Header Present; cannot route request");
                        return;
                    }
                    hostHeader = hostHeader.trim();
                    String[] hp = hostHeader.split(":", 2);
                    host = hp[0];
                    port = hp.length == 2 ? Integer.parseInt(hp[1]) : HTTP_DEFAULT_PORT;
                } else {
                    System.out.println("Unrecognized target form: " + target);
                    return;
                }
            }

            System.out.println("ROUTE => " + method + " " + host + ":" + port + " " + path);
            long startTime = System.currentTimeMillis();
            String clientAddr = clientSocket.getRemoteSocketAddress().toString();

            if (blockList != null && blockList.isBlocked(host, path)) {
                sendBlockedResponse(rawOut);
                if (requestListener != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    requestListener.onRequest(new RequestRecord(Instant.now(), method, host, port, path, clientAddr,
                            true, duration, RequestRecord.SOURCE_BLOCKED));
                }
                System.out.println("BLOCKED => " + host + (path.isEmpty() ? "" : path));
                return;
            }

            String cacheKey = method.equalsIgnoreCase("GET") ? ResponseCache.cacheKey(host, port, path) : null;
            if (method.equalsIgnoreCase("GET") && responseCache != null) {
                CacheEntry cached = responseCache.get(cacheKey);
                if (cached != null) {
                    sendCachedResponse(rawOut, cached);
                    rawOut.flush();
                    if (requestListener != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        requestListener.onRequest(new RequestRecord(Instant.now(), method, host, port, path, clientAddr,
                                false, duration, RequestRecord.SOURCE_CACHE));
                    }
                    System.out.println("CACHE HIT => " + host + path + " (" + (System.currentTimeMillis() - startTime) + " ms)");
                    return;
                }
            }

            if (method.equalsIgnoreCase("CONNECT")) {
                runConnectTunnel(rawIn, rawOut, host, port, method, path, clientAddr);
                return;
            }

            fetchFromOrigin(rawOut, reader, method, path, httpVer, headers, host, port, startTime, clientAddr, cacheKey);
        } catch (IOException e) {
            System.err.println("[" + threadName + "] ClientHandler Error: " + e.getMessage());
        }
    }

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    private void sendBlockedResponse(OutputStream rawOut) throws IOException {
        byte[] bodyBytes = FORBIDDEN_BODY.getBytes(StandardCharsets.UTF_8);
        rawOut.write(("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Length: "
                + bodyBytes.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        rawOut.write(bodyBytes);
        rawOut.flush();
    }

    private void runConnectTunnel(InputStream rawIn, OutputStream rawOut, String host, int port, String method, String path, String clientAddr) throws IOException {
        try (Socket serverSocket = new Socket(host, port)) {
            serverSocket.setSoTimeout(0);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();
            rawOut.write(CONNECTION_ESTABLISHED.getBytes(StandardCharsets.ISO_8859_1));
            rawOut.flush();
            Thread clientToServer = new Thread(() -> copyStream(rawIn, serverOut, serverSocket), "client->server");
            Thread serverToClient = new Thread(() -> copyStream(serverIn, rawOut, null), "server->client");
            clientToServer.start();
            serverToClient.start();
            if (requestListener != null) {
                requestListener.onRequest(new RequestRecord(Instant.now(), method, host, port, path, clientAddr,
                        false, null, RequestRecord.SOURCE_TUNNEL));
            }
            try {
                clientToServer.join();
                serverToClient.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void copyStream(InputStream in, OutputStream out, Socket shutdownOutputOnClose) {
        byte[] buf = new byte[TUNNEL_BUFFER_SIZE];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
            if (shutdownOutputOnClose != null) {
                shutdownOutputOnClose.shutdownOutput();
            }
        } catch (IOException ignored) {
        }
    }

    private void fetchFromOrigin(OutputStream rawOut, BufferedReader reader, String method, String path, String httpVer,
                                 Map<String, String> headers, String host, int port, long startTime, String clientAddr, String cacheKey) throws IOException {
        try (Socket serverSocket = new Socket(host, port)) {
            serverSocket.setSoTimeout(ORIGIN_SO_TIMEOUT_MS);
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream serverOut = serverSocket.getOutputStream();
            writeForwardRequest(serverOut, method, path, httpVer, headers, host);
            HttpResponseReader.Response response = HttpResponseReader.readResponse(serverIn);
            if (response == null) {
                return;
            }
            sendResponse(rawOut, response);
            rawOut.flush();
            long duration = System.currentTimeMillis() - startTime;
            if (requestListener != null) {
                requestListener.onRequest(new RequestRecord(Instant.now(), method, host, port, path, clientAddr,
                        false, duration, RequestRecord.SOURCE_ORIGIN));
            }
            System.out.println("ORIGIN => " + host + path + " (" + duration + " ms)");
            maybeStoreInCache(response, cacheKey, host, path, method);
        }
    }

    private static void writeForwardRequest(OutputStream serverOut, String method, String path, String httpVer,
                                           Map<String, String> headers, String host) throws IOException {
        serverOut.write((method + " " + path + " " + httpVer + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("proxy-connection") || key.equalsIgnoreCase("connection")) {
                continue;
            }
            String value = entry.getValue();
            serverOut.write((key.equalsIgnoreCase("host") ? "Host: " + value : key + ": " + value).getBytes(StandardCharsets.ISO_8859_1));
            serverOut.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        if (!headers.containsKey("host")) {
            serverOut.write(("Host: " + host + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        serverOut.write("Connection: close \r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        serverOut.flush();
    }

    private void maybeStoreInCache(HttpResponseReader.Response response, String cacheKey, String host, String path, String method) {
        if (!method.equalsIgnoreCase("GET") || responseCache == null || cacheKey == null || !isSuccessStatus(response.statusLine)) {
            return;
        }
        long expiry = responseCache.computeExpiry(response.headers);
        if (expiry <= 0) {
            System.out.println("CACHE SKIP (no-store/private) => " + host + path);
            return;
        }
        List<String> storeHeaders = new ArrayList<>(response.headerLines);
        normalizeHeadersForCache(storeHeaders, response.body.length);
        responseCache.put(cacheKey, new CacheEntry(response.statusLine, storeHeaders, response.body, expiry));
        System.out.println("CACHE STORE => " + host + path);
    }

    private static void sendCachedResponse(OutputStream out, CacheEntry entry) throws IOException {
        out.write((entry.getStatusLine() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        for (String h : entry.getHeaders()) {
            out.write((h + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        out.write(entry.getBody());
    }

    private static void sendResponse(OutputStream out, HttpResponseReader.Response response) throws IOException {
        out.write((response.statusLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        for (String h : response.headerLines) {
            out.write((h + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        out.write(response.body);
    }

    private static boolean isSuccessStatus(String statusLine) {
        if (statusLine == null || statusLine.length() < 12) {
            return false;
        }
        int firstSpace = statusLine.indexOf(' ');
        if (firstSpace < 0) {
            return false;
        }
        int secondSpace = statusLine.indexOf(' ', firstSpace + 1);
        if (secondSpace < 0) {
            secondSpace = statusLine.length();
        }
        return STATUS_CODE_200.equals(statusLine.substring(firstSpace + 1, secondSpace));
    }

    private static void normalizeHeadersForCache(List<String> headerLines, int bodyLength) {
        Iterator<String> it = headerLines.iterator();
        while (it.hasNext()) {
            String line = it.next().toLowerCase();
            if (line.startsWith("transfer-encoding:") || line.startsWith("content-length:")) {
                it.remove();
            }
        }
        headerLines.add("Content-Length: " + bodyLength);
    }
}
