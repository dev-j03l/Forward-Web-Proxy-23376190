import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable record of a single request through the proxy.
 */
public class RequestRecord {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final String method;
    private final String host;
    private final int port;
    private final String path;
    private final String clientAddress;

    public RequestRecord(Instant timestamp, String method, String host, int port, String path, String clientAddress) {
        this.timestamp = timestamp;
        this.method = method;
        this.host = host;
        this.port = port;
        this.path = path;
        this.clientAddress = clientAddress;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getMethod() { return method; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public String getClientAddress() { return clientAddress; }

    public String getTimeString() {
        return TIME_FMT.format(timestamp);
    }

    public String getHostPort() {
        return (port == 80 || port == 443) ? host : host + ":" + port;
    }
}
