package volley.android.com.toolbox;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import volley.android.com.Cache;
import volley.android.com.VolleyLog;

/**
 * 本地缓存实现，将内存的内容以文件的形式缓存到一个目录中，缓存大小可以配置，默认的是5M.
 * 这个缓存支持{@link Entry#allResponseHeaders}的头部
 */
public class DiskBasedCache implements Cache{

    private final Map<String, CacheHeader> mEntries =
            new LinkedHashMap<String, CacheHeader>(16, .75f, true);

    /**
     * 当前缓存使用的总大小(字节)
     */
    private long mTotalSize = 0;

    /**
     * 缓存文件的根目录
     */
    private final File mRootDirectory;

    /**
     * 缓存的最大空间(字节)
     */
    private final int mMaxCacheSizeInBytes;

    /**
     * 默认缓存空间 5M
     */
    private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /**
     * 缓存的高水位线百分比（到了这个值就说明缓存快要满了）
     */
    private static final float HYSTERESIS_FACTOR = 0.9f;

    /**
     * magic number,用来作为文件头的标记
     */
    private static final int CACHE_MAGIC = 0x20150306;

    /**
     * 创建一个本地缓存实现DiskBasedCache的实例
     * @param rootDirectory 缓存落地文件的根目录
     * @param maxCacheSizeInBytes 缓存的最大容量
     */
    public DiskBasedCache(File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectory = rootDirectory;
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * 创建一个本地缓存实现DiskBasedCache的实例,采用默认容量(5M)
     * @param rootDirectory 缓存的根目录
     */
    public DiskBasedCache(File rootDirectory) {
        this(rootDirectory, DEFAULT_DISK_USAGE_BYTES);
    }

    @Override
    public void clear() {
        File[] files = mRootDirectory.listFiles();
        if (files != null) {
            for (File file : files){
                file.delete();
            }
        }
        mEntries.clear();
        mTotalSize = 0;
        VolleyLog.d("Cache cleared.");
    }

    /**
     * 更新disk on memory的内容
     * @param key
     * @param entry
     */
    private void putEntry(String key, CacheHeader entry) {
        if (!mEntries.containsKey(key)){
            mTotalSize += entry.size;
        } else {
            CacheHeader oldEntry = mEntries.get(key);
            mTotalSize += (entry.size - oldEntry.size);
        }
        mEntries.put(key, entry);
    }

    /**
     * 删除这个key对应的缓存(disk on memory)
     * @param key
     */
    private void removeEntry(String key) {
        CacheHeader removed = mEntries.remove(key);
        if (removed != null) {
            mTotalSize -= removed.size;
        }
    }

    @Override
    public Entry get(String key) {
        CacheHeader entry = mEntries.get(key);
        if (entry == null){
            return null;
        }

        File file = getFileForKey(key);

        try {
            //从文件读输入流
            CountingInputStream cis = new CountingInputStream(
                    new BufferedInputStream(createInputStream(file)), file.length());

            try {
                //1.从输入流中读出entry on Disk这个CacheHeader
                CacheHeader entryOnDisk = CacheHeader.readHeader(cis);
                if (!TextUtils.equals(key, entryOnDisk.key)){
                    //同一个文件被写入了两个缓存对象!
                    VolleyLog.d("%s: key=%s, found=%s",
                            file.getAbsolutePath(), key, entryOnDisk.key);

                    //删除旧key的缓存内容，因为文件的数据已经被覆盖了
                    removeEntry(key);
                    return null;
                }

                //2.从输入流中读出剩下的数据
                byte[] data = streamToBytes(cis, cis.bytesRemaining());

                return entry.toCacheEntry(data);
            } finally {
                cis.close();
            }
        } catch (IOException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());

            //读取缓存都出错了，删除这个key对应的缓存
            remove(key);

            return null;
        }
    }

    /**
     * 返回一个key对应的文件对象,该对象的根目录是mRootDirectory
     * @param key 文件对象对应的key
     * @return
     */
    public File getFileForKey(String key) {
        return new File(mRootDirectory, getFilenameForKey(key));
    }

