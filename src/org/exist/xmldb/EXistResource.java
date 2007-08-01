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

import java.util.Date;

import org.exist.security.Permission;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.XMLDBException;

/**
 * Defines additional methods implemented by XML and binary 
 * resources.
 * 
 * @author wolf
 *
 */
public interface EXistResource {

	Date getCreationTime() throws XMLDBException;
	
	Date getLastModificationTime() throws XMLDBException;
	
	Permission getPermissions() throws XMLDBException;
	
	int getContentLength() throws XMLDBException;
	
	void setLexicalHandler(LexicalHandler handler);
    
    void setMimeType(String mime);

    String getMimeType() throws XMLDBException;
    
    DocumentType getDocType() throws XMLDBException;
    
    void setDocType(DocumentType doctype)  throws XMLDBException;
}
