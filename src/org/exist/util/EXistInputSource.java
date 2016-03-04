package org.exist.util;

import org.xml.sax.InputSource;

import java.io.Closeable;

public abstract class EXistInputSource extends InputSource implements Closeable {
	public EXistInputSource() {
		super();
	}
	
	public abstract long getByteStreamLength();
	
	public abstract String getSymbolicPath();

	@Override
    public abstract void close();
}
