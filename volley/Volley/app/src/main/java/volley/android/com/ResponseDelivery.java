package volley.android.com;

import volley.android.com.toolbox.Response;

/**
 * 请求响应的派发接口
 */
public interface ResponseDelivery {
    /**
     * 在网络或缓存派发线程中解析一个请求结果并派发
     * @param request 请求本身
     * @param response 请求完成后的响应结果
     */
    void postResponse(Request<?> request, Response<?> response);

    /**
     * 在网络或缓存派发线程中解析一个请求结果并派发
     * @param request 请求本身
     * @param response 请求完成后的响应结果
     * @param runnable 该函数派发完成后的回调
     */
    void postResponse(Request<?> request, Response<?> response, Runnable runnable);

    /**
     * 派发一个请求错误
     * @param request 请求本身
     * @param error 请求错误
     */
    void postError(Request<?> request, VolleyError error);
}
