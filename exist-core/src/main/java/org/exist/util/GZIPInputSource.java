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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * This class extends InputSource to be able to deal with
 * GZIP compressed files. Its main feature is that each time
 * {@link #getByteStream()} is called, a new uncompressed
 * stream is created from the compressed GZIP gzipFile.
 * This is very useful for eXist, which works in two steps:
 * validation and insertion.
 * 
 * @author José María Fernández (jmfg@users.sourceforge.net)
 *
 */
public final class GZIPInputSource extends EXistInputSource {
	private final static Logger LOG = LogManager.getLogger(GZIPInputSource.class);
	private final Path gzipFile;
	private Optional<InputStream> inputStream = Optional.empty();
	
	/**
	 * Constructor which with gzip-gzipFile
	 * @param gzipFile The gzip gzipFile.
	 */
	public GZIPInputSource(final Path gzipFile) {
		super();
		this.gzipFile = gzipFile;
	}
	
	/**
	 * This method was re-implemented to open a
	 * new GZIPInputStream each time it is called.
	 * @return
	 * If the gzipFile was set, and it could be opened, and it was
	 * a correct gzip gzipFile, a GZIPInputStream object.
	 * null, otherwise.
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public InputStream getByteStream() {
		assertOpen();
		try {
			final InputStream is = new BufferedInputStream(Files.newInputStream(gzipFile));
			this.inputStream = Optional.of(new GZIPInputStream(is));
		} catch (final IOException ioe) {
			LOG.error(ioe);
		}
		
		return inputStream.orElse(null);
	}
    
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public void setByteStream(final InputStream is) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public void setCharacterStream(final Reader r) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public void setSystemId(final String systemId) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}

	/**
	 * @see EXistInputSource#getByteStreamLength()
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public long getByteStreamLength() {
		assertOpen();
		try {
			return Files.size(gzipFile);
		} catch (final IOException e) {
			LOG.error(e);
			return -1;
		}
	}

	/**
	 * @see EXistInputSource#getSymbolicPath()
	 *
	 * @throws IllegalStateException If the InputSource was previously closed
	 */
	@Override
	public String getSymbolicPath() {
		assertOpen();
		return gzipFile.toAbsolutePath().toString();
	}

	@Override
	public void close() {
		if(!isClosed()) {
			try {
				if (inputStream.isPresent()) {
					try {
						inputStream.get().close();
					} catch (final IOException e) {
						LOG.warn(e);
					} finally {
						this.inputStream = Optional.empty();
					}
				}
			} finally {
				super.close();
			}
		}
	}
}
