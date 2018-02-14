package volley.android.com;

/**
 * 服务端返回响应反馈客户端出错
 */
public class ClientError extends VolleyError{
    public ClientError(NetworkResponse networkResponse) {
        super(networkResponse);
    }

    public ClientError() {
        super();
    }
}
