/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;


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
		this(vtempFile,null);
	}
	
	public VirtualTempFileInputSource(VirtualTempFile vtempFile,String encoding)
		throws IOException
	{
		this.file = null;
		this.vtempFile = vtempFile;
		// Temp file must be immutable from this point
		vtempFile.close();
		if(encoding!=null)
			{super.setEncoding(encoding);}
		
		if(vtempFile.tempFile!=null) {
			absolutePath = vtempFile.tempFile.getAbsolutePath();
			super.setSystemId(vtempFile.tempFile.toURI().toASCIIString());
		} else {
			absolutePath="";
		}
	}
	
	public VirtualTempFileInputSource(File file) {
		this(file,null);
	}
	
	public VirtualTempFileInputSource(File file,String encoding) {
		this.file = file;
		this.vtempFile = null;
		
		if(encoding!=null)
			{super.setEncoding(encoding);}
		
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
				{bs = vtempFile.getByteStream();}
			else if(file!=null)
				{bs = new BufferedInputStream(new FileInputStream(file));}
		} catch(final IOException ioe) {
			// DoNothing(R)
		}
		
		return bs;
	}
	
	public Reader getCharacterStream() {
		final String encoding = getEncoding();
		Reader retval = null;
		if(encoding!=null) {
			final InputStream is = getByteStream();
			if(is!=null) {
				try {
					retval = new InputStreamReader(is,encoding);
				} catch(final UnsupportedEncodingException uee) {
					// DoNothing(R)
				}
			}
		}
		
		return retval;
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
			{file=null;}
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
