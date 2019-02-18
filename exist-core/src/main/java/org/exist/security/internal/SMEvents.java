/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.security.internal;

import java.util.List;
import java.util.Optional;

import org.exist.Database;
import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@ConfigurationClass("events")
public class SMEvents implements Configurable {
	
    public final static String NAMESPACE_URI = "http://exist-db.org/security/events";
    public final static String PREFIX = "sec-ev"; //security-events //secev //sev

	@ConfigurationFieldAsAttribute("script-uri")
	protected String scriptURI = "";

	@ConfigurationFieldAsElement("authentication")
	protected EventAuthentication authentication = null;
	
	protected SecurityManager sm;
	
	private Configuration configuration = null;
	
	public SMEvents(SecurityManagerImpl sm, Configuration config) {
		this.sm = sm;
		
        configuration = Configurator.configure(this, config);
	}
	
	public Database getDatabase() {
		return sm.getDatabase();
	}
	
	public SecurityManager getSecurityManager() {
		return sm;
	}
	
	protected void authenticated(Subject subject) {
		if (authentication == null) {
//			List<Expression> args = new ArrayList<Expression>(2);
//			args.add(new LiteralValue(context, new StringValue(subject.getRealmId()) ));
//			args.add(new LiteralValue(context, new StringValue(subject.getName()) ));
			runScript(subject, scriptURI, null, EventAuthentication.functionName, null);
		} else {
			authentication.onEvent(subject);
		}
	}
	
	protected void runScript(Subject subject, String scriptURI, String script, QName functionName, List<Expression> args) {
        
		final Database db = getDatabase();
        try(final DBBroker broker = db.get(Optional.ofNullable(subject))) {

            final Source source = getQuerySource(broker, scriptURI, script);
            if(source == null) {return;}

            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());

            final CompiledXQuery compiled = xquery.compile(broker, context, source);

//            Sequence result = xquery.execute(compiled, subject.getName());

    		final ProcessMonitor pm = db.getProcessMonitor();

            //execute the XQuery
            try {
        		final UserDefinedFunction function = context.resolveFunction(functionName, 0);
        		if (function != null) {
    	            context.getProfiler().traceQueryStart();
    	            pm.queryStarted(context.getWatchDog());
    	            
    	            final FunctionCall call = new FunctionCall(context, function);
    	            if (args != null) {
    	            	call.setArguments(args);
    	            }

					final Sequence contextSequence;
					final ContextItemDeclaration cid = context.getContextItemDeclartion();
					if(cid != null) {
						contextSequence = cid.eval(null);
					} else {
						contextSequence = NodeSet.EMPTY_SET;
					}

    	            call.analyze(new AnalyzeContextInfo());
    	    		call.eval(contextSequence);
        		}
            } catch(final XPathException e) {
            	//XXX: log
            	e.printStackTrace();
            } finally {
            	if (pm != null) {
            		context.getProfiler().traceQueryEnd(context);
            		pm.queryCompleted(context.getWatchDog());
            	}
            	compiled.reset();
        		context.reset();
            }
            
        } catch (final Exception e) {
        	//XXX: log
        	e.printStackTrace();
        }
 	}
	
	private Source getQuerySource(DBBroker broker, String scriptURI, String script) {
		if(scriptURI != null) {

			final XmldbURI pathUri = XmldbURI.create(scriptURI);
        	try(final LockedDocument lockedResource = broker.getXMLResource(pathUri, LockMode.READ_LOCK)) {
				if (lockedResource != null) {
					return new DBSource(broker, (BinaryDocument)lockedResource.getDocument(), true);
				}
        	} catch (final PermissionDeniedException e) {
        		//XXX: log
				e.printStackTrace();
			}

//			try {
//				querySource = SourceFactory.getSource(broker, null, scriptURI, false);
//			} catch(Exception e) {
//				//LOG.error(e);
//			}
		} else if(script != null && !script.isEmpty()) {
			return new StringSource(script);
		}
	
		return null;
	}

	@Override
	public boolean isConfigured() {
		return configuration != null;
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}