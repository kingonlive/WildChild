package volley.android.com.toolbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * 字节数组的分配器，用于分配和回收字节数组，内部按大小和使用顺序排序，删除元素时先删最早还回的元素
 */
public class ByteArrayPool {
    /**
     * 按使用先后顺序排序的缓冲区，第0个字节数组是最早使用的元素，最后一个字节数据是最近刚使用过的元素
     */
    private final List<byte[]> mBuffersByLastUse = new LinkedList<byte[]>();

    /**
     * 按字节数组的大小排序的缓冲区
     */
    private final List<byte[]> mBuffersBySize = new ArrayList<byte[]>(64);

    /**
     * 分配器缓冲区的当前大小
     */
    private int mCurrentSize = 0;

    /**
     * 分配器缓冲区的最大尺寸，超过这个大小，再往缓存区塞东西就要先掉已有的腾位置了
     */
    private final int mSizeLimit;

    /**
     * 用于排序的比较器，按照字节数组的大小排序
     */
    protected static final Comparator<byte[]> BUF_COMPARATOR = new Comparator<byte[]>() {
        @Override
        public int compare(byte[] lhs, byte[] rhs) {
            return lhs.length - rhs.length;
        }
    };

    /**
     * 创建一个字节数组分配器
     * @param sizeLimit 该分配器最大的缓冲区尺寸,单位是字节
     */
    public ByteArrayPool(int sizeLimit) {
        mSizeLimit = sizeLimit;
    }

    /**
     * 返回一个指定大小的字节数组，若缓冲区中有合适大小的就直接返回，否则创建一个
     * @param len 字节数组的最小长度
     * @return 返回字节数组，这个数组有可能比len要大
     */
    public synchronized byte[] getBuf(int len) {
        for (int i = 0 ; i < mBuffersBySize.size() ; i++){
            byte[] buf = mBuffersBySize.get(i);
            if (buf.length >= len){
                mCurrentSize -= buf.length;
                mBuffersBySize.remove(i);
                mBuffersByLastUse.remove(buf);
                return buf;
            }
        }
        return new byte[len];
    }

    /**
     * 返还一个字节数组到缓冲区中，如果返还之后缓冲区大小超过上限，则删掉最旧的字节数组
     * @param buf
     */
    public synchronized void returnBuf(byte[] buf) {
        if (buf == null || buf.length > mSizeLimit) {
            return;
        }

        mBuffersByLastUse.add(buf);

        //二分查找mBuffersBySize中buf存放的位置,如果找不到的话会返回 [-插入点 -1] 这个数值
        int pos = Collections.binarySearch(mBuffersBySize, buf, BUF_COMPARATOR);

        if (pos < 0) {
            //转换成正确的插入位置
            pos = -pos - 1;
        }

        mBuffersBySize.add(pos, buf);

        //确保缓冲区不超过已限制的大小
        //@FIXME 这里是不是有问题？ 这里只是删除掉第一个元素，怎么就知道删完之后mCurrentSize就一定比mSizeLimit 小了呢
        trim();
    }

    /**
     * 从缓冲区中删除字节数组,确保当前缓存区大小不要超过上限
     */
    private synchronized void trim() {
        if (mCurrentSize > mSizeLimit){
            byte[] buf = mBuffersByLastUse.remove(0);
            mBuffersBySize.remove(buf);
            mCurrentSize -= buf.length;
        }
    }
}
