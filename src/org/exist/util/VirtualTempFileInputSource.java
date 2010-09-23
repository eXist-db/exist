package org.exist.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;


/**
 * This class extends {@link org.xml.sax.InputSource}, so
 * it also manages {@link java.io.File} and
 * {@link org.exist.util.VirtualTempFile} as input sources.
 * 
 * @author jmfernandez
 *
 */
public class VirtualTempFileInputSource
	extends EXistInputSource
{
	private VirtualTempFile vtempFile;
	private File file;
	private String absolutePath;
	
	public VirtualTempFileInputSource(VirtualTempFile vtempFile)
		throws IOException
	{
		this.file = null;
		this.vtempFile = vtempFile;
		// Temp file must be immutable from this point
		vtempFile.close();
		
		if(vtempFile.tempFile!=null) {
			absolutePath = vtempFile.tempFile.getAbsolutePath();
			super.setSystemId(vtempFile.tempFile.toURI().toASCIIString());
		} else {
			absolutePath="";
		}
	}
	
	public VirtualTempFileInputSource(File file) {
		this.file = file;
		this.vtempFile = null;
		
		if(file!=null) {
			absolutePath = file.getAbsolutePath();
			super.setSystemId(file.toURI().toASCIIString());
		}
	}
	
	public InputStream getByteStream() {
		InputStream bs =null;
		
		// An stream is something without a URI, like a memory buffer
		try {
			if(vtempFile!=null)
				bs = vtempFile.getByteStream();
			else if(file!=null)
				bs = new BufferedInputStream(new FileInputStream(file));
		} catch(IOException ioe) {
			// DoNothing(R)
		}
		
		return bs;
	}
	
	public long getByteStreamLength() {
		long length = -1L;
		
		if(vtempFile!=null) {
			length = vtempFile.length();
		} else if(file!=null) {
			length = file.length();
		}
		
		return length;
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
	
	public void free() {
		if(vtempFile!=null) {
			vtempFile.delete();
			vtempFile = null;
		}
		if(file!=null)
			file=null;
	}
	
	protected void finalize()
		throws Throwable
	{
		free();
	}

	@Override
	public String getSymbolicPath() {
		return absolutePath;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}
}
