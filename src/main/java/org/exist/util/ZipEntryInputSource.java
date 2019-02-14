package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This class extends InputSource to be able to deal with
 * ZipEntry objects from ZIP compressed files. Its main
 * feature is that each time {@link #getByteStream()} is
 * called, a new uncompressed stream is created from the
 * ZipEntry in the compressed ZIP file.
 * This is very useful for eXist, which works in two steps:
 * validation and insertion.
 * 
 * @author jmfernandez
 */
public final class ZipEntryInputSource extends EXistInputSource {
	private final static Logger LOG = LogManager.getLogger(ZipEntryInputSource.class);

	private final ZipEntry zipEntry;
	private final ZipFile zipFile;
	
	public ZipEntryInputSource(final ZipFile zipFile, final ZipEntry zipEntry) {
		super();
		this.zipFile = zipFile;
		this.zipEntry = zipEntry;
	}

	/**
	 * @see org.xml.sax.InputSource#getByteStream()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public InputStream getByteStream() {
		assertOpen();
		try {
			return zipFile.getInputStream(zipEntry);
		} catch(final IOException e) {
			LOG.error(e);
			return null;
		}
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
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
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public void setCharacterStream(final Reader r) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}

	/**
	 * @see EXistInputSource#getByteStreamLength()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public long getByteStreamLength() {
		assertOpen();
		return zipEntry.getSize();
	}

	/**
	 * @see EXistInputSource#getSymbolicPath()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public String getSymbolicPath() {
		assertOpen();
		return zipFile.getName() + "#" + zipEntry.getName();
	}

	@Override
    public void close() {
		if(!isClosed()) {
			super.close();
		}
    }
}
