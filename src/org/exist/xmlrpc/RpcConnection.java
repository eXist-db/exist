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
package org.exist.xmlrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.SortedNodeSet;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.Version;
import org.exist.backup.Backup;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.scheduler.SystemTaskJob;
import org.exist.scheduler.impl.SystemTaskJobImpl;
import org.exist.security.ACLPermission;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.PermissionFactory.PermissionModifier;
import org.exist.security.SchemaType;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.*;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import org.xmldb.api.base.XMLDBException;

/**
 * This class implements the actual methods defined by
 * {@link org.exist.xmlrpc.RpcAPI}.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 */
public class RpcConnection implements RpcAPI {
    
    private final static Logger LOG = LogManager.getLogger(RpcConnection.class);

    private final static int MAX_DOWNLOAD_CHUNK_SIZE = 0x40000;
    private final static String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private final XmldbRequestProcessorFactory factory;
    private final Subject user;

    public RpcConnection(final XmldbRequestProcessorFactory factory, final Subject user) {
        super();
        this.factory = factory;
        this.user = user;
    }

    @Override
    public String getVersion() {
        return Version.getVersion();
    }

    private void handleException(final Throwable e) throws EXistException, PermissionDeniedException {
        LOG.debug(e.getMessage(), e);
        if (e instanceof EXistException) {
            throw (EXistException) e;
        } else if (e instanceof PermissionDeniedException) {
            throw (PermissionDeniedException) e;
        } else {
            throw new EXistException(e);
        }
    }

    @Override
    public boolean createCollection(final String name) throws EXistException, PermissionDeniedException {
        return createCollection(name, null);
    }

    @Override
    public boolean createCollection(final String name, final Date created) throws EXistException, PermissionDeniedException {
        try {
            return createCollection(XmldbURI.xmldbUriFor(name), created);
        } catch (final URISyntaxException e) {
            handleException(e);
        }
        return false;
    }

    private boolean createCollection(final XmldbURI collUri, final Date created) throws PermissionDeniedException, EXistException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            Collection current = broker.getCollection(collUri);
            if (current != null) {
                return true;
            }

            current = broker.getOrCreateCollection(transaction, collUri);

            //TODO : register a lock (wich one ?) within the transaction ?
            if (created != null) {
                current.setCreationTime(created.getTime());
            }
            LOG.debug("creating collection " + collUri);

            broker.saveCollection(transaction, current);
            transact.commit(transaction);
            broker.flush();

