package volley.android.com;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个拥有线程池的请求派发队列
 */
public class RequestQueue {

    /**
     * 请求完成的回调接口
     * @param <T>
     */
    public interface RequestFinishedListener<T> {
        /**
         * 当请求被处理完成回调该接口
         * @param request
         */
        void onRequestFinished(Request<T> request);
    }

    /**
     * 用于生成请求序列号的生成器
     */
    private final AtomicInteger mSequenceGenerator = new AtomicInteger();

    /**
     * 当前RequestQueue内所有请求的集合
     */
    private final Set<Request<?>> mCurrentRequests = new HashSet<Request<?>>();

    /**
     * 用于从本地缓存获取结果的请求队列
     */
    private final PriorityBlockingQueue<Request<?>> mCacheQueue =
            new PriorityBlockingQueue<>();

    /**
     * 用于发起网络请求的请求队列
     */
    private final PriorityBlockingQueue<Request<?>> mNetworkQueue =
            new PriorityBlockingQueue<>();

    /**
     * 默认的网络请求线程数
     */
    private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /**
     * 用于操作本地缓存的接口
     */
    private final Cache mCache;

    /**
     * 网络请求接口
     */
    private final Network mNetwork;

    /**
     * 响应结果派发接口
     */
    private final ResponseDelivery mDelivery;

    /**
     * 网络请求线程
     */
    private final NetworkDispatcher[] mDispatchers;

    /**
     * 缓存请求线程
     */
    private CacheDispatcher mCacheDispatcher;


    private final List<RequestFinishedListener> mFinishedListeners =
            new ArrayList<>();

    /**
     * 创建一个工作池,调用{@link #start()}方法启动
     * @param cache 缓存操作接口
     * @param network 网络操作接口
     * @param threadPoolSize 访问网络的线程数
     * @param delivery 请求响应派发线程
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize,
                        ResponseDelivery delivery) {
        mCache = cache;
        mNetwork = network;
        mDispatchers = new NetworkDispatcher[threadPoolSize];
        mDelivery = delivery;
    }

}