    /**
     * 为缓存键创建一个伪随机文件名
     * @param key
     * @return
     */
    private String getFilenameForKey(String key) {
        int firstHalfLength = key.length() / 2;

        //作为文件名 = 左半部分取哈希码 + 右半部分哈希码
        String localFileName = String.valueOf(key.substring(0, firstHalfLength).hashCode());
        localFileName += String.valueOf(key.substring(firstHalfLength).hashCode());

        return localFileName;
    }

    /**
     * 缓存压力大，缩小缓存的大小
     * @param neededSpace 目前需要的缓存大小
     */
    private void pruneIfNeeded(int neededSpace) {
        if (neededSpace + mTotalSize < mMaxCacheSizeInBytes){
            return;
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v("Pruning old cache entries.");
        }

        long before = mTotalSize;
        int prunedFiles = 0;
        long startTime = SystemClock.elapsedRealtime();

        Iterator<Map.Entry<String, CacheHeader>> iterator = mEntries.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, CacheHeader> entry = iterator.next();
            CacheHeader e = entry.getValue();

            //按返回顺序删掉缓存文件
            boolean deleted = getFileForKey(e.key).delete();

            if (deleted) {
                mTotalSize -= e.size;
            } else {
                VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
                        e.key, getFilenameForKey(e.key));
            }

            //删掉缓存文件在内存里面的缓存项
            iterator.remove();

            prunedFiles++;

