package org.exist.util;

/**
 * StringUtil.java
 * 
 * @author Wolfgang Meier
 */
public class StringUtil {

	public final static void utfwrite(
		byte[] data,
		int len,
		FastStringBuffer s) {
		final int slen = s.length();
		for (int i = 0; i < slen; i++) {
			final int code = (int) s.charAt(i);
			if (code >= 0x01 && code <= 0x7F)
				data[len++] = (byte) code;
			else if (((code >= 0x80) && (code <= 0x7FF)) || code == 0) {
				data[len++] = (byte) (0xC0 | (code >> 6));
				data[len++] = (byte) (0x80 | (code & 0x3F));
			} else {
				data[len++] = (byte) (0xE0 | (code >>> 12));
				data[len++] = (byte) (0x80 | ((code >> 6) & 0x3F));
				data[len++] = (byte) (0x80 | (code & 0x3F));
			}
		}
	}

	public final static void utfwrite(byte[] data, int len, String s) {
		final int slen = s.length();
		for (int i = 0; i < slen; i++) {
			final int code = (int) s.charAt(i);
			if (code >= 0x01 && code <= 0x7F)
				data[len++] = (byte) code;
			else if (((code >= 0x80) && (code <= 0x7FF)) || code == 0) {
				data[len++] = (byte) (0xC0 | (code >> 6));
				data[len++] = (byte) (0x80 | (code & 0x3F));
			} else {
				data[len++] = (byte) (0xE0 | (code >>> 12));
				data[len++] = (byte) (0x80 | ((code >> 6) & 0x3F));
				data[len++] = (byte) (0x80 | (code & 0x3F));
			}
		}
	}

	public final static int utflen(FastStringBuffer s) {
		final int slen = s.length();
		int len = 0;
		for (int i = 0; i < slen; i++) {
			final int code = (int) s.charAt(i);
			if (code >= 0x01 && code <= 0x7F)
				++len;
			else if (((code >= 0x80) && (code <= 0x7FF)) || code == 0) {
				len += 2;
			} else {
				len += 3;
			}
		}
		return len;
	}

	public final static int utflen(String s) {
		final int slen = s.length();
		int len = 0;
		for (int i = 0; i < slen; i++) {
			final int code = (int) s.charAt(i);
			if (code >= 0x01 && code <= 0x7F)
				++len;
			else if (((code >= 0x80) && (code <= 0x7FF)) || code == 0) {
				len += 2;
			} else {
				len += 3;
			}
		}
		return len;
	}
    
    public final static String hexDump(byte[] data) {
        StringBuffer buf = new StringBuffer(data.length);
        for(int i = 0; i < data.length; i++) {
            buf.append(Integer.toHexString((int)data[i]));
            buf.append(' ');
        }
        return buf.toString();
    }
}
