/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  
 *  $Id$
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/**
 * Ant task to execute an XQuery.
 * 
 * The query is either passed as nested text in the element,
 * or via an attribute "query".
 * 
 * Please note that the task doesn't output the query results.
 * 
 * @author wolf
 */
public class XMLDBQueryTask extends AbstractXMLDBTask {

	private String query = null;
	private String text = null;
	
	/* (non-Javadoc)
	 * @see org.apache.tools.ant.Task#execute()
	 */
	public void execute() throws BuildException {
		if (uri == null)
			throw new BuildException("you have to specify an XMLDB collection URI");
		if (text != null) {
			PropertyHelper helper = PropertyHelper.getPropertyHelper(getProject());
			query = helper.replaceProperties(null, text, null);
		}
		if (query == null)
			throw new BuildException("you have to specify a query");
        
        log("XQuery is:\n"+query, org.apache.tools.ant.Project.MSG_DEBUG);
		
		registerDatabase();
		try {
			Collection collection = DatabaseManager.getCollection(uri, user, password);

            if(collection==null){
          	  String msg="Collection " + uri + " could not be found.";
        	  if(failonerror)
        		  throw new BuildException(msg);
        	  else
        		  log(msg,Project.MSG_ERR);
            } else {
	            XPathQueryService service = (XPathQueryService)
					collection.getService("XPathQueryService", "1.0");
				ResourceSet results = service.query(query);
				log("Found " + results.getSize());
            }
		} catch (XMLDBException e) {
	    	  String msg="XMLDB exception caught while executing query: " + e.getMessage();
	    	  if(failonerror)
	    		  throw new BuildException(msg,e);
	    	  else
	    		  log(msg,e,Project.MSG_ERR);
		}
	}

	/**
	 * @param query
	 */
	public void setQuery(String query) {
		this.query = query;
	}
	
	public void addText(String text) {
		this.text = text;
	}
}
