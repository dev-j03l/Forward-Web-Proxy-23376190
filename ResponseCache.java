// In-memory HTTP response cache for GET requests. Bounded, thread-safe, LRU eviction.
// TTL from Cache-Control/Expires or default; no-store/private responses are not stored.

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResponseCache {

    private static final int DEFAULT_MAX_ENTRIES = 500;
    private static final long DEFAULT_TTL_SECONDS = 300;
    private static final long NO_CACHE_TTL_SECONDS = 60;
    private static final String CACHE_CONTROL = "cache-control";
    private static final String EXPIRES = "expires";
    private static final String MAX_AGE_PREFIX = "max-age=";

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long defaultTtlMillis;

    public ResponseCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_SECONDS);
    }

    public ResponseCache(int maxEntries, long defaultTtlSeconds) {
        this.maxEntries = maxEntries;
        this.defaultTtlMillis = defaultTtlSeconds * 1000;
    }

    public static String cacheKey(String host, int port, String path) {
        return host + "|" + port + "|" + (path != null ? path : "/");
    }

    public CacheEntry get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        entry.touch();
        return entry;
    }

    public void put(String key, CacheEntry entry) {
        cache.put(key, entry);
        evictIfNeeded();
    }

    private void evictIfNeeded() {
        if (cache.size() <= maxEntries) {
            return;
        }
        List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(cache.entrySet());
        entries.sort((a, b) -> {
            boolean aExp = a.getValue().isExpired();
            boolean bExp = b.getValue().isExpired();
            if (aExp != bExp) {
                return aExp ? -1 : 1;
            }
            return Long.compare(a.getValue().getLastAccessMillis(), b.getValue().getLastAccessMillis());
        });
        int toRemove = cache.size() - maxEntries;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            cache.remove(entries.get(i).getKey());
        }
    }

    public long computeExpiry(Map<String, String> responseHeaders) {
        String cacheControl = responseHeaders.get(CACHE_CONTROL);
        if (cacheControl != null) {
            String lower = cacheControl.toLowerCase();
            if (lower.contains("no-store") || lower.contains("private")) {
                return 0;
            }
            if (lower.contains("no-cache")) {
                return System.currentTimeMillis() + NO_CACHE_TTL_SECONDS * 1000L;
            }
            int maxAge = parseMaxAge(cacheControl);
            if (maxAge >= 0) {
                return System.currentTimeMillis() + maxAge * 1000L;
            }
        }
        String expires = responseHeaders.get(EXPIRES);
        if (expires != null) {
            try {
                long exp = java.time.ZonedDateTime.parse(expires,
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                if (exp > System.currentTimeMillis()) {
                    return exp;
                }
            } catch (Exception ignored) {
            }
        }
        return System.currentTimeMillis() + defaultTtlMillis;
    }

    private static int parseMaxAge(String cacheControl) {
        String lower = cacheControl.toLowerCase();
        int idx = lower.indexOf(MAX_AGE_PREFIX);
        if (idx < 0) {
            return -1;
        }
        int start = idx + MAX_AGE_PREFIX.length();
        int end = start;
        while (end < cacheControl.length()
                && (Character.isDigit(cacheControl.charAt(end)) || cacheControl.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(cacheControl.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int size() {
        return cache.size();
    }

    public static class CacheEntryInfo {
        private final String key;
        private final int bodySize;
        private final long expiryMillis;
        private final long lastAccessMillis;

        public CacheEntryInfo(String key, int bodySize, long expiryMillis, long lastAccessMillis) {
            this.key = key;
            this.bodySize = bodySize;
            this.expiryMillis = expiryMillis;
            this.lastAccessMillis = lastAccessMillis;
        }

        public String getKey() {
            return key;
        }

        public int getBodySize() {
            return bodySize;
        }

        public long getExpiryMillis() {
            return expiryMillis;
        }

        public long getLastAccessMillis() {
            return lastAccessMillis;
        }

        public String getDisplayUrl() {
            if (key == null) {
                return "";
            }
            int first = key.indexOf('|');
            int second = key.indexOf('|', first + 1);
            if (first < 0 || second < 0) {
                return key;
            }
            String hostPart = key.substring(0, first);
            String portPart = key.substring(first + 1, second);
            String pathPart = key.substring(second + 1);
            return hostPart + ":" + portPart + " " + pathPart;
        }
    }

    public List<CacheEntryInfo> getSnapshot() {
        List<CacheEntryInfo> list = new ArrayList<>();
        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            CacheEntry ent = e.getValue();
            list.add(new CacheEntryInfo(e.getKey(), ent.getBody().length, ent.getExpiryMillis(), ent.getLastAccessMillis()));
        }
        list.sort((a, b) -> Long.compare(b.getLastAccessMillis(), a.getLastAccessMillis()));
        return list;
    }
}
