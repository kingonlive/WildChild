package volley.android.com;

/**
 * Volley库自定义错误
 */

public class VolleyError extends Exception {
    public final NetworkResponse networkResponse;

    /**
     * 处理 {@linkplain Request} 到出现异常花费的时间
     */
    private long networkTimeMs;

    public VolleyError() {
        networkResponse = null;
    }

    public VolleyError(NetworkResponse response) {
        networkResponse = response;
    }

    public VolleyError(String exceptionMessage) {
        super(exceptionMessage);
        networkResponse = null;
    }

    public VolleyError(String exceptionMessage, Throwable reason) {
        super(exceptionMessage, reason);
        networkResponse = null;
    }

    public VolleyError(Throwable cause) {
        super(cause);
        networkResponse = null;
    }

    /* package */ void setNetworkTimeMs(long networkTimeMs) {
        this.networkTimeMs = networkTimeMs;
    }

    public long getNetworkTimeMs() {
        return networkTimeMs;
    }
}
