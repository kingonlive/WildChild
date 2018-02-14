package volley.android.com;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import volley.android.com.toolbox.Header;

/**
 * 缓存接口，缓存内容是键值对，其中键是字符串，值是字节数组
 */

public interface Cache {

    /**
     * 缓存内部的缓存项，包含数据和元数据
     */
    class Entry {
        /**
         * 缓存内部的原始数据
         */
        public byte[] data;

        /**
         * HTTP头部的etag
         * 这是HTTP请求协议定义的缓存协议：
         * 服务端返回etag字符串标记客户端请求的资源，客户端下次请求会通过if-none-match头部把该值带过去
         * 若服务端发现该值与客户端先前请求的值相同，则服务端会返回304(未修改)
         * @see <a href="https://www.jianshu.com/p/a3ea9619c38d">https://www.jianshu.com/p/a3ea9619c38d</a>
         */
        public String etag;

        /**
         * 服务端针对该请求响应返回的日期
         */
        public long serverDate;

        /**
         * 请求对象在服务端上一次修改的时间（从HTTP响应头部Last-Modified获得）
         */
        public long lastModified;

        /**
         * 根据Cache-Control的max-age及其他条件计算出来的缓存到期最终时间（时间戳）
         */
        public long ttl;

        /**
         * 缓存需被刷新的时间
         * 根据max-age及stale-while-revalidate计算出来的缓存到期时间（时间戳）
         * 非stale-while-revalidate的情况下这个值和ttl是一样的,stale-while-revalidate的情况这个值比ttl小
         */
        public long softTtl;

        /**
         * 后台返回的响应头部，这个集合一旦赋值不可更改.
         * 这个集合中的响应头部所有的键都是唯一的，若服务端返回多个相同的键，则集合只会存储相同键的最后一个键值对
         */
        public Map<String, String> responseHeaders = Collections.emptyMap();

        /**
         * 所有的请求头部，对不同的Cache实现有可能出现空的情况.该集合一旦赋值不应该被修改
         */
        public List<Header> allResponseHeaders;

        /**
         * 返回缓存是否过期
         * @return
         */
        public boolean isExpired() {
            return this.ttl < System.currentTimeMillis();
        }

        /**
         * 当前缓存是否需要刷新
         * @return
         */
        public boolean refreshNeeded() {
            return this.softTtl < System.currentTimeMillis();
        }

    }

    /**
     * 从本地缓存中获取缓存项
     * @param key 缓存项的键
     * @return
     */
    Entry get(String key);

    /**
     * 添加或替换缓存项(落地)
     * @param key 缓存项的键
     * @param entry 新的缓存项
     */
    void put(String key, Entry entry);

    /**
     * 刷新缓存项
     */
    void initialize();

    /**
     * 刷新缓存新鲜度,刷新之后缓存需要请求后台更新
     * @param key 需被刷新的缓存项
     * @param fullExpire 让缓存项完全过期
     */
    void invalidate(String key, boolean fullExpire);

    /**
     * 从缓存中移除指定的缓存项
     * @param key 被移除的缓存项
     */
    void remove(String key);

    /**
     * 清空缓存
     */
    void clear();
}
