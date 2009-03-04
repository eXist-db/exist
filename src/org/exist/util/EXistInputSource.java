package org.exist.util;

import org.xml.sax.InputSource;

public abstract class EXistInputSource extends InputSource {
	public EXistInputSource() {
		super();
	}
	
	public abstract long getByteStreamLength();
	
	public abstract String getSymbolicPath();
}
