package org.exist.util;

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
 *
 */
public final class ZipEntryInputSource
	extends EXistInputSource
{
	private ZipEntry zipEntry;
	private ZipFile zipFile;
	
	public ZipEntryInputSource()
	{
		super();
		zipEntry=null;
		zipFile=null;
	}
	
	public ZipEntryInputSource(ZipFile zipFile,ZipEntry zipEntry)
	{
		this();
		setZipEntry(zipFile,zipEntry);
	}
	
	public void setZipEntry(ZipFile zipFile,ZipEntry zipEntry)
	{
		this.zipFile=zipFile;
		this.zipEntry=zipEntry;
	}
	
	public InputStream getByteStream() {
		InputStream retval=null;
		if(zipFile!=null && zipEntry!=null) {
			try {
				retval=zipFile.getInputStream(zipEntry);
			} catch(IOException ioe) {
				// No way to notify :-(
			}
		}
			
		return retval;
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	public void setByteStream(InputStream is) {
		// Nothing, so collateral effects are avoided!
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	public void setCharacterStream(Reader r) {
		// Nothing, so collateral effects are avoided!
	}
	
	public long getByteStreamLength() {
		long retval=-1;
		
		if(zipEntry!=null) {
			retval=zipEntry.getSize();
		}
		return retval;
	}
	
	public String getSymbolicPath() {
		return zipFile.getName()+"#"+zipEntry.getName();
	}
}
