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

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to list the sub-collections or resources in a collection.
 *
 * @author  peter.klotz@blue-elephant-systems.com
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

        if( ( hasCollections == false ) && ( hasResources == false ) ) {
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
                }
            }

            final StringBuilder buffer = new StringBuilder();

            if( hasCollections ) {
                final String[] childCollections = base.listChildCollections();

                if( childCollections != null ) {
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
                final String[] resources = base.listResources();

                if( resources != null ) {

                    if( buffer.length() > 0 ) {
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

            if( buffer.length() > 0 ) {
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


    public void setCollections( boolean collections )
    {
        this.hasCollections = collections;
    }


    public void setResources( boolean resources )
    {
        this.hasResources = resources;
    }


    public void setSeparator( String separator )
    {
        this.separator = separator;
    }


    public void setOutputproperty( String outputproperty )
    {
        this.outputproperty = outputproperty;
    }
}