            //删到水位线以下了
            if ((mTotalSize + neededSpace) < mMaxCacheSizeInBytes * HYSTERESIS_FACTOR) {
                break;
            }
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v("pruned %d files, %d bytes, %d ms",
                    prunedFiles, (mTotalSize - before), SystemClock.elapsedRealtime() - startTime);
        }
    }

    /**
     * 新增或者替换缓存项（落地)
     * @param key 缓存项的键
     * @param entry 新的缓存项
     */
    @Override
    public void put(String key, Entry entry) {
        //确保有缓存空间可以插入缓存项
        pruneIfNeeded(entry.data.length);

        File file = getFileForKey(key);
        try {
            BufferedOutputStream fos = new BufferedOutputStream(createOutputStream(file));
            CacheHeader e = new CacheHeader(key, entry);
            boolean success = e.writeHeader(fos);

            if (!success){
                fos.close();
                VolleyLog.d("Failed to write header for %s", file.getAbsolutePath());
                throw new IOException();
            }

            fos.write(entry.data);
            fos.close();

            putEntry(key, e);
            return;
        } catch (IOException e) {
        }

        boolean deleted = file.delete();
        if (!deleted) {
            VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
        }
    }


    @Override
    public void initialize() {
        if (!mRootDirectory.exists()){
            if (!mRootDirectory.mkdir()){
                VolleyLog.e("Unable to create cache dir %s", mRootDirectory.getAbsolutePath());
            }
            //TODO 这里有点不理解,这个return为什么不是在创建不成功之后才return?
            return;
        }

        File[] files = mRootDirectory.listFiles();
        if (files == null){
            //目录下没有任何缓存文件
            return;
        }

        for (File file : files){
            try {
                //文件内的数据大小(字节数)
                long entrySize = file.length();

                CountingInputStream cis = new CountingInputStream(new BufferedInputStream(createInputStream(file)), entrySize);

                try {
                    CacheHeader entry = CacheHeader.readHeader(cis);

                    //当我们数据从文件读出来，这里传入的就是文件的长度，当我们put一个entry进来这里传入的就是data的字节数
                    entry.size = entrySize;

                    //已读出数据,存入mEntries
                    putEntry(entry.key, entry);
                } finally {
                    //不管出现异常与否,都需要输入流
                    cis.close();
                }

            } catch (IOException e) {
                //非我族类,得而诛之
                file.delete();
            }

        }
    }

    @Override
    public void invalidate(String key, boolean fullExpire) {
        Entry entry = get(key);
        if (entry != null) {
            entry.softTtl = 0;
            if (fullExpire) {
                entry.ttl = 0;
            }
            put(key, entry);
        }
    }

    @Override
    public void remove(String key) {
        boolean deleted = getFileForKey(key).delete();
        removeEntry(key);
        if (!deleted) {
            VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
                    key, getFilenameForKey(key));
        }
    }

    /**
     * 写入一个n值到输出流
     * 由于一个数值是4个字节，{@link OutputStream#write(int)}每次只写入一个最低位字节，因此我们要自己写入另外的三位高位字节
     * @param os 被写入数据的输出流
     * @param n 要写入的数值
     * @throws IOException
     */
    static void writeInt(OutputStream os, int n) throws IOException {
        //写入最低位字节
        os.write(n >> 0);
        //写入第二低位字节
        os.write(n >> 8);
        //写入第三低位字节
        os.write(n >> 16);
        //写入最高位字节
        os.write(n >> 24);
    }

    /**
     * 从输入流中读取一个n值
     * @param is 要读取内容的输入流
     * @throws IOException
     */
    static int readInt(InputStream is) throws IOException {
        int n = 0;
        //四个字节的最低字节
        n |= read(is) << 0;
        //四个字节的第二个字节
        n |= read(is) << 8;
        //四个字节的第三个字节
        n |= read(is) << 16;
        //四个字节的第四个字节
        n |= read(is) << 24;
        return n;
    }

    /**
     * 写入一个long型值
     * @param os 需要写入数据的输出流
     * @param n 需要写入的long值
     * @throws IOException
     * 由于{@link OutputStream#write(int)}每次只能写入一个字节，这里我们基于它来实现写入一个long值
     */
    static void writeLong(OutputStream os, long n) throws IOException {
        os.write((byte)(n >>> 0));
        os.write((byte)(n >>> 8));
        os.write((byte)(n >>> 16));
        os.write((byte)(n >>> 24));
        os.write((byte)(n >>> 32));
        os.write((byte)(n >>> 40));
        os.write((byte)(n >>> 48));
        os.write((byte)(n >>> 56));
    }

    /**
     * 从输入流中读取一个long值
     * @param is  输入流
     * @return
     * @throws IOException 因为读写都是我们自己内部控制的，会配套使用，当出现读出预期不会出现的-1时会抛出异常
     */
    static long readLong(InputStream is) throws IOException {
        long n = 0;
        n |= ((read(is) & 0xFFL) << 0);
        n |= ((read(is) & 0xFFL) << 8);
        n |= ((read(is) & 0xFFL) << 16);
        n |= ((read(is) & 0xFFL) << 24);
        n |= ((read(is) & 0xFFL) << 32);
        n |= ((read(is) & 0xFFL) << 40);
        n |= ((read(is) & 0xFFL) << 48);
        n |= ((read(is) & 0xFFL) << 56);
        return n;
    }

    /**
     * 一个简单的{@link InputStream#read()}包装，如果读出来的值是-1则直接抛异常(我们必须要读写配套使用不应该出现-1的情况)
     * @param is
     * @return
     * @throws IOException
     */
    private static int read(InputStream is) throws IOException {
        int n = is.read();
        if (n == -1) {
            throw new IOException();
        }
        return n;
    }

    /**
     * 向输出流中写入一个String字符串
     * @param os 要写入数据的输出流
     * @param s 要写入的字符串
     * @throws IOException
     */
    static void writeString(OutputStream os, String s) throws IOException {
        //获取字符的UTF-8编码格式的内容
        //这里要知道Unicode只是字符集，里面是一堆全世界都有可能出现的字符，在计算机里面字符被用不同的编码格式存储起来，编码格式就有utf-8和其他的
        byte[] b = s.getBytes("UTF-8");

        //先写入字符的长度
        writeLong(os, b.length);

        //再写入字符的内容
        os.write(b, 0 ,b.length);
    }

    /**
     * 从输入流中读出一个String
     * @param cis
     * @return
     * @throws IOException
     */
    static String readString(CountingInputStream cis) throws IOException{
        long n = readLong(cis);
        byte[] b = streamToBytes(cis, n);
        return new String(b, "UTF-8");
    }

    /**
     * 从输入流中读取length长度个字节
     * @param cis 要读取数据的输入流
     * @param length 要读取的字节数
     * @return 返回length个字节的字节数组
     * @throws IOException
     */
    static byte[] streamToBytes(CountingInputStream cis, long length) throws IOException {
        long maxLength = cis.bytesRemaining();
        //要读取的字节长度length不能够小于能够读取的字节数，同时length的数值不能够超过一个int的大小(原因是数组的长度不能够超过int的大小,总空间4G了都)
        if (length < 0 || maxLength < length || (int)length != length){
            throw new IOException("streamToBytes length=" + length + ", maxLength=" + maxLength);
        }

        byte[] bytes = new byte[(int) length];
        //@FIXME 不知道这里为什么要这么写，是我就直接cis.read(bytes,0,length)了,难道是为了判断读出来的值?
        new DataInputStream(cis).readFully(bytes);
        return bytes;
    }

    /**
     * 写入头部字段到输出流中,数据写入前会先写入头部字段的数量
     * @param headers 需要写入到本地的头部字段
     * @param os 要写入数据的输出流
     * @throws IOException
     */
    static void writeHeaderList(List<Header> headers, OutputStream os) throws IOException {
        if (headers != null){
            //先写入头部列表的长度
            writeInt(os, headers.size());

            //再依次写入键值对
            for (Header header : headers){
                writeString(os, header.getName());
                writeString(os, header.getValue());
            }
        } else {
            writeInt(os, 0);
        }
    }

    /**
     * 从输入流中读取头部字段
     * @param cis 要读出头部字段的输入流
     * @return 返回一个包含所有头部字段输入流的列表
     * @throws IOException
     */
    static List<Header> readHeaderList(CountingInputStream cis) throws IOException {
//        int n = readInt(cis);
//        List<Header> headers = new ArrayList<>(n);
//        for (int i = 0 ; i < n ; i++){
//            headers.add(new Header(readString(cis), readString(cis)));
//        }
//        return headers;
//        @FIXME 为啥漏了考虑要校验n?!! 啊！！？ 你说！

        int size = readInt(cis);
        if (size < 0) {
            throw new IOException("readHeaderList size=" + size);
        }

        List<Header> result = size == 0 ? Collections.<Header>emptyList() : new ArrayList<Header>();

        for (int i = 0 ; i < size ; i++){
            String key = readString(cis);
            String value = readString(cis);
            result.add(new Header(key, value));
        }

        return result;
    }

    /**
     * 一个能够统计已读字节流的输入流
     *
     * @FIXME
     * 这个类是为了方便调试才重载的，为了让代码可测不惜把代码改成可测的代码，嗯！这样是正确的，我们都应这样做!
     */
    static class CountingInputStream extends FilterInputStream {
        private final long length;
        private long bytesRead;

        /**
         * 创建一个能够统计字节数的输入流，创建时必须传递输入流的字节数
         * @param in 数据源
         * @param length 数据源中数据点字节数
         */
        CountingInputStream(InputStream in, long length) {
            super(in);
            this.length = length;
        }

        @Override
        public int read() throws IOException {
            int n = super.read();
            if (n != -1){
                bytesRead++;
            }
            return n;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n != -1){
                bytesRead += n;
            }
            return n;
        }

        /**
         * 返回已读字节数
         * @return
         */
        long bytesRead() {
            return bytesRead;
        }

        /**
         * 返回剩余的未读字节数
         * @return
         */
        long bytesRemaining() {
            return length - bytesRead;
        }
    }

    static class CacheHeader {
        //该对象所有数据的长度(这个值不会序列化到本地)
        long size;

        /**
         * 标示该缓存项的一个键
         */
        final String key;

        /**
         * [http响应字段] http响应的etag,用于缓存逻辑
         */
        final String etag;

        /**
         * [http响应字段] 服务端返回响应的时间
         */
        final long serverDate;

        /**
         * [http响应字段] 被请求资源上一次修改的时间
         */
        final long lastModified;

        /**
         * [http响应字段] 缓存的真实过期时间
         */
        final long ttl;

        /**
         * [http响应字段] 缓存需被刷新的时间
         */
        final long softTtl;

        /**
         * [http响应字段] 来自缓存项的所有http响应头部
         */
        final List<Header> allResponseHeaders;

        private CacheHeader(String key, String etag, long serverDate, long lastModified, long ttl,
                            long softTtl, List<Header> allResponseHeaders) {
            this.key = key;
            this.etag = ("".equals(etag)) ? null : etag;
            this.serverDate = serverDate;
            this.lastModified = lastModified;
            this.ttl = ttl;
            this.softTtl = softTtl;
            this.allResponseHeaders = allResponseHeaders;
        }


        private static List<Header> getAllResponseHeaders(Entry entry) {
            // entry里面若包含了所有的头部则直接返回
            if (entry.allResponseHeaders != null) {
                return entry.allResponseHeaders;
            }

            // Legacy fallback - copy headers from the map.
            // @TODO 这我tm就不懂了,allResponseHeaders里面的东西不是比responseHeaders还多的吗?
            return HttpHeaderParser.toAllHeaderList(entry.responseHeaders);
        }

        /**
         * 实例化一个CacheHeader对象
         * @param key 该缓存项对应的键
         * @param entry 需要被缓存的缓存项{@link volley.android.com.Cache.Entry}
         */
        CacheHeader(String key, Entry entry) {
            this(key, entry.etag, entry.serverDate, entry.lastModified, entry.ttl, entry.softTtl,
                    getAllResponseHeaders(entry));
            size = entry.data.length;
        }

        /**
         * 从输入数据流中读出一个 CacheHeader
         * @param is
         * @return
         * @throws IOException
         */
        static CacheHeader readHeader(CountingInputStream is) throws IOException {
            int magic = readInt(is);
            if (magic != CACHE_MAGIC){
                throw new IOException();
            }
            String key = readString(is);
            String etag = readString(is);
            long serverDate = readLong(is);
            long lastModified = readLong(is);
            long ttl = readLong(is);
            long softTtl = readLong(is);
            List<Header> allResponseHeaders = readHeaderList(is);

            return new CacheHeader(key, etag, serverDate, lastModified, ttl, softTtl, allResponseHeaders);
        }

        /**
         * 往输出流中写入CacheHeader中的数据
         * @param os
         * @return
         */
        boolean writeHeader(OutputStream os) {

            try {
                //先写入magic number
                writeInt(os, CACHE_MAGIC);
                writeString(os, key);
                //etag有可能会是null,需要处理
                writeString(os, etag == null ? "" : etag);
                writeLong(os, serverDate);
                writeLong(os, lastModified);
                writeLong(os, ttl);
                writeLong(os, softTtl);
                writeHeaderList(allResponseHeaders, os);
                os.flush();
                return true;
            } catch (IOException e) {
                VolleyLog.d("%s", e.toString());
                return false;
            }

        }

        /**
         * 创建一个缓存项目Entry
         * @param data 要放到缓存项目{@link volley.android.com.Cache.Entry#data}成员的字节流
         * @return
         */
        Entry toCacheEntry(byte[] data) {
            Entry e = new Entry();
            e.data = data;
            e.etag = etag;
            e.serverDate = serverDate;
            e.lastModified = lastModified;
            e.ttl = ttl;
            e.softTtl = softTtl;

            /** @FIXME 这里是否有问题? {@link HttpHeaderParser#toHeaderMap(List)}返回的map其实是可以修改的 */
            e.responseHeaders = HttpHeaderParser.toHeaderMap(allResponseHeaders);

            e.allResponseHeaders = Collections.unmodifiableList(allResponseHeaders);
            return e;
        }
    }

    //VisibleForTesting
    InputStream createInputStream(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    //VisibleForTesting
    OutputStream createOutputStream(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

}
