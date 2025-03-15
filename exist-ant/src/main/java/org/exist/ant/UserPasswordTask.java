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
import org.exist.security.Account;
import org.exist.security.internal.Password;
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to set the password of a user.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class UserPasswordTask extends UserTask
{
    private String name;
    private String secret;

    /**
     * Executes the task to update a user's password in the XMLDB system.
     * 
     * <p>This method retrieves an existing user account by name and updates the 
     * password if a new secret is provided.</p>
     * 
     * <p>If the user is not found, it either logs an error or throws an exception 
     * based on the {@code failonerror} flag.</p>
     * 
     * @throws BuildException if the user name is missing or if an error occurs during execution.
     */
    @Override
    public void execute() throws BuildException
    {
        super.execute();

        // Ensure that a user name is provided
        if (name == null) {
            throw new BuildException("Must specify at least a user name");
        }

        try {
            log("Looking up user " + name, Project.MSG_INFO);
            final Account usr = service.getAccount(name);

            if (usr != null) {
                log("Setting password for user " + name, Project.MSG_INFO);

                // Update the password if a new secret is provided
                if (secret != null) {
                    usr.setCredential(new Password(usr, secret));
                    service.updateAccount(usr);
                }

            } else {
                final String msg = "User " + name + " not found";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }
            }

        } catch (final XMLDBException e) {
            final String msg = "XMLDB exception caught: " + e.getMessage();

            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }



    /**
     * @param name set for this object
     */
    public void setName(final String name )
    {
        this.name = name;
    }


    /**
     * @param secret set for this object
     */
    public void setSecret(final String secret )
    {
        this.secret = secret;
    }
}
