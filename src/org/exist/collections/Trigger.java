/*
 *  Trigger.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
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
 * $Id$
 *
 */
package org.exist.collections;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * @author wolf
 */
public interface Trigger extends ContentHandler, LexicalHandler {
	
	public final static int STORE_EVENT = 0;
	public final static int UPDATE_EVENT = 1;
	public final static int REMOVE_EVENT = 2;
	
	public void configure(Map parameters) throws CollectionConfigurationException;
	
	public void prepare(DBBroker broker, Collection collection, String documentName,
		Document existingDocument) throws TriggerException;
	
	public void setValidating(boolean validating);
	
	public boolean isValidating();
	
	public void setOutputHandler(ContentHandler handler);
	
	public void setLexicalOutputHandler(LexicalHandler handler);
	
	public ContentHandler getOutputHandler();
	
	public ContentHandler getInputHandler();
	
	public LexicalHandler getLexicalOutputHandler();
	
	public LexicalHandler getLexicalInputHandler();
	
	public Logger getLogger();
}
