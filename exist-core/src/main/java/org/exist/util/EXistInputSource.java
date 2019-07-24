package org.exist.util;

import net.jcip.annotations.NotThreadSafe;
import org.xml.sax.InputSource;

import java.io.Closeable;

@NotThreadSafe
public abstract class EXistInputSource extends InputSource implements Closeable {

	private boolean closed = false;

	/**
	 * Get the length of the byte stream.
	 *
	 * @return the length of the byte stream.
	 *
	 * @deprecated Should be avoided, trying to get the length of a stream may involve buffering
	 */
	@Deprecated
	public abstract long getByteStreamLength();
	
	public abstract String getSymbolicPath();

	/**
	 * Determines if the InputSource was closed
	 *
	 * @return true if the InputSource was previously closed, false otherwise
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Throws an exception if the InputSource is closed
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	protected void assertOpen() {
		if(isClosed()) {
			throw new IllegalStateException("The InputSource has been closed");
		}
	}

	/**
	 * Re-Opens the InputSource by just
	 * setting the closed flag to false
	 */
	protected void reOpen() {
		this.closed = false;
	}

	/**
	 * Just sets the status of the InputStream to closed
	 *
	 * Sub-classes that override this should call {@code super.close()}
	 */
	@Override
    public void close() {
		closed = true;
	}
}
