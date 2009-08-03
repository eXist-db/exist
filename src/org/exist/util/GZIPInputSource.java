package org.exist.util;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

/**
 * This class extends InputSource to be able to deal with
 * GZIP compressed files. Its main feature is that each time
 * {@link #getByteStream()} is called, a new uncompressed
 * stream is created from the compressed GZIP file.
 * This is very useful for eXist, which works in two steps:
 * validation and insertion.
 * 
 * @author José María Fernández (jmfg@users.sourceforge.net)
 *
 */
public final class GZIPInputSource
	extends FileInputSource
{
	/**
	 * Empty constructor
	 */
	public GZIPInputSource() {
		super();
	}
	
	/**
	 * Constructor which calls {@link #getFile()}
	 * @param gzipFile
	 * The file passed to {@link #getFile()}
	 */
	public GZIPInputSource(File gzipFile) {
		super(gzipFile);
	}
	
	/**
	 * This method was re-implemented to open a
	 * new GZIPInputStream each time it is called.
	 * @return
	 * If the file was set, and it could be opened, and it was
	 * a correct gzip file, a GZIPInputStream object.
	 * null, otherwise.
	 */
	public InputStream getByteStream() {
		InputStream retval=super.getByteStream();
		
		if(retval!=null) {
			try {
				retval=new GZIPInputStream(retval);
			} catch(IOException ioe) {
				retval=null;
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
		InputStream str=getByteStream();
		byte[] buffer=new byte[4096];
		long retval=0;
		int readed;
		try {
			while((readed=str.read(buffer,0,buffer.length))!=-1) {
				retval+=readed;
			}
			str.close();
		} catch(IOException ioe) {
			retval=-1;
		}

		return retval;
	}
}
