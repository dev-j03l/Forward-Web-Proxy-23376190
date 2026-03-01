// Thread-safe block list for URL/host rules. Supports full URLs (normalized to host or host/path)
// and subdomain matching. Used by the proxy to deny requests before forwarding.

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockList {

    private static final String PREFIX_HTTPS = "https://";
    private static final String PREFIX_HTTP = "http://";

    private final Set<String> rules = ConcurrentHashMap.newKeySet();

    public static String normalizeRule(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith(PREFIX_HTTPS)) {
            s = s.substring(PREFIX_HTTPS.length());
        } else if (s.startsWith(PREFIX_HTTP)) {
            s = s.substring(PREFIX_HTTP.length());
        }
        if (s.isEmpty()) {
            return null;
        }
        while (s.endsWith("/") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public String add(String rule) {
        String normalized = normalizeRule(rule);
        if (normalized == null) {
            return null;
        }
        rules.add(normalized);
        return normalized;
    }

    public void remove(String rule) {
        if (rule == null) {
            return;
        }
        String normalized = normalizeRule(rule);
        if (normalized != null) {
            rules.remove(normalized);
        }
        rules.remove(rule.trim().toLowerCase());
    }

    public boolean contains(String rule) {
        if (rule == null) {
            return false;
        }
        String normalized = normalizeRule(rule);
        return normalized != null && rules.contains(normalized);
    }

    private static boolean hostMatches(String requestHost, String ruleHost) {
        if (requestHost == null || ruleHost == null) {
            return false;
        }
        if (requestHost.equals(ruleHost)) {
            return true;
        }
        return requestHost.endsWith("." + ruleHost);
    }

    public boolean isBlocked(String host, String path) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        String p = (path == null || path.isEmpty()) ? "/" : path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        for (String rule : rules) {
            int slash = rule.indexOf('/');
            if (slash < 0) {
                if (hostMatches(h, rule)) {
                    return true;
                }
            } else {
                String ruleHost = rule.substring(0, slash);
                String rulePath = "/" + rule.substring(slash + 1);
                if (hostMatches(h, ruleHost) && (rulePath.equals("/") || p.startsWith(rulePath))) {
                    return true;
                }
            }
        }
        return false;
    }

    public String[] getAll() {
        return rules.toArray(new String[0]);
    }
}
