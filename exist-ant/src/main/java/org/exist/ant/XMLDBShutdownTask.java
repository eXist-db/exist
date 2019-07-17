/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import org.exist.xmldb.DatabaseInstanceManager;


/**
 * an ant task to shutdown a XMLDB database.
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
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "you have to specify an XMLDB collection URI" ) );
        }

        registerDatabase();

        try {
            log( "Get base collection: " + uri, Project.MSG_DEBUG );
            final Collection root = DatabaseManager.getCollection( uri, user, password );

            if( root == null ) {
                final String msg = "Collection " + uri + " could not be found.";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
                }

            } else {
                final DatabaseInstanceManager mgr = (DatabaseInstanceManager)root.getService( "DatabaseInstanceManager", "1.0" );
                log( "Shutdown database instance", Project.MSG_INFO );
                mgr.shutdown();
            }

        }
        catch( final XMLDBException e ) {
            final String msg = "Error during database shutdown: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }
}
