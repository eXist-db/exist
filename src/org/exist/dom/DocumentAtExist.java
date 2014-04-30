/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2009 The eXist Project
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

package org.exist.dom;

import org.exist.Database;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
//import org.exist.collections.Collection;
//import org.exist.interpreter.ContextAtExist;
//import org.exist.security.User;
//import org.exist.storage.lock.Lock;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface DocumentAtExist extends NodeAtExist, Document {

//	public void setContext(ContextAtExist context);
//	public ContextAtExist getContext();
	
    public int getFirstChildFor(int nodeNumber);
    
    public NodeAtExist getNode(int nodeNr) throws DOMException;

    //memory
    public int getNextNodeNumber(int nodeNr) throws DOMException;

    //memory
	public boolean hasReferenceNodes();

//    public boolean isLockedForWrite(); //synchronized 
//    public Lock getUpdateLock(); //final synchronized 
//        
//	public void setUserLock(User user);
//	public User getUserLock();
	
//	public Collection getCollection();
	
	public int getDocId();

	public XmldbURI getURI();
    
    public Database getDatabase();

	public Object getUUID();
}
