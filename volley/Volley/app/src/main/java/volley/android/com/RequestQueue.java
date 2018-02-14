package volley.android.com;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
     * 创建一个工作,调用{@link #start()}方法启动
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


    /**
     * 创建一个工作,调用{@link #start()}方法启动
     * @param cache 用来读写缓存的接口
     * @param network 用来发送网络请求的接口
     * @param threadPoolSize 线程池的大小
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize){
        this(cache, network, threadPoolSize, new ExecutorDelivery(new Handler(Looper.myLooper())));
    }

    /**
     * * 创建一个工作,调用{@link #start()}方法启动
     * @param cache 用来读写缓存的接口
     * @param network 用来发送网络请求的接口
     */
    public RequestQueue(Cache cache, Network network) {
        this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE);
    }

    /**
     * 启动这个工作队列中的所有工作线程，包括缓存请求工作线程，网络请求工作线程
     */
    public void start() {
        //确保当前工作队列中的所有线程是已暂停了的
        stop();

        //创建并启动缓存请求工作线程
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();

        //创建并启动网络请求工作线程
        for (int i = 0 ; i < mDispatchers.length ; i++){
            mDispatchers[i] = new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
            mDispatchers[i].start();
        }

    }

    /**
     * 暂停网络请求线程和缓存请求线程
     */
    public void stop(){
        if (mCacheDispatcher != null){
            mCacheDispatcher.quit();
        }

        for (NetworkDispatcher dispatcher : mDispatchers){
            dispatcher.quit();
        }
    }

    /**
     * 返回一个序列化
     * @return
     */
    public int getSequenceNumber(){
        return mSequenceGenerator.incrementAndGet();
    }

    /**
     * 返回当前正在使用的缓存接口实例
     * @return
     */
    public Cache getCache() {
        return mCache;
    }

    /**
     * 一个过滤请求的接口，用于{@link RequestQueue#cancelAll(RequestFilter)}取消指定特征的请求
     */
    public interface RequestFilter{
        /**
         * 该接口返回true表示传入的请求参数是符合这个接口预期的
         * @param request
         * @return
         */
        boolean apply(Request<?> request);
    }

    /**
     * 取消通过{@link RequestFilter}接口过滤的请求
     * @param filter 需要过滤请求接口
     */
    public void cancelAll(RequestFilter filter){
        synchronized (mCurrentRequests){
            for (Request<?> request : mCurrentRequests){
                if (filter.apply(request)){
                    request.cancel();
                }
            }
        }
    }

    /**
     * 取消所有相同tag的请求
     * @param tag 需要被取消的请求的tag
     */
    public void cancelAll(final Object tag){
        if (tag == null){
            //@FIXME 这我就不懂了，为什么不是设置tag的时候不允许为空要等到这个时候才来干这个事情
            throw new IllegalArgumentException("Cannot cancelAll with a null tag");
        }

        cancelAll(new RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                //@FIXME 大爷的，我理解错tag的意思了我还以为是内容相同，这个要求是同一个对象！
                return request.getTag() == tag;
            }
        });
    }

    /**
     * 添加一个请求到工作队列中去
     * @param request 需要被处理的请求
     * @param <T>
     * @return 需要被处理的请求
     */
    public <T> Request<T> add(Request<T> request){
        request.setRequestQueue(this);

        synchronized (mCurrentRequests){
            mCurrentRequests.add(request);
        }

        request.setSequence(getSequenceNumber());
        request.addMarker("add-to-queue");

        //当请求不需要走缓存，则直接扔到网络请求队列中
        if (!request.shouldCache()){
            mNetworkQueue.add(request);
            return request;
        }

        mCacheQueue.add(request);
        return request;
    }

    /**
     * 从{@link Request#finish(String)}中调过来，指定该请求已经结束
     * @param request 已经结束的请求
     * @param <T>
     */
    <T> void finish(Request<T> request){
        //请求已经结束，移出请求集合
        synchronized (mCurrentRequests){
            mCurrentRequests.remove(request);
        }

        //通知请求完成的监听者
        synchronized (mFinishedListeners){
            for (RequestFinishedListener listener : mFinishedListeners){
                listener.onRequestFinished(request);
            }
        }
    }

    /**
     * 注册一个监听请求完成与否的监听
     * @param listener
     * @param <T>
     */
    public  <T> void addRequestFinishedListener(RequestFinishedListener<T> listener){
        synchronized (mFinishedListeners){
            mFinishedListeners.add(listener);
        }
    }

    /**
     * 移除请求完成的监听
     * @param listener
     * @param <T>
     */
    public  <T> void removeRequestFinishedListener(RequestFinishedListener<T> listener){
        synchronized (mFinishedListeners){
            mFinishedListeners.remove(listener);
        }
    }

}
