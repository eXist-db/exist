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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.security.Permission;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;
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
public class RemoteBinaryResource implements BinaryResource, EXistResource {

	private XmldbURI path;
    private String mimeType = MimeType.BINARY_TYPE.getName();
	private RemoteCollection parent;
	private byte[] data = null;
	
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
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	public Object getContent() throws XMLDBException {
		if(data != null)
			return data;
		Vector params = new Vector();
		params.addElement(path.toString());
		try {
			data = (byte[])parent.getClient().execute("getBinaryResource", params);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
		return data;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#setContent(java.lang.Object)
	 */
	public void setContent(Object obj) throws XMLDBException {
		if(obj instanceof File)
			readFile((File)obj);
		else if(obj instanceof byte[])
			data = (byte[])obj;
		else if(obj instanceof String)
			data = ((String)obj).getBytes();
		else
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"don't know how to handle value of type " + obj.getClass().getName());
	}
	
	private void readFile(File file) throws XMLDBException {
		try {
			FileInputStream is = new FileInputStream(file);
			ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
			byte[] temp = new byte[1024];
			int count = 0;
			while((count = is.read(temp)) > -1) {
				bos.write(temp, 0, count);
			}
			data = bos.toByteArray();
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
