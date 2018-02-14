package volley.android.com.toolbox;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import volley.android.com.AuthFailureError;
import volley.android.com.Request;

/**
 * {@link BaseHttpStack}的实现，包装了一个 {@link HttpStack}
 *
 * <p> 正常情况{@link BasicNetwork}使用{@link BaseHttpStack}来构造一个实例，若构造时使用了{@link HttpStack}，那需要我们这个类来做适配 </p>
 */
public class AdaptedHttpStack extends BaseHttpStack {

    private final HttpStack mHttpStack;

    AdaptedHttpStack(HttpStack httpStack) {
        mHttpStack = httpStack;
    }

    /**
     * 通过apache网络库指向请求，并将结果转换成volley定义的响应结构HttpResponse
     * @param request 需要发起的请求
     * @param additionalHeaders 需要和请求一起发起的而外的请求头部,{@link Request#getHeaders()}里面已经有一部分头部字段了
     * @return {@link HttpResponse}
     * @throws IOException
     * @throws AuthFailureError
     */
    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
        org.apache.http.HttpResponse apacheResp;

        try {
            apacheResp = mHttpStack.performRequest(request, additionalHeaders);
        } catch (ConnectTimeoutException e) {
            // ConnectTimeoutException是apche网络库的错误，这里包装一下让BasicNetwork能够处理
            throw new SocketTimeoutException(e.getMessage());
        }

        int statusCode = apacheResp.getStatusLine().getStatusCode();
        org.apache.http.Header[] headers = apacheResp.getAllHeaders();

        List<Header> headerList = new ArrayList<>(headers.length);
        for (org.apache.http.Header header : headers) {
            headerList.add(new Header(header.getName(), header.getValue()));
        }

        //响应正文为空
        if (apacheResp.getEntity() == null) {
            return new HttpResponse(statusCode, headerList);
        }

        //响应正文长度
        long contentLength = apacheResp.getEntity().getContentLength();

        if ((int) contentLength != contentLength) {
            throw new IOException("Response too large: " + contentLength);
        }

        //构建volley库的响应
        return new HttpResponse(
                statusCode,
                headerList,
                (int) apacheResp.getEntity().getContentLength(),
                apacheResp.getEntity().getContent());
    }
}
