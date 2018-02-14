package volley.android.com.toolbox;

import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import volley.android.com.AuthFailureError;
import volley.android.com.Cache;
import volley.android.com.ClientError;
import volley.android.com.Network;
import volley.android.com.NetworkError;
import volley.android.com.NetworkResponse;
import volley.android.com.NoConnectionError;
import volley.android.com.Request;
import volley.android.com.RetryPolicy;
import volley.android.com.ServerError;
import volley.android.com.TimeoutError;
import volley.android.com.VolleyError;
import volley.android.com.VolleyLog;

/**
 * Volley的网络接口实现,通过{@link HttpStack}完成底层操作
 */
public class BasicNetwork implements Network{
    protected static final boolean DEBUG = VolleyLog.DEBUG;

    /**
     * 我们给网络请求定义一个慢的时间，大于等于这个值我们就认为这个网络请求是很慢的
     */
    private static final int SLOW_REQUEST_THRESHOLD_MS = 3000;

    /**
     * 字节数组分配器缓冲区的默认大小
     */
    private static final int DEFAULT_POOL_SIZE = 4096;

    @Deprecated
    protected final HttpStack mHttpStack;

    private final BaseHttpStack mBaseHttpStack;

    protected final ByteArrayPool mPool;

    /**
     * 创建一个网络接口实现
     * @param httpStack 需要用来访问网络的http栈
     * @param pool 用来管理字节数组的缓冲池
     * @deprecated 该构造函数在未来会被废弃，请使用{@link #BasicNetwork(BaseHttpStack, ByteArrayPool)}
     */
    @Deprecated
    public BasicNetwork(HttpStack httpStack, ByteArrayPool pool) {
        mHttpStack = httpStack;
        mBaseHttpStack = new AdaptedHttpStack(httpStack);
        mPool = pool;
    }

