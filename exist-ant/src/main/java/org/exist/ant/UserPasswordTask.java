/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2013 The eXist-db Project
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

import org.exist.security.internal.Password;
import org.xmldb.api.base.XMLDBException;

import org.exist.security.Account;


/**
 * an ant task to set the password of a user.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class UserPasswordTask extends UserTask
{
    private String name;
    private String secret;

    @Override
    public void execute() throws BuildException
    {
        super.execute();

        if( name == null ) {
            throw( new BuildException( "Must specify at least a user name" ) );
        }

        try {
            log( "Looking up user " + name, Project.MSG_INFO );
            final Account usr = service.getAccount( name );

            if( usr != null ) {
                log( "Setting password for user " + name, Project.MSG_INFO );

                if( secret != null ) {
                    usr.setCredential(new Password(usr, secret));
                    this.service.updateAccount(usr);
                }

            } else {
                final String msg = "user " + name + " not found";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
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


    public void setName( String name )
    {
        this.name = name;
    }


    public void setSecret( String secret )
    {
        this.secret = secret;
    }
}
