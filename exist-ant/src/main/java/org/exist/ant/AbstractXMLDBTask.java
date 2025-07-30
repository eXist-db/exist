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
import org.apache.tools.ant.Task;
import org.exist.security.Permission;
import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.util.SyntaxException;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
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
    protected String  		driver         		= "org.exist.xmldb.DatabaseImpl";
    protected String  		user           		= "guest";
    protected String  		password       		= "guest";
    protected String  		uri            		= null;
    protected boolean		ssl					= false;
    protected boolean 	createDatabase 		= false;
    protected String  		configuration  		= null;
    protected boolean 	failonerror    		= true;
    protected String		permissions	   		= null;
    
    private final String	UNIX_PERMS_REGEX 	= "([r-][w-][x-]){3}";

    @Override
    public void init() throws BuildException {
        super.init();

        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();
        } catch (final StartException e) {
            throw new BuildException(e.getMessage());
        }
    }

    /**
     * Set the driver.
     *
     * @param driver the driver
     */
    public void setDriver(final String driver )
    {
        this.driver = driver;
    }


    /**
     * Set the password.
     *
     * @param password the password
     */
    public void setPassword(final String password )
    {
        this.password = password;
    }


    /**
     * Set the user.
     *
     * @param user the user.
     */
    public void setUser(final String user )
    {
        this.user = user;
    }


    /**
     * Set the URI.
     *
     * @param uri the URI
     */
    public void setUri(final String uri )
    {
        this.uri = uri;
    }


    /**
     * Set whether to use SSL
     *
     * @param ssl true to use SSL, false otherwise
     */
    public void setSsl(final boolean ssl )
    {
        this.ssl = ssl;
    }


    /**
     * Set whether to initialise the database.
     *
     * @param create true to initialise the database, false otherwise.
     */
    public void setInitdb(final boolean create )
    {
        this.createDatabase = create;
    }


    public void setConfiguration(final String config )
    {
        this.configuration = config;
    }


    public void setFailonerror(final boolean failonerror )
    {
        this.failonerror = failonerror;
    }
    
    
    public void setPermissions(final String permissions )
    {
        this.permissions = permissions;
    }


    protected void registerDatabase() throws BuildException
    {
        try {
            log( "Registering database", Project.MSG_DEBUG );
            for( final Database database : DatabaseManager.getDatabases() ) {

                if( database.acceptsURI( uri ) ) {
                    return;
                }
            }

            final Class<?> clazz    = Class.forName( driver );
            final Database database = (Database)clazz.getDeclaredConstructor().newInstance();
            database.setProperty( "create-database", createDatabase ? "true" : "false" );
            database.setProperty( "ssl-enable", ssl ? "true" : "false" );

            if( configuration != null ) {
                database.setProperty( "configuration", configuration );
            }

            DatabaseManager.registerDatabase( database );

            log( "Database driver registered." );

        }
        catch( final Exception e ) {
            throw new BuildException("failed to initialize XMLDB database driver", e);
        }
    }


    protected final Collection mkcol(final Collection rootCollection, final String baseURI, String path, final String relPath ) throws XMLDBException
    {
        CollectionManagementService mgtService;
        Collection                  current   = rootCollection;
        Collection                  collection;
        String                      token;

        ///TODO : use dedicated function in XmldbURI
        final StringTokenizer             tokenizer = new StringTokenizer( relPath, "/" );

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
                mgtService = current.getService( CollectionManagementService.class );
                log( "Create child collection " + token, Project.MSG_DEBUG );
                current = mgtService.createCollection( token );
                log( "Created collection " + current.getName() + '.', Project.MSG_DEBUG );

            } else {
                current = collection;
            }
        }
        return( current );
    }
    
    
    protected final void setPermissions(final Resource res ) throws BuildException
    {
    	Collection            base    = null;
    	UserManagementService service = null;
    	
    	if( uri == null ) {
            throw( new BuildException( "you have to specify an XMLDB collection URI" ) );
        }

        try {
            log( "Get base collection: " + uri, Project.MSG_DEBUG );
            base = DatabaseManager.getCollection( uri, user, password );

            if( base == null ) {
                final String msg = "Collection " + uri + " could not be found.";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
                }
            } else {
                service = base.getService( UserManagementService.class);
                
                setPermissions( res, service );
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
    
    
    protected final void setPermissions(final Collection col ) throws BuildException
    {
        try {
        	if( permissions != null ) {
                 setPermissions( null, col.getService( UserManagementService.class));
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
    
    
    protected final void setPermissions(final Resource res, final UserManagementService service ) throws BuildException
    {
    	 try {
    	 	if( permissions != null ) {
				// if the permissions string matches the Unix Perms Regex, we use a unix style
				// permission string approach, otherwise we assume permissions are specified
				// in eXist's own syntax (user=+write,...). 
				
				if( permissions.matches( UNIX_PERMS_REGEX ) ) {
					// Unix-style permissions string provided
					final Permission perm = UnixStylePermissionAider.fromString( permissions );
	
					if( res != null ) {
						service.chmod( res, perm.getMode() );
					} else {
						service.chmod( perm.getMode() );
					}
				} else {
					// eXist-style syntax for permission string (eg. user=+write,...)
					if( res != null ) {
						 service.chmod( res, permissions );
					} else {
						service.chmod( permissions );
					}
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
        catch( final SyntaxException e ) {
            final String msg = "Syntax error in permissions: " + permissions;

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }
}
