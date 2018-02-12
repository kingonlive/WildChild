package volley.android.com;

import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;

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

    /**
     * 一个用于调试的事件日志,记录当前请求的整个生命周期
     */
    private final VolleyLog.MarkerLog mEventLog = VolleyLog.MarkerLog.ENABLED ? new VolleyLog.MarkerLog() : null;

    /**
     * 当前请求的请求方法,当前支持的方法是GET/POST/PUT/DELETE/HEAD/OPTIONS/TRACE/PATCH
     */
    private final int mMethod;

    /**
     * 当前请求的url
     */
    private final String mUrl;

    /**
     * 流量统计的默认标签,标签的用法见{@link TrafficStats}
     */
    private final int mDefaultTrafficStatsTag;

    /**
     * 对象锁,当请求对象被添加到队列中,该锁决定谁可以操作这个对象
     */
    private final Object mLock = new Object();

    /**
     * 请求出错的监听器
     */
    private Response.ErrorListener mErrorListener;

    /**
     * 该请求对象的序列号,用来给{@link Comparable#compareTo(Object)}来排序,该值递增，因此顺序会是FIFO
     */
    private Integer mSequence;

    /**
     * 该请求所处的请求队列
     */
    private RequestQueue mRequestQueue;

    /**
     * 该请求对应的响应结果是否允许被缓存
     */
    private boolean mShouldCache = true;

    /**
     * 该请求是否被取消
     */
    private boolean mCanceled = false;

    /**
     * 该请求对应的响应结果是否已经被派发
     */
    private boolean mResponseDelivered = false;

    /**
     * 当出现服务端错误时(5xx),该请求是否要重试
     */
    private boolean mShouldRetryServerErrors = false;

    /**
     * 当前请求的重试策略
     */
    private RetryPolicy mRetryPolicy;

    /**
     * 当一个请求响应能够从本地缓存获取但http协议要求该缓存必须再确认是否可用时,缓存存储在这里.
     * 当下一次http响应返回的是"Not Modified"时,该缓存可用．
     */
    private Cache.Entry mCacheEntry = null;

    /**
     * 该请求的一个TAG,跟我们Adapter里面View的Tag是相同的作用(在volley里面可用根据Tag来取消某些请求)
     */
    private Object mTag;

    /**
     * 请求完成的回调接口
     */
    private NetworkRequestCompleteListener mRequestCompleteListener;

    /**
     * 用url和Response.ErrorListener两个参数创建一个request请求,这里没有请求完成的回调,是因为设计者把请求完成回调放到了子类，子类更清除怎么解析一个请求结果
     * @param url　要请求的url
     * @param listener　请求出错时的回调接口
     * @deprecated 这个类已不建议使用,建议使用{@link #Request(int, String, com.android.volley.Response.ErrorListener)}.
     */
    @Deprecated
    public Request(String url, Response.ErrorListener listener) {
        this(Method.DEPRECATED_GET_OR_POST, url, listener);
    }

    public Request(int method, String url, Response.ErrorListener listener) {
        mMethod = method;
        mUrl = url;
        mErrorListener = listener;
        setRetryPolicy(new DefaultRetryPolicy());

        mDefaultTrafficStatsTag = findDefaultTrafficStatsTag(url);
    }

    /**
     * 设置一个而重试策略
     * @param retryPolicy　希望在该请求使用的重试策略
     * @return 返回请求对象本身，方便使用者使用链式调用
     */
    public Request<?> setRetryPolicy(RetryPolicy retryPolicy) {
        mRetryPolicy = retryPolicy;
        return this;
    }

    /**
     * 返回一个用于流量统计的默认标签,返回值是url对应主机名的hashcode
     * @param url
     * @return
     */
    private static int findDefaultTrafficStatsTag(String url) {
        if (!TextUtils.isEmpty(url)) {
            Uri uri = Uri.parse(url);
            if (uri != null) {
                String host = uri.getHost();
                if (host != null) {
                    return host.hashCode();
                }
            }
        }
        return 0;
    }

    /**
     * 返回当前请求的http方法
     * @return
     */
    public int getMethod() {
        return mMethod;
    }

    /**
     * 设置该请求的TAG
     * @param tag
     * @return
     */
    public Request<?> setTag(Object tag) {
        mTag = tag;
        return this;
    }

    /**
     * 返回该请求的TAG
     * @return
     */
    public Object getTag() {
        return mTag;
    }

    public Response.ErrorListener getErrorListener() {
        return mErrorListener;
    }

    /**
     * 返回用于流量统计的tag标签
     * @return
     */
    public int getTrafficStatsTag() {
        return mDefaultTrafficStatsTag;
    }

    /**
     * 添加一个跟踪日志请求信息的tag
     * @param tag
     */
    public void addMarker(String tag){
        if (VolleyLog.MarkerLog.ENABLED){
            mEventLog.add(tag, Thread.currentThread().getId());
        }
    }

    /**
     * 通知请求队列该请求已经完成(包括出错),同时打印出来该请求所有的路径信息便于调试
     * @param tag
     */
    void finish(final String tag){
        if (mRequestQueue != null){
            mRequestQueue.finish(this);
        }

        if (VolleyLog.MarkerLog.ENABLED){
            final long threadID = Thread.currentThread().getId();
            if (Looper.myLooper() != Looper.getMainLooper()){
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mEventLog.add(tag, threadID);
                        mEventLog.finish(Request.this.toString());
                    }
                });
                return;
            }

            mEventLog.add(tag, threadID);
            mEventLog.finish(toString());
        }
    }

    /**
     * 关联该请求对应的请求队列,当该请求完成时会通知到该请求队列
     * @param requestQueue 请求队列
     * @return 返回请求对象本身，便于链式调用
     */
    public Request<?> setRequestQueue(RequestQueue requestQueue) {
        mRequestQueue = requestQueue;
        return this;
    }

    /**
     * 设置该请求的序列号，该需要号用于 {@link RequestQueue}
     * @param sequence
     * @return 返回对象本身，便于链式调用
     */
    public final Request<?> setSequence(int sequence) {
        mSequence = sequence;
        return this;
    }

    /**
     * 返回该请求的序列号
     * @return
     */
    public final int getSequence() {
        if (mSequence == null) {
            throw new IllegalStateException("getSequence called before setSequence");
        }

        return mSequence;
    }

    /**
     * 返回该请求的url
     * @return
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * 返回该请求的缓存健,默认情况下返回的是该请求的url
     * @return
     */
    public String getCacheKey() {
        return getUrl();
    }

    /**
     * 给该请求设置一个被标记成过期但正等待刷新结果(http的not modified)的缓存
     * @param entry
     * @return
     */
    public Request<?> setCacheEntry(Cache.Entry entry) {
        mCacheEntry = entry;
        return this;
    }

    /**
     * 返回该请求先前被识别成已过期的缓存响应
     * @return 有则返回否则返回null
     */
    public Cache.Entry getCacheEntry() {
        return mCacheEntry;
    }

    /**
     * 取消该请求
     * 该函数被调用后将不会有任何回调发生，但必须确保以下两点中的任何一点
     * <p>
     *     <ul>
     *         <li>1.这个函数的调用线程必须和调用 {@link ResponseDelivery}接口内函数的线程是同一个.默认情况下是主线程.
     *         <li>2.重写cancel()的子类在cancel()被调之后不要调用 {@link #deliverResponse}
     *     </ul>
     * </p>
     */
    public void cancel(){
        synchronized (mLock){
            mCanceled = true;
            mErrorListener = null;
        }
    }

    /**
     * 返回该请求是否已被取消
     * @return
     */
    public boolean isCanceled() {
        synchronized (mLock){
            return mCanceled;
        }
    }

    /**
     * 返回该请求对应的头部信息，若需要调用者认证身份则该调用会直接抛出{@link AuthFailureError}错误
     * @return　返回该请求对应的头部信息
     * @throws AuthFailureError
     */
    public Map<String, String> getHeaders() throws AuthFailureError {
        return Collections.emptyMap();
    }

    /**
     * 返回一个用于POST/PUT方法的Map集合,若要求调用者认证过身份该调用会抛出{@link AuthFailureError}错误
     * <p>
     *     客户端可以直接使用{@link #getBody()}反回自定义数据
     * </p>
     * @return
     * @throws AuthFailureError
     */
    protected Map<String, String> getParams() throws AuthFailureError {
        return null;
    }

    /**
     * 返回一个用于POST方法的Map集合,若要求调用者认证过身份该调用会抛出{@link AuthFailureError}错误
     * @return
     * @throws AuthFailureError
     */
    @Deprecated
    protected Map<String, String> getPostParams() throws AuthFailureError {
        return getParams();
    }

    /**
     * 返回PUT/POST方法参数的编码格式
     * @return
     */
    protected String getParamsEncoding() {
        return DEFAULT_PARAMS_ENCODING;
    }

    /**
     * 返回POST方法参数的编码格式
     * @return
     */
    @Deprecated
    protected String getPostParamsEncoding() {
        return getParamsEncoding();
    }

    /**
     * 该函数弃用,用{@link #getBodyContentType()}
     * @return
     */
    @Deprecated
    public String getPostBodyContentType() {
        return getBodyContentType();
    }

    /**
     * 返回body内容类型,用于http头部的Content-Type字段
     * @return
     */
    public String getBodyContentType() {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
    }

    /**
     * 返回指定编码格式进行编码的url参数
     * @param params　需要被编码的url参数
     * @param paramsEncoding 编码格式
     * @return 返回url参数的字节数组
     */
    private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    /**
     * 返回POST方法的body内容,该函数已废弃，用{@link #getBody()}
     * @return
     * @throws AuthFailureError
     */
    public byte[] getPostBody() throws AuthFailureError {
        Map<String, String> postParams = getPostParams();
        if (postParams != null && postParams.size() > 0) {
            return encodeParameters(postParams, getPostParamsEncoding());
        }
        return null;
    }

    /**
     * 返回PUT/POST方法的body内容,默认返回的格式是application/x-www-form-urlencoded,若要自定义格式，需重载{@link #getBodyContentType()}方法
     * @return
     * @throws AuthFailureError
     */
    public byte[] getBody() throws AuthFailureError {
        Map<String, String> params = getParams();
        if (params != null && params.size() > 0) {
            return encodeParameters(params, getParamsEncoding());
        }
        return null;
    }

    /**
     * 指定该请求是否可以缓存
     * @param shouldCache
     * @return
     */
    public final Request<?> setShouldCache(boolean shouldCache) {
        mShouldCache = shouldCache;
        return this;
    }

    /**
     * 返回该请求是否可以缓存
     * @return
     */
    public final boolean shouldCache() {
        return mShouldCache;
    }

    /**
     *　指定该请求在收到5xx错误的时候是否要重试
     * @param shouldRetryServerErrors
     * @return
     */
    public final Request<?> setShouldRetryServerErrors(boolean shouldRetryServerErrors) {
        mShouldRetryServerErrors = shouldRetryServerErrors;
        return this;
    }

    /**
     * 在服务器出错(收到5xx)时是否要重试
     * @return
     */
    public final boolean shouldRetryServerErrors() {
        return mShouldRetryServerErrors;
    }

    /**
     * 请求在请求队列中的优先级定义,相同优先级情况下会看谁先插入到嘟列
     */
    public enum Priority {
        /**
         * 低优先级
         */
        LOW,

        /**
         * 普通优先级,默认都是低优先级
         */
        NORMAL,

        /**
         * 高优先级
         */
        HIGH,

        /**
         * 立刻(最高优先级)
         */
        IMMEDIATE
    }

    /**
     * 返回该请求的优先级，默认优先级是{@link Priority#NORMAL}
     * @return
     */
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    /**
     * 返回请求的当前超时时间,超时时间会随着每次的重试次数而增加,若重试次数用完会导致抛出{@link TimeoutError}错误
     * @return
     */
    public final int getTimeoutMs() {
        return mRetryPolicy.getCurrentTimeout();
    }

    /**
     * 返回该请求的重试策略
     * @return
     */
    public RetryPolicy getRetryPolicy() {
        return mRetryPolicy;
    }

    /**
     * 标记该请求的响应已经派发
     */
    public void markDelivered() {
        synchronized (mLock) {
            mResponseDelivered = true;
        }
    }

    /**
     * 该请求对应的响应是否已经派发
     * @return ture表示请求响应已经派发过了
     */
    public boolean hasHadResponseDelivered() {
        synchronized (mLock) {
            return mResponseDelivered;
        }
    }

    /**
     * 子类需要重写这个方法将网络请求响应中的结果解析成合适的Response子类型.这个函数会在工作线程被调用,如果直接返回null会导致无法解析响应结果
     * @param response 网路请求返回的响应结果
     * @return 返回解析过后的响应结果,若内部出现错误则返回null
     */
    abstract protected Response<T> parseNetworkResponse(NetworkResponse response);

    /**
     * 子类可以重写这个方法,返回一个更加具体的错误,默认情况下会返回错误本身
     * @param volleyError 访问网络出现的错误
     * @return 应该根据请求类型返回一个更加具体的错误,方便确认问题
     */
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        return volleyError;
    }

    /**
     * 子类实现这个函数来派发解析过的结果,该函数默认在UI线程执行,如果要改请看下{@link ExecutorDelivery}
     * @param response
     */
    abstract protected void deliverResponse(T response);

    /**
     * 派发出错信息给{@link Response.ErrorListener}
     * @param error
     */
    public void deliverError(VolleyError error) {
        Response.ErrorListener listener;
        synchronized (mLock){
            listener = mErrorListener;
        }
        listener.onErrorResponse(error);
    }

    /**
     * {@link NetworkRequestCompleteListener}接口会在网络请求结束的时候收到回调
     * @param requestCompleteListener
     */
    /* package */ void setNetworkRequestCompleteListener(
            NetworkRequestCompleteListener requestCompleteListener) {
        synchronized (mLock) {
            mRequestCompleteListener = requestCompleteListener;
        }
    }

    /**
     * 通知　mRequestCompleteListener下载已经完成
     * @param response
     */
    /* package */ void notifyListenerResponseReceived(Response<?> response) {
        NetworkRequestCompleteListener listener;
        synchronized (mLock) {
            listener = mRequestCompleteListener;
        }
        if (listener != null) {
            listener.onResponseReceived(this, response);
        }
    }

    /**
     * 通知mRequestCompleteListener,未来拿到响应结果
     */
    /* package */ void notifyListenerResponseNotUsable() {
        NetworkRequestCompleteListener listener;
        synchronized (mLock) {
            listener = mRequestCompleteListener;
        }
        if (listener != null) {
            listener.onNoUsableResponseReceived(this);
        }
    }

    /**
     * 1.对比先优先级,优先级高的在队列之前
     * 2.优先级相同情况下,先进队列的在前
     * @param other
     * @return
     */
    @Override
    public int compareTo(Request<T> other) {
        Priority left = this.getPriority();
        Priority right = other.getPriority();

        // High-priority requests are "lesser" so they are sorted to the front.
        // Equal priorities are sorted by sequence number to provide FIFO ordering.
        return left == right ?
                this.mSequence - other.mSequence :
                right.ordinal() - left.ordinal();
    }

    @Override
    public String toString() {
        String trafficStatsTag = "0x" + Integer.toHexString(getTrafficStatsTag());
        return (mCanceled ? "[X] " : "[ ] ") + getUrl() + " " + trafficStatsTag + " "
                + getPriority() + " " + mSequence;
    }
}
