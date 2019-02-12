/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.debuggee.Debuggee;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;
import org.xmldb.api.base.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XMLResource;

import java.io.Writer;
import java.util.*;

import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.security.Permission;
import com.evolvedbinary.j8fu.Either;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

public class LocalXPathQueryService extends AbstractLocalService implements EXistXPathQueryService, EXistXQueryService {

	private final static Logger LOG = LogManager.getLogger(LocalXPathQueryService.class);

    private final TreeMap<String, String> namespaceDecls = new TreeMap<>();
    private final TreeMap<String, Object> variableDecls = new TreeMap<>();
    private boolean xpathCompatible = true;
    private String moduleLoadPath = null;
    private final  Properties properties;
    private boolean lockDocuments = false;
    private LockedDocumentMap lockedDocuments = null;
    private DBBroker reservedBroker = null;

    public LocalXPathQueryService(final Subject user, final BrokerPool pool, final LocalCollection collection) {
        super(user, pool, collection);

        this.properties = new Properties(collection.getProperties());
    }

    @Override
    public String getName() throws XMLDBException {
        return "XPathQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public void clearNamespaces() throws XMLDBException {
        namespaceDecls.clear();
    }

    @Override
    public String getNamespace(final String prefix) throws XMLDBException {
        return namespaceDecls.get(prefix);
    }

    @Override
    public String getProperty(final String property) throws XMLDBException {
        return properties.getProperty(property);
    }

    @Override
    public ResourceSet query(final String query) throws XMLDBException {
        return query(query, null);
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query) throws XMLDBException {
        return query(res, query, null);
    }

