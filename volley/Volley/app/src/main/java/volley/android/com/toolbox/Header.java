package volley.android.com.toolbox;


import android.text.TextUtils;

/** HTTP头部 */
public final class Header {
    private final String mName;
    private final String mValue;

    public Header(String name, String value) {
        mName = name;
        mValue = value;
    }

    public final String getName() {
        return mName;
    }

    public final String getValue() {
        return mValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Header header = (Header) o;

        return TextUtils.equals(mName, header.mName)
                && TextUtils.equals(mValue, header.mValue);
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mValue.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Header[name=" + mName + ",value=" + mValue + "]";
    }
}
