package volley.android.com;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import volley.android.com.toolbox.Response;

/**
 * 一个从缓存队列中获取请求的后台线程,它从缓存队列中获取请求,并把结果派发给{@link ResponseDelivery}.
 * 若缓存没命中或者命中了但缓存无效则会将请求添加到网络队列中,由{@link NetworkDispatcher}去处理
 */
public class CacheDispatcher extends Thread {
    private static final boolean DEBUG = VolleyLog.DEBUG;

    /**
     * 其内元素是当前工作线程的输入,当前线程会一直从该阻塞队列中获取请求,
     */
    private final BlockingQueue<Request<?>> mCacheQueue;

    /**
     * 其内元素是当前工作线程的输出,当mCacheQueue中的请求未命中缓存或缓存无效,当前工作线程会将请求放入该队列
     */
    private final BlockingQueue<Request<?>> mNetworkQueue;

    /**
     * 用于获取缓存内容的缓存接口
     */
    private final Cache mCache;

    /**
     * 用于派发请求结果的派发接口
     */
    private final ResponseDelivery mDelivery;

    /**
     * 告诉我们当前线程是否可以去死
     */
    private volatile boolean mQuit = false;

    /**
     * 维护一个请求到列表的映射，管理重复请求
     */
    private final WaitingRequestManager mWaitingRequestManager;

    /**
     * 创建一个缓存请求处理线程
     * @param cacheQueue 用于等待从缓存获取请求结果的队列
     * @param networkQueue 用于请求网络获取访问接口的队列
     * @param cache 用户获取缓存数据的缓存接口
     * @param delivery 用于派发请求结果的接口
     */
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue, BlockingQueue<Request<?>> networkQueue,
            Cache cache, ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
        mWaitingRequestManager = new WaitingRequestManager(this);
    }

    /**
     * 退出当前线程，如果队列中仍然有请求未被处理,不保证他们可以被正常处理
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    private static class WaitingRequestManager implements Request.NetworkRequestCompleteListener{

        /**
         * 请求暂存区,当存在多个相同的请求,我们会把重复的请求暂存在这个区域
         * <ul>
         *     <li>通过调用containsKey(cacheKey)可以知道当前是否已经有相同请求被发起</li>
         *     <li>get(cacheKey)返回同一个cacheKey对应请求的请求列表,那个正在被执行的请求不在这个列表中</li>
         * </ul>
         */
        private final Map<String, List<Request<?>>> mWaitingRequests = new HashMap<>();

        private final CacheDispatcher mCacheDispatcher;

        WaitingRequestManager(CacheDispatcher cacheDispatcher) {
            mCacheDispatcher = cacheDispatcher;
        }

        //收到了一个请求的响应结果,其他所有相同资源的请求可以复用这个请求结果
        @Override
        public void onResponseReceived(Request<?> request, Response<?> response) {
            if (response.cacheEntry == null || response.cacheEntry.isExpired()) {
                //缓存为空或者缓存无效
                onNoUsableResponseReceived(request);
                return;
            }

            String cacheKey = request.getCacheKey();
            List<Request<?>> waitingRequests;

            synchronized (this) {
                //return null if there was no mapping for key
                waitingRequests = mWaitingRequests.remove(cacheKey);
            }

            //确实存重复的请求
            if (waitingRequests != null){
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("Releasing %d waiting requests for cacheKey=%s.",
                            waitingRequests.size(), cacheKey);
                }

                //派发所有正在等待的请求出去
                for (Request<?> waiting : waitingRequests){
                    mCacheDispatcher.mDelivery.postResponse(waiting, response);
                }
            }

        }

        //未收到有效的网络请求结果,释放所有等待的请求
        @Override
        public void onNoUsableResponseReceived(Request<?> request) {
            String cacheKey = request.getCacheKey();
            List<Request<?>> waitingRequests = mWaitingRequests.remove(cacheKey);

            if (waitingRequests != null && !waitingRequests.isEmpty()){
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("%d waiting requests for cacheKey=%s; resend to network",
                            waitingRequests.size(), cacheKey);
                }

                //等待队列第一个元素
                Request<?> nextInLine = waitingRequests.remove(0);

                //将等待队列第一个元素拿出来放到网络队列中
                mWaitingRequests.put(cacheKey, waitingRequests);

                //@FIXME 这里会不会tm有bug,前面刚put进去就被网络线程执行,执行完毕之后,这里才set监听,待会再分析

                //给拿出来的等待队列第一个元素设置监听器,当它有网络请求结果的时候告诉当前对象
                nextInLine.setNetworkRequestCompleteListener(this);

                try {
                    mCacheDispatcher.mNetworkQueue.put(nextInLine);
                } catch (InterruptedException iex) {
                    //阻塞时候被打断
                    VolleyLog.e("Couldn't add request to queue. %s", iex.toString());

                    //恢复网络线程的中断状态
                    Thread.currentThread().interrupt();

                    //退出缓存线程
                    mCacheDispatcher.quit();
                }
            }
        }

        /**
         * 如果该请求已经有相同请求在执行了，则将该请求添加到相同请求的等待列表中,在有请求结果之后一起派发.
         * 若之前没有相同请求在执行,则应该继续把请求送入到网络队列等待网络线程处理
         * @param request 需要添加到等待列表的请求对象
         * @return 返回true表示添加到了等待队列中,返回false表示当前还没有相同请求在执行
         */
        private synchronized boolean maybeAddToWaitingRequests(Request<?> request){
            String cacheKey = request.getCacheKey();

            //当前已经有请求正在执行,我们需要添加到请求队列中
            if (mWaitingRequests.containsKey(cacheKey)){
                List<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);

                if (stagedRequests == null){
                    stagedRequests = new ArrayList<Request<?>>();
                }

                request.addMarker("waiting-for-response");
                stagedRequests.add(request);
                mWaitingRequests.put(cacheKey, stagedRequests);

                if (VolleyLog.DEBUG) {
                    VolleyLog.d("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
                }

                return true;
            } else {
                //当前已有一个请求正在执行,
                mWaitingRequests.put(cacheKey, null);

                //给正在执行的请求设置一个回调,当有请求结果的时候让我们这边知道
                request.setNetworkRequestCompleteListener(this);

                if (VolleyLog.DEBUG) {
                    VolleyLog.d("new request, sending to network %s", cacheKey);
                }
                return false;
            }
        }

    }
}
