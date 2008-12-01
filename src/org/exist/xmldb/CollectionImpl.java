/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-04 Wolfgang M. Meier wolfgang@exist-db.org
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.xmldb;

import java.util.Date;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * This interface extends org.xmldb.api.base.Collection with extensions specific to eXist.
 */
public interface CollectionImpl extends Collection {

    public boolean isRemoteCollection() throws XMLDBException;
    
	/**
	 * Returns the time of creation of the collection.
	 */
	Date getCreationTime() throws XMLDBException;

	/* Alternative methods, especially to be used from jsp */
	public String[] getChildCollections() throws XMLDBException;
	
	public String[] getResources() throws XMLDBException;
	
	public void storeResource(Resource res, Date a, Date b) throws XMLDBException;
	
	public XmldbURI getPathURI();
	
	
}