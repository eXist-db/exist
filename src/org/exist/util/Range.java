/*
 * Created on Sep 8, 2003
 */
package org.exist.util;

public class Range {
	
	private long start_, end_;
	
	public Range(long start, long end) {
		start_ = start;
		end_ = end;
	}
	
	public long getStart() {
		return start_;
	}
	
	public long getEnd() {
		return end_;
	}
}
