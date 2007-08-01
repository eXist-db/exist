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
 *  $Id:
 */
package org.exist.security;

import org.apache.log4j.Logger;

/**
 * Instatiates an appropriate Permission class based on the current configuration
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class PermissionFactory
{
	private final static Logger LOG = Logger.getLogger(PermissionFactory.class);
	
	public static final Permission getPermission()
	{
		try
		{
			//Class permissionClass = (Class)broker.getConfiguration().getProperty(BrokerPool.PROPERTY_SECURITY_CLASS);
	        return (Permission)new UnixStylePermission();
		}
		catch(Throwable ex)
		{
	          LOG.warn("Exception while instantiating security permission class.", ex);
	    }
		return null;
	}
	
	public static final Permission getPermission(int perm)
	{
		try
		{
			//Class permissionClass = (Class)broker.getConfiguration().getProperty(BrokerPool.PROPERTY_SECURITY_CLASS);
	        return (Permission)new UnixStylePermission(perm);
		}
		catch(Throwable ex)
		{
	          LOG.warn("Exception while instantiating security permission class.", ex);
	    }
		return null;
	}
	
	public static final Permission getPermission(String user, String group, int permissions)
	{
		try
		{
			//Class permissionClass = (Class)broker.getConfiguration().getProperty(BrokerPool.PROPERTY_SECURITY_CLASS);
	        return (Permission)new UnixStylePermission(user, group, permissions);
		}
		catch(Throwable ex)
		{
	          LOG.warn("Exception while instantiating security permission class.", ex);
	    }
		return null;
	}
	
}
