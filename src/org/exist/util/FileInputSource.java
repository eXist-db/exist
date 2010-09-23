package org.exist.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;

public class FileInputSource extends EXistInputSource {

	private File file;
	private InputStream inputStream;

	/**
	 * Empty constructor
	 */
	public FileInputSource() {
		this(null);
	}
	
	/**
	 * Constructor which calls {@link #setFile(File)}
	 * @param file
	 * The file passed to {@link #setFile(File)}
	 */
	public FileInputSource(File file) {
		super();
		inputStream = null;
		setFile(file);
	}
	
	/**
	 * If a file source has been set, the File
	 * object used for that is returned
	 * @return
	 * The File object.
	 */
	public File getFile() {
		return file;
	}
	
	/**
	 * This method sets the File object used to get
	 * the uncompressed stream of data
	 * @param file
	 * The File object pointing to the GZIP file.
	 */
	public void setFile(File file) {
		close();
		this.file=file;
		// Remember: super.setSystemId must be used instead of local implementation
		if(file!=null)
			super.setSystemId(file.toURI().toASCIIString());
		else
			super.setSystemId(null);
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
        // close any open stream first
		close();

        if(file != null) {
			try {
				inputStream = new BufferedInputStream(new FileInputStream(file));
			} catch(Exception fnfe) {
				// No way to notify :-(
			}
		}
		
		return inputStream;
	}

    public void close() {
        if(inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore if the stream is already closed
            }
	        inputStream = null;
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
		long retval=-1L;
		
		if(file!=null) {
			retval=file.length();
		}
		return retval;
	}
	
	public String getSymbolicPath()
	{
		return file.getAbsolutePath();
	}
}
