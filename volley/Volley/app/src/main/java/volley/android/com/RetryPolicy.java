package volley.android.com;

/**
 * 请求的重试策略
 */
public interface RetryPolicy {

    /**
     * 返回当前的超时时间
     * @return
     */
    int getCurrentTimeout();

    /**
     * 返回当前的重试次数
     * @return
     */
    int getCurrentRetryCount();

    /**
     * 准备下一次重试,计算下一次的重试时间和重试次数
     * @param error 最后一次重试的出错信息{@link VolleyError}
     * @throws VolleyError
     */
    void retry(VolleyError error) throws VolleyError;
}
