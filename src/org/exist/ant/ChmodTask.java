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
import org.exist.security.Permission;
import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.exist.util.SyntaxException;

import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to change permissions on a resource
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class ChmodTask extends UserTask {

    private String resource = null;
    private String mode = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException {
        super.execute();
        
        try {
        	// if the mode string contains an '=', we assume permissions are specified
        	// in eXist's own syntax (user=+write,...). Otherwise, we assume a unix style
        	// permission string
        	if (mode.indexOf('=') < 0) {
        		Permission perm = UnixStylePermissionAider.fromString(mode);
        		if (resource != null) {
                    Resource res = base.getResource(resource);
                    service.chmod(res, perm.getMode());
                } else {
                    service.chmod(perm.getMode());
                }
        	} else {
	            if (resource != null) {
	                Resource res = base.getResource(resource);
	                service.chmod(res, mode);
	            } else {
	                service.chmod(mode);
	            }
        	}
        } catch (XMLDBException e) {
            String msg = "XMLDB exception caught: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        } catch (SyntaxException e) {
        	String msg = "Syntax error in permissions: " + mode;
        	if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
		}
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
