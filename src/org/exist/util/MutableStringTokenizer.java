/*
 * MutableStringTokenizer.java - Jun 8, 2003
 * 
 * @author wolf
 */
package org.exist.util;

public class MutableStringTokenizer {

	private CharSequence buf_;
	private int pos_ = 0 ;
	private int last_ = 0 ;
	private String tokens_;
	
	public MutableStringTokenizer(CharSequence buf, String tokens) {
		buf_ = buf;
		tokens_ = tokens;
	}
	
	public MutableStringTokenizer() {
	}

	public void set(CharSequence buf) {
		buf_ = buf;
		pos_ = 0;
		last_ = 0;
	}
	
	public void set(CharSequence buf, String tokens) {
		set(buf);
		tokens_ = tokens;
	}
	
	public CharSequence nextToken() {
		if(buf_ == null)
			return null;
		char ch;
		CharSequence next = null;
		while(pos_ < buf_.length()) {
			ch = buf_.charAt(pos_);
			if(tokens_.indexOf(ch) > -1) {
				if(pos_ > 0) {
					next = new SharedCharSequence(buf_, last_, pos_);
					last_ = ++pos_;
					return next;
				} else
					++last_;
			}
			++pos_;
		}
		if(pos_ > last_) {
			next = new SharedCharSequence(buf_, last_, pos_);
			last_ = pos_;
		}
		return next;
	}
	
	private final static class SharedCharSequence implements CharSequence {
		
		CharSequence buf_ = null;
		int start_;
		int end_;
		
		public SharedCharSequence() {
		}
		
		public SharedCharSequence(CharSequence buf, int start, int end) {
			buf_ = buf;
			start_ = start;
			end_ = end;
		}
		
		public void set(CharSequence buf, int start, int end) {
			buf_ = buf;
			start_ = start;
			end_ = end;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.CharSequence#charAt(int)
		 */
		public char charAt(int index) {
			if(index < 0 || start_ + index > end_)
				throw new IndexOutOfBoundsException(String.valueOf(index));
			return buf_.charAt(start_ + index);
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object obj) {
			CharSequence s;
			if(obj instanceof CharSequence)
				s = (CharSequence)obj;
			else
				s = String.valueOf(obj);
			if(s.length() != length())
				return false;
			for(int i = 0; i < s.length(); i++) {
				if(s.charAt(i) != buf_.charAt(start_ + i))
					return false;
			}
			return true;
		}

		/* (non-Javadoc)
		 * @see java.lang.CharSequence#length()
		 */
		public int length() {
			return end_ - start_;
		}

		/* (non-Javadoc)
		 * @see java.lang.CharSequence#subSequence(int, int)
		 */
		public CharSequence subSequence(int start, int end) {
			return new SharedCharSequence(buf_, start_ + start, start_ + end);
		}
		
		public String toString() {
			StringBuffer b = new StringBuffer(length());
			for(int i = start_; i < end_; i++)
				b.append(buf_.charAt(i));
			return b.toString();
		}
	}
	
	public static void main(String[] args) {
		MutableStringTokenizer t = new MutableStringTokenizer("/PLAY/SCENE/SPEECH/", "/");
		CharSequence s;
		while((s = t.nextToken()) != null)
			System.out.println(s);
	}
}
