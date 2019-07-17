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

import org.xmldb.api.base.XMLDBException;

import org.exist.security.internal.aider.UserAider;

import java.net.URISyntaxException;


/**
 * an ant task to add a user.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class AddUserTask extends UserTask {
    private String name;
    private String primaryGroup;
    private String secret;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( name == null ) {
            throw( new BuildException( "Must specify at leat a user name" ) );
        }

        try {
            final UserAider usr = new UserAider( name );

            if( secret != null ) {
                usr.setPassword( secret );
            }

            if( primaryGroup != null ) {
                usr.addGroup( primaryGroup );
            }

            log( "Adding user " + name, Project.MSG_INFO );
            service.addAccount( usr );

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


    public void setName( String name )
    {
        this.name = name;
    }


    public void setPrimaryGroup( String primaryGroup )
    {
        this.primaryGroup = primaryGroup;
    }


    public void setSecret( String secret )
    {
        this.secret = secret;
    }
}
