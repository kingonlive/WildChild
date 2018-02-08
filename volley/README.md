# 简介
volley是谷歌在2013年GoogleIO上推出的网络库，专用于大并发小数量的网络请求，在架构设计上高内聚低耦合，扩展性和灵活性都非常好．
官方介绍:　https://developer.android.com/training/volley/index.html
volley项目地址:　https://github.com/google/volley


# 整体架构
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
![N|Solid](https://raw.githubusercontent.com/kingonlive/WildChild/master/volley/threadmodel.png)
