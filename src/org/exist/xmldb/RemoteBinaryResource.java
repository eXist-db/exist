/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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
 * 
 *  $Id$
 */
package org.exist.xmldb;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.security.Permission;
import org.exist.util.EXistInputSource;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * 
 * @author wolf
 *
 */
public class RemoteBinaryResource implements ExtendedResource, BinaryResource, EXistResource {

	private XmldbURI path;
    private String mimeType = MimeType.BINARY_TYPE.getName();
	private RemoteCollection parent;
	private byte[] data = null;
	private File file = null;
	private InputSource inputSource = null;
	private boolean isExternal=false;
	
	private Permission permissions = null;
	private int contentLen = 0;
	
	protected Date dateCreated= null;
	protected Date dateModified= null;

	
	public RemoteBinaryResource(RemoteCollection parent, XmldbURI documentName) throws XMLDBException {
		this.parent = parent;
 		if (documentName.numSegments()>1) {
			this.path = documentName;
		} else {
			this.path = parent.getPathURI().append(documentName);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getParentCollection()
	 */
	public Collection getParentCollection() throws XMLDBException {
		return parent;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getId()
	 */
	public String getId() throws XMLDBException {
		return path.lastSegment().toString();
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getResourceType()
	 */
	public String getResourceType() throws XMLDBException {
		return "BinaryResource";
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.ExtendedResource#getExtendedContent()
	 */
	public Object getExtendedContent() throws XMLDBException {
		if(file!=null)
			return file;
		if(inputSource!=null)
			return inputSource;
		
		if(data != null)
			return data;
		
		List params = new ArrayList();
		params.add(path.toString());
		File tmpfile=null;
		try {
			tmpfile=File.createTempFile("eXistRBR",".bin");
			tmpfile.deleteOnExit();
			getContentIntoAFile(tmpfile);
		} catch(IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
        return tmpfile;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	public Object getContent() throws XMLDBException {
		Object res=getExtendedContent();
		if(res!=null) {
			if(res instanceof File) {
				return readFile((File)res);
			} else if(res instanceof InputSource) {
				return readFile((InputSource)res);
			}
		}
		
		return res;
	}
	
	public InputStream getStreamContent()
		throws XMLDBException
	{
		InputStream retval=null;
		if(file!=null) {
			try {
				retval=new FileInputStream(file);
			} catch(FileNotFoundException fnfe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
			}
		} else if(inputSource!=null) {
			retval=inputSource.getByteStream();
		} else if(data!=null) {
			retval=new ByteArrayInputStream(data);
		} else {
			try {
				File tmpfile=File.createTempFile("eXistRBR",".bin");
				tmpfile.deleteOnExit();
				getContentIntoAFile(tmpfile);
				retval = new FileInputStream(tmpfile);
			} catch(IOException ioe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
			}
		}
		
		return retval;
	}
	
	public void getContentIntoAFile(File tmpfile)
		throws XMLDBException
	{
		try {
			FileOutputStream fos=new FileOutputStream(tmpfile);
			BufferedOutputStream bos=new BufferedOutputStream(fos);
			
			getContentIntoAStream(bos);
			bos.close();
			fos.close();

			file=tmpfile;
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
	}
	
	public void getContentIntoAStream(OutputStream os)
		throws XMLDBException
	{
		Properties properties = parent.getProperties();
		List params = new ArrayList();
		params.add(path.toString());
		params.add(properties);
		try {
			Map table = (Map) parent.getClient().execute("getDocumentData", params);
			String method;
			boolean useLongOffset;
			if(table.containsKey("supports-long-offset") && (Boolean)(table.get("supports-long-offset"))) {
				useLongOffset=true;
				method="getNextExtendedChunk";
			} else {
				useLongOffset=false;
				method="getNextChunk";
			}
			long offset = ((Integer)table.get("offset")).intValue();
			byte[] data = (byte[])table.get("data");
			os.write(data);
			while(offset > 0) {
				params.clear();
				params.add(table.get("handle"));
				params.add(useLongOffset?Long.toString(offset):new Integer((int)offset));
				table = (Map) parent.getClient().execute(method, params);
				offset = useLongOffset?new Long((String)table.get("offset")).longValue():((Integer)table.get("offset")).longValue();
				data = (byte[])table.get("data");
				os.write(data);
			}
		} catch (XmlRpcException xre) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
	}

	public void freeLocalResources()
	{
		if(!isExternal && file!=null) {
			file=null;
		}
	}
	
	protected String getStreamSymbolicPath() {
		String retval="<streamunknown>";
		
		if(file!=null) {
			retval=file.getAbsolutePath();
		} else if(inputSource!=null && inputSource instanceof EXistInputSource) {
			retval=((EXistInputSource)inputSource).getSymbolicPath();
		} 
		
		return retval;
	}
	
	public long getStreamLength()
		throws XMLDBException
	{
		long retval=-1;
		if(file!=null) {
			retval=file.length();
		} else if(inputSource!=null && inputSource instanceof EXistInputSource) {
			retval=((EXistInputSource)inputSource).getByteStreamLength();
		} else if(data!=null) {
			retval=data.length;
		} else {
			Properties properties = parent.getProperties();
			List params = new ArrayList();
			params.add(path.toString());
			params.add(properties);
			try {
				Map table = (Map) parent.getClient().execute("describeResource", params);
				retval=((Integer)table.get("content-length")).intValue();
			} catch (XmlRpcException xre) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
			}
		}
		
		return retval;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#setContent(java.lang.Object)
	 */
	public void setContent(Object obj) throws XMLDBException {
		if(obj instanceof File) {
			file=(File)obj;
			isExternal=true;
		} else if(obj instanceof InputSource) {
			inputSource=(InputSource)obj;
			isExternal=true;
		} else if(obj instanceof byte[]) {
			data = (byte[])obj;
			isExternal=true;
		} else if(obj instanceof String) {
			data = ((String)obj).getBytes();
			isExternal=true;
		} else
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"don't know how to handle value of type " + obj.getClass().getName());
	}
	
	private byte[] readFile(File file) throws XMLDBException {
		try {
			return readFile(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"file " + file.getAbsolutePath() + " could not be found", e);
		}
	}

	private byte[] readFile(InputSource is) throws XMLDBException {
		return readFile(is.getByteStream());
	}

	private byte[] readFile(InputStream is) throws XMLDBException {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
			byte[] temp = new byte[1024];
			int count = 0;
			while((count = is.read(temp)) > -1) {
				bos.write(temp, 0, count);
			}
			return bos.toByteArray();
		} catch (FileNotFoundException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"file " + file.getAbsolutePath() + " could not be found", e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"IO exception while reading file " + file.getAbsolutePath(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getCreationTime()
	 */
	public Date getCreationTime() throws XMLDBException {
		return dateCreated;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getLastModificationTime()
	 */
	public Date getLastModificationTime() throws XMLDBException {
		return dateModified;
	}

	public void setPermissions(Permission perms) {
		this.permissions = perms;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getPermissions()
	 */
	public Permission getPermissions() {
		return permissions;
	}
	
	public void setContentLength(int len) {
		this.contentLen = len;
	}
	
	public int getContentLength() throws XMLDBException {
		return contentLen;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#setLexicalHandler(org.xml.sax.ext.LexicalHandler)
	 */
	public void setLexicalHandler(LexicalHandler handler) {
	}

    /* (non-Javadoc)
     * @see org.exist.xmldb.EXistResource#setMimeType(java.lang.String)
     */
    public void setMimeType(String mime) {
        this.mimeType = mime;
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.EXistResource#getMimeType()
     */
    public String getMimeType() {
        return mimeType;
    }
    
    public  DocumentType getDocType() throws XMLDBException {
    	return null;
        }

    public void setDocType(DocumentType doctype) throws XMLDBException {
		
    }

    protected void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    protected void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }
}
