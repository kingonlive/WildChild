# 简介
- volley是谷歌在2013年GoogleIO上推出的网络库，专用于大并发小数据量的网络请求，在架构设计上高内聚低耦合，扩展性和灵活性都非常好．
- 官方介绍:　https://developer.android.com/training/volley/index.html
- volley项目地址:　https://github.com/google/volley


# 整体架构
整体架构大致可以分成四层
- 用户接口层，包含RequestQueue/Request/JsonRequest/StringRequest/ImageRequest/ErrorListener等
- 核心层：CacheDispatcher和NetworkDispatcher
- 基础层：网络和缓存
![N|Solid](https://raw.githubusercontent.com/kingonlive/WildChild/master/volley/overall.png)

# 使用示例
```
// Instantiate the RequestQueue.
RequestQueue queue = Volley.newRequestQueue(this);
String url ="http://www.google.com";

// Request a string response from the provided URL.
StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
    @Override
    public void onResponse(String response) {
        // Display the first 500 characters of the response string.
        mTextView.setText("Response is: "+ response.substring(0,500));
    }
}, new Response.ErrorListener() {
    @Override
    public void onErrorResponse(VolleyError error) {
        mTextView.setText("That didn't work!");
    }
});
// Add the request to the RequestQueue.
queue.add(stringRequest);

```


# 类图
![N|Solid](https://raw.githubusercontent.com/kingonlive/WildChild/master/volley/classes.png)

# 线程模型
- 下图是一个示例，实际上有四条network线程一条cache线程
- 用户在主线程添加请求，默认将请求加到缓存请求队列，当本地或内存无缓存请求时，请求将被添加到网络请求队列
- 缓存线程在有请求结果时将结果派发到UI线程,网络线程在请求网络并解析请求结果后，将请求结果派发到UI线程
![N|Solid](https://raw.githubusercontent.com/kingonlive/WildChild/master/volley/threadmodel.png)
