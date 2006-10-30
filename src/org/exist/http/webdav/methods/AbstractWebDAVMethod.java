/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.http.webdav.methods;

import org.apache.log4j.Logger;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.storage.BrokerPool;

/**
 * Abstract base class for all WebDAV methods.
 * 
 * @author wolf
 */
public abstract class AbstractWebDAVMethod implements WebDAVMethod {

	final static Logger LOG = Logger.getLogger(AbstractWebDAVMethod.class);
	
	// common error messages
	final static String READ_PERMISSION_DENIED  = "Not allowed to read resource";
        final static String WRITE_PERMISSION_DENIED = "Not allowed to write resource";
        final static String LOCK_PERMISSION_DENIED  = "Not allowed to lock resource";
        
	final static String NOT_FOUND_ERR = "No resource or collection found";
        
        
        final static int    SC_UNLOCK_SUCCESSFULL   = 204;
        
        final static int    SC_PRECONDITION_FAILED  = 412;
        final static String PRECONDITION_FAILED     = "Precondition Failed";
        
        final static int    SC_RESOURCE_IS_LOCKED     = 423;
        final static String RESOURCE_IS_LOCKED        = "Locked";
        
	protected BrokerPool pool;
	
	public AbstractWebDAVMethod(BrokerPool pool) {
		this.pool = pool;
	}
}
