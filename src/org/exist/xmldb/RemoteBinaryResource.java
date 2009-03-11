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

import java.io.InputStream;
import java.io.OutputStream;

import org.exist.util.EXistInputSource;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * 
 * @author wolf
 *
 */
public class RemoteBinaryResource
	extends AbstractRemoteResource
	implements BinaryResource
{
	private byte[] data = null;
	
	public RemoteBinaryResource(RemoteCollection parent, XmldbURI documentName) throws XMLDBException {
		super(parent,documentName);
 		mimeType = MimeType.BINARY_TYPE.getName();
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
		return getExtendedContentInternal(data,false,-1,-1);
	}
	
	public InputStream getStreamContent()
		throws XMLDBException
	{
		return getStreamContentInternal(data,false,-1,-1);
	}
	
	public void getContentIntoAStream(OutputStream os)
		throws XMLDBException
	{
		getContentIntoAStreamInternal(os,data,false,-1,-1);
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
		return getStreamLengthInternal(data);
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#setContent(java.lang.Object)
	 */
	public void setContent(Object obj) throws XMLDBException {
		data = null;
    	if(!super.setContentInternal(obj)) {
    		if(obj instanceof byte[]) {
    			data = (byte[])obj;
    		} else if(obj instanceof String) {
    			data = ((String)obj).getBytes();
    		} else
    			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
    					"don't know how to handle value of type " + obj.getClass().getName());
    	}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#setLexicalHandler(org.xml.sax.ext.LexicalHandler)
	 */
	public void setLexicalHandler(LexicalHandler handler) {
	}

	public  DocumentType getDocType() throws XMLDBException {
    	return null;
    }

    public void setDocType(DocumentType doctype) throws XMLDBException {
		
    }
}
