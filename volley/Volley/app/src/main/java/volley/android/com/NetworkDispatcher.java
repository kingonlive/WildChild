package volley.android.com;

import android.annotation.TargetApi;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import java.util.concurrent.BlockingQueue;

import volley.android.com.toolbox.Response;

/**
 * 该类负责从网络请求队列中获取请求,并通过{@link Network}接口执行请求,
 * 将请求结果通过{@link Cache}接口进行缓存,之后通过{@link ResponseDelivery}接口将请求结果派发回去
 */
public class NetworkDispatcher extends Thread {

    /**
     * 当前线程从该队列中获取请求
     */
    private final BlockingQueue<Request<?>> mQueue;

    /**
     * 处理请求的网络接口
     */
    private final Network mNetwork;

    /**
     * 当前线程用来写入缓存的缓存接口
     */
    private final Cache mCache;

    /**
     * 当前线程用来派发结果或者错误的接口
     */
    private final ResponseDelivery mDelivery;

    /**
     * 该标志告知当前线程是否需要退出
     */
    private volatile boolean mQuit = false;

    /**
     * 创建一个网络派发线程
     * @param mQueue 该线程用于获取请求的请求队列
     * @param mNetwork 该线程用于处理请求的接口
     * @param mCache 该线程用于写入缓存的接口
     * @param mDelivery 该线程用于派发请求结果的接口
     */
    public NetworkDispatcher(BlockingQueue<Request<?>> mQueue, Network mNetwork, Cache mCache, ResponseDelivery mDelivery) {
        this.mQueue = mQueue;
        this.mNetwork = mNetwork;
        this.mCache = mCache;
        this.mDelivery = mDelivery;
    }

    /**
     * 退出当前线程,退出时不保证队列中请求的会被处理
     */
    public void quit(){
        mQuit = true;
        interrupt();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addTrafficStatsTag(Request<?> request) {
        // Tag the request (if API >= 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag());
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        while (true){
            try {
                processRequest();
            } catch (InterruptedException e) {
                if (mQuit){
                    break;
                }
            }
        }
    }

    private void processRequest() throws InterruptedException{
        Request<?> request = mQueue.take();
        long startTimeMs = SystemClock.elapsedRealtime();

        try {
            request.addMarker("network-queue-take");

            //如果请求已经取消,则终止请求
            if (request.isCanceled()) {
                request.finish("network-discard-cancelled");
                request.notifyListenerResponseNotUsable();
                return;
            }

            addTrafficStatsTag(request);

            //发起网络请求
            NetworkResponse networkResponse = mNetwork.performRequest(request);
            request.addMarker("network-http-complete");

            //如果服务端返回304(not modified) 且 之前这个请求已经派发过一次结果了,我们不需要再派发结果
            if (networkResponse.notModified && request.hasHadResponseDelivered()){
                request.finish("not-modified");
                request.notifyListenerResponseNotUsable();
                return;
            }

            //解析返回结果
            Response<?> response = request.parseNetworkResponse(networkResponse);
            request.addMarker("network-parse-complete");

            //写入缓存
            //TODO(这个TODO是volley写的):对于304应该只更新缓存的元数据也不是整响应
            if (request.shouldCache() && response.cacheEntry != null){
                mCache.put(request.getCacheKey(), response.cacheEntry);
                request.addMarker("network-cache-written");
            }

            request.markDelivered();

            //派发响应结果
            mDelivery.postResponse(request, response);

            //通知request的监听者请求已经结束
            request.notifyListenerResponseReceived(response);

        } catch (VolleyError volleyError) {
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            parseAndDeliverNetworkError(request, volleyError);
            request.notifyListenerResponseNotUsable();
        } catch (Exception e){
            VolleyLog.e(e, "Unhandled exception %s", e.toString());
            VolleyError volleyError = new VolleyError(e);
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            mDelivery.postError(request, volleyError);
            request.notifyListenerResponseNotUsable();
        }
    }

    private void parseAndDeliverNetworkError(Request<?> request, VolleyError error) {
        error = request.parseNetworkError(error);
        mDelivery.postError(request, error);
    }
}
