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
import org.exist.security.internal.aider.UserAider;
import org.xmldb.api.base.XMLDBException;


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


    /**
     * @param name to be assigned to this object.
     */
    public void setName(final String name )
    {
        this.name = name;
    }


    /**
     * @param primaryGroup to be set to this object.
     */
    public void setPrimaryGroup(final String primaryGroup )
    {
        this.primaryGroup = primaryGroup;
    }


    /**
     * @param secret to be set to this object
     */
    public void setSecret(final String secret )
    {
        this.secret = secret;
    }
}
