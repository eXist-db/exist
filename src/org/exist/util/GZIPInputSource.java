package org.exist.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
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
	extends EXistInputSource
{
	private File file;
	private InputStream inputStream;
	private long streamLength;
	
	/**
	 * Empty constructor
	 */
	public GZIPInputSource() {
		super();
		file=null;
		inputStream = null;
		streamLength = -1L;
	}
	
	/**
	 * Constructor 
	 */
	public GZIPInputSource(File gzipFile) {
		super();
		file = gzipFile;
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
		InputStream retval = null;
		try {
			InputStream is = new BufferedInputStream(new FileInputStream(file));
			retval = inputStream = new GZIPInputStream(is);
		} catch(IOException ioe) {
			// No way to notify :-(
		}
		
		return retval;
	}
	
    public void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore if the stream is already closed
            } finally {
                inputStream = null;
            }
        }
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
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	public void setSystemId(String systemId) {
		// Nothing, so collateral effects are avoided!
	}
	
	public long getByteStreamLength() {
		if(streamLength==-1L) {
			InputStream str=getByteStream();
			byte[] buffer=new byte[4096];
			long retval=0;
			int readed;
			try {
				while((readed=str.read(buffer,0,buffer.length))!=-1) {
					retval+=readed;
				}
				streamLength = retval;
				close();
			} catch(IOException ioe) {
				// DoNothing(R)
			}
		}

		return streamLength;
	}
	
	public String getSymbolicPath()
	{
		return file.getAbsolutePath();
	}
	
	protected void finalize()
		throws Throwable
	{
		close();
	}
}
