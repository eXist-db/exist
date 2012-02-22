/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.StringTokenizer;


/**
 * DOCUMENT ME!
 *
 * @author  wolf
 * @author  andrzej@chaeron.com
 */
public abstract class AbstractXMLDBTask extends Task
{
    protected String  driver         = "org.exist.xmldb.DatabaseImpl";
    protected String  user           = "guest";
    protected String  password       = "guest";
    protected String  uri            = null;
    protected boolean createDatabase = false;
    protected String  configuration  = null;
    protected boolean failonerror    = true;

    /**
     * DOCUMENT ME!
     *
     * @param  driver
     */
    public void setDriver( String driver )
    {
        this.driver = driver;
    }


    /**
     * DOCUMENT ME!
     *
     * @param  password
     */
    public void setPassword( String password )
    {
        this.password = password;
    }


    /**
     * DOCUMENT ME!
     *
     * @param  user
     */
    public void setUser( String user )
    {
        this.user = user;
    }


    /**
     * DOCUMENT ME!
     *
     * @param  uri
     */
    public void setUri( String uri )
    {
        this.uri = uri;
    }


    /**
     * DOCUMENT ME!
     *
     * @param  create
     */
    public void setInitdb( boolean create )
    {
        this.createDatabase = create;
    }


    public void setConfiguration( String config )
    {
        this.configuration = config;
    }


    public void setFailonerror( boolean failonerror )
    {
        this.failonerror = failonerror;
    }


    protected void registerDatabase() throws BuildException
    {
        try {
            log( "Registering database", Project.MSG_DEBUG );
            Database[] allDataBases = DatabaseManager.getDatabases();

            for( Database database : allDataBases ) {

                if( database.acceptsURI( uri ) ) {
                    return;
                }
            }

            Class<?> clazz    = Class.forName( driver );
            Database database = (Database)clazz.newInstance();
            database.setProperty( "create-database", createDatabase ? "true" : "false" );

            if( configuration != null ) {
                database.setProperty( "configuration", configuration );
            }

            DatabaseManager.registerDatabase( database );

            log( "Database driver registered." );

        }
        catch( Exception e ) {
            throw( new BuildException( "failed to initialize XMLDB database driver" ) );
        }
    }


    protected final Collection mkcol( Collection rootCollection, String baseURI, String path, String relPath ) throws XMLDBException
    {
        CollectionManagementService mgtService;
        Collection                  current   = rootCollection;
        Collection                  collection;
        String                      token;

        ///TODO : use dedicated function in XmldbURI
        StringTokenizer             tokenizer = new StringTokenizer( relPath, "/" );

        while( tokenizer.hasMoreTokens() ) {

            token = tokenizer.nextToken();

            if( path != null ) {
                path = path + "/" + token;
            } else {
                path = "/" + token;
            }

            log( "Get collection " + baseURI + path, Project.MSG_DEBUG );
            collection = DatabaseManager.getCollection( baseURI + path, user, password );

            if( collection == null ) {
                log( "Create collection management service for collection " + current.getName(), Project.MSG_DEBUG );
                mgtService = (CollectionManagementService)current.getService( "CollectionManagementService", "1.0" );
                log( "Create child collection " + token, Project.MSG_DEBUG );
                current = mgtService.createCollection( token );
                log( "Created collection " + current.getName() + '.', Project.MSG_DEBUG );

            } else {
                current = collection;
            }
        }
        return( current );
    }
}
