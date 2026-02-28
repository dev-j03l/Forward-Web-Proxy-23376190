/**
 * Listener notified when the proxy handles a request.
 */
public interface RequestListener {
    void onRequest(RequestRecord record);
}
