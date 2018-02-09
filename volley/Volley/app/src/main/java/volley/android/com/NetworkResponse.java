package volley.android.com;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import volley.android.com.toolbox.Header;

/**
 * 从网络接口{@link Network#performRequest(Request)}　返回的响应数据
 */
public class NetworkResponse {
    /** HTTP状态码　*/
    public final int statusCode;

    /** HTTP响应数据　**/
    public final byte[] data;

    /**
     * HTTP响应头部，区分大小写
     * 注意:
     * 1.改集合不可修改
     * 2.若服务端返回的头部中有多个相同键的键值对，仅取最后一个键值对
     */
    public final Map<String, String> headers;

    /**
     * 所有的HTTP响应头部,该集合不可直接操作
     */
    public final List<Header> allHeaders;

    /**
     * true，若后台返回了304(无修改)
     */
    public final boolean notModified;

    /**
     * 网络往返时间(ms)
     */
    public final long networkTimeMs;

    /**
     * 创建一个请求响应
     * @param statusCode 服务端返回的HTTP状态码
     * @param data　服务端返回响应的内容
     * @param headers　服务端返回响应的头部
     * @param notModified　服务端返回304且响应数据已经被缓存
     * @param networkTimeMs 请求的往返时间
     * @deprecated 请对比 {@link #NetworkResponse(int, byte[], Map, boolean, long)}.
     *             这个构造器不处理后台返回的请求头部，若头部中存在多个相同健的键值对，将原样保留
     */
    public NetworkResponse(int statusCode, byte[] data, Map<String, String> headers,
                           boolean notModified, long networkTimeMs) {
        this(statusCode, data, headers, toAllHeaderList(headers), notModified, networkTimeMs);
    }

    /**
     * 创建一个请求响应
     * @param statusCode HTTP状态码
     * @param data 响应数据
     * @param notModified 服务端返回304且响应数据已经被缓存
     * @param networkTimeMs 请求往返时间
     * @param allHeaders 服务端返回的响应头部
     */
    public NetworkResponse(int statusCode, byte[] data, boolean notModified, long networkTimeMs,
                           List<Header> allHeaders) {
        this(statusCode, data, toHeaderMap(allHeaders), allHeaders, notModified, networkTimeMs);
    }

    /**
     * 创建一个响应
     * @param statusCode HTTP返回的状态码
     * @param data　响应数据
     * @param headers 响应的头部
     * @param notModified 若后台返回304且响应数据已经被缓存
     */
    public NetworkResponse(int statusCode, byte[] data, Map<String, String> headers,
                           boolean notModified) {
        this(statusCode, data, headers, notModified, 0);
    }

    /**
     * 返回一个不带头部的响应
     * @param data 响应的内容
     */
    public NetworkResponse(byte[] data) {
        this(HttpURLConnection.HTTP_OK, data, false, 0, Collections.<Header>emptyList());
    }

    /**
     * 创建一个服务端返回OK的响应
     * @param data 响应的内容
     * @param headers 响应的头部
     */
    public NetworkResponse(byte[] data, Map<String, String> headers) {
        this(HttpURLConnection.HTTP_OK, data, headers, false, 0);
    }

    private NetworkResponse(int statusCode, byte[] data, Map<String, String> headers,
                            List<Header> allHeaders, boolean notModified, long networkTimeMs) {
        this.statusCode = statusCode;
        this.data = data;
        this.headers = headers;
        if (allHeaders == null) {
            this.allHeaders = null;
        } else {
            this.allHeaders = Collections.unmodifiableList(allHeaders);
        }
        this.notModified = notModified;
        this.networkTimeMs = networkTimeMs;
    }

    private static Map<String, String> toHeaderMap(List<Header> allHeaders) {
        if (allHeaders == null) {
            return null;
        }
        if (allHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Later elements in the list take precedence.
        for (Header header : allHeaders) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    private static List<Header> toAllHeaderList(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        if (headers.isEmpty()) {
            return Collections.emptyList();
        }
        List<Header> allHeaders = new ArrayList<>(headers.size());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            allHeaders.add(new Header(header.getKey(), header.getValue()));
        }
        return allHeaders;
    }
}
