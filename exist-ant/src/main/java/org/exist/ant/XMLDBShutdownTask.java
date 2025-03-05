/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;


/**
 * ant task to shutdown an XMLDB database.
 *
 * @author  Wolfgang Meier (wolfgang@exist-db.org)
 *
 *          slightly modified by:
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBShutdownTask extends AbstractXMLDBTask
{
    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
	
	/**
	 * Executes the task to shutdown the database instance associated with a specified XMLDB collection.
	 * It checks for necessary parameters, logs actions, and handles exceptions appropriately.
	 */
	@Override
	public void execute() throws BuildException
	{
	    // Ensure the URI for the XMLDB collection is specified
	    if (uri == null) {
	        throw new BuildException("You have to specify an XMLDB collection URI");
	    }

	    // Register the database before proceeding
	    registerDatabase();

	    try {
	        // Log the attempt to get the base collection
	        log("Get base collection: " + uri, Project.MSG_DEBUG);

	        // Retrieve the root collection from the database
	        final Collection root = DatabaseManager.getCollection(uri, user, password);

	        // Check if the root collection was successfully retrieved
	        if (root == null) {
	            final String msg = "Collection " + uri + " could not be found.";

	            // Handle error based on failonerror flag
	            if (failonerror) {
	                throw new BuildException(msg);
	            } else {
	                log(msg, Project.MSG_ERR);
	            }

	        } else {
	            // Get the DatabaseInstanceManager service for the root collection
	            final DatabaseInstanceManager mgr = root.getService(DatabaseInstanceManager.class);

	            // Log and attempt to shutdown the database instance
	            log("Shutdown database instance", Project.MSG_INFO);
	            mgr.shutdown();
	        }

	    } catch (final XMLDBException e) {
	        // Handle XMLDB exceptions that occur during the shutdown process
	        final String msg = "Error during database shutdown: " + e.getMessage();

	        // Handle error based on failonerror flag
	        if (failonerror) {
	            throw new BuildException(msg, e);
	        } else {
	            log(msg, e, Project.MSG_ERR);
	        }
	    }
	}
}