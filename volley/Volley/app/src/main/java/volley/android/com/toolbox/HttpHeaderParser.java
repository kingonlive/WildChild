package volley.android.com.toolbox;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import volley.android.com.Cache;
import volley.android.com.NetworkResponse;

/**
 * HTTP头部解析器
 */

public class HttpHeaderParser {

    /**
     * 头部内容类型 Content-Type
     */
    static final String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * 默认字符集 ISO-8859-1
     */
    private static final String DEFAULT_CONTENT_CHARSET = "ISO-8859-1";

    /**
     * RFC1123 协议的日期格式
     */
    private static final String RFC1123_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * 新建一个RFC1123协议日期格式的{@link SimpleDateFormat}
     * @return
     */
    private static SimpleDateFormat newRfc1123Formatter() {
        SimpleDateFormat formatter =
                new SimpleDateFormat(RFC1123_FORMAT, Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        return formatter;
    }

    /**
     * 从{@link NetworkResponse } 构造出一个缓存项 Cache.Entry
     * @param response 待被解析的http请求响应
     * @return 返回解析出来的缓存项目，若该http响应无缓存则返回null
     */
    public static Cache.Entry parseCacheHeaders(NetworkResponse response){
        //当前时间
        long now = System.currentTimeMillis();

        //http请求结果返回的头部
        Map<String, String> headers = response.headers;

        //服务端返回结果的时间,来自 http响应头部的Date
        long serverDate = 0;

        //服务端返回该资源的最后修改时间,来自http响应头部的Last-Modified
        long lastModified = 0;

        //服务端返回该资源的过期时间(http 1.0)，来自http响应头部的Expires
        long serverExpires = 0;

        //服务端返回该资源的过期时间,当中了Cache-Control缓存逻辑，该时间是当前时间加上响应头部max-age时间
        long softExpire = 0;

        //最终缓存时间，当中了Cache-Control逻辑，则是softExpire，否则是基于serverExpires计算出来的时间
        long finalExpire = 0;

        //服务返回的该资源的有效时间，来自http响应头部的max-age
        long maxAge = 0;

        long staleWhileRevalidate = 0;

        //
        boolean hasCacheControl = false;
        boolean mustRevalidate = false;

        String serverEtag = null;
        String headerValue;
    }


}