            //broker.sync();
            LOG.info("collection " + collUri + " has been created");
            return true;

        } catch (final Throwable e) {
            handleException(e);
        }
        return false;
    }

    @Override
    public boolean configureCollection(final String collName, final String configuration)
            throws EXistException, PermissionDeniedException {
        try {
            return configureCollection(XmldbURI.xmldbUriFor(collName), configuration);
        } catch (final URISyntaxException e) {
            handleException(e);
        }
        return false;
    }

    private boolean configureCollection(final XmldbURI collUri, final String configuration)
            throws EXistException, PermissionDeniedException {
        Collection collection = null;
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            try {
                collection = broker.openCollection(collUri, Lock.READ_LOCK);
                if (collection == null) {
                    transact.abort(transaction);
                    throw new EXistException("collection " + collUri + " not found!");
                }
            } finally {
                if (collection != null) {
                    collection.release(Lock.READ_LOCK);
                }
            }
            final CollectionConfigurationManager mgr = factory.getBrokerPool().getConfigurationManager();
            mgr.addConfiguration(transaction, broker, collection, configuration);
            transact.commit(transaction);
            LOG.info("Configured '" + collection.getURI() + "'");
        } catch (final CollectionConfigurationException e) {
            throw new EXistException(e.getMessage());
        }
        return false;
    }

    public String createId(final String collName) throws EXistException, URISyntaxException, PermissionDeniedException {
        return createId(XmldbURI.xmldbUriFor(collName));
    }

    private String createId(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        final DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + collUri + " not found!");
            }
            XmldbURI id;
            final Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this id does already exist
                if (collection.hasDocument(broker, id)) {
                    ok = false;
                }

                if (collection.hasSubcollection(broker, id)) {
                    ok = false;
                }

            } while (!ok);
            return id.toString();
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    protected QueryResult doQuery(final DBBroker broker, final CompiledXQuery compiled,
            final NodeSet contextSet, final Map<String, Object> parameters)
            throws Exception {
        final XQuery xquery = broker.getXQueryService();
        final XQueryPool pool = xquery.getXQueryPool();

        checkPragmas(compiled.getContext(), parameters);
        LockedDocumentMap lockedDocuments = null;
        try {
            final long start = System.currentTimeMillis();
            lockedDocuments = beginProtected(broker, parameters);
            if (lockedDocuments != null) {
                compiled.getContext().setProtectedDocs(lockedDocuments);
            }
            final Properties outputProperties = new Properties();
            final Sequence result = xquery.execute(compiled, contextSet, outputProperties);
            // pass last modified date to the HTTP response
            HTTPUtils.addLastModifiedHeader(result, compiled.getContext());
            LOG.info("query took " + (System.currentTimeMillis() - start) + "ms.");
            return new QueryResult(result, outputProperties);
        } catch (final XPathException e) {
            return new QueryResult(e);
        } finally {
            if (lockedDocuments != null) {
                lockedDocuments.unlock();
            }
        }
    }

    protected LockedDocumentMap beginProtected(final DBBroker broker, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final String protectColl = (String) parameters.get(RpcAPI.PROTECTED_MODE);
        if (protectColl == null) {
            return null;
        }
        do {
            MutableDocumentSet docs = null;
            final LockedDocumentMap lockedDocuments = new LockedDocumentMap();
            try {
                final Collection coll = broker.getCollection(XmldbURI.createInternal(protectColl));
                docs = new DefaultDocumentSet();
                coll.allDocs(broker, docs, true, lockedDocuments, Lock.WRITE_LOCK);
                return lockedDocuments;
            } catch (final LockException e) {
                LOG.debug("Deadlock detected. Starting over again. Docs: " + docs.getDocumentCount() + "; locked: "
                        + lockedDocuments.size());
                lockedDocuments.unlock();
            }
        } while (true);
    }

    private CompiledXQuery compile(final DBBroker broker, final Source source, final Map<String, Object> parameters) throws XPathException, IOException, PermissionDeniedException {
        final XQuery xquery = broker.getXQueryService();
        final XQueryPool pool = xquery.getXQueryPool();
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        XQueryContext context;
        if (compiled == null) {
            context = xquery.newContext(AccessContext.XMLRPC);
        } else {
            context = compiled.getContext();
        }
        final String base = (String) parameters.get(RpcAPI.BASE_URI);
        if (base != null) {
            context.setBaseURI(new AnyURIValue(base));
        }
        final String moduleLoadPath = (String) parameters.get(RpcAPI.MODULE_LOAD_PATH);
        if (moduleLoadPath != null) {
            context.setModuleLoadPath(moduleLoadPath);
        }
        final Map<String, String> namespaces = (Map<String, String>) parameters.get(RpcAPI.NAMESPACES);
        if (namespaces != null && namespaces.size() > 0) {
            context.declareNamespaces(namespaces);
        }
        //  declare static variables
        final Map<String, Object> variableDecls = (Map<String, Object>) parameters.get(RpcAPI.VARIABLES);
        if (variableDecls != null) {
            for (final Map.Entry<String, Object> entry : variableDecls.entrySet()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("declaring " + entry.getKey() + " = " + entry.getValue());
                }
                context.declareVariable(entry.getKey(), entry.getValue());
            }
        }
        final Object[] staticDocuments = (Object[]) parameters.get(RpcAPI.STATIC_DOCUMENTS);
        if (staticDocuments != null) {
            try {
                final XmldbURI[] d = new XmldbURI[staticDocuments.length];
                for (int i = 0; i < staticDocuments.length; i++) {
                    XmldbURI next = XmldbURI.xmldbUriFor((String) staticDocuments[i]);
                    d[i] = next;
                }
                context.setStaticallyKnownDocuments(d);
            } catch (final URISyntaxException e) {
                throw new XPathException(e);
            }
        } else if (context.isBaseURIDeclared()) {
            context.setStaticallyKnownDocuments(new XmldbURI[]{context.getBaseURI().toXmldbURI()});
        }
        if (compiled == null) {
            compiled = xquery.compile(context, source);
        }
        return compiled;
    }

    @Override
    public String printDiagnostics(final String query, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final Source source = new StringSource(query);
            final XQuery xquery = broker.getXQueryService();
            final XQueryPool pool = xquery.getXQueryPool();
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            if (compiled == null) {
                compiled = compile(broker, source, parameters);
            }
            final StringWriter writer = new StringWriter();
            compiled.dump(writer);
            return writer.toString();

        } catch (final Throwable e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
        return null;
    }

    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output
     * properties.
     *
     * @param context
     * @param parameters
     * @throws org.exist.xquery.XPathException
     */
    protected void checkPragmas(final XQueryContext context, final Map<String, Object> parameters) throws XPathException {
        final Option pragma = context.getOption(Option.SERIALIZE_QNAME);
        checkPragmas(pragma, parameters);
    }

    protected void checkPragmas(final Option pragma, final Map<String, Object> parameters) throws XPathException {
        if (pragma == null) {
            return;
        }
        final String[] contents = pragma.tokenizeContents();
        for (final String content : contents) {
            final String[] pair = Option.parseKeyValuePair(content);
            if (pair == null) {
                throw new XPathException("Unknown parameter found in " + pragma.getQName().getStringValue()
                        + ": '" + content + "'");
            }
            LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
            parameters.put(pair[0], pair[1]);
        }
    }

    @Override
    public int executeQuery(final byte[] xpath, final String encoding, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        String xpathString = null;
        if (encoding != null) {
            try {
                xpathString = new String(xpath, encoding);
            } catch (final UnsupportedEncodingException e) {
                LOG.warn(e);
            }
        }

        if (xpathString == null) {
            xpathString = new String(xpath);
        }

        LOG.debug("query: " + xpathString);
        return executeQuery(xpathString, parameters);
    }

    @Override
    public int executeQuery(final String xpath, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        Source source = null;
        CompiledXQuery compiled = null;
        try {
            source = new StringSource(xpath);
            broker = factory.getBrokerPool().get(user);
            compiled = compile(broker, source, parameters);
            final QueryResult result = doQuery(broker, compiled, null,
                    parameters);
            if (result.hasErrors()) {
                throw result.getException();
            }
            result.queryTime = System.currentTimeMillis() - startTime;
            return factory.resultSets.add(result);

        } catch (final Throwable e) {
            handleException(e);

        } finally {
            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if(broker != null) {
                    broker.getXQueryService().getXQueryPool().returnCompiledXQuery(source, compiled);
                }
            }
            factory.getBrokerPool().release(broker);
        }
        return -1;
    }

    protected String formatErrorMsg(final String message) {
        return formatErrorMsg("error", message);
    }

    protected String formatErrorMsg(final String type, final String message) {
        final StringBuilder buf = new StringBuilder();
        buf.append("<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" ");
        buf.append("hitCount=\"0\">");
        buf.append('<');
        buf.append(type);
        buf.append('>');
        buf.append(message);
        buf.append("</");
        buf.append(type);
        buf.append("></exist:result>");
        return buf.toString();
    }

    @Override
    public boolean existsAndCanOpenCollection(final String collectionUri) throws EXistException, PermissionDeniedException {
        final DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(XmldbURI.xmldbUriFor(collectionUri), Lock.READ_LOCK);
            if (collection == null) {
                return false;
            }

            return true;
        } catch (final URISyntaxException use) {
            throw new EXistException("Collection '" + collectionUri + "' does not indicate a valid collection URI: " + use.getMessage(), use);
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getCollectionDesc(final String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return getCollectionDesc((rootCollection == null) ? XmldbURI.ROOT_COLLECTION_URI : XmldbURI.xmldbUriFor(rootCollection));
        } catch (final Throwable e) {
            handleException(e);
        }
        return null;
    }

    private Map<String, Object> getCollectionDesc(final XmldbURI rootUri) throws Exception {
        final DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(rootUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + rootUri + " not found!");
            }
            final Map<String, Object> desc = new HashMap<>();
            final List<Map<String, Object>> docs = new ArrayList<>();
            final List<String> collections = new ArrayList<>();
            if (collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                    final DocumentImpl doc = i.next();
                    final Permission perms = doc.getPermissions();

                    final Map<String, Object> hash = new HashMap<>(4);
                    hash.put("name", doc.getFileURI().toString());
                    hash.put("owner", perms.getOwner().getName());
                    hash.put("group", perms.getGroup().getName());
                    hash.put("permissions", perms.getMode());
                    hash.put("type", doc.getResourceType() == DocumentImpl.BINARY_FILE ? "BinaryResource" : "XMLResource");
                    docs.add(hash);
                }
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                    collections.add(i.next().toString());
                }
            }

            final Permission perms = collection.getPermissionsNoLock();
            desc.put("collections", collections);
            desc.put("documents", docs);
            desc.put("name", collection.getURI().toString());
            desc.put("created", Long.toString(collection.getCreationTime()));
            desc.put("owner", perms.getOwner().getName());
            desc.put("group", perms.getGroup().getName());
            desc.put("permissions", perms.getMode());

            return desc;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> describeResource(final String resourceName)
            throws EXistException, PermissionDeniedException {
        try {
            return describeResource(XmldbURI.xmldbUriFor(resourceName));
        } catch (final URISyntaxException | EXistException | PermissionDeniedException e) {
            handleException(e);
        }
        return null;
    }

    private Map<String, Object> describeResource(final XmldbURI resourceUri)
            throws EXistException, PermissionDeniedException {
        final DBBroker broker = factory.getBrokerPool().get(user);
        DocumentImpl doc = null;
        final Map<String, Object> hash = new HashMap<>(5);
        try {
            doc = broker.getXMLResource(resourceUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + resourceUri + " not found!");
                return hash;
            }
            final Permission perms = doc.getPermissions();
            hash.put("name", resourceUri.toString());
            hash.put("owner", perms.getOwner().getName());
            hash.put("group", perms.getGroup().getName());
            hash.put("permissions", perms.getMode());

            if (perms instanceof ACLPermission) {
                hash.put("acl", getACEs(perms));
            }

            hash.put("type",
                    doc.getResourceType() == DocumentImpl.BINARY_FILE
                            ? "BinaryResource"
                            : "XMLResource");
            final long resourceLength = doc.getContentLength();
            hash.put("content-length", (resourceLength > (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) resourceLength);
            hash.put("content-length-64bit", Long.toString(resourceLength));
            hash.put("mime-type", doc.getMetadata().getMimeType());
            hash.put("created", new Date(doc.getMetadata().getCreated()));
            hash.put("modified", new Date(doc.getMetadata().getLastModified()));
            return hash;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> describeCollection(final String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return describeCollection((rootCollection == null) ? XmldbURI.ROOT_COLLECTION_URI : XmldbURI.xmldbUriFor(rootCollection));
        } catch (final Throwable e) {
            handleException(e);
            return null;
        }
    }

    /**
     * The method <code>describeCollection</code>
     *
     * Returns details of a collection - collections (list of sub-collections) -
     * name - created - owner - group - permissions - acl
     *
     * If you do not have read access on the collection, the list of
     * sub-collections will be empty, an exception will not be thrown!
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>Map</code> value
     * @exception Exception if an error occurs
     */
    private Map<String, Object> describeCollection(final XmldbURI collUri)
            throws Exception {
        final DBBroker broker = factory.getBrokerPool().get(user);
        Collection collection = null;
        try {
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + collUri + " not found!");
            }
            final Map<String, Object> desc = new HashMap<>();
            final List<String> collections = new ArrayList<>();
            if (collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                    collections.add(i.next().toString());
                }
            }
            final Permission perms = collection.getPermissionsNoLock();
            desc.put("collections", collections);
            desc.put("name", collection.getURI().toString());
            desc.put("created", Long.toString(collection.getCreationTime()));
            desc.put("owner", perms.getOwner().getName());
            desc.put("group", perms.getGroup().getName());
            desc.put("permissions", perms.getMode());
            if (perms instanceof ACLPermission) {
                desc.put("acl", getACEs(perms));
            }
            return desc;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public byte[] getDocument(final String name, final Map<String, Object> parametri) throws EXistException,
            PermissionDeniedException {

        final String encoding;
        String compression = "no";

        if (parametri.get("encoding") == null) {
            encoding = DEFAULT_ENCODING;
        } else {
            encoding = (String) parametri.get("encoding");
        }

        if (parametri.get(EXistOutputKeys.COMPRESS_OUTPUT) != null) {
            compression = (String) parametri.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }

        try {
            final String xml = getDocumentAsString(name, parametri);
            if (xml == null) {
                throw new EXistException("document " + name + " not found!");
            }
            try {
                if ("no".equals(compression)) {
                    return xml.getBytes(encoding);
                } else {
                    LOG.debug("getdocument with compression");
                    return Compressor.compress(xml.getBytes(encoding));
                }

            } catch (final UnsupportedEncodingException uee) {
                LOG.warn(uee);
                if ("no".equals(compression)) {
                    return xml.getBytes();
                } else {
                    LOG.debug("getdocument with compression");
                    return Compressor.compress(xml.getBytes());
                }

            }
        } catch (final EXistException | PermissionDeniedException | IOException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getDocumentAsString(final String docName, final Map<String, Object> parametri) throws EXistException, PermissionDeniedException {
        try {
            return getDocumentAsString(XmldbURI.xmldbUriFor(docName), parametri);

        } catch (final Throwable e) {
            handleException(e);
            return null;
        }
    }

    private String getDocumentAsString(final XmldbURI docUri, final Map<String, Object> parametri)
            throws Exception {
        DBBroker broker = null;

        Collection collection = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(docUri.removeLastSegment(), Lock.READ_LOCK);
            if (collection == null) {
                LOG.debug("collection " + docUri.removeLastSegment() + " not found!");
                return null;
            }
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            doc = collection.getDocumentWithLock(broker, docUri.lastSegment(), Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }

            if (!doc.getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource " + docUri);
            }
            final Serializer serializer = broker.getSerializer();
            serializer.setProperties(toProperties(parametri));
            final String xml = serializer.serialize(doc);

            return xml;
        } catch (final NoSuchMethodError nsme) {
            LOG.error(nsme.getMessage(), nsme);
            return null;
        } finally {
            if (collection != null) {
                collection.releaseDocument(doc, Lock.READ_LOCK);
            }
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getDocumentData(final String docName, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        Collection collection = null;
        DocumentImpl doc = null;
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final XmldbURI docURI = XmldbURI.xmldbUriFor(docName);
            collection = broker.openCollection(docURI.removeLastSegment(), Lock.READ_LOCK);
            if (collection == null) {
                LOG.debug("collection " + docURI.removeLastSegment() + " not found!");
                throw new EXistException("Collection " + docURI.removeLastSegment() + " not found!");
            }
            //if(!collection.getPermissions().validate(user, Permission.READ)) {
            //	throw new PermissionDeniedException("Insufficient privileges to read resource");
            //}
            doc = collection.getDocumentWithLock(broker, docURI.lastSegment(), Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docURI + " not found!");
                throw new EXistException("document " + docURI + " not found");
            }

            //if(!doc.getPermissions().validate(user, Permission.READ)) {
            //  throw new PermissionDeniedException("Insufficient privileges to read resource " + docName);
            //}
            String encoding = (String) parameters.get(OutputKeys.ENCODING);
            if (encoding == null) {
                encoding = DEFAULT_ENCODING;
            }

            // A tweak for very large resources, VirtualTempFile
            final Map<String, Object> result = new HashMap<>();
            VirtualTempFile vtempFile = null;
            try {
                vtempFile = new VirtualTempFile(MAX_DOWNLOAD_CHUNK_SIZE, MAX_DOWNLOAD_CHUNK_SIZE);
                vtempFile.setTempPrefix("eXistRPCC");

                // binary check TODO dwes
                if (doc.getResourceType() == DocumentImpl.XML_FILE) {
                    vtempFile.setTempPostfix(".xml");
                    final Serializer serializer = broker.getSerializer();
                    serializer.setProperties(toProperties(parameters));

                    try (Writer writer = new OutputStreamWriter(vtempFile, encoding)) {
                        serializer.serialize(doc, writer);
                    }
                } else {
                    vtempFile.setTempPostfix(".bin");
                    broker.readBinaryResource((BinaryDocument) doc, vtempFile);
                }
            } finally {
                if (vtempFile != null) {
                    vtempFile.close();
                }
            }

            final byte[] firstChunk = vtempFile.getChunk(0);
            result.put("data", firstChunk);
            int offset = 0;
            if (vtempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(vtempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                vtempFile.delete();
            }
            result.put("offset", offset);

            return result;

        } catch (final EXistException | URISyntaxException | PermissionDeniedException | LockException | IOException | SAXException e) {
            handleException(e);
            return null;
        } finally {
            if (collection != null) {
                collection.releaseDocument(doc, Lock.READ_LOCK);
                collection.getLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getNextChunk(final String handle, final int offset)
            throws EXistException, PermissionDeniedException {
        try {
            final int resultId = Integer.parseInt(handle);
            final SerializedResult sr = factory.resultSets.getSerializedResult(resultId);

            if (sr == null) {
                throw new EXistException("Invalid handle specified");
            }
            // This will keep the serialized result in the cache
            sr.touch();
            final VirtualTempFile vfile = sr.result;

            if (offset <= 0 || offset > vfile.length()) {
                factory.resultSets.remove(resultId);
                throw new EXistException("No more data available");
            }
            final byte[] chunk = vfile.getChunk(offset);
            final long nextChunk = offset + chunk.length;

            final Map<String, Object> result = new HashMap<>();
            result.put("data", chunk);
            result.put("handle", handle);
            if (nextChunk > (long) Integer.MAX_VALUE || nextChunk == vfile.length()) {
                factory.resultSets.remove(resultId);
                result.put("offset", 0);
            } else {
                result.put("offset", nextChunk);
            }
            return result;
        } catch (final NumberFormatException | EXistException | IOException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getNextExtendedChunk(final String handle, final String offset)
            throws EXistException, PermissionDeniedException {
        try {
            final int resultId = Integer.parseInt(handle);
            final SerializedResult sr = factory.resultSets.getSerializedResult(resultId);

            if (sr == null) {
                throw new EXistException("Invalid handle specified");
            }
            // This will keep the serialized result in the cache
            sr.touch();
            final VirtualTempFile vfile = sr.result;

            final long longOffset = Long.valueOf(offset);
            if (longOffset < 0 || longOffset > vfile.length()) {
                factory.resultSets.remove(resultId);
                throw new EXistException("No more data available");
            }
            final byte[] chunk = vfile.getChunk(longOffset);
            final long nextChunk = longOffset + chunk.length;

            final Map<String, Object> result = new HashMap<>();
            result.put("data", chunk);
            result.put("handle", handle);
            if (nextChunk == vfile.length()) {
                factory.resultSets.remove(resultId);
                result.put("offset", Long.toString(0));
            } else {
                result.put("offset", Long.toString(nextChunk));
            }
            return result;

        } catch (final NumberFormatException | EXistException | IOException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public byte[] getBinaryResource(final String name)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getBinaryResource(XmldbURI.xmldbUriFor(name));
    }

    private byte[] getBinaryResource(final XmldbURI name) throws EXistException, PermissionDeniedException {
        return getBinaryResource(name, Permission.READ);
    }

    private byte[] getBinaryResource(final XmldbURI name, final int requiredPermissions) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(name, Lock.READ_LOCK);

            if (doc == null) {
                throw new EXistException("Resource " + name + " not found");
            }

            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new EXistException("Document " + name + " is not a binary resource");
            }

            if (!doc.getPermissions().validate(user, requiredPermissions)) {
                throw new PermissionDeniedException("Insufficient privileges to access resource");
            }

            try {
                final byte[] data;
                try (final InputStream is = broker.getBinaryResource((BinaryDocument) doc)) {
                    final long resourceSize = broker.getBinaryResourceSize((BinaryDocument) doc);
                    if (resourceSize > (long) Integer.MAX_VALUE) {
                        throw new EXistException("Resource too big to be read using this method.");
                    }
                    data = new byte[(int) resourceSize];
                    is.read(data);
                }
                return data;
            } catch (final IOException ex) {
                throw new EXistException("I/O error while reading resource.", ex);
            }
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public int xupdate(final String collectionName, final byte[] xupdate) throws PermissionDeniedException, EXistException {
        try {
            return xupdate(XmldbURI.xmldbUriFor(collectionName), new String(xupdate, DEFAULT_ENCODING));

        } catch (final URISyntaxException | UnsupportedEncodingException | SAXException | LockException | PermissionDeniedException | EXistException | XPathException e) {
            handleException(e);
            return -1;
        }
    }

    private int xupdate(final XmldbURI collUri, final String xupdate)
            throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            final Collection collection = broker.getCollection(collUri);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("collection " + collUri + " not found");
            }
            //TODO : register a lock (which one ?) in the transaction ?
            final DocumentSet docs = collection.allDocs(broker, new DefaultDocumentSet(), true);
            final XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.XMLRPC);
            final Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (final Modification modification : modifications) {
                mods += modification.process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (final ParserConfigurationException | IOException e) {
            throw new EXistException(e.getMessage());
        }
    }

    @Override
    public int xupdateResource(final String resource, final byte[] xupdate, final String encoding) throws PermissionDeniedException, EXistException {
        try {
            return xupdateResource(XmldbURI.xmldbUriFor(resource), new String(xupdate, encoding));
        } catch (final URISyntaxException | UnsupportedEncodingException | SAXException | LockException | PermissionDeniedException | EXistException | XPathException e) {
            handleException(e);
            return -1;
        }
    }

    private int xupdateResource(final XmldbURI docUri, final String xupdate)
            throws SAXException, LockException, PermissionDeniedException, EXistException,
            XPathException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            final DocumentImpl doc = broker.getResource(docUri, Permission.READ);
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("document " + docUri + " not found");
            }
            //TODO : register a lock (which one ?) within the transaction ?
            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(doc);
            final XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.XMLRPC);
            final Modification modifications[] = processor.parse(new InputSource(
                    new StringReader(xupdate)));
            long mods = 0;
            for (final Modification modification : modifications) {
                mods += modification.process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (final ParserConfigurationException | IOException e) {
            throw new EXistException(e.getMessage());
        }
    }

    @Override
    public boolean sync() {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(factory.getBrokerPool().getSecurityManager().getSystemSubject());
            broker.sync(Sync.MAJOR_SYNC);
        } catch (final EXistException e) {
            LOG.warn(e.getMessage(), e);
        } finally {
            factory.getBrokerPool().release(broker);
        }
        return true;
    }

    @Override
    public boolean isXACMLEnabled() {
        return factory.getBrokerPool().getSecurityManager().isXACMLEnabled();
    }

    @Override
    public boolean dataBackup(final String dest) {
        factory.getBrokerPool().triggerSystemTask(new DataBackup(dest));
        return true;
    }

    @Override
    public List<String> getDocumentListing() throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final DocumentSet docs = broker.getAllXMLResources(new DefaultDocumentSet());
            final XmldbURI names[] = docs.getNames();
            final List<String> list = new ArrayList<>();
            for (final XmldbURI name : names) {
                list.add(name.toString());
            }

            return list;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<String> getCollectionListing(final String collName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return getCollectionListing(XmldbURI.xmldbUriFor(collName));
    }

    private List<String> getCollectionListing(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            final List<String> list = new ArrayList<>();
            if (collection == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("collection " + collUri + " not found.");
                }
                return list;
            }
            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                list.add(i.next().toString());
            }
            return list;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<String> getDocumentListing(final String collName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getDocumentListing(XmldbURI.xmldbUriFor(collName));
    }

    private List<String> getDocumentListing(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            final List<String> list = new ArrayList<>();
            if (collection == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("collection " + collUri + " not found.");
                }
                return list;
            }
            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                list.add(i.next().getFileURI().toString());
            }
            return list;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public int getResourceCount(final String collectionName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getResourceCount(XmldbURI.xmldbUriFor(collectionName));
    }

    private int getResourceCount(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            return collection.getDocumentCount(broker);
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public String createResourceId(final String collectionName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return createResourceId(XmldbURI.xmldbUriFor(collectionName));
    }

    /**
     * Creates a unique name for a database resource Uniqueness is only
     * guaranteed within the eXist instance
     *
     * The name is based on a hex encoded string of a random integer and will
     * have the format xxxxxxxx.xml where x is in the range 0 to 9 and a to f
     *
     * @return the unique resource name
     */
    private String createResourceId(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            XmldbURI id;
            final Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this id does already exist
                if (collection.hasDocument(broker, id)) {
                    ok = false;
                }

                if (collection.hasSubcollection(broker, id)) {
                    ok = false;
                }

            } while (!ok);
            return id.toString();
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public int getHits(final int resultId) throws EXistException {
        final QueryResult qr = factory.resultSets.getResult(resultId);
        if (qr == null) {
            throw new EXistException("result set unknown or timed out");
        }
        qr.touch();
        if (qr.result == null) {
            return 0;
        }
        return qr.result.getItemCount();
    }

    @Override
    public Map<String, Object> getPermissions(final String name)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getPermissions(XmldbURI.xmldbUriFor(name));
    }

    private Map<String, Object> getPermissions(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;

        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            final Permission perm;
            if (collection == null) {
                DocumentImpl doc = null;
                try {
                    doc = broker.getXMLResource(uri, Lock.READ_LOCK);
                    if (doc == null) {
                        throw new EXistException("document or collection " + uri + " not found");
                    }
                    perm = doc.getPermissions();
                } finally {
                    if (doc != null) {
                        doc.getUpdateLock().release(Lock.READ_LOCK);
                    }
                }
            } else {
                perm = collection.getPermissionsNoLock();
            }

            final Map<String, Object> result = new HashMap<>();
            result.put("owner", perm.getOwner().getName());
            result.put("group", perm.getGroup().getName());
            result.put("permissions", perm.getMode());

            if (perm instanceof ACLPermission) {
                result.put("acl", getACEs(perm));
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getSubCollectionPermissions(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {

        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        DBBroker broker = null;
        Collection collection = null;

        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            final Permission perm;
            if (collection == null) {
                throw new EXistException("collection " + uri + " not found");
            } else {
                perm = collection.getSubCollectionEntry(broker, name).getPermissions();
            }

            final Map<String, Object> result = new HashMap<>();
            result.put("owner", perm.getOwner().getName());
            result.put("group", perm.getGroup().getName());
            result.put("permissions", perm.getMode());

            if (perm instanceof ACLPermission) {
                result.put("acl", getACEs(perm));
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getSubResourcePermissions(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        DBBroker broker = null;
        Collection collection = null;

        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            final Permission perm;
            if (collection == null) {
                throw new EXistException("collection " + uri + " not found");
            } else {
                perm = collection.getResourceEntry(broker, name).getPermissions();
            }

            final Map<String, Object> result = new HashMap<>();
            result.put("owner", perm.getOwner().getName());
            result.put("group", perm.getGroup().getName());
            result.put("permissions", perm.getMode());

            if (perm instanceof ACLPermission) {
                result.put("acl", getACEs(perm));
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public long getSubCollectionCreationTime(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        DBBroker broker = null;
        Collection collection = null;

        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + uri + " not found");
            } else {
                return collection.getSubCollectionEntry(broker, name).getCreated();
            }

        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    private List<ACEAider> getACEs(final Permission perm) {
        final List<ACEAider> aces = new ArrayList<>();
        final ACLPermission aclPermission = (ACLPermission) perm;
        for (int i = 0; i < aclPermission.getACECount(); i++) {
            aces.add(new ACEAider(aclPermission.getACEAccessType(i), aclPermission.getACETarget(i), aclPermission.getACEWho(i), aclPermission.getACEMode(i)));
        }
        return aces;
    }

    @Override
    public Map<String, List> listDocumentPermissions(final String name)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return listDocumentPermissions(XmldbURI.xmldbUriFor(name));
    }

    private Map<String, List> listDocumentPermissions(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("Collection " + collUri + " not found");
            }
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                throw new PermissionDeniedException(
                        "not allowed to read collection " + collUri);
            }
            final Map<String, List> result = new HashMap<>(collection.getDocumentCount(broker));

            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                final DocumentImpl doc = i.next();

                final Permission perm = doc.getPermissions();

                final List tmp = new ArrayList(3);
                tmp.add(perm.getOwner().getName());
                tmp.add(perm.getGroup().getName());
                tmp.add(perm.getMode());
                if (perm instanceof ACLPermission) {
                    tmp.add(getACEs(perm));
                }
                result.put(doc.getFileURI().toString(), tmp);
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<XmldbURI, List> listCollectionPermissions(final String name)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return listCollectionPermissions(XmldbURI.xmldbUriFor(name));
    }

    private Map<XmldbURI, List> listCollectionPermissions(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("Collection " + collUri + " not found");
            }
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("not allowed to read collection " + collUri);
            }
            final Map<XmldbURI, List> result = new HashMap<>(collection.getChildCollectionCount(broker));
            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                final XmldbURI child = i.next();
                final XmldbURI path = collUri.append(child);
                final Collection childColl = broker.getCollection(path);
                final Permission perm = childColl.getPermissionsNoLock();
                final List tmp = new ArrayList(3);
                tmp.add(perm.getOwner().getName());
                tmp.add(perm.getGroup().getName());
                tmp.add(perm.getMode());
                if (perm instanceof ACLPermission) {
                    tmp.add(getACEs(perm));
                }
                result.put(child, tmp);
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Date getCreationDate(final String collectionPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getCreationDate(XmldbURI.xmldbUriFor(collectionPath));
    }

    private Date getCreationDate(final XmldbURI collUri)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + collUri + " not found");
            }
            return new Date(collection.getCreationTime());
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<Date> getTimestamps(final String documentPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getTimestamps(XmldbURI.xmldbUriFor(documentPath));
    }

    private List<Date> getTimestamps(final XmldbURI docUri)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }
            final DocumentMetadata metadata = doc.getMetadata();
            final List<Date> list = new ArrayList<>(2);
            list.add(new Date(metadata.getCreated()));
            list.add(new Date(metadata.getLastModified()));
            return list;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> getAccount(final String name) throws EXistException, PermissionDeniedException {

        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);

            final Account u = factory.getBrokerPool().getSecurityManager().getAccount(name);
            if (u == null) {
                throw new EXistException("account '" + name + "' does not exist");
            }

            final Map<String, Object> tab = new HashMap<>();
            tab.put("uid", user.getId());
            tab.put("name", u.getName());
            tab.put("groups", Arrays.asList(u.getGroups()));

            final Group dg = u.getDefaultGroup();
            if (dg != null) {
                tab.put("default-group-id", dg.getId());
                tab.put("default-group-realmId", dg.getRealmId());
                tab.put("default-group-name", dg.getName());
            }

            tab.put("enabled", Boolean.toString(u.isEnabled()));

            tab.put("umask", u.getUserMask());

            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : u.getMetadataKeys()) {
                metadata.put(key.getNamespace(), u.getMetadataValue(key));
            }
            tab.put("metadata", metadata);

            return tab;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<Map<String, Object>> getAccounts() throws EXistException, PermissionDeniedException {

        final java.util.Collection<Account> users = factory.getBrokerPool().getSecurityManager().getUsers();
        final List<Map<String, Object>> r = new ArrayList<>();
        for (final Account user : users) {
            final Map<String, Object> tab = new HashMap<>();
            tab.put("uid", user.getId());
            tab.put("name", user.getName());
            tab.put("groups", Arrays.asList(user.getGroups()));
            tab.put("enabled", Boolean.toString(user.isEnabled()));
            tab.put("umask", user.getUserMask());

            final Map<String, String> metadata = new HashMap<>();
            for (final SchemaType key : user.getMetadataKeys()) {
                metadata.put(key.getNamespace(), user.getMetadataValue(key));
            }
            tab.put("metadata", metadata);
            r.add(tab);
        }
        return r;
    }

    @Override
    public List<String> getGroups() throws EXistException, PermissionDeniedException {
        final java.util.Collection<Group> groups = factory.getBrokerPool().getSecurityManager().getGroups();
        final List<String> v = new ArrayList<>(groups.size());
        for (final Group group : groups) {
            v.add(group.getName());
        }
        return v;
    }

    @Override
    public Map<String, Object> getGroup(final String name) throws EXistException, PermissionDeniedException {
        try {
            return executeWithBroker(new BrokerOperation<Map<String, Object>>() {
                @Override
                public Map<String, Object> withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    final SecurityManager securityManager = factory.getBrokerPool().getSecurityManager();
                    final Group group = securityManager.getGroup(name);
                    if (group != null) {
                        final Map<String, Object> map = new HashMap<>();
                        map.put("id", group.getId());
                        map.put("realmId", group.getRealmId());
                        map.put("name", name);

                        final List<Account> groupManagers = group.getManagers();
                        final List<String> managers = new ArrayList<>(groupManagers.size());
                        for (final Account groupManager : groupManagers) {
                            managers.add(groupManager.getName());
                        }
                        map.put("managers", managers);

                        final Map<String, String> metadata = new HashMap<>();
                        for (final SchemaType key : group.getMetadataKeys()) {
                            metadata.put(key.getNamespace(), group.getMetadataValue(key));
                        }
                        map.put("metadata", metadata);

                        return map;
                    }
                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }
    }

    @Override
    public void removeGroup(final String name) throws EXistException, PermissionDeniedException {
        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    broker.getBrokerPool().getSecurityManager().deleteGroup(name);
                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }
    }

    @Override
    public boolean hasDocument(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return hasDocument(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean hasDocument(final XmldbURI docUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            return (broker.getXMLResource(docUri) != null);
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean hasCollection(final String collectionName) throws EXistException, URISyntaxException, PermissionDeniedException {
        return hasCollection(XmldbURI.xmldbUriFor(collectionName));
    }

    private boolean hasCollection(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            return (broker.getCollection(collUri) != null);
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean parse(byte[] xml, String documentPath, int overwrite) throws URISyntaxException, EXistException, PermissionDeniedException {
        return parse(xml, documentPath, overwrite, null, null);
    }

    @Override
    public boolean parse(final byte[] xml, final String documentPath,
            final int overwrite, final Date created, final Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
        return parse(xml, XmldbURI.xmldbUriFor(documentPath), overwrite, created, modified);
    }

    private boolean parse(final byte[] xml, final XmldbURI docUri,
            final int overwrite, final Date created, final Date modified) throws EXistException, PermissionDeniedException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();

        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction();
                final InputStream is = new ByteArrayInputStream(xml)) {

            final long startTime = System.currentTimeMillis();

            IndexInfo info = null;
            InputSource source = null;
            Collection collection = null;
            try {
                collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
                if (collection == null) {
                    transact.abort(transaction);
                    throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
                }

                if (overwrite == 0) {
                    final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                    //TODO : register the lock within the transaction ?
                    if (old != null) {
                        transact.abort(transaction);
                        throw new PermissionDeniedException("Document exists and overwrite is not allowed");
                    }
                }

                source = new InputSource(is);
                info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
                final MimeType mime = MimeTable.getInstance().getContentTypeFor(docUri.lastSegment());
                if (mime != null && mime.isXMLType()) {
                    info.getDocument().getMetadata().setMimeType(mime.getName());
                }
                if (created != null) {
                    info.getDocument().getMetadata().setCreated(created.getTime());
                }

                if (modified != null) {
                    info.getDocument().getMetadata().setLastModified(modified.getTime());
                }

            } finally {
                if (collection != null) {
                    collection.release(Lock.WRITE_LOCK);
                }
            }

            collection.store(transaction, broker, info, source, false);
            transact.commit(transaction);

            LOG.debug("parsing " + docUri + " took " + (System.currentTimeMillis() - startTime) + "ms.");
            return true;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        }
    }

    /**
     * Parse a file previously uploaded with upload.
     *
     * The temporary file will be removed.
     *
     * @param localFile
     * @param documentPath
     * @param overwrite
     * @param mimeType
     * @return
     * @throws EXistException
     * @throws java.net.URISyntaxException
     */
    public boolean parseLocal(final String localFile, final String documentPath,
            final int overwrite, final String mimeType) throws Exception, URISyntaxException {
        return parseLocal(localFile, documentPath, overwrite, mimeType, null, null);
    }

    /**
     * Parse a file previously uploaded with upload, forcing it to XML or
     * Binary.
     *
     * The temporary file will be removed.
     *
     * @param localFile
     * @param documentPath
     * @param overwrite
     * @param mimeType
     * @param isXML
     * @return
     * @throws EXistException
     * @throws java.net.URISyntaxException
     */
    public boolean parseLocalExt(final String localFile, final String documentPath,
            final int overwrite, final String mimeType, final int isXML) throws Exception, URISyntaxException {
        return parseLocalExt(localFile, documentPath, overwrite, mimeType, isXML, null, null);
    }

    @SuppressWarnings("unused")
    private boolean parseLocal(final String localFile, final XmldbURI docUri,
            final int overwrite, final String mimeType) throws Exception {
        return parseLocal(localFile, docUri, overwrite, mimeType, null, null, null);
    }

    @SuppressWarnings("unused")
    private boolean parseLocalExt(final String localFile, final XmldbURI docUri,
            final int overwrite, final String mimeType, final int isXML) throws Exception {
        return parseLocal(localFile, docUri, overwrite, mimeType, isXML != 0, null, null);
    }

    public boolean parseLocal(final String localFile, final String documentPath, final int overwrite,
            final String mimeType, final Date created, final Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
        return parseLocal(localFile, XmldbURI.xmldbUriFor(documentPath), overwrite, mimeType, null, created, modified);
    }

    public boolean parseLocalExt(final String localFile, final String documentPath, final int overwrite,
            final String mimeType, final int isXML, final Date created, final Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
        return parseLocal(localFile, XmldbURI.xmldbUriFor(documentPath), overwrite, mimeType, isXML != 0, created, modified);
    }

    private boolean parseLocal(final String localFile, final XmldbURI docUri, final int overwrite, final String mimeType, final Boolean isXML, final Date created, final Date modified)
            throws EXistException, PermissionDeniedException {
        VirtualTempFileInputSource source = null;

        try {
            final int handle = Integer.parseInt(localFile);
            final SerializedResult sr = factory.resultSets.getSerializedResult(handle);
            if (sr == null) {
                throw new EXistException("Invalid handle specified");
            }

            source = new VirtualTempFileInputSource(sr.result);

            // Unlinking the VirtualTempFile from the SerializeResult
            sr.result = null;
            factory.resultSets.remove(handle);
        } catch (final NumberFormatException nfe) {
    		// As this file can be a non-temporal one, we should not
            // blindly erase it!
            final File file = new File(localFile);
            if (!file.canRead()) {
                throw new EXistException("unable to read file " + file.getAbsolutePath());
            }

            source = new VirtualTempFileInputSource(file);
        } catch (final IOException ioe) {
            throw new EXistException("Error preparing virtual temp file for parsing");
        }

        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        final DocumentImpl doc;

        // DWES
        MimeType mime = MimeTable.getInstance().getContentType(mimeType);
        if (mime == null) {
            mime = MimeType.BINARY_TYPE;
        }

        final boolean treatAsXML = (isXML != null && isXML) || (isXML == null && mime.isXMLType());
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            Collection collection = null;
            IndexInfo info = null;

            try {

                collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
                if (collection == null) {
                    transact.abort(transaction);
                    throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
                }

                if (overwrite == 0) {
                    final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                    if (old != null) {
                        transact.abort(transaction);
                        throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
                    }
                }

                //XML
                if (treatAsXML) {
                    info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
                    if (created != null) {
                        info.getDocument().getMetadata().setCreated(created.getTime());
                    }
                    if (modified != null) {
                        info.getDocument().getMetadata().setLastModified(modified.getTime());
                    }
                } else {
                    final InputStream is = source.getByteStream();
                    doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), is,
                            mime.getName(), source.getByteStreamLength());
                    is.close();
                    if (created != null) {
                        doc.getMetadata().setCreated(created.getTime());
                    }
                    if (modified != null) {
                        doc.getMetadata().setLastModified(modified.getTime());
                    }
                }

            } finally {
                if (collection != null) {
                    collection.release(Lock.WRITE_LOCK);
                }
            }

            // DWES why seperate store?
            if (treatAsXML) {
                collection.store(transaction, broker, info, source, false);
            }

            // generic
            transact.commit(transaction);

        } catch (final Throwable e) {
            handleException(e);
        } finally {
            // DWES there are situations the file is not cleaned up
            source.free();
        }

        return true; // when arrived here, insert/update was successful
    }

    public boolean storeBinary(final byte[] data, final String documentPath, final String mimeType,
            final int overwrite) throws Exception, URISyntaxException {
        return storeBinary(data, documentPath, mimeType, overwrite, null, null);
    }

    @SuppressWarnings("unused")
    private boolean storeBinary(final byte[] data, final XmldbURI docUri, final String mimeType,
            final int overwrite) throws Exception {
        return storeBinary(data, docUri, mimeType, overwrite, null, null);
    }

    public boolean storeBinary(final byte[] data, final String documentPath, final String mimeType,
            final int overwrite, final Date created, final Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
        return storeBinary(data, XmldbURI.xmldbUriFor(documentPath), mimeType, overwrite, created, modified);
    }

    private boolean storeBinary(final byte[] data, final XmldbURI docUri, final String mimeType,
            final int overwrite, final Date created, final Date modified) throws EXistException, PermissionDeniedException {
        DocumentImpl doc = null;
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            final Collection collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            if (overwrite == 0) {
                final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                if (old != null) {
                    transact.abort(transaction);
                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
                }
            }
            LOG.debug("Storing binary resource to collection " + collection.getURI());

            doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), data, mimeType);
            if (created != null) {
                doc.getMetadata().setCreated(created.getTime());
            }
            if (modified != null) {
                doc.getMetadata().setLastModified(modified.getTime());
            }
            transact.commit(transaction);

        } catch (final Throwable e) {
            handleException(e);
            return false;

        }
        return doc != null;
    }

    public String upload(final byte[] chunk, final int length, String fileName, final boolean compressed)
            throws EXistException, IOException {
        VirtualTempFile vtempFile;
        if (fileName == null || fileName.length() == 0) {
            // create temporary file
            vtempFile = new VirtualTempFile(MAX_DOWNLOAD_CHUNK_SIZE, MAX_DOWNLOAD_CHUNK_SIZE);
            vtempFile.setTempPrefix("rpc");
            vtempFile.setTempPostfix(".xml");
            final int handle = factory.resultSets.add(new SerializedResult(vtempFile));
            fileName = Integer.toString(handle);
        } else {
//            LOG.debug("appending to file " + fileName);
            try {
                final int handle = Integer.parseInt(fileName);
                final SerializedResult sr = factory.resultSets.getSerializedResult(handle);
                if (sr == null) {
                    throw new EXistException("Invalid handle specified");
                }
                // This will keep the serialized result in the cache
                sr.touch();
                vtempFile = sr.result;
            } catch (final NumberFormatException nfe) {
                throw new EXistException("Syntactically invalid handle specified");
            }
        }
        if (compressed) {
            Compressor.uncompress(chunk, vtempFile);
        } else {
            vtempFile.write(chunk, 0, length);
        }

        return fileName;
    }

    protected String printAll(final DBBroker broker, final Sequence resultSet, int howmany,
            int start, final Map<String, Object> properties, long queryTime) throws Exception {
        if (resultSet.isEmpty()) {
            final StringBuilder buf = new StringBuilder();
            final String opt = (String) properties.get(OutputKeys.OMIT_XML_DECLARATION);
            if (opt == null || opt.equalsIgnoreCase("no")) {
                buf.append("<?xml version=\"1.0\"?>\n");
            }
            buf.append("<exist:result xmlns:exist=\"").append(Namespaces.EXIST_NS).append("\" ");
            buf.append("hitCount=\"0\"/>");
            return buf.toString();
        }
        if (howmany > resultSet.getItemCount() || howmany == 0) {
            howmany = resultSet.getItemCount();
        }

        if (start < 1 || start > resultSet.getItemCount()) {
            throw new EXistException("start parameter out of range");
        }

        final StringWriter writer = new StringWriter();
        writer.write("<exist:result xmlns:exist=\"");
        writer.write(Namespaces.EXIST_NS);
        writer.write("\" hits=\"");
        writer.write(Integer.toString(resultSet.getItemCount()));
        writer.write("\" start=\"");
        writer.write(Integer.toString(start));
        writer.write("\" count=\"");
        writer.write(Integer.toString(howmany));
        writer.write("\">\n");

        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(toProperties(properties));

        Item item;
        for (int i = --start; i < start + howmany; i++) {
            item = resultSet.itemAt(i);
            if (item == null) {
                continue;
            }
            if (item.getType() == Type.ELEMENT) {
                final NodeValue node = (NodeValue) item;
                writer.write(serializer.serialize(node));
            } else {
                writer.write("<exist:value type=\"");
                writer.write(Type.getTypeName(item.getType()));
                writer.write("\">");
                writer.write(item.getStringValue());
                writer.write("</exist:value>");
            }
        }
        writer.write("\n</exist:result>");
        return writer.toString();
    }

    public Map<String, Object> compile(final String query, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final Map<String, Object> ret = new HashMap<>();
        DBBroker broker = null;
        XQueryPool pool = null;
        CompiledXQuery compiled = null;
        final Source source = new StringSource(query);
        try {
            broker = factory.getBrokerPool().get(user);
            final XQuery xquery = broker.getXQueryService();
            pool = xquery.getXQueryPool();
            compiled = compile(broker, source, parameters);

        } catch (final XPathException e) {
            ret.put(RpcAPI.ERROR, e.getMessage());
            if (e.getLine() != 0) {
                ret.put(RpcAPI.LINE, e.getLine());
                ret.put(RpcAPI.COLUMN, e.getColumn());
            }

        } catch (final EXistException | IOException | PermissionDeniedException e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
            if (compiled != null && pool != null) {
                pool.returnCompiledXQuery(source, compiled);
            }
        }
        return ret;
    }

    public String query(final String xpath, final int howmany, final int start,
            final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();
        String result = null;
        DBBroker broker = null;
        Source source = null;
        CompiledXQuery compiled = null;
        try {
            broker = factory.getBrokerPool().get(user);
            source = new StringSource(xpath);
            compiled = compile(broker, source, parameters);
            final QueryResult qr = doQuery(broker, compiled, null, parameters);
            if (qr == null) {
                return "<?xml version=\"1.0\"?>\n"
                        + "<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" "
                        + "hitCount=\"0\"/>";
            }
            if (qr.hasErrors()) {
                throw qr.getException();
            }

            result = printAll(broker, qr.result, howmany, start, parameters,
                    (System.currentTimeMillis() - startTime));

        } catch (final Throwable e) {
            handleException(e);

        } finally {
            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if (broker != null) {
                    broker.getXQueryService().getXQueryPool().returnCompiledXQuery(source, compiled);
                }
            }
            factory.getBrokerPool().release(broker);
        }
        return result;
    }

    public Map<String, Object> queryP(final String xpath, final String documentPath,
            final String s_id, final Map<String, Object> parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
        return queryP(xpath,
                (documentPath == null) ? null : XmldbURI.xmldbUriFor(documentPath),
                s_id, parameters);
    }

    private Map<String, Object> queryP(final String xpath, final XmldbURI docUri,
            final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();
        final String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);

        final Map<String, Object> ret = new HashMap<>();
        final List result = new ArrayList();
        NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        CompiledXQuery compiled = null;
        Source source = null;
        try {
            broker = factory.getBrokerPool().get(user);
            if (docUri != null && s_id != null) {
                final DocumentImpl doc = (DocumentImpl) broker.getXMLResource(docUri);
                final Object[] docs = new Object[1];
                docs[0] = docUri.toString();
                parameters.put(RpcAPI.STATIC_DOCUMENTS, docs);

                if (s_id.length() > 0) {
                    final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
                    final NodeProxy node = new NodeProxy(doc, nodeId);
                    nodes = new ExtArrayNodeSet(1);
                    nodes.add(node);
                }
            }
            source = new StringSource(xpath);
            compiled = compile(broker, source, parameters);

            queryResult = doQuery(broker, compiled, nodes, parameters);
            if (queryResult == null) {
                return ret;
            }
            if (queryResult.hasErrors()) {
                // return an error description
                final XPathException e = queryResult.getException();
                ret.put(RpcAPI.ERROR, e.getMessage());
                if (e.getLine() != 0) {
                    ret.put(RpcAPI.LINE, e.getLine());
                    ret.put(RpcAPI.COLUMN, e.getColumn());
                }
                return ret;
            }
            resultSeq = queryResult.result;
            if (LOG.isDebugEnabled()) {
                LOG.debug("found " + resultSeq.getItemCount());
            }

            if (sortBy != null) {
                SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user,
                        sortBy, AccessContext.XMLRPC);
                sorted.addAll(resultSeq);
                resultSeq = sorted;
            }
            NodeProxy p;
            List<String> entry;
            if (resultSeq != null) {
                final SequenceIterator i = resultSeq.iterate();
                if (i != null) {
                    Item next;
                    while (i.hasNext()) {
                        next = i.nextItem();
                        if (Type.subTypeOf(next.getType(), Type.NODE)) {
                            entry = new ArrayList<>();
                            if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                                p = (NodeProxy) next;
                                entry.add(p.getOwnerDocument().getURI().toString());
                                entry.add(p.getNodeId().toString());
                            } else {
                                entry.add("temp_xquery/" + next.hashCode());
                                entry.add(String.valueOf(((NodeImpl) next).getNodeNumber()));
                            }
                            result.add(entry);
                        } else {
                            result.add(next.getStringValue());
                        }
                    }
                } else {
                    LOG.debug("sequence iterator is null. Should not");
                }
            } else {
                LOG.debug("result sequence is null. Skipping it...");
            }

        } catch (final Throwable e) {
            handleException(e);
            return null;

        } finally {

            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if (broker != null) {
                    broker.getXQueryService().getXQueryPool().returnCompiledXQuery(source, compiled);
                }
            }

            factory.getBrokerPool().release(broker);
        }

        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        final int id = factory.resultSets.add(queryResult);
        ret.put("id", id);
        ret.put("hash", queryResult.hashCode());
        ret.put("results", result);
        return ret;
    }

    @Override
    public Map<String, Object> execute(final String pathToQuery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();

        final String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);

        final Map<String, Object> ret = new HashMap<>();
        final List result = new ArrayList();
        final NodeSet nodes = null;
        QueryResult queryResult;
        Sequence resultSeq = null;
        DBBroker broker = null;
        BinaryDocument xquery = null;
        Source source = null;
        CompiledXQuery compiled = null;
        try {
            broker = factory.getBrokerPool().get(user);

            xquery = (BinaryDocument) broker.getResource(XmldbURI.createInternal(pathToQuery), Lock.READ_LOCK);

            if (xquery == null) {
                throw new EXistException("Resource " + pathToQuery + " not found");
            }

            if (xquery.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new EXistException("Document " + pathToQuery + " is not a binary resource");
            }

            if (!xquery.getPermissions().validate(user, Permission.READ | Permission.EXECUTE)) {
                throw new PermissionDeniedException("Insufficient privileges to access resource");
            }

            source = new DBSource(broker, xquery, true);
            compiled = compile(broker, source, parameters);
            queryResult = doQuery(broker, compiled, nodes, parameters);
            if (queryResult == null) {
                return ret;
            }
            if (queryResult.hasErrors()) {
                // return an error description
                final XPathException e = queryResult.getException();
                ret.put(RpcAPI.ERROR, e.getMessage());
                if (e.getLine() != 0) {
                    ret.put(RpcAPI.LINE, e.getLine());
                    ret.put(RpcAPI.COLUMN, e.getColumn());
                }
                return ret;
            }
            resultSeq = queryResult.result;
            if (LOG.isDebugEnabled()) {
                LOG.debug("found " + resultSeq.getItemCount());
            }

            if (sortBy != null) {
                SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user,
                        sortBy, AccessContext.XMLRPC);
                sorted.addAll(resultSeq);
                resultSeq = sorted;
            }
            NodeProxy p;
            List<String> entry;
            if (resultSeq != null) {
                final SequenceIterator i = resultSeq.iterate();
                if (i != null) {
                    Item next;
                    while (i.hasNext()) {
                        next = i.nextItem();
                        if (Type.subTypeOf(next.getType(), Type.NODE)) {
                            entry = new ArrayList<>();
                            if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                                p = (NodeProxy) next;
                                entry.add(p.getOwnerDocument().getURI().toString());
                                entry.add(p.getNodeId().toString());
                            } else {
                                entry.add("temp_xquery/"
                                        + next.hashCode());
                                entry.add(String
                                        .valueOf(((NodeImpl) next)
                                                .getNodeNumber()));
                            }
                            result.add(entry);
                        } else {
                            result.add(next.getStringValue());
                        }
                    }
                } else {
                    LOG.debug("sequence iterator is null. Should not");
                }
            } else {
                LOG.debug("result sequence is null. Skipping it...");
            }

        } catch (final Throwable e) {
            handleException(e);
            return null;

        } finally {
            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if (broker != null) {
                    broker.getXQueryService().getXQueryPool().returnCompiledXQuery(source, compiled);
                }
            }
            factory.getBrokerPool().release(broker);

            if (xquery != null) {
                xquery.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
        queryResult.result = resultSeq;
        queryResult.queryTime = (System.currentTimeMillis() - startTime);
        final int id = factory.resultSets.add(queryResult);
        ret.put("id", id);
        ret.put("hash", queryResult.hashCode());
        ret.put("results", result);
        return ret;
    }

    @Override
    public boolean releaseQueryResult(final int handle) {
        factory.resultSets.remove(handle);
        LOG.debug("removed query result with handle " + handle);
        return true;
    }

    @Override
    public boolean releaseQueryResult(final int handle, final int hash) {
        factory.resultSets.remove(handle, hash);
        LOG.debug("removed query result with handle " + handle);
        return true;
    }

    @Override
    public boolean remove(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return remove(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean remove(final XmldbURI docUri) throws EXistException, PermissionDeniedException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Collection collection;
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            collection = broker.openCollection(docUri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);

            final DocumentImpl doc = collection.getDocument(broker, docUri.lastSegment());
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("Document " + docUri + " not found");
            }

            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                collection.removeBinaryResource(transaction, broker, doc);
            } else {
                collection.removeXMLResource(transaction, broker, docUri.lastSegment());
            }
            transact.commit(transaction);
            return true;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean removeCollection(final String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
        return removeCollection(XmldbURI.xmldbUriFor(collectionName));
    }

    private boolean removeCollection(final XmldbURI collURI) throws EXistException, PermissionDeniedException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Collection collection;
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            collection = broker.openCollection(collURI, Lock.WRITE_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                return false;
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            LOG.debug("removing collection " + collURI);
            final boolean removed = broker.removeCollection(transaction, collection);
            transact.commit(transaction);
            return removed;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean removeAccount(final String name) throws EXistException, PermissionDeniedException {
        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasAdminPrivileges(user)) {
            throw new PermissionDeniedException("you are not allowed to remove users");
        }

        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    manager.deleteAccount(name);
                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }

        return true;
    }

    @Override
    public byte[] retrieve(final String doc, final String id, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        try {
            final String xml = retrieveAsString(doc, id, parameters);
            try {
                String encoding = (String) parameters.get(OutputKeys.ENCODING);
                if (encoding == null) {
                    encoding = DEFAULT_ENCODING;
                }
                return xml.getBytes(encoding);
            } catch (final UnsupportedEncodingException uee) {
                LOG.warn(uee);
                return xml.getBytes();
            }

        } catch (final URISyntaxException | EXistException | PermissionDeniedException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String retrieveAsString(final String documentPath, final String s_id,
            final Map<String, Object> parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
        return retrieve(XmldbURI.xmldbUriFor(documentPath), s_id, parameters);
    }

    private String retrieve(final XmldbURI docUri, final String s_id,
            final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
            DocumentImpl doc;
            LOG.debug("loading doc " + docUri);
            doc = (DocumentImpl) broker.getXMLResource(docUri);
            final NodeProxy node = new NodeProxy(doc, nodeId);
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(toProperties(parameters));
            return serializer.serialize(node);

        } catch (final EXistException | PermissionDeniedException | SAXException e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> retrieveFirstChunk(final String docName, final String id, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        String encoding = (String) parameters.get(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }
        String compression = "no";
        if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }
        try {
            final XmldbURI docUri = XmldbURI.xmldbUriFor(docName);
            broker = factory.getBrokerPool().get(user);
            final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(id);
            DocumentImpl doc;
            LOG.debug("loading doc " + docUri);
            doc = (DocumentImpl) broker.getXMLResource(docUri);
            final NodeProxy node = new NodeProxy(doc, nodeId);

            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(toProperties(parameters));

            final Map<String, Object> result = new HashMap<>();
            VirtualTempFile vtempFile = new VirtualTempFile(MAX_DOWNLOAD_CHUNK_SIZE, MAX_DOWNLOAD_CHUNK_SIZE);
            vtempFile.setTempPrefix("eXistRPCC");
            vtempFile.setTempPostfix(".xml");

            OutputStream os = null;
            if ("yes".equals(compression)) {
                LOG.debug("get result with compression");
                os = new DeflaterOutputStream(vtempFile);
            } else {
                os = vtempFile;
            }
            try {
                try (final Writer writer = new OutputStreamWriter(os, encoding)) {
                    serializer.serialize(node, writer);
                }
            } finally {
                try {
                    os.close();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
                if (os != vtempFile) {
                    try {
                        vtempFile.close();
                    } catch (final IOException ioe) {
                        //IgnoreIT(R)
                    }
                }
            }

            final byte[] firstChunk = vtempFile.getChunk(0);
            result.put("data", firstChunk);
            int offset = 0;
            if (vtempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(vtempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                vtempFile.delete();
            }
            result.put("offset", offset);
            return result;

        } catch (final URISyntaxException | EXistException | PermissionDeniedException | IOException | SAXException e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public byte[] retrieve(int resultId, int num, Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        String compression = "no";
        if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }

        try {
            final String xml = retrieveAsString(resultId, num, parameters);
            String encoding = (String) parameters.get(OutputKeys.ENCODING);
            if (encoding == null) {
                encoding = DEFAULT_ENCODING;
            }
            try {

                if ("no".equals(compression)) {
                    return xml.getBytes(encoding);
                } else {
                    LOG.debug("get result with compression");
                    return Compressor.compress(xml.getBytes(encoding));
                }

            } catch (final UnsupportedEncodingException uee) {
                LOG.warn(uee);
                if ("no".equals(compression)) {
                    return xml.getBytes();
                } else {
                    LOG.debug("get result with compression");
                    return Compressor.compress(xml.getBytes());
                }
            }

        } catch (final Throwable e) {
            handleException(e);
            return null;
        }
    }

    private String retrieveAsString(final int resultId, final int num,
            final Map<String, Object> parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out");
            }
            qr.touch();
            final Item item = qr.result.itemAt(num);
            if (item == null) {
                throw new EXistException("index out of range");
            }

            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                final NodeValue nodeValue = (NodeValue) item;
                final Serializer serializer = broker.getSerializer();
                serializer.reset();
                for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                    parameters.put(entry.getKey().toString(), entry.getValue().toString());
                }
                serializer.setProperties(toProperties(parameters));
                return serializer.serialize(nodeValue);
            } else {
                return item.getStringValue();
            }
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> retrieveFirstChunk(final int resultId, final int num, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        String encoding = (String) parameters.get(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }
        String compression = "no";
        if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out: " + resultId);
            }
            qr.touch();
            final Item item = qr.result.itemAt(num);
            if (item == null) {
                throw new EXistException("index out of range");
            }

            final Map<String, Object> result = new HashMap<>();
            VirtualTempFile vtempFile = new VirtualTempFile(MAX_DOWNLOAD_CHUNK_SIZE, MAX_DOWNLOAD_CHUNK_SIZE);
            vtempFile.setTempPrefix("eXistRPCC");
            vtempFile.setTempPostfix(".xml");

            OutputStream os;
            if ("yes".equals(compression)) {
                LOG.debug("get result with compression");
                os = new DeflaterOutputStream(vtempFile);
            } else {
                os = vtempFile;
            }
            try {
                try (final Writer writer = new OutputStreamWriter(os, encoding)) {
                    if (Type.subTypeOf(item.getType(), Type.NODE)) {
                        final NodeValue nodeValue = (NodeValue) item;
                        final Serializer serializer = broker.getSerializer();
                        serializer.reset();
                        for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                            parameters.put(entry.getKey().toString(), entry.getValue().toString());
                        }
                        serializer.setProperties(toProperties(parameters));

                        serializer.serialize(nodeValue, writer);
                    } else {
                        writer.write(item.getStringValue());
                    }
                }
            } finally {
                try {
                    os.close();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
                if (os != vtempFile) {
                    try {
                        vtempFile.close();
                    } catch (final IOException ioe) {
                        //IgnoreIT(R)
                    }
                }
            }

            final byte[] firstChunk = vtempFile.getChunk(0);
            result.put("data", firstChunk);
            int offset = 0;
            if (vtempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(vtempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                vtempFile.delete();
            }
            result.put("offset", offset);
            return result;

        } catch (final EXistException | IOException | SAXException | XPathException e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public byte[] retrieveAll(final int resultId, final Map<String, Object> parameters) throws EXistException,
            PermissionDeniedException {
        try {
            final String xml = retrieveAllAsString(resultId, parameters);
            String encoding = (String) parameters.get(OutputKeys.ENCODING);
            if (encoding == null) {
                encoding = DEFAULT_ENCODING;
            }
            try {
                return xml.getBytes(encoding);
            } catch (final UnsupportedEncodingException uee) {
                LOG.warn(uee);
                return xml.getBytes();
            }

        } catch (final Throwable e) {
            handleException(e);
            return null;
        }
    }

    private String retrieveAllAsString(final int resultId, final Map<String, Object> parameters) throws Exception {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out");
            }
            qr.touch();
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(qr.serialization);

            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            final StringWriter writer = new StringWriter();
            handler.setOutput(writer, toProperties(parameters));

//			serialize results
            handler.startDocument();
            handler.startPrefixMapping("exist", Namespaces.EXIST_NS);
            final AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "hitCount", "hitCount", "CDATA", Integer.toString(qr.result.getItemCount()));
            handler.startElement(Namespaces.EXIST_NS, "result", "exist:result", attribs);
            Item current;
            char[] value;
            for (final SequenceIterator i = qr.result.iterate(); i.hasNext();) {
                current = i.nextItem();
                if (Type.subTypeOf(current.getType(), Type.NODE)) {
                    current.toSAX(broker, handler, null);
                } else {
                    value = current.toString().toCharArray();
                    handler.characters(value, 0, value.length);
                }
            }
            handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
            handler.endPrefixMapping("exist");
            handler.endDocument();
            SerializerPool.getInstance().returnObject(handler);
            return writer.toString();
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public Map<String, Object> retrieveAllFirstChunk(final int resultId, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        String encoding = (String) parameters.get(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }
        String compression = "no";
        if (((String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)) != null) {
            compression = (String) parameters.get(EXistOutputKeys.COMPRESS_OUTPUT);
        }
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out");
            }
            qr.touch();
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                parameters.put(entry.getKey().toString(), entry.getValue().toString());
            }
            serializer.setProperties(toProperties(parameters));
            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            final Map<String, Object> result = new HashMap<>();
            VirtualTempFile vtempFile = new VirtualTempFile(MAX_DOWNLOAD_CHUNK_SIZE, MAX_DOWNLOAD_CHUNK_SIZE);
            vtempFile.setTempPrefix("eXistRPCC");
            vtempFile.setTempPostfix(".xml");

            OutputStream os;
            if ("yes".equals(compression)) {
                LOG.debug("get result with compression");
                os = new DeflaterOutputStream(vtempFile);
            } else {
                os = vtempFile;
            }
            try {
                try (final Writer writer = new OutputStreamWriter(os, encoding)) {
                    handler.setOutput(writer, toProperties(parameters));

                    // serialize results
                    handler.startDocument();
                    handler.startPrefixMapping("exist", Namespaces.EXIST_NS);
                    final AttributesImpl attribs = new AttributesImpl();
                    attribs.addAttribute(
                            "",
                            "hitCount",
                            "hitCount",
                            "CDATA",
                            Integer.toString(qr.result.getItemCount()));
                    handler.startElement(
                            Namespaces.EXIST_NS,
                            "result",
                            "exist:result",
                            attribs);
                    Item current;
                    char[] value;
                    for (final SequenceIterator i = qr.result.iterate(); i.hasNext();) {
                        current = i.nextItem();
                        if (Type.subTypeOf(current.getType(), Type.NODE)) {
                            ((NodeValue) current).toSAX(broker, handler, null);
                        } else {
                            value = current.toString().toCharArray();
                            handler.characters(value, 0, value.length);
                        }
                    }
                    handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
                    handler.endPrefixMapping("exist");
                    handler.endDocument();
                    SerializerPool.getInstance().returnObject(handler);
                }
            } finally {
                try {
                    os.close();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
                if (os != vtempFile) {
                    try {
                        vtempFile.close();
                    } catch (final IOException ioe) {
                        //IgnoreIT(R)
                    }
                }
            }

            final byte[] firstChunk = vtempFile.getChunk(0);
            result.put("data", firstChunk);
            int offset = 0;
            if (vtempFile.length() > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(vtempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                vtempFile.delete();
            }
            result.put("offset", offset);
            return result;

        } catch (final EXistException | IOException | SAXException | XPathException e) {
            handleException(e);
            return null;

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    private interface BrokerOperation<R> {
        public R withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException;
    }

    private <R> R executeWithBroker(final BrokerOperation<R> brokerOperation) throws EXistException, URISyntaxException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            return brokerOperation.withBroker(broker);
        } finally {
            if (broker != null) {
                factory.getBrokerPool().release(broker);
            }
        }
    }

    @Override
    public boolean chgrp(final String resource, final String ownerGroup) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setGroup(ownerGroup);
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean chown(final String resource, final String owner) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setOwner(owner);
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean chown(final String resource, final String owner, final String ownerGroup) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setOwner(owner);
                        permission.setGroup(ownerGroup);
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean setPermissions(final String resource, final int permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setMode(permissions);
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean setPermissions(final String resource, final String permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        try {
                            permission.setMode(permissions);
                        } catch (final SyntaxException se) {
                            throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
                        }
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String ownerGroup, final String permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setOwner(owner);
                        permission.setGroup(ownerGroup);
                        try {
                            permission.setMode(permissions);
                        } catch (final SyntaxException se) {
                            throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
                        }
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String ownerGroup, final int permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setOwner(owner);
                        permission.setGroup(ownerGroup);
                        permission.setMode(permissions);

                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String group, final int mode, final List<ACEAider> aces) throws EXistException, PermissionDeniedException, URISyntaxException {
        executeWithBroker(new BrokerOperation<Void>() {
            @Override
            public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                PermissionFactory.updatePermissions(broker, XmldbURI.xmldbUriFor(resource), new PermissionModifier() {
                    @Override
                    public void modify(final Permission permission) throws PermissionDeniedException {
                        permission.setOwner(owner);
                        permission.setGroup(group);
                        permission.setMode(mode);

                        if (permission instanceof ACLPermission) {
                            final ACLPermission aclPermission = ((ACLPermission) permission);
                            aclPermission.clear();
                            for (final ACEAider ace : aces) {
                                aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
                            }
                        }
                    }
                });
                return null;
            }
        });

        return true;
    }

    @Override
    public boolean addAccount(final String name, String passwd, final String passwdDigest, final List<String> groups, final Boolean enabled, final Integer umask, final Map<String, String> metadata) throws EXistException, PermissionDeniedException {

        if (passwd.length() == 0) {
            passwd = null;
        }

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (manager.hasAccount(name)) {
            throw new PermissionDeniedException("Account '" + name + "' exist");
        }

        if (!manager.hasAdminPrivileges(user)) {
            throw new PermissionDeniedException("Account '" + user.getName() + "' not allowed to create new account");
        }

        final UserAider u = new UserAider(name);
        u.setEncodedPassword(passwd);
        u.setPasswordDigest(passwdDigest);

        for (final String g : groups) {
            if (!u.hasGroup(g)) {
                u.addGroup(g);
            }
        }

        if (enabled != null) {
            u.setEnabled(enabled);
        }

        if (umask != null) {
            u.setUserMask(umask);
        }

        if (metadata != null) {
            for (final String key : metadata.keySet()) {
                if (AXSchemaType.valueOfNamespace(key) != null) {
                    u.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                } else if (EXistSchemaType.valueOfNamespace(key) != null) {
                    u.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                }
            }
        }

        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    manager.addAccount(u);
                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }

        return true;
    }

    @Override
    public boolean updateAccount(final String name, final String passwd, final String passwdDigest, final List<String> groups) throws EXistException, PermissionDeniedException {
        return updateAccount(name, passwd, passwdDigest, groups, null, null, null);
    }

    @Override
    public boolean updateAccount(final String name, String passwd, final String passwdDigest, final List<String> groups, final Boolean enabled, final Integer umask, final Map<String, String> metadata) throws EXistException, PermissionDeniedException {
        if (passwd.length() == 0) {
            passwd = null;
        }

        final UserAider account = new UserAider(name);
        account.setEncodedPassword(passwd);
        account.setPasswordDigest(passwdDigest);

        for (final String g : groups) {
            account.addGroup(g);
        }

        if (enabled != null) {
            account.setEnabled(enabled);
        }

        if (umask != null) {
            account.setUserMask(umask);
        }

        if (metadata != null) {
            for (final String key : metadata.keySet()) {
                if (AXSchemaType.valueOfNamespace(key) != null) {
                    account.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                } else if (EXistSchemaType.valueOfNamespace(key) != null) {
                    account.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                }
            }
        }

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
        try {
            return executeWithBroker(new BrokerOperation<Boolean>() {
                @Override
                public Boolean withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    return manager.updateAccount(account);
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }
    }

    @Override
    public boolean addGroup(final String name, final Map<String, String> metadata) throws EXistException, PermissionDeniedException {

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasGroup(name)) {

            if (!manager.hasAdminPrivileges(user)) {
                throw new PermissionDeniedException("Not allowed to create group");
            }

            final Group role = new GroupAider(name);

            for (final String key : metadata.keySet()) {
                if (AXSchemaType.valueOfNamespace(key) != null) {
                    role.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                } else if (EXistSchemaType.valueOfNamespace(key) != null) {
                    role.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                }
            }

            try {
                executeWithBroker(new BrokerOperation<Void>() {
                    @Override
                    public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                        manager.addGroup(role);
                        return null;
                    }
                });
                return true;
            } catch (final URISyntaxException use) {
                throw new EXistException(use.getMessage(), use);
            }
        }

        return false;
    }

    public boolean setUserPrimaryGroup(final String username, final String groupName) throws EXistException, PermissionDeniedException {
        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasGroup(groupName)) {
            throw new EXistException("Group '" + groupName + "' does not exist!");
        }

        if (!manager.hasAdminPrivileges(user)) {
            throw new PermissionDeniedException("Not allowed to modify user");
        }

        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                    final Account account = manager.getAccount(username);
                    final Group group = manager.getGroup(groupName);
                    account.setPrimaryGroup(group);
                    manager.updateAccount(account);
                    return null;
                }
            });
            return true;
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }
    }

    @Override
    public boolean updateGroup(final String name, final List<String> managers, final Map<String, String> metadata) throws EXistException, PermissionDeniedException {

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (manager.hasGroup(name)) {

            final GroupAider group = new GroupAider(name);

            for (final String groupManager : managers) {
                group.addManager(new UserAider(groupManager));
            }

            if (metadata != null) {
                for (final String key : metadata.keySet()) {
                    if (AXSchemaType.valueOfNamespace(key) != null) {
                        group.setMetadataValue(AXSchemaType.valueOfNamespace(key), metadata.get(key));
                    } else if (EXistSchemaType.valueOfNamespace(key) != null) {
                        group.setMetadataValue(EXistSchemaType.valueOfNamespace(key), metadata.get(key));
                    }
                }
            }

            try {
                executeWithBroker(new BrokerOperation<Void>() {
                    @Override
                    public Void withBroker(final DBBroker broker) throws EXistException, URISyntaxException, PermissionDeniedException {
                        manager.updateGroup(group);
                        return null;
                    }
                });
                return true;
            } catch (final URISyntaxException use) {
                throw new EXistException(use.getMessage(), use);
            }

        } else {
            return false;
        }
    }

    @Override
    public List<String> getGroupMembers(final String groupName) throws EXistException, PermissionDeniedException {

        try {
            final List<String> groupMembers = executeWithBroker(new BrokerOperation<List<String>>() {
                @Override
                public List<String> withBroker(final DBBroker broker) {
                    return broker.getBrokerPool().getSecurityManager().findAllGroupMembers(groupName);
                }

            });
            return new ArrayList<>(groupMembers);
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        }
    }

    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws EXistException, PermissionDeniedException {
        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, PermissionDeniedException {
                    final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    final Account account = sm.getAccount(accountName);
                    account.addGroup(groupName);
                    sm.updateAccount(account);

                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        } catch (final PermissionDeniedException pde) {
            throw new EXistException(pde.getMessage(), pde);
        }
    }

    @Override
    public void addGroupManager(final String manager, final String groupName) throws EXistException, PermissionDeniedException {
        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, PermissionDeniedException {
                    final SecurityManager sm = broker.getBrokerPool().getSecurityManager();

                    final Account account = sm.getAccount(manager);
                    final Group group = sm.getGroup(groupName);
                    group.addManager(account);
                    sm.updateGroup(group);

                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        } catch (final PermissionDeniedException pde) {
            throw new EXistException(pde.getMessage(), pde);
        }
    }

    @Override
    public void removeGroupManager(final String groupName, final String manager) throws EXistException, PermissionDeniedException {
        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, PermissionDeniedException {
                    final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
                    final Group group = sm.getGroup(groupName);
                    final Account account = sm.getAccount(manager);

                    group.removeManager(account);
                    sm.updateGroup(group);

                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        } catch (final PermissionDeniedException pde) {
            throw new EXistException(pde.getMessage(), pde);
        }
    }

    public void removeGroupMember(final String group, final String member) throws EXistException, PermissionDeniedException {
        try {
            executeWithBroker(new BrokerOperation<Void>() {
                @Override
                public Void withBroker(final DBBroker broker) throws EXistException, PermissionDeniedException {
                    final SecurityManager sm = broker.getBrokerPool().getSecurityManager();

                    final Account account = sm.getAccount(member);
                    account.remGroup(group);
                    sm.updateAccount(account);

                    return null;
                }
            });
        } catch (final URISyntaxException use) {
            throw new EXistException(use.getMessage(), use);
        } catch (final PermissionDeniedException pde) {
            throw new EXistException(pde.getMessage(), pde);
        }
    }

    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     *
     * modified by Chris Tomlinson based on above updateAccount - it appears
     * that this code can rely on the SecurityManager to enforce policy about
     * whether user is or is not permitted to update the Account with name.
     *
     * This is called via RemoteUserManagementService.addUserGroup(Account)
     *
     * @param name
     * @return
     * @throws org.exist.security.PermissionDeniedException
     */
    public boolean updateAccount(final String name, final List<String> groups) throws EXistException, PermissionDeniedException {

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
        DBBroker broker = null;

        try {
            broker = factory.getBrokerPool().get(user);

            Account u;

            if (!manager.hasAccount(name)) {
                u = new UserAider(name);
            } else {
                u = manager.getAccount(name);
            }

            for (final String g : groups) {
                if (!u.hasGroup(g)) {
                    u.addGroup(g);
                }
            }

            return manager.updateAccount(u);

        } catch (final EXistException | PermissionDeniedException ex) {
            LOG.debug("addUserGroup encountered error", ex);
            return false;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     *
     * modified by Chris Tomlinson based on above updateAccount - it appears
     * that this code can rely on the SecurityManager to enforce policy about
     * whether user is or is not permitted to update the Account with name.
     *
     * This is called via RemoteUserManagementService.removeGroup(Account,
     * String)
     *
     * @param name
     * @param groups
     * @param rgroup
     * @return
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    public boolean updateAccount(final String name, final List<String> groups, final String rgroup) throws EXistException, PermissionDeniedException {

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        DBBroker broker = null;

        try {
            broker = factory.getBrokerPool().get(user);

            final Account u = manager.getAccount(name);

            for (final String g : groups) {
                if (g.equals(rgroup)) {
                    u.remGroup(g);
                }
            }

            return manager.updateAccount(u);

        } catch (final EXistException | PermissionDeniedException ex) {
            LOG.debug("removeGroup encountered error", ex);
            return false;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean lockResource(final String documentPath, final String userName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return lockResource(XmldbURI.xmldbUriFor(documentPath), userName);
    }

    private boolean lockResource(final XmldbURI docURI, final String userName) throws EXistException, PermissionDeniedException {
        DocumentImpl doc = null;
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);
            if (doc == null) {
                throw new EXistException("Resource " + docURI + " not found");
            }
            //TODO : register the lock within the transaction ?
            if (!doc.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            }
            final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
            if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("User " + user.getName() + " is not allowed "
                        + "to lock the resource for user " + userName);
            }
            final Account lockOwner = doc.getUserLock();
            if (lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("Resource is already locked by user "
                        + lockOwner.getName());
            }
            doc.setUserLock(user);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
        }
    }

    @Override
    public String hasUserLock(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return hasUserLock(XmldbURI.xmldbUriFor(documentPath));
    }

    private String hasUserLock(final XmldbURI docURI) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        DocumentImpl doc = null;
        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docURI, Lock.READ_LOCK);
            if (doc == null) {
                throw new EXistException("Resource " + docURI + " not found");
            }
            if (!doc.getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            final Account u = doc.getUserLock();
            return u == null ? "" : u.getName();

        } catch (final EXistException | PermissionDeniedException e) {
            handleException(e);
            return null;

        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean unlockResource(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return unlockResource(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean unlockResource(final XmldbURI docURI) throws EXistException, PermissionDeniedException {
        DocumentImpl doc = null;
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            doc = broker.getXMLResource(docURI, Lock.WRITE_LOCK);
            if (doc == null) {
                throw new EXistException("Resource " + docURI + " not found");
            }
            if (!doc.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            }
            final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
            final Account lockOwner = doc.getUserLock();
            if (lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("Resource is already locked by user "
                        + lockOwner.getName());
            }
            doc.setUserLock(null);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
        }
    }

    public Map<String, Object> summary(final String xpath) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();
        DBBroker broker = null;
        Source source = null;
        CompiledXQuery compiled = null;
        final Map<String, Object> parameters = new HashMap<>();
        try {
            broker = factory.getBrokerPool().get(user);
            source = new StringSource(xpath);
            compiled = compile(broker, source, parameters);
            final QueryResult qr = doQuery(broker, compiled, null, parameters);
            if (qr == null) {
                return new HashMap<>();
            }
            if (qr.hasErrors()) {
                throw qr.getException();
            }
            final Map<String, NodeCount> map = new HashMap<>();
            final Map<String, DoctypeCount> doctypes = new HashMap<>();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (final SequenceIterator i = qr.result.iterate(); i.hasNext();) {
                final Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue nv = (NodeValue) item;
                    if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        p = (NodeProxy) nv;
                        docName = p.getOwnerDocument().getURI().toString();
                        doctype = p.getOwnerDocument().getDoctype();
                        if (map.containsKey(docName)) {
                            counter = map.get(docName);
                            counter.inc();
                        } else {
                            counter = new NodeCount(p.getOwnerDocument());
                            map.put(docName, counter);
                        }
                        if (doctype == null) {
                            continue;
                        }
                        if (doctypes.containsKey(doctype.getName())) {
                            doctypeCounter = (DoctypeCount) doctypes.get(doctype
                                    .getName());
                            doctypeCounter.inc();
                        } else {
                            doctypeCounter = new DoctypeCount(doctype);
                            doctypes.put(doctype.getName(), doctypeCounter);
                        }
                    }
                }
            }
            final Map<String, Object> result = new HashMap<>();
            result.put("queryTime", System.currentTimeMillis() - startTime);
            result.put("hits", qr.result.getItemCount());
            final List<List> documents = new ArrayList<>();
            for (final NodeCount nodeCounter : map.values()) {
                final List hitsByDoc = new ArrayList();
                hitsByDoc.add(nodeCounter.doc.getFileURI().toString());
                hitsByDoc.add(nodeCounter.doc.getDocId());
                hitsByDoc.add(nodeCounter.count);
                documents.add(hitsByDoc);
            }
            result.put("documents", documents);

            final List<List> dtypes = new ArrayList<>();
            for (final DoctypeCount docTemp : doctypes.values()) {
                final List hitsByType = new ArrayList();
                hitsByType.add(docTemp.doctype.getName());
                hitsByType.add(docTemp.count);
                dtypes.add(hitsByType);
            }
            result.put("doctypes", dtypes);
            return result;

        } catch (final Throwable e) {
            handleException(e);
            return null;

        } finally {
            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if (broker != null) {
                    broker.getXQueryService().getXQueryPool().returnCompiledXQuery(source, compiled);
                }
            }
            factory.getBrokerPool().release(broker);
        }
    }

    public Map<String, Object> summary(final int resultId) throws EXistException, XPathException {
        final QueryResult qr = factory.resultSets.getResult(resultId);
        if (qr == null) {
            throw new EXistException("result set unknown or timed out");
        }
        qr.touch();
        final Map<String, Object> result = new HashMap<>();
        result.put("queryTime", qr.queryTime);
        if (qr.result == null) {
            result.put("hits", 0);
            return result;
        }
        final DBBroker broker = factory.getBrokerPool().get(user);
        try {
            final Map<String, NodeCount> map = new HashMap<>();
            final Map<String, DoctypeCount> doctypes = new HashMap<>();
            NodeProxy p;
            String docName;
            DocumentType doctype;
            NodeCount counter;
            DoctypeCount doctypeCounter;
            for (final SequenceIterator i = qr.result.iterate(); i.hasNext();) {
                final Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue nv = (NodeValue) item;
                    if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        p = (NodeProxy) nv;
                        docName = p.getOwnerDocument().getURI().toString();
                        doctype = p.getOwnerDocument().getDoctype();
                        if (map.containsKey(docName)) {
                            counter = map.get(docName);
                            counter.inc();
                        } else {
                            counter = new NodeCount(p.getOwnerDocument());
                            map.put(docName, counter);
                        }
                        if (doctype == null) {
                            continue;
                        }
                        if (doctypes.containsKey(doctype.getName())) {
                            doctypeCounter = (DoctypeCount) doctypes.get(doctype
                                    .getName());
                            doctypeCounter.inc();
                        } else {
                            doctypeCounter = new DoctypeCount(doctype);
                            doctypes.put(doctype.getName(), doctypeCounter);
                        }
                    }
                }
            }
            result.put("hits", qr.result.getItemCount());

            final List<List> documents = new ArrayList<>();
            for (final NodeCount nodeCounter : map.values()) {
                final List hitsByDoc = new ArrayList();
                hitsByDoc.add(nodeCounter.doc.getFileURI().toString());
                hitsByDoc.add(nodeCounter.doc.getDocId());
                hitsByDoc.add(nodeCounter.count);
                documents.add(hitsByDoc);
            }
            result.put("documents", documents);

            final List<List> dtypes = new ArrayList<>();
            for (final DoctypeCount docTemp : doctypes.values()) {
                final List hitsByType = new ArrayList();
                hitsByType.add(docTemp.doctype.getName());
                hitsByType.add(docTemp.count);
                dtypes.add(hitsByType);
            }
            result.put("doctypes", dtypes);
            return result;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<List> getIndexedElements(final String collectionName,
            final boolean inclusive) throws EXistException, PermissionDeniedException, URISyntaxException {
        return getIndexedElements(XmldbURI.xmldbUriFor(collectionName), inclusive);
    }

    private List<List> getIndexedElements(final XmldbURI collUri,
            final boolean inclusive) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + collUri + " not found");
            }
            final Occurrences occurrences[] = broker.getElementIndex().scanIndexedElements(collection,
                    inclusive);
            final List<List> result = new ArrayList<>(occurrences.length);
            for (final Occurrences occurrence : occurrences) {
                final QName qname = (QName) occurrence.getTerm();
                final List temp = new ArrayList(4);
                temp.add(qname.getLocalPart());
                temp.add(qname.getNamespaceURI());
                temp.add(qname.getPrefix() == null ? "" : qname.getPrefix());
                temp.add(occurrence.getOccurrences());
                result.add(temp);
            }
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<List> scanIndexTerms(final String collectionName,
            final String start, final String end, final boolean inclusive)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return scanIndexTerms(XmldbURI.xmldbUriFor(collectionName), start, end, inclusive);
    }

    private List<List> scanIndexTerms(final XmldbURI collUri,
            final String start, final String end, final boolean inclusive)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = factory.getBrokerPool().get(user);
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null) {
                throw new EXistException("collection " + collUri + " not found");
            }
            final MutableDocumentSet docs = new DefaultDocumentSet();
            collection.allDocs(broker, docs, inclusive);
            final NodeSet nodes = docs.docsToNodeSet();
            final List<List> result = scanIndexTerms(start, end, broker, docs, nodes);
            return result;
        } finally {
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public List<List> scanIndexTerms(final String xpath,
            final String start, final String end)
            throws PermissionDeniedException, EXistException, XPathException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            final XQuery xquery = broker.getXQueryService();
            final Sequence nodes = xquery.execute(xpath, null, AccessContext.XMLRPC);
            final List<List> result = scanIndexTerms(start, end, broker, nodes.getDocumentSet(), nodes.toNodeSet());
            return result;
        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    private List<List> scanIndexTerms(final String start, final String end, final DBBroker broker, final DocumentSet docs, final NodeSet nodes)
            throws PermissionDeniedException {
        final Occurrences occurrences[]
                = broker.getTextEngine().scanIndexTerms(docs, nodes, start, end);
        final List<List> result = new ArrayList<>(occurrences.length);
        for (final Occurrences occurrence : occurrences) {
            final List temp = new ArrayList(2);
            temp.add(occurrence.getTerm().toString());
            temp.add(occurrence.getOccurrences());
            result.add(temp);
        }
        return result;
    }

    public void synchronize() {
    }

    private Properties toProperties(final Map<String, Object> parameters) {
        final Properties properties = new Properties();
        properties.putAll(parameters);
        return properties;
    }

    class CachedQuery {

        final PathExpr expression;
        final String queryString;
        final long timestamp;

        public CachedQuery(final PathExpr expr, final String query) {
            this.expression = expr;
            this.queryString = query;
            this.timestamp = System.currentTimeMillis();
        }
    }

    class DoctypeCount {

        int count = 1;
        final DocumentType doctype;

        public DoctypeCount(final DocumentType doctype) {
            this.doctype = doctype;
        }

        public void inc() {
            count++;
        }
    }

    class NodeCount {

        int count = 1;
        final DocumentImpl doc;

        public NodeCount(final DocumentImpl doc) {
            this.doc = doc;
        }

        public void inc() {
            count++;
        }
    }

//	FIXME: Check it for possible security hole. Check name.
    @Override
    public byte[] getDocumentChunk(final String name, final int start, final int len)
            throws EXistException, PermissionDeniedException, IOException {
        final File file = new File(System.getProperty("java.io.tmpdir")
                + File.separator + name);
        if (!file.canRead()) {
            throw new EXistException("unable to read file " + name);
        }
        if (file.length() < start + len) {
            throw new EXistException("address too big " + name);
        }
        final byte buffer[] = new byte[len];
        try(final RandomAccessFile os = new RandomAccessFile(file.getAbsolutePath(), "r")) {
            LOG.debug("Read from: " + start + " to: " + (start + len));
            os.seek(start);
            os.read(buffer);
        }
        return buffer;
    }

    public boolean moveOrCopyResource(final String documentPath, final String destinationPath,
            final String newName, final boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyResource(XmldbURI.xmldbUriFor(documentPath),
                XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName), move);
    }

    private boolean moveOrCopyResource(final XmldbURI docUri, final XmldbURI destUri,
            final XmldbURI newName, final boolean move)
            throws EXistException, PermissionDeniedException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Collection collection = null;
        Collection destination = null;
        DocumentImpl doc = null;
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            //TODO : use  transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            collection = broker.openCollection(docUri.removeLastSegment(), move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + docUri.removeLastSegment() + " not found");
            }
            doc = collection.getDocumentWithLock(broker, docUri.lastSegment(), Lock.WRITE_LOCK);
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("Document " + docUri + " not found");
            }
            //TODO : register the lock within the transaction ?

            // get destination collection
            destination = broker.openCollection(destUri, Lock.WRITE_LOCK);
            if (destination == null) {
                transact.abort(transaction);
                throw new EXistException("Destination collection " + destUri + " not found");
            }
            if (move) {
                broker.moveResource(transaction, doc, destination, newName);
            } else {
                broker.copyResource(transaction, doc, destination, newName);
            }
            transact.commit(transaction);
            return true;
        } catch (final LockException e) {
            throw new PermissionDeniedException("Could not acquire lock on document " + docUri);
        } catch (final IOException | TriggerException e) {
            throw new EXistException("Exception [" + e.getMessage() + "] on document " + docUri);
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            if (collection != null) {
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            }
            if (destination != null) {
                destination.release(Lock.WRITE_LOCK);
            }
        }
    }

    public boolean moveOrCopyCollection(final String collectionName, final String destinationPath,
            final String newName, final boolean move)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyCollection(XmldbURI.xmldbUriFor(collectionName),
                XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName), move);
    }

    private boolean moveOrCopyCollection(final XmldbURI collUri, final XmldbURI destUri,
            final XmldbURI newName, final boolean move)
            throws EXistException, PermissionDeniedException {
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        Collection collection = null;
        Collection destination = null;
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            // get source document
            //TODO : use  transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
            collection = broker.openCollection(collUri, move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + collUri + " not found");
            }
            // get destination collection
            destination = broker.openCollection(destUri, Lock.WRITE_LOCK);
            if (destination == null) {
                transact.abort(transaction);
                throw new EXistException("Destination collection " + destUri + " not found");
            }
            if (move) {
                broker.moveCollection(transaction, collection, destination, newName);
            } else {
                broker.copyCollection(transaction, collection, destination, newName);
            }
            transact.commit(transaction);
            return true;
        } catch (final LockException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (final IOException | TriggerException e) {
            throw new EXistException(e.getMessage());
        } finally {
            if (collection != null) {
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            }
            if (destination != null) {
                destination.release(Lock.WRITE_LOCK);
            }
        }
    }

    @Override
    public boolean reindexCollection(final String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
        reindexCollection(XmldbURI.xmldbUriFor(collectionName));
        return true;
    }

    private void reindexCollection(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);
            broker.reindexCollection(collUri);
            LOG.debug("collection " + collUri + " and sub collection reindexed");

        } catch (final EXistException | PermissionDeniedException e) {
            handleException(e);

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean backup(final String userbackup, final String password,
            final String destcollection, final String collection) throws EXistException, PermissionDeniedException {
        try {
            final Backup backup = new Backup(
                    userbackup,
                    password,
                    destcollection + "-backup",
                    XmldbURI.xmldbUriFor(XmldbURI.EMBEDDED_SERVER_URI.toString() + collection));
            backup.backup(false, null);

        } catch (final URISyntaxException | XMLDBException | IOException | SAXException e) {
            handleException(e);
        }
        return true;
    }

    /**
     * Validate if specified document is Valid.
     *
     * @param documentPath Path to XML document in database
     * @return TRUE if document is valid, FALSE if not or errors or.....
     * @throws java.net.URISyntaxException
     * @throws PermissionDeniedException User is not allowed to perform action.
     * @throws org.exist.EXistException
     */
    @Override
    public boolean isValid(final String documentPath)
            throws PermissionDeniedException, URISyntaxException, EXistException {
        return isValid(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean isValid(final XmldbURI docUri) throws EXistException, PermissionDeniedException {

        try {
            // Setup validator
            final Validator validator = new Validator(factory.getBrokerPool());

            // Get inputstream
            // TODO DWES reconsider
            try (final InputStream is = new EmbeddedInputStream(new XmldbURL(docUri))) {
                // Perform validation
                final ValidationReport report = validator.validate(is);

                // Return validation result
                return report.isValid();
            }
        } catch (final Throwable e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public List<String> getDocType(final String documentPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getDocType(XmldbURI.xmldbUriFor(documentPath));
    }

    private List<String> getDocType(final XmldbURI docUri)
            throws PermissionDeniedException, EXistException {
        DBBroker broker = null;
        DocumentImpl doc = null;

        try {
            broker = factory.getBrokerPool().get(user);
            doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
            if (doc == null) {
                LOG.debug("document " + docUri + " not found!");
                throw new EXistException("document not found");
            }

            final List<String> list = new ArrayList<>(3);

            if (doc.getDoctype() != null) {
                list.add(doc.getDoctype().getName());

                if (doc.getDoctype().getPublicId() != null) {
                    list.add(doc.getDoctype().getPublicId());
                } else {
                    list.add("");
                }

                if (doc.getDoctype().getSystemId() != null) {
                    list.add(doc.getDoctype().getSystemId());
                } else {
                    list.add("");
                }
            } else {
                list.add("");
                list.add("");
                list.add("");
            }
            return list;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
            factory.getBrokerPool().release(broker);
        }
    }

    @Override
    public boolean setDocType(final String documentPath, final String doctypename, final String publicid, final String systemid) throws
            URISyntaxException, EXistException, PermissionDeniedException {
        return setDocType(XmldbURI.xmldbUriFor(documentPath), doctypename, publicid, systemid);
    }

    private boolean setDocType(final XmldbURI docUri, final String doctypename, final String publicid, final String systemid) throws EXistException, PermissionDeniedException {
        DocumentImpl doc = null;
        DocumentType result = null;
        final TransactionManager transact = factory.getBrokerPool().getTransactionManager();
        try (final DBBroker broker = factory.getBrokerPool().get(user);
                final Txn transaction = transact.beginTransaction()) {
            doc = broker.getXMLResource(docUri, Lock.WRITE_LOCK);
            if (doc == null) {
                transact.abort(transaction);
                throw new EXistException("Resource " + docUri + " not found");
            }
            //TODO : register the lock within the transaction ?
            if (!doc.getPermissions().validate(user, Permission.WRITE)) {
                transact.abort(transaction);
                throw new PermissionDeniedException("User is not allowed to lock resource " + docUri);
            }

            if (!"".equals(doctypename)) {
                result = new DocumentTypeImpl(doctypename,
                        "".equals(publicid) ? null : publicid,
                        "".equals(systemid) ? null : systemid);
            }

            doc.setDocumentType(result);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return true;

        } catch (final Throwable e) {
            handleException(e);
            return false;
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
        }
    }

    @Override
    public boolean copyResource(final String docPath, final String destinationPath, final String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyResource(docPath, destinationPath, newName, false);
    }

    @Override
    public boolean copyCollection(final String collectionPath, final String destinationPath, final String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyCollection(collectionPath, destinationPath, newName, false);
    }

    @Override
    public boolean moveResource(final String docPath, final String destinationPath, final String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyResource(docPath, destinationPath, newName, true);
    }

    @Override
    public boolean moveCollection(final String collectionPath, final String destinationPath, final String newName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return moveOrCopyCollection(collectionPath, destinationPath, newName, true);
    }

    @Override
    public List<String> getDocumentChunk(final String name, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException, IOException {
        final List<String> result = new ArrayList<>(2);
        final File file = File.createTempFile("rpc", ".xml");
        file.deleteOnExit();
        try (final FileOutputStream os = new FileOutputStream(file.getAbsolutePath(), true)) {
            os.write(getDocument(name, parameters));
        }
        result.add(file.getName());
        result.add(Long.toString(file.length()));
        return result;
    }

    @Override
    public boolean copyCollection(final String name, final String namedest) throws PermissionDeniedException, EXistException {
        try {
            createCollection(namedest);

            final Map<String, Object> parametri = new HashMap<>();
            parametri.put(OutputKeys.INDENT, "no");
            parametri.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
            parametri.put(OutputKeys.ENCODING, DEFAULT_ENCODING);

            final Map<String, Object> desc = getCollectionDesc(name);
            final Object[] collections = (Object[]) desc.get("collections");
            final Object[] documents = (Object[]) desc.get("documents");

            //ricrea le directory
            for (final Object collection : collections) {
                final String nome = collection.toString();
                createCollection(namedest + "/" + nome);
                copyCollection(name + "/" + nome, namedest + "/" + nome);
            }

            //Copy i file
            int p, dsize = documents.length;
            for (int i = 0; i < dsize; i++) {
                final Map<String, Object> hash = (Map<String, Object>) documents[i];
                String nome = (String) hash.get("name");
                //TODO : use dedicated function in XmldbURI
                if ((p = nome.lastIndexOf("/")) != Constants.STRING_NOT_FOUND) {
                    nome = nome.substring(p + 1);
                }

                final byte[] xml = getDocument(name + "/" + nome, parametri);
                parse(xml, namedest + "/" + nome);
            }

            return true;

        } catch (final EXistException | PermissionDeniedException | URISyntaxException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int xupdateResource(final String resource, final byte[] xupdate) throws PermissionDeniedException, EXistException, SAXException {
        return xupdateResource(resource, xupdate, DEFAULT_ENCODING);
    }

    @Override
    public Map<String, Object> querySummary(final int resultId) throws EXistException, PermissionDeniedException, XPathException {
        return summary(resultId);
    }

    @Override
    public int executeQuery(final byte[] xpath, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return executeQuery(xpath, null, parameters);
    }

    @Override
    public boolean storeBinary(final byte[] data, final String docName, final String mimeType, final boolean replace, final Date created, final Date modified) throws EXistException, PermissionDeniedException, URISyntaxException {
        return storeBinary(data, docName, mimeType, replace ? 1 : 0, created, modified);
    }

    @Override
    public boolean storeBinary(final byte[] data, final String docName, final String mimeType, final boolean replace) throws EXistException, PermissionDeniedException, URISyntaxException {
        return storeBinary(data, docName, mimeType, replace ? 1 : 0, null, null);
    }

    @Override
    public boolean parseLocalExt(final String localFile, final String docName, final boolean replace, final String mimeType, final boolean treatAsXML, final Date created, final Date modified) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocalExt(localFile, docName, replace ? 1 : 0, mimeType, treatAsXML ? 1 : 0, created, modified);
    }

    @Override
    public boolean parseLocal(final String localFile, final String docName, final boolean replace, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocal(localFile, docName, replace ? 1 : 0, mimeType, created, modified);
    }

    @Override
    public boolean parseLocalExt(final String localFile, final String docName, final boolean replace, final String mimeType, final boolean treatAsXML) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocalExt(localFile, docName, replace ? 1 : 0, mimeType, treatAsXML ? 1 : 0, null, null);
    }

    @Override
    public boolean parseLocal(final String localFile, final String docName, final boolean replace, final String mimeType) throws EXistException, PermissionDeniedException, SAXException, URISyntaxException {
        return parseLocal(localFile, docName, replace ? 1 : 0, mimeType, null, null);
    }

    @Override
    public String uploadCompressed(final String file, final byte[] data, final int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(data, length, file, true);
    }

    @Override
    public String uploadCompressed(final byte[] data, final int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(data, length, null, true);
    }

    @Override
    public String upload(final String file, final byte[] chunk, final int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(chunk, length, file, false);
    }

    @Override
    public String upload(final byte[] chunk, final int length) throws EXistException, PermissionDeniedException, IOException {
        return upload(chunk, length, null, false);
    }

    @Override
    public boolean parse(final String xml, final String docName) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return parse(xml.getBytes(DEFAULT_ENCODING), docName, 0);

        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean parse(final String xml, final String docName, final int overwrite) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return parse(xml.getBytes(DEFAULT_ENCODING), docName, overwrite);

        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean parse(final byte[] xmlData, final String docName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return parse(xmlData, docName, 0);
    }

    @Override
    public Map<String, Object> querySummary(final String xquery) throws EXistException, PermissionDeniedException {
        return summary(xquery);
    }

    @Override
    public byte[] query(final byte[] xquery, final int howmany, final int start, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        try {
            final String result = query(new String(xquery, DEFAULT_ENCODING), howmany, start, parameters);
            return result.getBytes(DEFAULT_ENCODING);

        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Map<String, Object> queryP(final byte[] xpath, final String docName, final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException, URISyntaxException {
        try {
            return queryP(new String(xpath, DEFAULT_ENCODING), docName, s_id, parameters);
        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Map<String, Object> queryP(final byte[] xpath, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        try {
            return queryP(new String(xpath, DEFAULT_ENCODING), (XmldbURI) null, null, parameters);

        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Map<String, Object> compile(final byte[] xquery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        try {
            return compile(new String(xquery, DEFAULT_ENCODING), parameters);

        } catch (final UnsupportedEncodingException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public byte[] retrieve(final String doc, final String id) throws EXistException, PermissionDeniedException {
        return retrieve(doc, id, null);
    }

    @Override
    public String getDocumentAsString(final String name, final int prettyPrint, final String stylesheet) throws EXistException, PermissionDeniedException {
        final Map<String, Object> parametri = new HashMap<>();

        if (prettyPrint > 0) {
            parametri.put(OutputKeys.INDENT, "yes");
        } else {
            parametri.put(OutputKeys.INDENT, "no");
        }

        if (stylesheet != null) {
            parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }
        return getDocumentAsString(name, parametri);
    }

    @Override
    public String getDocumentAsString(final String name, final int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocumentAsString(name, prettyPrint, null);
    }

    @Override
    public byte[] getDocument(final String name, final String encoding, final int prettyPrint, final String stylesheet) throws EXistException, PermissionDeniedException {
        final Map<String, Object> parametri = new HashMap<>();

        if (prettyPrint > 0) {
            parametri.put(OutputKeys.INDENT, "yes");
        } else {
            parametri.put(OutputKeys.INDENT, "no");
        }

        if (stylesheet != null) {
            parametri.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }

        parametri.put(OutputKeys.ENCODING, encoding);

        //String xml = con.getDocument(user, name, (prettyPrint > 0),
        // encoding, stylesheet);
        final byte[] xml = getDocument(name, parametri);
        if (xml == null) {
            throw new EXistException("document " + name + " not found!");
        }
        return xml;
    }

    @Override
    public byte[] getDocument(final String name, final String encoding, final int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocument(name, encoding, prettyPrint, null);
    }

    @Override
    public boolean setTriggersEnabled(final String path, final String value) throws PermissionDeniedException {
        DBBroker broker = null;
        final boolean triggersEnabled = Boolean.parseBoolean(value);
        try {
            broker = factory.getBrokerPool().get(user);
            final Collection collection = broker.getCollection(XmldbURI.create(path));
            if (collection == null) {
                return false;
            }
            collection.setTriggersEnabled(triggersEnabled);
        } catch (final EXistException e) {
            LOG.warn(triggersEnabled ? "Enable" : "Disable" + " triggers failed", e);
            return false;
        } finally {
            factory.getBrokerPool().release(broker);
        }
        return true;
    }

    @Override
    public boolean shutdown() throws PermissionDeniedException {
        return shutdown(0);
    }

    @Override
    public boolean shutdown(final String delay) throws PermissionDeniedException {
        return shutdown(Long.parseLong(delay));
    }

    @Override
    public boolean shutdown(final long delay) throws PermissionDeniedException {
        if (!user.hasDbaRole()) {
            throw new PermissionDeniedException("not allowed to shut down" + "the database");
        }

        final SystemTaskJob shutdownJob = new SystemTaskJobImpl("rpc-api.shutdown", new SystemTask() {
            @Override
            public void configure(final Configuration config, final Properties properties) throws EXistException {
            }

            @Override
            public void execute(final DBBroker broker) throws EXistException {
                broker.getBrokerPool().shutdown();
            }

            @Override
            public boolean afterCheckpoint() {
                return true;
            }
        });

        return factory.getBrokerPool().getScheduler().createPeriodicJob(0, shutdownJob, delay, new Properties(), 0);
    }

    public boolean enterServiceMode() throws PermissionDeniedException, EXistException {
        final BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.enterServiceMode(user);
        return true;
    }

    public void exitServiceMode() throws PermissionDeniedException, EXistException {
        final BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.exitServiceMode(user);
    }

    @Override
    public void runCommand(final XmldbURI collectionURI, final List<String> params) throws EXistException, PermissionDeniedException {

        DBBroker broker = null;
        try {
            broker = factory.getBrokerPool().get(user);

            org.exist.plugin.command.Commands.command(collectionURI, params.toArray(new String[params.size()]));

        } finally {
            factory.getBrokerPool().release(broker);
        }
    }
}
