package volley.android.com.toolbox;

import org.apache.http.HttpResponse;

import java.io.IOException;
import java.util.Map;

import volley.android.com.AuthFailureError;
import volley.android.com.Request;

/**
 * 一个抽象的HTTP栈
 * 这个接口已经废弃，它依赖了已经废弃的Apache http库，这个接口在未来可能会被干掉，建议不要使用
 *
 */
@Deprecated
public interface HttpStack {

    /**
     * 根据给出的参数执行一个http请求
     * 如果request.getPostBody() == null那请求就是GET请求，反之是POST请求
     * @param request 需要发起的请求
     * @param additionalHeaders 需要和请求一起发起的而外的请求头部,{@link Request#getHeaders()}里面已经有一部分头部字段了
     * @return
     * @throws IOException
     * @throws AuthFailureError
     */
    HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError;
}