    @Override
    public ResourceSet query(final String query, final String sortBy) throws XMLDBException {
        return withDb((broker, transaction) -> {
            final XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(collection.getName(broker, transaction)) };
            return doQuery(broker, transaction, query, docs, null, sortBy);
        });
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query, final String sortBy) throws XMLDBException {
        final Node n = ((LocalXMLResource) res).root;

        return withDb((broker, transaction) -> {
            if (n != null && n instanceof org.exist.dom.memtree.NodeImpl) {
                final XmldbURI[] docs = new XmldbURI[]{ getCollectionUri(broker, transaction, res.getParentCollection()) };
                return doQuery(broker, transaction, query, docs, (org.exist.dom.memtree.NodeImpl) n, sortBy);
            }
            final NodeProxy node = ((LocalXMLResource) res).getNode(broker, transaction);
            if (node == null) {
                // resource is a document
                //TODO : use dedicated function in XmldbURI
                final XmldbURI[] docs = new XmldbURI[]{ getCollectionUri(broker, transaction, res.getParentCollection()).append(res.getDocumentId()) };
                return doQuery(broker, transaction, query, docs, null, sortBy);
            } else {
                final NodeSet set = new ExtArrayNodeSet(1);
                set.add(node);
                final XmldbURI[] docs = new XmldbURI[]{node.getOwnerDocument().getURI()};
                return doQuery(broker, transaction, query, docs, set, sortBy);
            }
        });
    }

    private ResourceSet doQuery(final DBBroker broker, final Txn transaction, final String query, final XmldbURI[] docs, final Sequence contextSet, final String sortExpr) throws XMLDBException {
        final Either<XPathException, CompiledExpression> maybeExpr = compileAndCheck(broker, transaction, query);
        if(maybeExpr.isLeft()) {
            final XPathException e = maybeExpr.left().get();
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } else {
            return execute(broker, transaction, docs, contextSet, maybeExpr.right().get(), sortExpr);
        }
    }

    @Override
    public ResourceSet execute(final CompiledExpression expression) throws XMLDBException {
    return withDb((broker, transaction) ->
        execute(broker, transaction, null, null, expression, null));
    }

    @Override
    public ResourceSet execute(final XMLResource res, final CompiledExpression expression) throws XMLDBException {
        return withDb((broker, transaction) -> {
            final NodeProxy node = ((LocalXMLResource) res).getNode(broker, transaction);
            if (node == null) {
                // resource is a document
                final XmldbURI[] docs = new XmldbURI[]{ getCollectionUri(broker, transaction, res.getParentCollection()).append(res.getDocumentId()) };
                return execute(broker, transaction, docs, null, expression, null);
            } else {
                final NodeSet set = new ExtArrayNodeSet(1);
                set.add(node);
                final XmldbURI[] docs = new XmldbURI[]{node.getOwnerDocument().getURI()};
                return execute(broker, transaction, docs, set, expression, null);
            }
        });
    }

    private ResourceSet execute(final DBBroker broker, final Txn transaction, XmldbURI[] docs, final Sequence contextSet, final CompiledExpression expression, final String sortExpr) throws XMLDBException {
        final long start = System.currentTimeMillis();
        final CompiledXQuery expr = (CompiledXQuery)expression;
        Sequence result = null;
        final XQueryContext context = expr.getContext();
        try {
            context.setStaticallyKnownDocuments(docs);
            if (lockedDocuments != null) {
                context.setProtectedDocs(lockedDocuments);
            }
            setupContext(null, context);

            final XQuery xquery = brokerPool.getXQueryService();
            result = xquery.execute(broker, expr, contextSet, properties);
        } catch (final Exception e) {
            // need to catch all runtime exceptions here to be able to release locked documents
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            /*
             * Run the cleanup tasks, but don't close BinaryValues which
             * are in the result set as the user has not yet accessed them.
             *
             * Final cleanup of those BinaryValues is done by the user
             * calling EXistResource#close(), ResourceSet#clear() or CompiledExpression#reset().
             */
            final Sequence resSeq = result;
            context.runCleanupTasks(o -> {
                if(resSeq != null && o instanceof BinaryValue) {
                    for(int i = 0; i < resSeq.getItemCount(); i++) {
                        if (resSeq.itemAt(i) == o) {
                            return false;
                        }
                    }
                }

                return true;
            });
        }
        LOG.debug("query took " + (System.currentTimeMillis() - start) + " ms.");
        if(result != null) {
            final Properties resourceSetProperties = new Properties(properties);
            resourceSetProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
            return new LocalResourceSet(user, brokerPool, collection, resourceSetProperties, result, sortExpr);
        } else {
            return null;
        }
    }

    @Override
    public ResourceSet execute(final Source source) throws XMLDBException {
        return execute((broker, transaction) -> source);
    }
	
    @Override
    public ResourceSet executeStoredQuery(final String uri) throws XMLDBException {
        return execute((broker, transaction) -> {
            final DocumentImpl resource = broker.getResource(new XmldbURI(uri), Permission.READ | Permission.EXECUTE);
            if (resource == null) {
                throw new XMLDBException(ErrorCodes.INVALID_URI, "No stored XQuery exists at: " + uri);
            }
            return new DBSource(broker, (BinaryDocument) resource, false);
        });
    }

    private ResourceSet execute(final LocalXmldbFunction<Source> sourceOp) throws XMLDBException {
        return withDb((broker, transaction) -> {

            final long start = System.currentTimeMillis();

            final Source source = sourceOp.apply(broker, transaction);

            final XmldbURI[] docs = new XmldbURI[]{XmldbURI.create(collection.getName(broker, transaction))};

            final XQuery xquery = brokerPool.getXQueryService();
            final XQueryPool pool = brokerPool.getXQueryPool();

            XQueryContext context;
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            if (compiled == null) {
                context = new XQueryContext(broker.getBrokerPool());
            } else {
                context = compiled.getContext();
                context.prepareForReuse();
            }

            context.setStaticallyKnownDocuments(docs);

            if (variableDecls.containsKey(Debuggee.PREFIX + ":session")) {
                context.declareVariable(Debuggee.SESSION, variableDecls.get(Debuggee.PREFIX + ":session"));
                variableDecls.remove(Debuggee.PREFIX + ":session");
            }

            setupContext(source, context);

            if (compiled == null) {
                compiled = xquery.compile(broker, context, source);
            }

            try {
                final Sequence result = xquery.execute(broker, compiled, null, properties);
                if(LOG.isDebugEnabled()) {
                    LOG.debug("query took " + (System.currentTimeMillis() - start) + " ms.");
                }
                final Properties resourceSetProperties = new Properties(properties);
                resourceSetProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
                return result != null ? new LocalResourceSet(user, brokerPool, collection, resourceSetProperties, result, null) : null;
            } finally {
                compiled.getContext().runCleanupTasks();
                pool.returnCompiledXQuery(source, compiled);
            }
        });
    }

    @Override
    public CompiledExpression compile(final String query) throws XMLDBException {
        return withDb((broker, transaction) -> {
            final Either<XPathException, CompiledExpression> maybeExpr = compileAndCheck(broker, transaction, query);
            if(maybeExpr.isLeft()) {
                final XPathException e = maybeExpr.left().get();
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } else {
                return maybeExpr.right().get();
            }
        });
    }

    @Override
    public CompiledExpression compileAndCheck(final String query) throws XMLDBException, XPathException {
    	final Either<XPathException, CompiledExpression> result = withDb((broker, transaction) -> compileAndCheck(broker, transaction, query));
        if(result.isLeft()) {
            throw result.left().get();
        } else {
            return result.right().get();
        }
    }

    private Either<XPathException, CompiledExpression> compileAndCheck(final DBBroker broker, final Txn transaction, final String query) throws XMLDBException {
        final long start = System.currentTimeMillis();
        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());

        try {
            setupContext(null, context);
            final CompiledExpression expr = xquery.compile(broker, context, query);
            if(LOG.isDebugEnabled()) {
                LOG.debug("compilation took " + (System.currentTimeMillis() - start));
            }
            return Either.Right(expr);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final IllegalArgumentException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final XPathException e) {
            return Either.Left(e);
        }
    }

    @Override
    public ResourceSet queryResource(final String resource, final String query) throws XMLDBException {
    	return withDb((broker, transaction) -> {
            final LocalXMLResource res = (LocalXMLResource) collection.getResource(broker, transaction, resource);
            if (res == null) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource '" + resource + "' not found");
            }
            final XmldbURI[] docs = new XmldbURI[]{ getCollectionUri(broker, transaction, res.getParentCollection()).append(res.getDocumentId()) };
            return doQuery(broker, transaction, query, docs, null, null);
        });
    }

    protected void setupContext(final Source source, final XQueryContext context) throws XMLDBException, XPathException {
        try {
            context.setBaseURI(new AnyURIValue(properties.getProperty("base-uri", collection.getPath())));
        } catch(final XPathException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,"Invalid base uri",e);
        }

        if(moduleLoadPath != null) {
            context.setModuleLoadPath(moduleLoadPath);
        } else if (source != null) {
            String modulePath = null;
            if (source instanceof DBSource) {
                modulePath = ((DBSource) source).getDocumentPath().removeLastSegment().toString();
            } else if (source instanceof FileSource) {
                modulePath = ((FileSource) source).getPath().getParent().toString();
            }

            if (modulePath != null) {
                context.setModuleLoadPath(modulePath);
            }
        }

        // declare namespace/prefix mappings
        for (final Map.Entry<String, String> entry : namespaceDecls.entrySet()) {
            context.declareNamespace(entry.getKey(), entry.getValue());
        }

        // declare static variables
        for (final Map.Entry<String, Object> entry : variableDecls.entrySet()) {
            context.declareVariable(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Execute all following queries in a protected environment.
     * Protected means: it is guaranteed that documents referenced by the
     * query or the result set are not modified by other threads
     * until {@link #endProtected} is called.
     */
    @Override
    public void beginProtected() throws XMLDBException {
        try {
            int retries = BEGIN_PROTECTED_MAX_LOCKING_RETRIES == - 1 ? -1 : BEGIN_PROTECTED_MAX_LOCKING_RETRIES - 2;
            boolean deadlockCaught;
            do {
                reservedBroker = brokerPool.get(Optional.of(user));
                deadlockCaught = false;
                MutableDocumentSet docs = null;
                try {
                    final org.exist.collections.Collection coll = reservedBroker.getCollection(collection.getPathURI());
                    lockedDocuments = new LockedDocumentMap();
                    docs = new DefaultDocumentSet();
                    coll.allDocs(reservedBroker, docs, true, lockedDocuments, LockMode.WRITE_LOCK);
                    return;
                } catch (final LockException e) {
                    LOG.warn("Deadlock detected. Starting over again. Docs: {}; locked: {}. Cause: {}", docs.getDocumentCount(), lockedDocuments.size(), e.getMessage());
                    lockedDocuments.unlock();
                    reservedBroker.close();
                    deadlockCaught = true;
                } catch (final PermissionDeniedException e) {
                    throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                            "Permission denied on document");
                }
                retries--;
            } while (deadlockCaught && retries >= -1);
        } catch (final EXistException e) {
            if(reservedBroker != null) {
                reservedBroker.close();
            }
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
        }

        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Unable to beginProtected after " + BEGIN_PROTECTED_MAX_LOCKING_RETRIES + " retries");
    }
	
    /**
     * Close the protected environment. All locks held
     * by the current thread are released. The result set
     * is no longer guaranteed to be stable.
     */
    @Override
    public void endProtected() {
        lockDocuments = false;
        if(lockedDocuments != null) {
            lockedDocuments.unlock();
        }
        lockedDocuments = null;

        if (reservedBroker != null) {
            reservedBroker.close();
        }
        reservedBroker = null;
    }

    @Override
    public void removeNamespace(final String ns) throws XMLDBException {
        for (final Iterator<String> i = namespaceDecls.values().iterator(); i.hasNext();) {
            if (i.next().equals(ns)) {
                i.remove();
            }
        }
    }

    @Override
    public void setCollection(final Collection col) throws XMLDBException {
    }

    @Override
    public void setNamespace(final String prefix, final String namespace) throws XMLDBException {
        namespaceDecls.put(prefix, namespace);
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        properties.setProperty(property, value);
    }

    @Override
    public void declareVariable(final String qname, final Object initialValue) throws XMLDBException {
        variableDecls.put(qname, initialValue);
    }

    @Override
    public void clearVariables() throws XMLDBException {
        variableDecls.clear();
    }

    @Override
    public void setXPathCompatibility(final boolean backwardsCompatible) {
        this.xpathCompatible = backwardsCompatible;
    }

    @Override
    public void setModuleLoadPath(final String path) {
        moduleLoadPath = path;		
    }

    @Override
    public void dump(final CompiledExpression expression, final Writer writer) throws XMLDBException {
        final CompiledXQuery expr = (CompiledXQuery)expression;
        expr.dump(writer);
    }
}
