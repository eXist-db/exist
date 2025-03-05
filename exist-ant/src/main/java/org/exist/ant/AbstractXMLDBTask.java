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
 * @author  carvazpal
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


    /**
     * @param config true to use the configuration, false otherwise.
     */
    public void setConfiguration(final String config )
    {
        this.configuration = config;
    }


    /**
     * @param failonerror true to set the Failonerror, false otherwise.
     */
    public void setFailonerror(final boolean failonerror )
    {
        this.failonerror = failonerror;
    }
    
    
    /**
     * @param permissions true to set the permissions, false otherwise.
     */
    public void setPermissions(final String permissions )
    {
        this.permissions = permissions;
    }


    /**
     * Registers the XMLDB database driver and initializes it with the specified properties.
     * 
     * <p>This method checks if the database corresponding to the provided URI is already registered. 
     * If the database is not registered, it attempts to load and initialize the driver class, set its 
     * properties, and then register it with the {@link DatabaseManager}. The method also handles potential 
     * errors by throwing a {@link BuildException} if any exceptions are encountered during the process.</p>
     *
     * <p>Properties such as whether the database should be created, whether SSL is enabled, and any 
     * additional configuration are set based on the values provided by the caller.</p>
     *
     * @throws BuildException If there is an error while initializing or registering the database driver.
     */
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
            final Database database = (Database)clazz.newInstance();
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


    /**
     * Creates or retrieves a collection in the XML database by navigating through 
     * the provided relative path. If any part of the path does not exist, it 
     * creates the missing collections as needed.
     *
     * <p>The method starts by checking if the root collection is valid, and then 
     * iterates through each segment of the given {@code relPath}. For each segment, 
     * it attempts to retrieve the corresponding collection from the database. If the 
     * collection is not found, it creates a new collection at that path. The path is 
     * dynamically built as the method traverses each token in the relative path.</p>
     *
     * <p>This method relies on {@link CollectionManagementService} to create new 
     * collections and uses {@link DatabaseManager} to fetch existing collections.</p>
     *
     * @param rootCollection The root collection from which the path traversal begins.
     * @param baseURI The base URI used to resolve the collection's location.
     * @param path The current path being constructed, which will be updated as the method processes 
     * each segment of the {@code relPath}.
     * @param relPath The relative path to the desired collection, with each segment separated by a "/".
     * @return The final {@link Collection} corresponding to the full resolved path.
     *         If any collections were created during the traversal, it returns the last created collection.
     * @throws XMLDBException If any error occurs while retrieving or creating collections from the database.
     */
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
    
    
    /**
     * Sets the permissions for the given resource in the XMLDB collection specified by the URI.
     * This method checks if the provided URI is valid, retrieves the base collection, 
     * and then attempts to apply the permissions using the {@link UserManagementService}.
     * 
     * <p>If the collection cannot be found, it will either throw an exception or log an error, 
     * depending on the value of the {@code failonerror} flag. If any XMLDB-related error occurs, 
     * it is caught and either thrown as a {@link BuildException} or logged, based on the same flag.</p>
     *
     * @param res The {@link Resource} whose permissions are to be set in the collection.
     * @throws BuildException If the specified collection cannot be found, or if an XMLDB error occurs during the process.
     */
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
    
    
    /**
     * Sets the permissions for the specified collection by using the UserManagementService.
     * This method checks if any permissions are defined and, if so, proceeds to set them 
     * using the UserManagementService associated with the given collection.
     * 
     * <p>If an XMLDB exception occurs while attempting to access the UserManagementService or 
     * apply the permissions, it will either throw a {@link BuildException} or log the error, 
     * depending on the {@code failonerror} flag.</p>
     *
     * @param col The {@link Collection} whose permissions will be set.
     * @throws BuildException If an XMLDB error occurs during the process or if the permissions cannot be set.
     */
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
    
    
    /**
     * Sets the permissions for the given resource using the specified {@link UserManagementService}.
     * This method checks if the permissions string matches a Unix-style or eXist-style format and 
     * applies the permissions accordingly.
     * 
     * <p>If the permissions string matches the Unix-style regular expression, the permissions 
     * will be applied using Unix-style permission syntax. Otherwise, it is assumed that the 
     * permissions are specified using eXist's own syntax (e.g., user=+write,...).</p>
     * 
     * <p>If any XMLDB-related or syntax errors occur during the process, they will either be 
     * thrown as {@link BuildException} or logged, depending on the value of the {@code failonerror} flag.</p>
     *
     * @param res The {@link Resource} for which the permissions will be set. 
     *            If null, permissions will be set globally.
     * @param service The {@link UserManagementService} used to set the permissions on the resource.
     * @throws BuildException If there is an XMLDB or syntax error while setting the permissions.
     */
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
