package volley.android.com;

import volley.android.com.toolbox.Response;

/**
 * 所有网络请求的请求类
 * @param <T> 该请求预期的请求结果，例如字符串或者图像等
 */

public abstract class Request<T> implements Comparable<Request<T>>{

    /**
     * POST和PUT参数的默认编码格式
     */
    private static final String DEFAULT_PARAMS_ENCODING = "UTF-8";

    /**
     * 请求所支持的HTTP请求方法
     */
    public interface Method {
        int DEPRECATED_GET_OR_POST = -1;
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
        int HEAD = 4;
        int OPTIONS = 5;
        int TRACE = 6;
        int PATCH = 7;
    }

    /**
     * 网络请求完成回调接口
     */
    interface NetworkRequestCompleteListener{
        /**
         * 当收到一个请求响应时回调该函数
         * @param request 请求原本的对象
         * @param response 返回的请求响应
         */
        void onResponseReceived(Request<?> request, Response<?> response);

        /**
         * 请求结束但未收到请求响应时候回调该函数
         * @param request 请求原对象
         */
        void onNoUsableResponseReceived(Request<?> request);
    }
}
