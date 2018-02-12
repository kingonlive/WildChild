package volley.android.com;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 日志输出工具
 */

public class VolleyLog {
    public static String TAG = "Volley";
    private static final String CLASS_NAME = VolleyLog.class.getName();

    public static boolean DEBUG = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * 设置日志输出的TAG,防止和其他日志(如果TAG名称相同)混在一起
     * @param tag 新的TAG
     */
    public static void setTag(String tag) {
        TAG = tag;
    }

    public static void v(String format, Object... args) {
        if (DEBUG) {
            Log.v(TAG, buildMessage(format, args));
        }
    }

    public static void d(String format, Object... args) {
        Log.d(TAG, buildMessage(format, args));
    }

    public static void e(String format, Object... args) {
        Log.e(TAG, buildMessage(format, args));
    }

    public static void e(Throwable tr, String format, Object... args) {
        Log.e(TAG, buildMessage(format, args), tr);
    }

    public static void wtf(String format, Object... args) {
        Log.wtf(TAG, buildMessage(format, args));
    }

    public static void wtf(Throwable tr, String format, Object... args) {
        Log.wtf(TAG, buildMessage(format, args), tr);
    }

    /**
     * 返回格式化过的信息,附带调用信息(调用线程id,调用类名及函数）
     * @param format　指定的字符串格式
     * @param args 字符串格式的变量部分,见{@link String#format(String, Object...)}
     * @return
     */
    private static String buildMessage(String format, Object... args) {
        String msg = (args == null) ? format : String.format(Locale.US, format, args);

        //函数调用栈栈帧
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();

        String caller = "<unknown>";

        //跳过栈顶两层虚拟机内部结构的栈帧
        for (int i = 2; i < trace.length; i++) {
            String clazz = trace[i].getClassName();
            if (!clazz.equals(VolleyLog.CLASS_NAME)) {
                String callingClass = trace[i].getClassName();
                callingClass = callingClass.substring(callingClass.lastIndexOf('.') + 1);
                callingClass = callingClass.substring(callingClass.lastIndexOf('$') + 1);

                caller = callingClass + "." + trace[i].getMethodName();
                break;
            }
        }
        return String.format(Locale.US, "[%d] %s: %s",
                Thread.currentThread().getId(), caller, msg);
    }

    /**
     * 拥有一串日志节点的日志
     */
    static class MarkerLog{
        public static final boolean ENABLED = VolleyLog.DEBUG;

        /**
         * 第一个日志节点到最后一个日志节点的最小时间间隔
         */
        private static final long MIN_DURATION_FOR_LOGGING_MS = 0;

        /**
         *  包含名,线程id,时间错的日志节点信息
         */
        private static class Marker {
            public final String name;
            public final long thread;
            public final long time;

            public Marker(String name, long thread, long time) {
                this.name = name;
                this.thread = thread;
                this.time = time;
            }
        }
        private final List<Marker> mMarkers = new ArrayList<Marker>();
        private boolean mFinished = false;

        /**
         * 添加一个日志节点
         * @param name 日志节点的名称
         * @param threadId  线程ID
         */
        public synchronized void add(String name, long threadId){
            //已完成的日志节点不可再添加日志信息
            if (mFinished) {
                throw new IllegalStateException("Marker added to finished log");
            }
        }

        /**
         * 关闭日志节点,当开始和结束的两个日志节点时间间隔大于{@link #MIN_DURATION_FOR_LOGGING_MS} 时将日志节点打印出来
         * @param header 日志节点打印前先打印的头部信息
         */
        public synchronized void finish(String header){
            mFinished = true;

            long duration = getTotalDuration();

            //首尾日志节点的时间必须要大于指定的最小值
            if (duration <= MIN_DURATION_FOR_LOGGING_MS) {
                return;
            }

            //首个日志节点的创建时间
            long prevTime = mMarkers.get(0).time;

            //输出日志头部信息
            d("(%-4d ms) %s", duration, header);

            //遍历每个时间节点,输出 '(耗时) [线程ID] 名称'
            for (Marker marker : mMarkers) {
                long thisTime = marker.time;
                d("(+%-4d) [%2d] %s", (thisTime - prevTime), marker.thread, marker.name);
                prevTime = thisTime;
            }
        }

        /**
         * 返回首尾两个日志节点的时间间隔
         * @return
         */
        private long getTotalDuration(){
            if (mMarkers.size() == 0) {
                return 0;
            }

            long first = mMarkers.get(0).time;
            long last = mMarkers.get(mMarkers.size() - 1).time;
            return last - first;
        }
    }
}
