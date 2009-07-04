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
import org.xmldb.api.base.Database;

/**
 * @author wolf
 * @author andrzej@chaeron.com
 */
public abstract class AbstractXMLDBTask extends Task
{
	
	protected String driver = "org.exist.xmldb.DatabaseImpl";
	protected String user = "guest";
	protected String password = "guest";
	protected String uri = null;
	protected boolean createDatabase = false;
	protected String configuration = null;
	protected boolean failonerror = true;
	
	/**
   * @param driver
   */
	public void setDriver( String driver )
	{
		this.driver = driver;
	}
	
	/**
   * @param password
   */
	public void setPassword( String password )
	{
		this.password = password;
	}
	
	/**
   * @param user
   */
	public void setUser(String user)
	{
		this.user = user;
	}
	
	/**
   * @param uri
   */
	public void setUri(String uri)
	{
		this.uri = uri;
	}
	
	/**
   * @param create
   */
	public void setInitdb(boolean create)
	{
		this.createDatabase = create;
	}
	
	public void setConfiguration(String config) {
		this.configuration = config;
	}
	
	public void setFailonerror(boolean failonerror)
	{
		this.failonerror = failonerror;
	}
	
	protected void registerDatabase() throws BuildException
	{
		try {
			log( "Registering database", Project.MSG_DEBUG );
			Database dbs[] = DatabaseManager.getDatabases();
			
			for( int i = 0; i < dbs.length; i++ ) {
				if( dbs[i].acceptsURI( uri ) ) {
					return;	
				}
			}
			
			Class clazz = Class.forName( driver );	
			Database database = (Database)clazz.newInstance();
			database.setProperty( "create-database", createDatabase ? "true" : "false" );
			
			if( configuration != null ) {
				database.setProperty( "configuration", configuration );
			}
			
			DatabaseManager.registerDatabase( database );
			
			log( "Database driver registered." );
		} 
		catch ( Exception e ) {
			throw( new BuildException( "failed to initialize XMLDB database driver" ) );
		}
	}
}
