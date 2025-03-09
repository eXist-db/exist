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
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.util.List;


/**
 * an ant task to list the sub-collections or resources in a collection.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBListTask extends AbstractXMLDBTask
{
    private boolean hasCollections = false;
    private boolean hasResources   = false;
    private String  separator      = ",";
    private String  outputproperty;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "You have to specify an XMLDB collection URI" ) );
        }

        if(!hasCollections && !hasResources) {
            throw( new BuildException( "You have at least one of collections or resources or both" ) );
        }

        registerDatabase();

        try {
            log( "Get base collection: " + uri, Project.MSG_DEBUG );
            final Collection base = DatabaseManager.getCollection( uri, user, password );

            if( base == null ) {
                final String msg = "Collection " + uri + " could not be found.";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
                    return;
                }
            }

            final StringBuilder buffer = new StringBuilder();

            if( hasCollections ) {
                final List<String> childCollections = base.listChildCollections();
                if( !childCollections.isEmpty() ) {
                    log( "Listing child collections", Project.MSG_DEBUG );
                    boolean isFirst = true;

                    for( final String col : childCollections ) {

                        // only insert separator for 2nd or later item
                        if( isFirst ) {
                            isFirst = false;
                        } else {
                            buffer.append( separator );
                        }

                        buffer.append( col );
                    }
                }
            }

            if( hasResources ) {
                log( "Listing resources", Project.MSG_DEBUG );
                final List<String> resources = base.listResources();
                if( !resources.isEmpty() ) {

                    if(!buffer.isEmpty()) {
                        buffer.append( separator );
                    }

                    boolean isFirst = true;

                    for( final String resource : resources ) {

                        // only insert separator for 2nd or later item
                        if( isFirst ) {
                            isFirst = false;
                        } else {
                            buffer.append( separator );
                        }

                        buffer.append( resource );
                    }
                }
            }

            if(!buffer.isEmpty()) {
                log( "Set property " + outputproperty, Project.MSG_INFO );
                getProject().setNewProperty( outputproperty, buffer.toString() );
            }

        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception during list: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    public void setCollections(final boolean collections )
    {
        this.hasCollections = collections;
    }


    public void setResources(final boolean resources )
    {
        this.hasResources = resources;
    }


    public void setSeparator(final String separator )
    {
        this.separator = separator;
    }


    public void setOutputproperty(final String outputproperty )
    {
        this.outputproperty = outputproperty;
    }
}
