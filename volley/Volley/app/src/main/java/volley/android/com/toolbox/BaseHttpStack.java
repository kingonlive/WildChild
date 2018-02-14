package volley.android.com.toolbox;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import volley.android.com.AuthFailureError;
import volley.android.com.Request;

/**
 * HTTP栈抽象
 */
public abstract class BaseHttpStack implements HttpStack{

    /**
     * 子类重写它发起一个http请求
     * @param request 需要发起的请求
     * @param additionalHeaders 需要和请求一起发起的而外的请求头部,{@link Request#getHeaders()}里面已经有一部分头部字段了
     * @return 返回{@link HttpResponse}
     * @throws IOException
     * @throws AuthFailureError
     */
    public abstract HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError;

    /**
     * 这个函数现在没地方用到，写出来的原因是因为{@link BasicNetwork#mHttpStack}已经在之前的版本发布了，使用旧版本的volley库仍然依赖了这个函数.
     * @param request 需要发起的请求
     * @param additionalHeaders 需要和请求一起发起的而外的请求头部,{@link Request#getHeaders()}里面已经有一部分头部字段了
     * @return
     * @throws IOException
     * @throws AuthFailureError
     * @deprecated 由于依赖了废弃的Apache网络库,这个函数现在废弃了
     */
    public final org.apache.http.HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
        HttpResponse response = executeRequest(request, additionalHeaders);

        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        StatusLine statusLine = new BasicStatusLine(protocolVersion, response.getStatusCode(), "" /* reasonPhrase */);
        BasicHttpResponse apacheResponse = new BasicHttpResponse(statusLine);

        List<org.apache.http.Header> headers = new ArrayList<>();
        for (Header header : response.getHeaders()) {
            headers.add(new BasicHeader(header.getName(), header.getValue()));
        }
        apacheResponse.setHeaders(headers.toArray(new org.apache.http.Header[headers.size()]));

        InputStream responseStream = response.getContent();
        if (responseStream != null) {
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(responseStream);
            entity.setContentLength(response.getContentLength());
            apacheResponse.setEntity(entity);
        }

        return apacheResponse;
    }


}
