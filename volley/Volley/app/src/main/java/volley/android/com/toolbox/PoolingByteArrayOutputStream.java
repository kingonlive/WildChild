package volley.android.com.toolbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 一个使用了缓冲区的{@link java.io.ByteArrayOutputStream}
 */
public class PoolingByteArrayOutputStream extends ByteArrayOutputStream {
    /**
     * 默认缓冲区的大小
     */
    private static final int DEFAULT_SIZE = 256;

    private final ByteArrayPool mPool;

    /**
     * 构造一个默认大小缓冲区的字节输出流，若写入字节流比默认的要大，缓冲区内部会自动扩大
     * @param pool
     */
    public PoolingByteArrayOutputStream(ByteArrayPool pool) {
        this(pool, DEFAULT_SIZE);
    }

    /**
     * 构造一个指定大小缓冲区的字节输出流，若写入字节流比默认的要大，缓冲区内部会自动扩大
     * @param pool
     * @param size
     */
    public PoolingByteArrayOutputStream(ByteArrayPool pool, int size) {
        mPool = pool;
        buf = mPool.getBuf(Math.max(size, DEFAULT_SIZE));
    }

    @Override
    public void close() throws IOException {
        //关闭输出流时将申请的缓冲区还回去
        mPool.returnBuf(buf);
        buf = null;
        super.close();
    }

    @Override
    public void finalize() {
        //对象被回收时也将申请的缓冲区块还回去
        mPool.returnBuf(buf);
    }

    /**
     * 确保当前的容量能够容纳指定字节大小
     * @param i 当前需要被容纳的新的字节大小
     */
    private void expand(int i) {
        if (count + i <= buf.length) {
            return;
        }

        //从缓冲区中申请一块当前需要大小2倍大的空间
        byte[] newbuf = mPool.getBuf((count + i) * 2);

        //拷贝数据到新的内存块
        System.arraycopy(buf, 0 , newbuf , 0 , count);

        //把旧的内存块还回去
        mPool.returnBuf(buf);
        buf = newbuf;
    }

    @Override
    public synchronized void write(byte[] buffer, int offset, int len) {
        expand(len);
        super.write(buffer, offset, len);
    }

    @Override
    public synchronized void write(int oneByte) {
        expand(1);
        super.write(oneByte);
    }
}
