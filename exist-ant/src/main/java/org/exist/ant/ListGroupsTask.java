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
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to list groups.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class ListGroupsTask extends UserTask
{
    private String outputproperty = null;
    private String separator      = ",";

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        super.execute();

        try {
            log( "Listing all groups", Project.MSG_DEBUG );
            final String[] groups = service.getGroups();

            if( groups != null ) {

                boolean       isFirst = true;
                final StringBuilder buffer  = new StringBuilder();

                for( final String group : groups ) {

                    // only insert separator for 2nd or later item
                    if( isFirst ) {
                        isFirst = false;
                    } else {
                        buffer.append( separator );
                    }

                    buffer.append( group );

                }

                if(!buffer.isEmpty()) {
                    log( "Setting output property " + outputproperty + " to " + buffer, Project.MSG_DEBUG );
                    getProject().setNewProperty( outputproperty, buffer.toString() );
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


    public void setOutputproperty(final String outputproperty )
    {
        this.outputproperty = outputproperty;
    }


    public void setSeparator(final String separator )
    {
        this.separator = separator;
    }
}
