package volley.android.com.toolbox;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import volley.android.com.Cache;
import volley.android.com.NetworkResponse;
import volley.android.com.VolleyLog;

/**
 * HTTP头部解析器
 * 学习者备注：
 * 头部解析的时候缓存部分逻辑和http协议的缓存逻辑是想对应的，这里梳理一下http的缓存逻辑
 * http协议在1.0和1.1都提供了响应的缓存机制,这里梳理如下:
 *
 * http 1.0/1.1的缓存设计
 *  1. 服务端响应头 [expired:日期] : 该资源在服务端的过期时间,
 *  2. 服务端响应头 [Cache-Control: max-age=xxx]: 该资源距离当前的过期时间
 *
 *  3. 客户端请求头 [If-Modified-Since: 日期] : 和 响应头的 [Last-Modified:日期] 配合，若缓存可用则返回304
 *  4. 客户端请求头 [If-Not-Match-Since: tag] : 和 响应头的 [ETag],若缓存可用则返回304
 *
 *  5. 服务端响应头 [Cache-Control: no-store] : 不使用缓存
 *  6. 服务端响应头 [Cache-Control: no-cache] : 可以缓存，但使用前需要到服务端验证，无论缓存是否过期
 *  7. 服务端响应头 [Cache-Control: must-revalidate] : 可以缓存，过期后必须去服务端验证新鲜度
 *  8. 服务端响应头 [Cache-Control: stale-while-revalidate=xx] 缓存过期后，仍然可以使用xx时间(秒)
 *
 *  例如:
 *  Cache-Control: max-age=600, stale-while-revalidate=30
 *  (https://tools.ietf.org/html/rfc5861)
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

        //缓存在服务端过期后，本地仍然可以忍受的
        long staleWhileRevalidate = 0;

        //响应头是否有Cache-Control
        boolean hasCacheControl = false;

        //缓存过期后必须刷新
        boolean mustRevalidate = false;

        //服务端资源你的Etag (资源哈希值)
        String serverEtag = null;

        String headerValue;

        headerValue = headers.get("Date");
        if (headerValue != null){
            //解析响应返回时间
            serverDate = parseDateAsEpoch(headerValue);
        }

        headerValue = headers.get("Cache-Control");
        if (headerValue != null){
            hasCacheControl = true;
            String[] tokens = headerValue.split(",");

            for (int i = 0 ; i < tokens.length ; i++){
                String token = tokens[i];

                if (token.equals("no-cache") || token.equals("no store")){
                    //不给我们本地缓存以及无论缓存与否都要到服务端验证，我们还缓存个毛线啊
                    return null;
                } else if (token.startsWith("max-age=")){
                    //缓存的有效时间
                    try {
                        maxAge = Long.parseLong(token.substring(8));
                    }catch (Exception e){}

                } else if (token.startsWith("stale-while-revalidate=")){
                    //缓存可以用，过期之后也能用一段时间
                    try {
                        staleWhileRevalidate = Long.parseLong(token.substring(23));
                    }catch (Exception e){}
                } else if (token.equals("must-revalidate") || token.equals("proxy-revalidate")){
                    //缓存可以用，但是过期之后必须到后台去刷新缓存
                    mustRevalidate = true;
                }
            }
        }

        headerValue = headers.get("Expires");
        if (headerValue != null){
            serverExpires = parseDateAsEpoch(headerValue);
        }

        headerValue = headers.get("Last-Modified");
        if (headerValue != null){
            lastModified = parseDateAsEpoch(headerValue);
        }

        headerValue = headers.get("Etag");
        if (headerValue != null){
            serverEtag = headerValue;
        }

        //Cache-Control的优先级要比Expired的优先级要高
        if (hasCacheControl) {
            //缓存过期过期时间
            softExpire = now + maxAge * 1000;

            //must-revalidate 指缓存过期就不能用了，否则可再用多stale-while-revalidate指定的时间
            finalExpire = mustRevalidate ? softExpire : staleWhileRevalidate * 1000 + softExpire;

        } else if (serverDate > 0 && serverExpires >= serverDate){
            //计算只有Expired时的过期时间，服务端时间和本地时间可能不同，这样计算是靠谱的
            softExpire = now + (serverExpires - serverDate);

            finalExpire = softExpire;
        }

        Cache.Entry entry = new Cache.Entry();
        //响应正文的原始数据
        entry.data = response.data;
        entry.etag = serverEtag;
        entry.softTtl = softExpire;
        entry.ttl = finalExpire;
        entry.serverDate = serverDate;
        entry.lastModified = lastModified;
        entry.allResponseHeaders = response.allHeaders;
        entry.responseHeaders = response.headers;

        return entry;
    }

    /**
     * 解析RFC1123格式的时间，返回时间戳
     * @param dateStr
     * @return
     */
    public static long parseDateAsEpoch(String dateStr){
        try {
            return newRfc1123Formatter().parse(dateStr).getTime();
        } catch (ParseException e) {
            //日期格式不对
            VolleyLog.e(e, "Unable to parse dateStr: %s, falling back to 0", dateStr);
            return 0;
        }
    }

    /**
     * 返回头部的中的字符集
     * @param headers 需要找字符集字段的Map集合
     * @param defaultCharset 在找不到字符集时该函数返回的默认字符集
     * @return
     *
     * Content-Type一般是这样的: Content-Type:text/html;charset=ISO-8859-1
     */
    public static String parseCharset(Map<String, String> headers, String defaultCharset){
        String contentType = headers.get(HEADER_CONTENT_TYPE);
        if (contentType != null){
            String[] params = contentType.split(";");
            for (int i = 1 ; i < params.length ; i++){
                String[] pairs = params[i].split("=");
                if (pairs.length == 2){
                    if (pairs[0].equals("charset")){
                        return pairs[1];
                    }
                }
            }

        }

        return defaultCharset;
    }

    /**
     * 返回头部字段中Content-Type的字符集
     * @param headers http头部
     * @return 返回字符集,若头部中没有指定字符集,则使用http协议的默认字符集 ISO-8859-1
     */
    public static String parseCharset(Map<String, String> headers){
        return parseCharset(headers, DEFAULT_CONTENT_CHARSET);
    }

    static List<Header> toAllHeaderList(Map<String, String> headers) {
        List<Header> allHeaders = new ArrayList<>(headers.size());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            allHeaders.add(new Header(header.getKey(), header.getValue()));
        }
        return allHeaders;
    }

    static Map<String, String> toHeaderMap(List<Header> allHeaders) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Later elements in the list take precedence.
        for (Header header : allHeaders) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    static String formatEpochAsRfc1123(long epoch) {
        return newRfc1123Formatter().format(new Date(epoch));
    }
}
