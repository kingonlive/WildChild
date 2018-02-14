package volley.android.com.toolbox;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 来自服务端的http响应
 */
public class HttpResponse {
    private final int mStatusCode;
    private final List<Header> mHeaders;
    private final int mContentLength;
    private final InputStream mContent;

    /**
     * 创建一个内容(body)是空的http响应
     * @param statusCode 该响应对应的状态码
     * @param headers 该响应的响应头部
     */
    public HttpResponse(int statusCode, List<Header> headers) {
        this(statusCode, headers, -1 /* contentLength */, null /* content */);
    }

    /**
     * 创建一个http响应
     * @param statusCode 该响应的状态码
     * @param headers 该响应的响应头部
     * @param contentLength 响应正文的长度，如果内容是空则忽略这个值
     * @param content 响应正文的输入流，若传空表示内容区是空
     */
    public HttpResponse(
            int statusCode, List<Header> headers, int contentLength, InputStream content) {
        mStatusCode = statusCode;
        mHeaders = headers;
        mContentLength = contentLength;
        mContent = content;
    }

    /**
     * 返回该响应的状态码
     * @return
     */
    public final int getStatusCode() {
        return mStatusCode;
    }

    /**
     * 返回该响应的响应头部
     * @return 返回响应头部集合，该集合不可操作
     */
    public final List<Header> getHeaders() {
        return Collections.unmodifiableList(mHeaders);
    }

    /**
     * 返回内容的长度，仅当内容有数据时有效
     * @return
     */
    public final int getContentLength() {
        return mContentLength;
    }

    /**
     * 返回正文的输入流
     * @return
     */
    public final InputStream getContent() {
        return mContent;
    }

}
