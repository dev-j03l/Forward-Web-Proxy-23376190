// Immutable value object for one proxied request: method, host, path, client, status, duration, source.

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RequestRecord {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;

    public static final String SOURCE_CACHE = "Cache";
    public static final String SOURCE_ORIGIN = "Origin";
    public static final String SOURCE_BLOCKED = "Blocked";
    public static final String SOURCE_TUNNEL = "Tunnel";

    private final Instant timestamp;
    private final String method;
    private final String host;
    private final int port;
    private final String path;
    private final String clientAddress;
    private final boolean blocked;
    private final Long durationMs;
    private final String source;

    public RequestRecord(Instant timestamp, String method, String host, int port, String path, String clientAddress) {
        this(timestamp, method, host, port, path, clientAddress, false, null, null);
    }

    public RequestRecord(Instant timestamp, String method, String host, int port, String path, String clientAddress, boolean blocked) {
        this(timestamp, method, host, port, path, clientAddress, blocked, null, null);
    }

    public RequestRecord(Instant timestamp, String method, String host, int port, String path, String clientAddress,
                         boolean blocked, Long durationMs, String source) {
        this.timestamp = timestamp;
        this.method = method;
        this.host = host;
        this.port = port;
        this.path = path;
        this.clientAddress = clientAddress;
        this.blocked = blocked;
        this.durationMs = durationMs;
        this.source = source;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getSource() {
        return source;
    }

    public String getTimeString() {
        return TIME_FMT.format(timestamp);
    }

    public String getHostPort() {
        return (port == PORT_HTTP || port == PORT_HTTPS) ? host : host + ":" + port;
    }
}
