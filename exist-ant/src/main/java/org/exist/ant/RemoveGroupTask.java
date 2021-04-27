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
import org.exist.security.Group;
import org.xmldb.api.base.XMLDBException;


/**
 * Created by IntelliJ IDEA. User: lcahlander Date: Aug 25, 2010 Time: 3:09:13 PM To change this template use File | Settings | File Templates.
 */
public class RemoveGroupTask extends UserTask
{
    private String name = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        if( name == null ) {
            throw( new BuildException( "You have to specify a name" ) );
        }

        log( "Removing group " + name, Project.MSG_INFO );

        try {
            final Group group = service.getGroup( name );

            if( group != null ) {
                service.removeGroup( group );
            } else {
                log( "Group " + name + " does not exist.", Project.MSG_INFO );
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


    public void setName(final String name )
    {
        this.name = name;
    }
}
