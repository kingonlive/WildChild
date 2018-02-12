package volley.android.com.toolbox;

import volley.android.com.Cache;
import volley.android.com.VolleyError;

/**
 * 即将分派的请求响应，封装了解析过的请求结果
 * @param <T> 解析后的请求结果数据
 */
public class Response<T> {

    /**
     * 解析过后的请求结果，若请求结果出错则改值为null
     */
    public final T result;

    /**
     * 该请求结果的缓存实体,内有请求结果的元数据，若请求结果出错则该值为null
     */
    public final Cache.Entry cacheEntry;

    /**
     * 请求出错时候该值存储详细的错误信息
     */
    public final VolleyError error;

    /**
     * 当前该请求结果有效，但该结果需被后台更新
     */
    public boolean intermediate = false;

    private Response(T result, Cache.Entry cacheEntry) {
        this.result = result;
        this.cacheEntry = cacheEntry;
        this.error = null;
    }

    private Response(VolleyError error) {
        this.result = null;
        this.cacheEntry = null;
        this.error = error;
    }

    /**
     * 派发请求结果的回调
     * @param <T> 解析过后的请求结果
     */
    public interface Listener<T>{
        /**
         * 请求结果收到时的回调
         * @param response 解析过后的请求结果
         */
        void onResponse(T response);
    }

    /**
     * 请求结果出错回调
     */
    public interface ErrorListener{
        /**
         * 返回请求结果出错
         * @param error 封装了错误码及请求错误信息
         */
        void onErrorResponse(VolleyError error);
    }

    /**
     * 返回一个表示解析成功的请求结果
     * @param result 解析过后的请求结果
     * @param cacheEntry  需要被缓存的元数据
     * @param <T> 请求结果的类型
     * @return
     */
    public static <T> Response<T> success(T result, Cache.Entry cacheEntry){
        return new Response<T>(result, cacheEntry);
    }

    /**
     * 返回一个表示请求结果错误的请求结果
     * @param error 出错的详细信息，包括错误码和错误信息
     * @param <T> 原本希望返回的请求结果数据类型
     * @return
     */
    public static <T> Response<T> error(VolleyError error){
        return new Response<T>(error);
    }

    /**
     * 返回请求结果是否成功
     * @return
     */
    public boolean isSuccess() {
        return error == null;
    }
}
