package volley.android.com;

/**
 * 网络异常
 */

public class NetworkError extends VolleyError{
    public NetworkError() {
        super();
    }

    public NetworkError(Throwable cause) {
        super(cause);
    }

    public NetworkError(NetworkResponse networkResponse) {
        super(networkResponse);
    }
}
