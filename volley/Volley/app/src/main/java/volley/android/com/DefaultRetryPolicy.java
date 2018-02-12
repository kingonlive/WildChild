package volley.android.com;


/**
 * 请求的默认重试策略
 */
public class DefaultRetryPolicy implements RetryPolicy{

    /**
     * 当前超时时间(ms)
     */
    private int mCurrentTimeoutMs;

    /**
     * 当前重试次数
     */
    private int mCurrentRetryCount;

    /**
     * 最大重试次数
     */
    private final int mMaxNumRetries;

    /**
     * 每次重试之后.超时时间的增长倍数(下一次的超时时间是当前超时时间加上该值乘以当前超时时间)
     */
    private final float mBackoffMultiplier;

    /**
     * 默认的socket超时时间
     */
    public static final int DEFAULT_TIMEOUT_MS = 2500;

    /**
     * 最大重试次数
     */
    public static final int DEFAULT_MAX_RETRIES = 1;

    /**
     * 默认的超时时间增长倍数
     */
    public static final float DEFAULT_BACKOFF_MULT = 1f;

    /**
     * 创建一个重试策略
     * @param initialTimeoutMs 初始超时时间
     * @param maxNumRetries 最大的重试次数
     * @param backoffMultiplier 超时时间的增长倍数
     */
    public DefaultRetryPolicy(int initialTimeoutMs, int maxNumRetries, float backoffMultiplier) {
        mCurrentTimeoutMs = initialTimeoutMs;
        mMaxNumRetries = maxNumRetries;
        mBackoffMultiplier = backoffMultiplier;
    }

    /**
     * 创建一个重试策略
     */
    public DefaultRetryPolicy() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MULT);
    }

    /**
     * 返回当前超时时间
     * @return
     */
    @Override
    public int getCurrentTimeout() {
        return mCurrentTimeoutMs;
    }

    /**
     * 返回当前重试次数
     * @return
     */
    @Override
    public int getCurrentRetryCount() {
        return mCurrentRetryCount;
    }

    /**
     * 返回超时时间的增长倍数
     * @return
     */
    public float getBackoffMultiplier() {
        return mBackoffMultiplier;
    }

    /**
     * 准备下一次重试
     * @param error 最后一次重试的出错信息{@link VolleyError}
     * @throws VolleyError
     */
    @Override
    public void retry(VolleyError error) throws VolleyError {
        mCurrentTimeoutMs++;

        //增长下一次超时时间
        mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackoffMultiplier);

        if (!hasAttemptRemaining()){
            throw error;
        }
    }

    /**
     * 是否可以继续重试
     * @return
     */
    protected boolean hasAttemptRemaining() {
        return mCurrentRetryCount <= mMaxNumRetries;
    }
}
