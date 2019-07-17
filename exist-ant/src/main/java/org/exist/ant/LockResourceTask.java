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

import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import org.exist.security.Account;


/**
 * an ant task to lock a resource for a user.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class LockResourceTask extends UserTask
{
    private String name     = null;
    private String resource = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( ( resource == null ) || ( name == null ) ) {
            throw( new BuildException( "Must specify user and resource name" ) );
        }

        try {
            final Resource res = base.getResource( resource );

            if( res == null ) {
                final String msg = "Resource " + resource + " not found";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
                }

            } else {
                final Account usr = service.getAccount( name );

                if( usr == null ) {
                    final String msg = "User " + name + " not found";

                    if( failonerror ) {
                        throw( new BuildException( msg ) );
                    } else {
                        log( msg, Project.MSG_ERR );
                    }
                } else {
                    service.lockResource( res, usr );
                }
            }

        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception caught: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    public void setName( String user )
    {
        this.name = user;
    }


    public void setResource( String resource )
    {
        this.resource = resource;
    }
}
