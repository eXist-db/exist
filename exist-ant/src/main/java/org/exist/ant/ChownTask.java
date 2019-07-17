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
 * an ant task to change permissions on a resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class ChownTask extends UserTask
{
    private String name     = null;
    private String group    = null;
    private String resource = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( ( name == null ) || ( group == null ) ) {
            throw( new BuildException( "Must specify user and group" ) );
        }

        try {
            final Account usr = service.getAccount( name );

            if( resource != null ) {
                final Resource res = base.getResource( resource );
                service.chown( res, usr, group );
            } else {
                service.chown( usr, group );
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


    public void setGroup( String group )
    {
        this.group = group;
    }
}
