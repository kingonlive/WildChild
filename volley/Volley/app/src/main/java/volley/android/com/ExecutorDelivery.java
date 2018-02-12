package volley.android.com;

import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;

import volley.android.com.toolbox.Response;

/**
 * 派发请求结果或请求出错的派发器
 */
public class ExecutorDelivery implements ResponseDelivery{

    /**
     * 提交派发任务的Executor,默认情况下任务会被派发到UI线程
     */
    private final Executor mResponsePoster;

    public ExecutorDelivery(final Handler handler) {
        mResponsePoster = new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * 创建一个派发任务的任务派发器
     * @param executor 用于派发任务的Executor,任务会派发给它
     */
    public ExecutorDelivery(Executor executor) {
        mResponsePoster = executor;
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response) {
        postResponse(request, response, null);
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        //标记该任务位已派发结果
        request.markDelivered();

        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }

    @Override
    public void postError(Request<?> request, VolleyError error) {
        request.addMarker("post-error");
        Response<?> response = Response.error(error);
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, null));
    }

    /**
     * 用于派发任务的Runnable,它会在UI线程（默认情况下,取决与mResponsePoster派发的任务在哪个线程)解析访问网络获得的响应结果并派发给{@link Request}的监听器
     */
    private class ResponseDeliveryRunnable implements Runnable{
        private final Request mRequest;
        private final Response mResponse;
        private final Runnable mRunnable;

        public ResponseDeliveryRunnable(Request mRequest, Response mResponse, Runnable mRunnable) {
            this.mRequest = mRequest;
            this.mResponse = mResponse;
            this.mRunnable = mRunnable;
        }

        @Override
        public void run() {

            /*
               若请求被取消了，则终止派发过程
               注意:
               没办法保证请求被取消了就不会派发结果,有可能刚好在我们检查完任务是否取消之后，外部才调用了请求的取消接口.因此必须保证派发结果的线程和取消任务的线程是同一个线程,
             */
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }

            //派发请求结果给监听器
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }

            //当前请求结果(从缓存中拿出来的)需要被刷新,增加一个日志节点
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                //派发完成
                mRequest.finish("done");
            }

            //派发完成回调
            if (mRunnable != null){
                mRunnable.run();
            }
        }
    }

}
