/*
 * Created on Sep 8, 2003
 */
package org.exist.util;

public class Range {
	
	private long start_;
	private long end_;
	
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
	
	public boolean inRange(long value) {
		return value >= start_ && value <= end_;
	}
	
	public int getDistance() {
		return (int)(end_ - start_) + 1;
	}
}
