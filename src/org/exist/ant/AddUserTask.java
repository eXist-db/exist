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

import java.net.URISyntaxException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.exist.security.User;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to add a user
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class AddUserTask extends UserTask {

    private String name;
    private String primaryGroup;
    private String home;
    private String secret;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException {
        super.execute();
        if (name == null) {
            throw new BuildException("Must specify at leat a user name");
        }

        try {
            User usr = new User(name);
            if (secret != null) {
                usr.setPassword(secret);
            }

            if (home != null) {
                usr.setHome(XmldbURI.xmldbUriFor(home));
            }

            if (primaryGroup != null) {
                usr.addGroup(primaryGroup);
            }

            log("Adding user " + name, Project.MSG_INFO);
            service.addUser(usr);

        } catch (XMLDBException e) {
            String msg = "XMLDB exception caught: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
            
        } catch (URISyntaxException e) {
            String msg = "URI syntax exception caught: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
