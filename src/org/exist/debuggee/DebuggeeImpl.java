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
 *  $Id:$
 */
package org.exist.debuggee;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeImpl implements Debuggee {

    protected final static Logger LOG = Logger.getLogger(Debuggee.class);

	private static final Map<String, String> FEATURES = new HashMap<String, String>();

    static {
    	FEATURES.put("language_supports_threads", "0");
    	FEATURES.put("language_name", "XQuery");
    	FEATURES.put("language_version", "1.0");
    	FEATURES.put("encoding", "UTF-8");
    	FEATURES.put("protocol_version", "1");
    	FEATURES.put("supports_async", "1");
        FEATURES.put("breakpoint_types", "line");
        FEATURES.put("multiple_sessions", "0");
        FEATURES.put("max_children", "32");
        FEATURES.put("max_data", "1024");
        FEATURES.put("max_depth", "1");
    }
    
    //ID -> Session
	private Map<String, DebuggeeJoint> sessions = new HashMap<String, DebuggeeJoint>(); 
    
	public DebuggeeImpl() {
	}
	
	public DebuggeeJoint joint() {
		DebuggeeConnection connection = new DebuggeeConnection();
		if (connection.isConnected()) {
			//put to map
		}
		
		DebuggeeJoint joint = new DebuggeeJointImpl();
		return joint;
	}
}
