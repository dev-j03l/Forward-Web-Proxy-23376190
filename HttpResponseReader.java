// Parses an HTTP response from a stream: status line, headers, body (Content-Length or chunked).

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpResponseReader {

    private static final int CHUNKED_MARKER = -2;
    private static final int BUFFER_SIZE = 8192;
    private static final String CHARSET = StandardCharsets.ISO_8859_1.name();
    private static final String TRANSFER_ENCODING = "transfer-encoding";
    private static final String CONTENT_LENGTH = "content-length";

    public static class Response {
        public final String statusLine;
        public final List<String> headerLines;
        public final Map<String, String> headers;
        public final byte[] body;

        Response(String statusLine, List<String> headerLines, Map<String, String> headers, byte[] body) {
            this.statusLine = statusLine;
            this.headerLines = headerLines;
            this.headers = headers;
            this.body = body;
        }
    }

    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return buf.toString(CHARSET);
    }

    public static Response readResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isEmpty()) {
            return null;
        }
        List<String> headerLines = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            headerLines.add(line);
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }
        int contentLength = resolveBodyLength(headers);
        byte[] body = readBody(in, contentLength);
        return new Response(statusLine, headerLines, headers, body);
    }

    private static int resolveBodyLength(Map<String, String> headers) {
        String te = headers.get(TRANSFER_ENCODING);
        if (te != null && te.toLowerCase().contains("chunked")) {
            return CHUNKED_MARKER;
        }
        String cl = headers.get(CONTENT_LENGTH);
        if (cl != null) {
            try {
                return Integer.parseInt(cl.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static byte[] readBody(InputStream in, int contentLength) throws IOException {
        if (contentLength == 0) {
            return new byte[0];
        }
        if (contentLength > 0) {
            return readExactly(in, contentLength);
        }
        if (contentLength == CHUNKED_MARKER) {
            return readChunkedBody(in);
        }
        return readUntilClose(in);
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r <= 0) {
                break;
            }
            off += r;
        }
        return buf;
    }

    private static byte[] readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                break;
            }
            int semicolon = sizeLine.indexOf(';');
            String sizeStr = semicolon >= 0 ? sizeLine.substring(0, semicolon).trim() : sizeLine.trim();
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (chunkSize == 0) {
                break;
            }
            byte[] chunk = readExactly(in, chunkSize);
            out.write(chunk);
            readLine(in);
        }
        return out.toByteArray();
    }

    private static byte[] readUntilClose(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
