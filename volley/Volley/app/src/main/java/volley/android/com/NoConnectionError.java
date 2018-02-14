package volley.android.com;

/**
 * 当发起请求时无连接错误
 */
public class NoConnectionError extends VolleyError {

    public NoConnectionError() {
        super();
    }

    public NoConnectionError(Throwable reason) {
        super(reason);
    }
}
