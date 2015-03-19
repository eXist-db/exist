/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 * $Id$
 */
package org.exist.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;

import javax.servlet.http.HttpSessionListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSession;
import java.util.Properties;

/**
 * Executes an XQuery script whose filename is retrieved from the
 * java option 'org.exist.http.session_create_listener' when an
 * HTTP session is created and 'org.exist.http.session_destroy_listener'
 * when an HTTP session is destroyed.
 *
 * If the java option is not set, then do nothing.
 *
 * If the java option is set, then retrieve the script from the file
 * or resource designated by the value of the property.  Execute the
 * XQuery script to record the creation or destruction of a HTTP session.
 */
public class AuditTrailSessionListener implements HttpSessionListener {

    private final static Logger LOG = LogManager.getLogger(AuditTrailSessionListener.class);
    private static final String REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY = "org.exist.http.session_create_listener";
    private static final String REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY = "org.exist.http.session_destroy_listener";

    

    /**
     *
     * @param sessionEvent
     */
    public void sessionCreated(HttpSessionEvent sessionEvent) {
        final HttpSession session = sessionEvent.getSession();

        LOG.info("session created " + session.getId());
        final String xqueryResourcePath = System.getProperty(REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY);
        executeXQuery(xqueryResourcePath);
    }

    /**
     *
     * @param sessionEvent
     */
    public void sessionDestroyed(HttpSessionEvent sessionEvent) {
        final HttpSession session = (sessionEvent != null) ? sessionEvent.getSession() : null;
        if (session != null)
            {LOG.info("destroy session " + session.getId());}
        else
            {LOG.info("destroy session");}

        final String xqueryResourcePath = System.getProperty(REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY);
        executeXQuery(xqueryResourcePath);
    }

    private void executeXQuery(String xqueryResourcePath) {
        if (xqueryResourcePath != null && xqueryResourcePath.length() > 0) {
            xqueryResourcePath = xqueryResourcePath.trim();
            BrokerPool pool = null;
            DBBroker broker = null;
            Subject subject = null;

            try {
                DocumentImpl resource = null;
                Source source = null;

                pool = BrokerPool.getInstance();
                subject = pool.getSecurityManager().getSystemSubject();

                broker = pool.get(subject);
                if (broker == null) {
                    LOG.error("Unable to retrieve DBBroker for " + subject.getName());
                    return;
                }

                final XmldbURI pathUri = XmldbURI.create(xqueryResourcePath);


                resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

                if(resource != null) {
                    LOG.info("Resource [" + xqueryResourcePath + "] exists.");
                    source = new DBSource(broker, (BinaryDocument)resource, true);
                } else {
                    LOG.error("Resource [" + xqueryResourcePath + "] does not exist.");
                    return;
                }


                final XQuery xquery = broker.getXQueryService();

                if (xquery == null) {
                    LOG.error("broker unable to retrieve XQueryService");
                    return;
                }

                final XQueryPool xqpool = xquery.getXQueryPool();
                CompiledXQuery compiled = xqpool.borrowCompiledXQuery(broker, source);
                XQueryContext context;
                if (compiled == null)
                    {context = xquery.newContext(AccessContext.REST);}
                else
                    {context = compiled.getContext();}
                context.setStaticallyKnownDocuments(new XmldbURI[] { pathUri });
                context.setBaseURI(new AnyURIValue(pathUri.toString()));

                if (compiled == null)
                    {compiled = xquery.compile(context, source);}
                else {
                    compiled.getContext().updateContext(context);
                    context.getWatchDog().reset();
                }

                final Properties outputProperties = new Properties();
                Sequence result = null;

                try {
                    final long startTime = System.currentTimeMillis();
                    result = xquery.execute(compiled, null, outputProperties);
                    final long queryTime = System.currentTimeMillis() - startTime;
                    LOG.info("XQuery execution results: " + result.toString()  + " in " + queryTime + "ms.");
                } finally {
                    xqpool.returnCompiledXQuery(source, compiled);
                }

            } catch (final Exception e) {
                LOG.error("Exception while executing [" + xqueryResourcePath + "] script for " + subject.getName(), e);
            }
            finally {
                if (pool != null)
                    {pool.release(broker);}
            }
        }
    }
}