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
package org.exist.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Optional;
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

    private static final Logger LOG = LogManager.getLogger(AuditTrailSessionListener.class);
    public static final String REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY = "org.exist.http.session_create_listener";
    public static final String REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY = "org.exist.http.session_destroy_listener";


    @Override
    public void sessionCreated(final HttpSessionEvent sessionEvent) {
        final HttpSession session = sessionEvent.getSession();

        LOG.info("Session created {}", session.getId());
        final String xqueryResourcePath = System.getProperty(REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY);
        executeXQuery(xqueryResourcePath);
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent sessionEvent) {
        final HttpSession session = (sessionEvent != null) ? sessionEvent.getSession() : null;
        if (session != null) {
            LOG.info("Destroyed session {}", session.getId());
        } else {
            LOG.info("Destroyed session");
        }

        final String xqueryResourcePath = System.getProperty(REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY);
        executeXQuery(xqueryResourcePath);
    }

    private void executeXQuery(String xqueryResourcePath) {
        if (xqueryResourcePath != null && !xqueryResourcePath.isEmpty()) {
            xqueryResourcePath = xqueryResourcePath.trim();

            try {
                final BrokerPool pool = BrokerPool.getInstance();
                final Subject sysSubject = pool.getSecurityManager().getSystemSubject();

                try (final DBBroker broker = pool.get(Optional.of(sysSubject))) {
                    if (broker == null) {
                        LOG.error("Unable to retrieve DBBroker for {}", sysSubject.getName());
                        return;
                    }

                    final XmldbURI pathUri = XmldbURI.create(xqueryResourcePath);


                    try(final LockedDocument lockedResource = broker.getXMLResource(pathUri, LockMode.READ_LOCK)) {

                        final Source source;
                        if (lockedResource != null) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Resource [{}] exists.", xqueryResourcePath);
                            }
                            source = new DBSource(pool, (BinaryDocument) lockedResource.getDocument(), true);
                        } else {
                            LOG.error("Resource [{}] does not exist.", xqueryResourcePath);
                            return;
                        }


                        final XQuery xquery = pool.getXQueryService();
                        if (xquery == null) {
                            LOG.error("broker unable to retrieve XQueryService");
                            return;
                        }

                        final XQueryPool xqpool = pool.getXQueryPool();
                        CompiledXQuery compiled = xqpool.borrowCompiledXQuery(broker, source);
                        final XQueryContext context;
                        if (compiled == null) {
                            context = new XQueryContext(broker.getBrokerPool());
                        } else {
                            context = compiled.getContext();
                            context.prepareForReuse();
                        }
                        context.setStaticallyKnownDocuments(new XmldbURI[]{pathUri});
                        context.setBaseURI(new AnyURIValue(pathUri.toString()));

                        if (compiled == null) {
                            compiled = xquery.compile(context, source);
                        } else {
                            compiled.getContext().updateContext(context);
                            context.getWatchDog().reset();
                        }

                        final Properties outputProperties = new Properties();

                        try {
                            final long startTime = System.currentTimeMillis();
                            final Sequence result = xquery.execute(broker, compiled, null, outputProperties);
                            final long queryTime = System.currentTimeMillis() - startTime;
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("XQuery execution results: {} in {}ms.", result.toString(), queryTime);
                            }
                        } finally {
                            context.runCleanupTasks();
                            xqpool.returnCompiledXQuery(source, compiled);
                        }
                    }
                }

            } catch (final Exception e) {
                LOG.error("Exception while executing [{}] script", xqueryResourcePath, e);
            }
        }
    }
}
