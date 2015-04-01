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
import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;;

/**
 * 
 * This class is a cross-over of many others, but mainly File and OutputStream
 * 
 * @author jmfernandez
 *
 * @deprecated Using this class should be avoided as it publishes an API that
 * makes using it correctly without leaking resources very difficult. It is very
 * likely that most uses of this class do not correctly cleanup the resources
 * they obtain. It needs to be rewritten...
 */
@Deprecated
public class VirtualTempFile
	extends OutputStream
{
    private final static Logger LOG = LogManager.getLogger(VirtualTempFile.class);
    
    private final static int DEFAULT_MAX_CHUNK_SIZE = 0x40000;
    private final static String DEFAULT_TEMP_PREFIX = "eXistRPCV";
    private final static String DEFAULT_TEMP_POSTFIX = ".res";
	
	protected File tempFile;
	protected boolean deleteTempFile;
	protected ByteArrayOutputStream baBuffer;
	protected FileOutputStream strBuffer;
	protected OutputStream os;
	
	protected byte[] tempBuffer;

	protected int maxMemorySize;
	protected int maxChunkSize;
	
	protected long vLength;
	
	protected String temp_prefix;
	protected String temp_postfix;
	
	/**
	 * Constructor for a fresh VirtualTempFile
	 */
	public VirtualTempFile() {
		this(DEFAULT_MAX_CHUNK_SIZE,DEFAULT_MAX_CHUNK_SIZE);
	}
	
	/**
	 * Constructor for a fresh VirtualTempFile, with some params
	 * @param maxMemorySize
	 * @param maxChunkSize
	 */
	public VirtualTempFile(int maxMemorySize,int maxChunkSize) {
		this.maxMemorySize = maxMemorySize;
		this.maxChunkSize = maxChunkSize;
		
		vLength = -1L;
		
		baBuffer = new ByteArrayOutputStream(maxMemorySize);
		strBuffer = null;
		
		tempFile = null;
		tempBuffer = null;
		
		deleteTempFile = true;
		
		os = baBuffer;
		temp_prefix = DEFAULT_TEMP_PREFIX;
		temp_postfix = DEFAULT_TEMP_POSTFIX;
	}
	
	/**
	 * Constructor for an already known file
	 * @param theFile
	 */
	public VirtualTempFile(File theFile) {
		this(theFile,DEFAULT_MAX_CHUNK_SIZE);
	}
	
	/**
	 * Constructor for an already known file, with params
	 * @param theFile
	 * @param maxChunkSize
	 */
	public VirtualTempFile(File theFile,int maxChunkSize) {
		// This one is not going to be used, but it is set to avoid uninitialized variables
		this.maxMemorySize = maxChunkSize;
		this.maxChunkSize = maxChunkSize;
		
		baBuffer = null;
		strBuffer = null;
		os = null;
		
		tempFile = theFile;
		deleteTempFile = false;
		vLength = theFile.length();
		tempBuffer = null;
		temp_prefix = DEFAULT_TEMP_PREFIX;
		temp_postfix = DEFAULT_TEMP_POSTFIX;
	}
	
	/**
	 * Constructor for an already known memory block
	 * @param theBlock
	 */
	public VirtualTempFile(byte[] theBlock) {
		this(theBlock,theBlock.length,DEFAULT_MAX_CHUNK_SIZE);
	}
	
	/**
	 * Constructor for an already known memory block, with params
	 * @param theBlock
	 * @param maxMemorySize
	 * @param maxChunkSize
	 */
	public VirtualTempFile(byte[] theBlock,int maxMemorySize, int maxChunkSize) {
		// This one is not going to be used, but it is set to avoid uninitialized variables
		this.maxMemorySize = maxMemorySize;
		this.maxChunkSize = maxChunkSize;
		
		baBuffer = null;
		strBuffer = null;
		os = null;
		
		temp_prefix = DEFAULT_TEMP_PREFIX;
		temp_postfix = DEFAULT_TEMP_POSTFIX;
		tempFile = null;
		deleteTempFile = true;
		vLength = theBlock.length;
		if(vLength<=maxMemorySize) {
			tempBuffer = theBlock;
		} else {
			try {
		        tempFile = File.createTempFile(temp_prefix, temp_postfix);
		        
		        tempFile.deleteOnExit();
		        LOG.debug("Writing to temporary file: " + tempFile.getName());
		        
		        final OutputStream tmpBuffer = new FileOutputStream(tempFile);
		        try {
		        	tmpBuffer.write(theBlock);
		        } finally {
		        	tmpBuffer.close();
		        }
			} catch(final IOException ioe) {
				// Do Nothing(R)
			}
		}
	}
	
	/**
	 * The prefix string used when the temp file is going to be created
	 * @return prefix string
	 */
	public String getTempPrefix() {
		return temp_prefix;
	}
	
	/**
	 * The postfix string used when the temp file is going to be created
	 * @return  postfix string
	 */
	public String getTempPostfix() {
		return temp_postfix;
	}
	
	/**
	 * It sets the used prefix string on temp filename creation
	 * @param newPrefix
	 */
	public void setTempPrefix(String newPrefix) {
		if(newPrefix==null)
			{newPrefix=DEFAULT_TEMP_PREFIX;}
		
		temp_prefix = newPrefix;
	}
	
	/**
	 * It sets the used prefix string on temp filename creation
	 * @param newPostfix
	 */
	public void setTempPostfix(String newPostfix) {
		if(newPostfix==null)
			{newPostfix=DEFAULT_TEMP_POSTFIX;}
		
		temp_postfix = newPostfix;
	}
	
	/**
	 * Method from OutputStream
	 */
	public void close()
		throws IOException
	{
		if(baBuffer!=null) {
			tempBuffer = baBuffer.toByteArray();
			baBuffer = null;
			vLength = tempBuffer.length;
		}
		
		if(strBuffer!=null) {
			strBuffer.close();
			strBuffer = null;
			vLength = tempFile.length();
		}
		
		if(os!=null)
			{os=null;}
	}
	
	/**
	 * Method from OutputStream
	 */
	public void flush()
		throws IOException
	{
		if(os==null)
			{throw new IOException("No stream to flush");}
		os.flush();
	}
	
	/**
     * The method <code>getChunk</code>
     *
     * @param offset a <code>long</code> value
     * @return a <code>byte[]</code> value
     * @exception IOException if an error occurs
     */
	public byte[] getChunk(long offset)
		throws IOException
	{
		byte[] data = null;
		
		if(os!=null)  {close();}
		
		if(tempFile!=null) {
	        final RandomAccessFile raf = new RandomAccessFile(tempFile, "r");
	        raf.seek(offset);
	        long remaining = raf.length() - offset;
	        if(remaining > maxChunkSize)
	            {remaining = maxChunkSize;}
	        else if(remaining<0)
	        	{remaining = 0;}
	        data = new byte[(int)remaining];
	        raf.readFully(data);
	        raf.close();
		} else if(tempBuffer!=null) {
			long remaining = tempBuffer.length - offset;
	        if(remaining > maxChunkSize)
	            {remaining = maxChunkSize;}
	        else if(remaining<0)
	        	{remaining = 0;}
	        data = new byte[(int)remaining];
	        if(remaining>0)
	        	{System.arraycopy(tempBuffer,(int)offset,data,0,(int)remaining);}
		}
		
		return data;
	}
	
	public boolean exists() {
		return tempFile!=null || tempBuffer!=null || baBuffer!=null;
	}
	
	public long length() {
		if(os!=null) {
			try {
				close();
			} catch(final IOException ioe) {
				// IgnoreIT(R)
			}
		}
		
		return vLength;
	}
	
	/**
	 * Method from File
	 * @return Always returns true
	 */
	public boolean delete() {
		if(os!=null) {
			try {
				close();
			} catch(final IOException ioe) {
				// IgnoreIT(R)
			}
		}
		
		if(tempFile!=null) {
			if(strBuffer!=null) {
				try {
					strBuffer.close();
				} catch(final IOException ioe) {
					// IgnoreIT(R)
				}
				strBuffer=null;
			}
			
			if(deleteTempFile)
				{tempFile.delete();}
			tempFile=null;
		}
		
		if(baBuffer!=null) {
			try {
				baBuffer.close();
			} catch(final IOException ioe) {
				// IgnoreIT(R)
			}
			baBuffer = null;
		}
		
		if(tempBuffer!=null) {
			tempBuffer = null;
		}
		
		return true;
	}
	
	private void writeSwitch()
		throws IOException
	{
		if(tempFile==null) {
			tempFile = File.createTempFile(temp_prefix, temp_postfix);

			tempFile.deleteOnExit();
			LOG.debug("Writing to temporary file: " + tempFile.getName());

			strBuffer = new FileOutputStream(tempFile);
			strBuffer.write(baBuffer.toByteArray());
			os = strBuffer;
		}
	}
	
	@Override
	public void write(int b)
		throws IOException
	{
		if(os==null) {
			throw new IOException("No stream to write to");
		}
		
		os.write(b);
		if(baBuffer!=null && baBuffer.size()>maxMemorySize) {
			writeSwitch();
		}
	}
	
	public void write(byte[] b, int off, int len)
		throws IOException
	{
		if(os==null) {
			throw new IOException("No stream to write to");
		}
		
		os.write(b,off,len);
		if(baBuffer!=null && baBuffer.size()>maxMemorySize) {
			writeSwitch();
		}
	}
	
	/**
	 * A commodity method to write the whole content of an InputStream
	 */
	public void write(InputStream is)
		throws IOException
	{
		write(is,-1L);
	}
	
	/**
	 * A commodity method to write the whole content of an InputStream,
	 * giving an optional max length (honored when it is bigger than 0)
	 */
	public void write(InputStream is,long lengthHint)
		throws IOException
	{
		if(os==null) {
			throw new IOException("No stream to write to");
		}
		
		final byte[] buffer = new byte[maxChunkSize];
		long off=0;
		int count=0;
		do {
			count = is.read(buffer);
			if(count>0) {
				os.write(buffer,0,count);
				off += count;
			}
			if(baBuffer!=null && baBuffer.size()>maxMemorySize) {
				writeSwitch();
			}
		} while(count!=-1 && (lengthHint<=0 || off < lengthHint));
	}
	
	/**
	 * An easy way to obtain an InputStream
	 * @return byte stream
	 * @throws IOException
	 */
	public InputStream getByteStream()
		throws IOException
	{
		if(os!=null)  {close();}
		
		InputStream result = null;
		if(tempFile!=null) {
			result = new BufferedInputStream(new FileInputStream(tempFile),655360);
		} else if(tempBuffer!=null) {
			result = new ByteArrayInputStream(tempBuffer);
		}
		
		return result;
	}
	
	/**
	 * It returns either a byte array or a File
	 * with the content. The initial threshold rules
	 * which kind of object you are getting
	 * @return Either a File or a byte[] object
	 */
	public Object getContent()
	{
		try {
			if(os!=null)  {close();}
		} catch(final IOException ioe) {
			// IgnoreIT(R)
		}
		
		return (tempFile!=null)?tempFile:tempBuffer;
	}
	
	/**
	 * Method to force materialization as a (temp)file the VirtualTempFile instance
	 * @return A (temporal) file with the content
	 * @throws IOException
	 */
	public File toFile()
		throws IOException
	{
		// First, forcing the write to temp file
		writeSwitch();
		// Second, close
		if(os!=null)  {close();}
		
		final File retFile = tempFile;
		
		// From this point the tempFile is not managed any more by this VirtualTempFile
		tempFile = null;
		
		return retFile;
	}
	
	/**
	 * Method to materialize the accumulated content in an OutputStream
	 * @param out The output stream where the content is going to be written
	 */
	public void writeToStream(OutputStream out)
		throws IOException
	{
		final InputStream result = null;
		if(tempFile!=null) {
//			byte[] writeBuffer=new byte[65536];
			final InputStream input = new BufferedInputStream(new FileInputStream(tempFile));
            IOUtils.copy(input, out);
            IOUtils.closeQuietly(input);

//			try {
//				int readBytes;
//				while((readBytes = input.read(writeBuffer,0,writeBuffer.length))!=-1) {
//					out.write(writeBuffer,0,readBytes);
//				}
//			} finally {
//				input.close();
//			}
		} else if(tempBuffer!=null) {
			out.write(tempBuffer);
		}
	}
}
