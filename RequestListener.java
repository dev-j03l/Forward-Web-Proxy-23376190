// Callback interface for the proxy to report each completed request (with timing and source).

public interface RequestListener {
    void onRequest(RequestRecord record);
}