    /**
     * 创建一个网络接口实现，使用默认大小的缓冲池，提高读写效率
     * @param httpStack 需要用来访问网络的http栈
     */
    public BasicNetwork(HttpStack httpStack) {
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * 创建一个网络接口实现，使用默认大小的缓冲池，提高读写效率
     * @param httpStack 需要用来访问网络的http栈
     */
    public BasicNetwork(BaseHttpStack httpStack) {
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * 创建一个网络接口实现
     * @param httpStack 需要用来访问网络的http栈
     * @param pool 用来管理字节数组的缓冲池
     */
    public BasicNetwork(BaseHttpStack httpStack, ByteArrayPool pool) {
        mBaseHttpStack = httpStack;
        //mHttpStack是为了兼容旧的版本，直接直接使用这个成员
        mHttpStack = httpStack;
        mPool = pool;
    }

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long requestStart = SystemClock.elapsedRealtime();

        while (true) {
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            List<Header> responseHeaders = Collections.emptyList();

            try {
                //收集请求头部
                Map<String, String> additionalRequestHeaders = getCacheHeaders(request.getCacheEntry());

                httpResponse = mBaseHttpStack.executeRequest(request, additionalRequestHeaders);
                int statusCode = httpResponse.getStatusCode();
                responseHeaders = httpResponse.getHeaders();

                //服务端返回资源未修改，我们要校验缓存
                if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    Cache.Entry entry = request.getCacheEntry();

                    //@FIXME 这个情况只有本地缓存被删掉了才会发生
                    if (entry == null) {
                        return new NetworkResponse(HttpURLConnection.HTTP_NOT_MODIFIED, null, true,
                                SystemClock.elapsedRealtime() - requestStart, responseHeaders);
                    }

                    //拼接请求的缓存实体中的头部字段和当前304响应的头部字段
                    List<Header> combinedHeaders = combineHeaders(responseHeaders, entry);

                    return new NetworkResponse(HttpURLConnection.HTTP_NOT_MODIFIED, entry.data,
                            true, SystemClock.elapsedRealtime() - requestStart, combinedHeaders);
                }

                InputStream inputStream = httpResponse.getContent();

                //有一些正常响应是没有正文内容的，例如204，我们需要检查这种情况
                if (inputStream != null) {
                    responseContents =
                            inputStreamToBytes(inputStream, httpResponse.getContentLength());
                } else {
                    //确实没有数据
                    responseContents = new byte[0];
                }

                //检查下请求时间，如果时间很长我们需要了解一下
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                logSlowRequests(requestLifetime, request, responseContents, statusCode);

                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }

                return new NetworkResponse(statusCode, responseContents, false,
                        SystemClock.elapsedRealtime() - requestStart, responseHeaders);
            } catch (SocketTimeoutException e) {
                //请求超时
                attemptRetryOnException("socket", request, new TimeoutError());
            } catch (MalformedURLException e) {
                //url不对,不需要重试
                throw new RuntimeException("Bad URL " + request.getUrl(), e);
            } catch (IOException e) {
                int statusCode;
                if (httpResponse != null) {
                    statusCode = httpResponse.getStatusCode();
                } else {
                    //无网络连接，不重试
                    throw new NoConnectionError(e);
                }

                VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());

                NetworkResponse networkResponse;
                if (responseContents != null) {
                    networkResponse = new NetworkResponse(statusCode, responseContents, false,
                            SystemClock.elapsedRealtime() - requestStart, responseHeaders);

                    //权限问题导致无法访问
                    if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        attemptRetryOnException("auth",
                                request, new AuthFailureError(networkResponse));
                    } else if (statusCode >= 400 && statusCode <= 499) {
                        //客户端问题，不重试
                        throw new ClientError(networkResponse);
                    } else if (statusCode >= 500 && statusCode <= 599) {
                        //服务端问题，可以重试
                        if (request.shouldRetryServerErrors()) {
                            attemptRetryOnException("server",
                                    request, new ServerError(networkResponse));
                        }
                    } else {
                        //可能是3xx，这个结果就不需要重试了
                        throw new ServerError(networkResponse);
                    }
                } else {
                    //连结果都没拿到，尝试重试
                    attemptRetryOnException("network", request, new NetworkError());
                }

            }
        }
    }

    /**
     * 获取缓存中的头部字段，以便发请求时带上客户端已有的信息
     * @param entry
     * @return
     */
    private Map<String, String> getCacheHeaders(Cache.Entry entry) {
        if (entry == null){
            return Collections.emptyMap();
        }

        Map<String, String> headers = new HashMap<>();

        if (entry.etag != null) {
            headers.put("If-None-Match", entry.etag);
        }

        if (entry.lastModified > 0) {
            headers.put("If-Modified-Since",
                    HttpHeaderParser.formatEpochAsRfc1123(entry.lastModified));
        }

        return headers;
    }


    /**
     * 拼接缓存实体的头部和304响应头的头部，304响应头的头部是不完整的
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
     * @param responseHeaders 304响应头的头部
     * @param entry 缓存实体
     * @return
     */
    private static List<Header> combineHeaders(List<Header> responseHeaders, Cache.Entry entry) {
        Set<String> headerNamesFromNetworkResponse = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        //1.首先响应头的头部字段
        if (!responseHeaders.isEmpty()) {
            for (Header header : responseHeaders) {
                headerNamesFromNetworkResponse.add(header.getName());
            }
        }

        //2.添加缓存实体中的头部字段
        List<Header> combinedHeaders = new ArrayList<>(responseHeaders);
        if (entry.allResponseHeaders != null) {
            if (!entry.allResponseHeaders.isEmpty()) {
                for (Header header : entry.allResponseHeaders) {
                    //排除掉响应头中已有的键的头部字段
                    if (!headerNamesFromNetworkResponse.contains(header.getName())) {
                        combinedHeaders.add(header);
                    }
                }
            }
        } else {
            if (!entry.responseHeaders.isEmpty()) {
                for (Map.Entry<String, String> header : entry.responseHeaders.entrySet()) {
                    if (!headerNamesFromNetworkResponse.contains(header.getKey())) {
                        combinedHeaders.add(new Header(header.getKey(), header.getValue()));
                    }
                }
            }
        }

        return combinedHeaders;
    }

    /**
     * 从输入流中读出数据，返回字节流
     * @param in 需要读数据的输入流
     * @param contentLength 要读取数据的长度
     * @return
     */
    private byte[] inputStreamToBytes(InputStream in, int contentLength) {
        PoolingByteArrayOutputStream bytes =
                new PoolingByteArrayOutputStream(mPool, contentLength);
    }

    /**
     * 若请求花费的时间特别长，超过{@link #SLOW_REQUEST_THRESHOLD_MS}，我们给它打印出来
     * @param requestLifetime
     * @param request
     * @param responseContents
     * @param statusCode
     */
    private void logSlowRequests(long requestLifetime, Request<?> request,
                                 byte[] responseContents, int statusCode) {
        if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
            VolleyLog.d("HTTP response for request=<%s> [lifetime=%d], [size=%s], " +
                            "[rc=%d], [retryCount=%s]", request, requestLifetime,
                    responseContents != null ? responseContents.length : "null",
                    statusCode, request.getRetryPolicy().getCurrentRetryCount());
        }
    }

    /**
     * 出现超时异常时重试，如果超过重试次数直接抛出异常
     *
     * NOTE: 这里的重试是这样的，这个attemptRetryOnException是样的{@link #performRequest(Request)} 执行请求的函数体是在一个while(true)里面
     * 因此，如果不抛出异常或者不返回结果，这个while循环就会一直执行，从而达到重试的目的
     *
     * @param logPrefix 输入log的前缀
     * @param request 当前执行的请求
     * @param exception 此刻出现的异常
     * @throws VolleyError
     */
    private static void attemptRetryOnException(String logPrefix, Request<?> request,
                                                VolleyError exception) throws VolleyError {
        //请求里面的重试策略
        RetryPolicy retryPolicy = request.getRetryPolicy();

        //当前的超时时间
        int oldTimeout = request.getTimeoutMs();

        try {
            retryPolicy.retry(exception);
        } catch (VolleyError e) {
            request.addMarker(
                    String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
    }
}
