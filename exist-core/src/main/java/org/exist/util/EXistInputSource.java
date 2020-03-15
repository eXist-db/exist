/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
