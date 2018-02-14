package volley.android.com;

/**
 * 服务端错误(5xx)
 */
public class ServerError extends VolleyError{
    public ServerError(NetworkResponse networkResponse) {
        super(networkResponse);
    }

    public ServerError() {
        super();
    }
}
