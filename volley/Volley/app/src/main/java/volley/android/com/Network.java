package volley.android.com;

/**
 * 网络请求接口
 */
public interface Network {

    /**
     * 发起一个网络请求
     * @param request 需要发起的网络请求
     * @return 返回请求结果，不会为null
     * @throws VolleyError 出错时候
     */
    NetworkResponse performRequest(Request<?> request) throws VolleyError;
}
