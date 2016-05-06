/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
 *  $Id$
 */
package org.exist.debuggee;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.exist.Database;
import org.exist.debuggee.dbgp.packets.Init;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeImpl implements Debuggee {

    protected final static Logger LOG = LogManager.getLogger(Debuggee.class);

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
    
    private DebuggeeConnectionTCP connection = null;
    
    private Map<String, Session> sessions = new HashMap<String, Session>();
    
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
			XQueryContext context = compiledXQuery.getContext();
			context.setDebuggeeJoint(joint);
			
			String idesession = "";
			if (context.isVarDeclared(Debuggee.SESSION)) {
				try {
					Variable var = context.resolveVariable(Debuggee.SESSION);
					idesession = var.getValue().toString();
				} catch (XPathException e) {
				}
			}
			
			String idekey = "";
			if (context.isVarDeclared(Debuggee.IDEKEY)) {
				try {
					Variable var = context.resolveVariable(Debuggee.IDEKEY);
					idekey = var.getValue().toString();
				} catch (XPathException e) {
				}
			}
			
			joint.continuation(new Init(session, idesession, idekey));
			
			return true;
		}
		
	}

	@Override
	public String start(String uri) throws Exception {
		Database db = null;
		ScriptRunner runner = null;
		
		try {
			db = BrokerPool.getInstance();
			
			try(final DBBroker broker = db.getBroker()) {

				// Try to find the XQuery
				Source source = SourceFactory.getSource(broker, "", uri, true);

				if (source == null) return null;

				XQuery xquery = broker.getBrokerPool().getXQueryService();

				XQueryContext queryContext = new XQueryContext(broker.getBrokerPool());

				// Find correct script load path
				queryContext.setModuleLoadPath(XmldbURI.create(uri).removeLastSegment().toString());

				CompiledXQuery compiled;
				try {
					compiled = xquery.compile(broker, queryContext, source);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}

				String sessionId = String.valueOf(queryContext.hashCode());

				//link debugger session & script
				DebuggeeJointImpl joint = new DebuggeeJointImpl();
				SessionImpl session = new SessionImpl();

				joint.setCompiledScript(compiled);
				queryContext.setDebuggeeJoint(joint);
				joint.continuation(new Init(session, sessionId, "eXist"));

				runner = new ScriptRunner(session, compiled);
				runner.start();

				int count = 0;
				while (joint.firstExpression == null && runner.exception == null && count < 10) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
					count++;
				}

				if (runner.exception != null) {
					throw runner.exception;
				}

				if (joint.firstExpression == null) {
					throw new XPathException("Can't run debug session.");
				}

				//queryContext.declareVariable(Debuggee.SESSION, sessionId);

				//XXX: make sure that it started up
				sessions.put(sessionId, session);

				return sessionId;
			}

		} catch (Exception e) {
			if (runner != null)
				runner.stop();
			
			throw e;
		}
	}

	@Override
	public Session getSession(String id) {
		return sessions.get(id);
	}
}
