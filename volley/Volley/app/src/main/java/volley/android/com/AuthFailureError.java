package volley.android.com;

import android.content.Intent;

/**
 * 网络请求时出现的身份认证错误
 */

public class AuthFailureError extends VolleyError{
    /**
     * 解决该认证错误的一个Intent,该Intent会调启一个对话框让用户输入密码
     */
    private Intent mResolutionIntent;

    public AuthFailureError() { }

    public AuthFailureError(Intent intent) {
        mResolutionIntent = intent;
    }

    public AuthFailureError(NetworkResponse response) {
        super(response);
    }

    public AuthFailureError(String message) {
        super(message);
    }

    public AuthFailureError(String message, Exception reason) {
        super(message, reason);
    }

    public Intent getResolutionIntent() {
        return mResolutionIntent;
    }

    @Override
    public String getMessage() {
        if (mResolutionIntent != null) {
            return "User needs to (re)enter credentials.";
        }
        return super.getMessage();
    }
}
