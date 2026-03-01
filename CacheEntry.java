// Single cached HTTP response: status line, headers, body, expiry and last-access for LRU.

import java.util.List;

public class CacheEntry {

    private final String statusLine;
    private final List<String> headers;
    private final byte[] body;
    private final long expiryMillis;
    private volatile long lastAccessMillis;

    public CacheEntry(String statusLine, List<String> headers, byte[] body, long expiryMillis) {
        this.statusLine = statusLine;
        this.headers = headers;
        this.body = body;
        this.expiryMillis = expiryMillis;
        this.lastAccessMillis = System.currentTimeMillis();
    }

    public String getStatusLine() {
        return statusLine;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public long getExpiryMillis() {
        return expiryMillis;
    }

    public long getLastAccessMillis() {
        return lastAccessMillis;
    }

    public void touch() {
        this.lastAccessMillis = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryMillis;
    }
}
