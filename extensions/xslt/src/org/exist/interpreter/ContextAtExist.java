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

package org.exist.interpreter;

import org.exist.storage.DBBroker;
import org.exist.util.hashtable.NamePool;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.value.AnyURIValue;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface ContextAtExist {
	
	public int getExpressionCount();

	public DBBroker getBroker();
	public NamePool getSharedNamePool();

	public String getModuleLoadPath();

	public String getPrefixForURI(String namespaceURI);
	public void declareInScopeNamespace(String string, String namespaceURI);

	public void declareNamespace(String prefix, String uri) throws XPathException;

	public void setDefaultElementNamespace(String uri, String schema) throws XPathException;

	public boolean isBaseURIDeclared();
	public AnyURIValue getBaseURI() throws XPathException;
	
	public void setWatchDog(XQueryWatchDog watchDog);
	public XQueryWatchDog getWatchDog();

	public boolean optimizationsEnabled();

	public void reset(boolean b);

//	public TraceListener getTraceListener();
//	public void getTraceListener(TraceListener listener);

}
