/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id: DebuggeeImpl.java 12464 2010-08-20 08:48:38Z shabanovd $
 */
package org.exist.debuggee;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.dbgp.packets.Init;
import org.exist.xquery.CompiledXQuery;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeImpl implements Debuggee {

    protected final static Logger LOG = Logger.getLogger(Debuggee.class);

	protected static final Map<String, String> GET_FEATURES = new HashMap<String, String>();
	protected static final Map<String, String> SET_GET_FEATURES = new HashMap<String, String>();

    static {
    	GET_FEATURES.put("language_supports_threads", "0");
    	GET_FEATURES.put("language_name", "XQuery");
    	GET_FEATURES.put("language_version", "1.0");
    	GET_FEATURES.put("protocol_version", "1");
    	GET_FEATURES.put("supports_async", "1");
    	GET_FEATURES.put("breakpoint_types", "line");
    	
    	SET_GET_FEATURES.put("multiple_sessions", "0");
    	SET_GET_FEATURES.put("encoding", "UTF-8");
    	SET_GET_FEATURES.put("max_children", "32");
    	SET_GET_FEATURES.put("max_data", "1024");
    	SET_GET_FEATURES.put("max_depth", "1");
    }
    
    DebuggeeConnectionTCP connection = null; 
    
	public DebuggeeImpl() {
		connection = new DebuggeeConnectionTCP();
	}
	
	public boolean joint(CompiledXQuery compiledXQuery) {
		synchronized (this) {
			IoSession session = connection.connect();
			
			if (session == null) 
				return false;

			//link debugger session & script
			DebuggeeJointImpl joint = new DebuggeeJointImpl();

			joint.setCompiledScript(compiledXQuery);
			compiledXQuery.getContext().setDebuggeeJoint(joint);
			
			joint.continuation(new Init(session));
			
			return true;
		}
		
	}
}
