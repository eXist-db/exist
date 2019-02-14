/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmlrpc;

import com.evolvedbinary.j8fu.function.ConsumerE;
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
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.scheduler.SystemTaskJob;
import org.exist.scheduler.impl.ShutdownTask;
import org.exist.scheduler.impl.SystemTaskJobImpl;
import org.exist.security.ACLPermission;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SchemaType;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.security.internal.aider.ACEAider;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.TemporaryFileManager;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.xmldb.XmldbURI;
import org.exist.xmlrpc.function.XmlRpcCollectionFunction;
import org.exist.xmlrpc.function.XmlRpcCompiledXQueryFunction;
import org.exist.xmlrpc.function.XmlRpcDocumentFunction;
import org.exist.xmlrpc.function.XmlRpcFunction;
import org.exist.xquery.*;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.evolvedbinary.j8fu.function.Function2E;
import com.evolvedbinary.j8fu.function.Function3E;
import com.evolvedbinary.j8fu.function.SupplierE;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.xmldb.api.base.*;

import static java.nio.file.StandardOpenOption.*;

/**
 * This class implements the actual methods defined by
 * {@link org.exist.xmlrpc.RpcAPI}.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *         Modified by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RpcConnection implements RpcAPI {

    private final static Logger LOG = LogManager.getLogger(RpcConnection.class);

    public final static int MAX_DOWNLOAD_CHUNK_SIZE = 1024 * 1024;  // 1 MB
    private final static Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;

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

    @Override
    public boolean createCollection(final String name) throws EXistException, PermissionDeniedException {
        return createCollection(name, null);
    }

    @Override
    public boolean createCollection(final String name, final Date created) throws EXistException, PermissionDeniedException {
        try {
            return createCollection(XmldbURI.xmldbUriFor(name), created);
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private boolean createCollection(final XmldbURI collUri, final Date created) throws PermissionDeniedException, EXistException {
        withDb((broker, transaction) -> {
            Collection current = broker.getCollection(collUri);
            if (current != null) {
                return true;
            }

            current = broker.getOrCreateCollection(transaction, collUri);

            //TODO : register a lock (wich one ?) within the transaction ?
            if (created != null) {
                current.setCreationTime(created.getTime());
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("creating collection " + collUri);
            }

            broker.saveCollection(transaction, current);

            return null;
        });

        LOG.info("collection " + collUri + " has been created");
        return true;
    }

    @Override
    public boolean configureCollection(final String collName, final String configuration)
            throws EXistException, PermissionDeniedException {
        try {
            return configureCollection(XmldbURI.xmldbUriFor(collName), configuration);
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private boolean configureCollection(final XmldbURI collUri, final String configuration)
            throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            final Collection colRef = this.<Collection>readCollection(broker, transaction, collUri).apply((collection, broker1, transaction1) -> collection);
            final CollectionConfigurationManager mgr = factory.getBrokerPool().getConfigurationManager();
            try {
                mgr.addConfiguration(transaction, broker, colRef, configuration);
            } catch (final CollectionConfigurationException e) {
                throw new EXistException(e.getMessage());
            }
            return null;
        });

        LOG.info("Configured '" + collUri + "'");
        return true;
    }

    public String createId(final String collName) throws EXistException, URISyntaxException, PermissionDeniedException {
        return createId(XmldbURI.xmldbUriFor(collName));
    }

    private String createId(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        return this.<String>readCollection(collUri).apply((collection, broker, transaction) -> {
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

                if (collection.hasChildCollection(broker, id)) {
                    ok = false;
                }

            } while (!ok);
            return id.toString();
        });
    }

    protected QueryResult doQuery(final DBBroker broker, final CompiledXQuery compiled,
                                  final NodeSet contextSet, final Map<String, Object> parameters) throws XPathException, EXistException, PermissionDeniedException {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();

        checkPragmas(compiled.getContext(), parameters);
        LockedDocumentMap lockedDocuments = null;
        try {
            final long start = System.currentTimeMillis();
            lockedDocuments = beginProtected(broker, parameters);
            if (lockedDocuments != null) {
                compiled.getContext().setProtectedDocs(lockedDocuments);
            }
            final Properties outputProperties = new Properties();
            final Sequence result = xquery.execute(broker, compiled, contextSet, outputProperties);
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
                coll.allDocs(broker, docs, true, lockedDocuments, LockMode.WRITE_LOCK);
                return lockedDocuments;
            } catch (final LockException e) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Deadlock detected. Starting over again. Docs: " + docs.getDocumentCount() + "; locked: "
                            + lockedDocuments.size());
                }
                lockedDocuments.unlock();
            }
        } while (true);
    }

    /**
     * @deprecated Use compileQuery lambda instead!
     */
    @Deprecated
    private CompiledXQuery compile(final DBBroker broker, final Source source, final Map<String, Object> parameters) throws XPathException, IOException, PermissionDeniedException {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        XQueryContext context;
        if (compiled == null) {
            context = new XQueryContext(broker.getBrokerPool());
        } else {
            context = compiled.getContext();
            context.prepareForReuse();
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
            compiled = xquery.compile(broker, context, source);
        }
        return compiled;
    }

    @Override
    public String printDiagnostics(final String query, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final Source source = new StringSource(query);
        return withDb((broker, transaction) -> {
            try {
                return this.<String>compileQuery(broker, transaction, source, parameters).apply(compiledQuery -> {
                    final StringWriter writer = new StringWriter();
                    compiledQuery.dump(writer);
                    return writer.toString();
                });
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
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
            if(LOG.isDebugEnabled()) {
                LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
            }
            parameters.put(pair[0], pair[1]);
        }
    }

    @Override
    public int executeQuery(final byte[] xpath, final String encoding, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final Charset charset = Optional.ofNullable(encoding).map(Charset::forName).orElse(DEFAULT_ENCODING);
        final String xpathString = new String(xpath, charset);
        if(LOG.isDebugEnabled()) {
            LOG.debug("query: " + xpathString);
        }
        return executeQuery(xpathString, parameters);
    }

    @Override
    public int executeQuery(final String xpath, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> {
            final Source source = new StringSource(xpath);
            final long startTime = System.currentTimeMillis();
            try {
                final QueryResult result = this.<QueryResult>compileQuery(broker, transaction, source, parameters).apply(compiledQuery -> doQuery(broker, compiledQuery, null, parameters));
                if (result.hasErrors()) {
                    throw new EXistException(result.getException());
                }
                result.queryTime = System.currentTimeMillis() - startTime;
                return factory.resultSets.add(result);
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    protected String formatErrorMsg(final String message) {
        return formatErrorMsg("error", message);
    }

    protected String formatErrorMsg(final String type, final String message) {
        return ("<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" ") + "hitCount=\"0\">" +
                '<' + type + '>' + message + "</" + type + "></exist:result>";
    }

    @Override
    public boolean existsAndCanOpenCollection(final String collectionUri) throws EXistException, PermissionDeniedException {
        final XmldbURI uri;
        try {
            uri = XmldbURI.xmldbUriFor(collectionUri);
        } catch (final URISyntaxException use) {
            throw new EXistException("Collection '" + collectionUri + "' does not indicate a valid collection URI: " + use.getMessage(), use);
        }

        return withDb((broker, transaction) -> {
            Collection collection = null;
            try {
                collection = broker.openCollection(uri, LockMode.READ_LOCK);
                return collection != null;
            } finally {
                if (collection != null) {
                    collection.release(LockMode.READ_LOCK);
                }
            }
        });
    }

    @Override
    public Map<String, Object> getCollectionDesc(final String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return getCollectionDesc((rootCollection == null) ? XmldbURI.ROOT_COLLECTION_URI : XmldbURI.xmldbUriFor(rootCollection));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private Map<String, Object> getCollectionDesc(final XmldbURI rootUri) throws EXistException, PermissionDeniedException {
        return this.<Map<String, Object>>readCollection(rootUri).apply((collection, broker, transaction) -> {
            final Map<String, Object> desc = new HashMap<>();
            final List<Map<String, Object>> docs = new ArrayList<>();
            final List<String> collections = new ArrayList<>();
            if (collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();
                    final Permission perms = doc.getPermissions();

                    final Map<String, Object> hash = new HashMap<>(5);
                    hash.put("name", doc.getFileURI().toString());
                    hash.put("owner", perms.getOwner().getName());
                    hash.put("group", perms.getGroup().getName());
                    hash.put("permissions", perms.getMode());
                    hash.put("type", doc.getResourceType() == DocumentImpl.BINARY_FILE ? "BinaryResource" : "XMLResource");
                    docs.add(hash);
                }
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
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
        });
    }

    @Override
    public Map<String, Object> describeResource(final String resourceName)
            throws EXistException, PermissionDeniedException {
        try {
            return describeResource(XmldbURI.xmldbUriFor(resourceName));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private Map<String, Object> describeResource(final XmldbURI resourceUri)
            throws EXistException, PermissionDeniedException {
        try {
            return this.<Map<String, Object>>readDocument(resourceUri).apply((document, broker, transaction) -> {
                final Map<String, Object> hash = new HashMap<>(11);
                final Permission perms = document.getPermissions();
                hash.put("name", resourceUri.toString());
                hash.put("owner", perms.getOwner().getName());
                hash.put("group", perms.getGroup().getName());
                hash.put("permissions", perms.getMode());

                if (perms instanceof ACLPermission) {
                    hash.put("acl", getACEs(perms));
                }

                hash.put("type",
                        document.getResourceType() == DocumentImpl.BINARY_FILE
                                ? "BinaryResource"
                                : "XMLResource");
                final long resourceLength = document.getContentLength();
                hash.put("content-length", (resourceLength > (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) resourceLength);
                hash.put("content-length-64bit", Long.toString(resourceLength));
                hash.put("mime-type", document.getMetadata().getMimeType());
                hash.put("created", new Date(document.getMetadata().getCreated()));
                hash.put("modified", new Date(document.getMetadata().getLastModified()));
                if (document.getResourceType() == DocumentImpl.BINARY_FILE) {
                    hash.put("blob-id", ((BinaryDocument)document).getBlobId().getId());

                    final MessageDigest messageDigest = broker.getBinaryResourceContentDigest(transaction, (BinaryDocument)document, DigestType.BLAKE_256);
                    hash.put("digest-algorithm", messageDigest.getDigestType().getCommonNames()[0]);
                    hash.put("digest", messageDigest.getValue());
                }
                return hash;
            });
        } catch (final EXistException e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug(e);
            }
            return new HashMap<>();
        }
    }

    @Override
    public Map<String, Object> describeCollection(final String rootCollection) throws EXistException, PermissionDeniedException {
        try {
            return describeCollection((rootCollection == null) ? XmldbURI.ROOT_COLLECTION_URI : XmldbURI.xmldbUriFor(rootCollection));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    /**
     * The method <code>describeCollection</code>
     * <p>
     * Returns details of a collection - collections (list of sub-collections) -
     * name - created - owner - group - permissions - acl
     * <p>
     * If you do not have read access on the collection, the list of
     * sub-collections will be empty, an exception will not be thrown!
     *
     * @param collUri a <code>XmldbURI</code> value
     * @return a <code>Map</code> value
     * @throws Exception if an error occurs
     */
    private Map<String, Object> describeCollection(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        return this.<Map<String, Object>>readCollection(collUri).apply((collection, broker, transaction) -> {
            final Map<String, Object> desc = new HashMap<>();
            final List<String> collections = new ArrayList<>();
            if (collection.getPermissionsNoLock().validate(user, Permission.READ)) {
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
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
        });
    }

    @Override
    public Map<String, Object> getContentDigest(final String path, final String digestAlgorithm) throws EXistException, PermissionDeniedException {
        try {
            final DigestType digestType = DigestType.forCommonName(digestAlgorithm);
            final MessageDigest messageDigest = this.<MessageDigest>readDocument(XmldbURI.xmldbUriFor(path)).apply((document, broker, transaction) -> {
                if (document instanceof BinaryDocument) {
                    return broker.getBinaryResourceContentDigest(transaction, (BinaryDocument) document, digestType);
                } else {
                    throw new EXistException("Only supported for binary documents");
                }
            });

            final Map<String, Object> result = new HashMap<>();
            result.put("digest-algorithm", messageDigest.getDigestType().getCommonNames()[0]);
            result.put("digest", messageDigest.getValue());
            return result;
        } catch (final IllegalArgumentException | URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    @Override
    public byte[] getDocument(final String name, final Map<String, Object> parameters) throws EXistException,
            PermissionDeniedException {
        final Charset encoding = getEncoding(parameters);
        final boolean compression = useCompression(parameters);

        final String xml = getDocumentAsString(name, parameters);

        if (compression) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("getDocument with compression");
            }
            try {
                return Compressor.compress(xml.getBytes(encoding));
            } catch (final IOException ioe) {
                throw new EXistException(ioe);
            }
        } else {
            return xml.getBytes(encoding);
        }
    }

    @Override
    public String getDocumentAsString(final String docName, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        try {
            return getDocumentAsString(XmldbURI.xmldbUriFor(docName), parameters);
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private String getDocumentAsString(final XmldbURI docUri, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return this.<String>readDocument(docUri).apply((document, broker, transaction) -> {
            try (final StringWriter writer = new StringWriter()) {
                serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.toSAX(document), writer);
                return writer.toString();
            }
        });
    }

    private void serialize(final DBBroker broker, final Properties properties, final ConsumerE<Serializer, SAXException> toSaxFunction, final Writer writer) throws SAXException, IOException {
        final Serializer serializer = broker.getSerializer();
        serializer.setUser(user);
        serializer.setProperties(properties);

        SAXSerializer saxSerializer = null;
        try {
            saxSerializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            saxSerializer.setOutput(writer, properties);
            serializer.setSAXHandlers(saxSerializer, saxSerializer);

            toSaxFunction.accept(serializer);

            writer.flush();
        } finally {
            if (saxSerializer != null) {
                SerializerPool.getInstance().returnObject(saxSerializer);
            }
        }
    }

    @Override
    public Map<String, Object> getDocumentData(final String docName, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {

        final XmldbURI docUri;
        try {
            docUri = XmldbURI.xmldbUriFor(docName);
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }

        return this.<Map<String, Object>>readDocument(docUri).apply((document, broker, transaction) -> {
            final Charset encoding = getEncoding(parameters);

            // A tweak for very large resources, VirtualTempFile
            final Map<String, Object> result = new HashMap<>();
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = temporaryFileManager.getTemporaryFile();

            if (document.getResourceType() == DocumentImpl.XML_FILE) {
                try (final Writer writer = Files.newBufferedWriter(tempFile, encoding)) {
                    serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.toSAX(document), writer);
                }
            } else {
                try (final OutputStream os = Files.newOutputStream(tempFile)) {
                    broker.readBinaryResource(transaction, (BinaryDocument) document, os);
                }
            }

            final byte[] firstChunk = getChunk(tempFile, 0);

            result.put("data", firstChunk);
            int offset = 0;
            if (Files.size(tempFile) > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(tempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                temporaryFileManager.returnTemporaryFile(tempFile);
            }
            result.put("offset", offset);

            return result;
        });
    }

    private byte[] getChunk(final Path file, final int offset) throws IOException {
        final long available = Files.size(file);
        final int len = (int)Math.min(Math.min(available - offset, MAX_DOWNLOAD_CHUNK_SIZE), Integer.MAX_VALUE);

        final byte[] chunk = new byte[len];
        try (final InputStream is = Files.newInputStream(file)) {
            is.skip(offset);
            final int read = is.read(chunk);
            if(read != len) {
                throw new IOException("Unable to read full chunk at offset: " + offset + ", from file: " + file.toAbsolutePath().toString());
            }
        }
        return chunk;
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
            final Path tempFile = sr.result;

            if (offset <= 0 || offset > Files.size(tempFile)) {
                factory.resultSets.remove(resultId);
                throw new EXistException("No more data available");
            }
            final byte[] chunk = getChunk(tempFile, offset);
            final long nextChunk = offset + chunk.length;

            final Map<String, Object> result = new HashMap<>();
            result.put("data", chunk);
            result.put("handle", handle);
            if (nextChunk > (long) Integer.MAX_VALUE || nextChunk >= Files.size(tempFile)) {
                factory.resultSets.remove(resultId);
                result.put("offset", 0);
            } else {
                result.put("offset", nextChunk);
            }
            return result;
        } catch (final NumberFormatException | IOException e) {
            throw new EXistException(e);
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
            final Path tempFile = sr.result;

            final long longOffset = Long.parseLong(offset);
            if (longOffset < 0 || longOffset > Files.size(tempFile)) {
                factory.resultSets.remove(resultId);
                throw new EXistException("No more data available");
            }
            final byte[] chunk = getChunk(tempFile, (int)longOffset);
            final long nextChunk = longOffset + chunk.length;

            final Map<String, Object> result = new HashMap<>();
            result.put("data", chunk);
            result.put("handle", handle);
            if (nextChunk >= Files.size(tempFile)) {
                factory.resultSets.remove(resultId);
                result.put("offset", Long.toString(0));
            } else {
                result.put("offset", Long.toString(nextChunk));
            }
            return result;

        } catch (final NumberFormatException | IOException e) {
            throw new EXistException(e);
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
        return this.<byte[]>readDocument(name).apply((document, broker, transaction) -> {
            if (document.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new EXistException("Document " + name + " is not a binary resource");
            }

            if (!document.getPermissions().validate(user, requiredPermissions)) {
                throw new PermissionDeniedException("Insufficient privileges to access resource");
            }

            try (final InputStream is = broker.getBinaryResource(transaction, (BinaryDocument) document)) {
                final long resourceSize = document.getContentLength();
                if (resourceSize > (long) Integer.MAX_VALUE) {
                    throw new EXistException("Resource too big to be read using this method.");
                }
                final byte[] data = new byte[(int) resourceSize];
                is.read(data);
                return data;
            }
        });
    }

    @Override
    public int xupdate(final String collectionName, final byte[] xupdate) throws PermissionDeniedException, EXistException {
        try {
            return xupdate(XmldbURI.xmldbUriFor(collectionName), new String(xupdate, DEFAULT_ENCODING));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private int xupdate(final XmldbURI collUri, final String xupdate) throws PermissionDeniedException, EXistException {
        return withDb((broker, transaction) -> {
            final Collection collectionRef = this.<Collection>readCollection(collUri).apply((collection, broker1, transaction1) -> collection);
            //TODO : register a lock (which one ?) in the transaction ?
            final DocumentSet docs = collectionRef.allDocs(broker, new DefaultDocumentSet(), true);
            try {
                final XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
                final Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
                long mods = 0;
                for (final Modification modification : modifications) {
                    mods += modification.process(transaction);
                    broker.flush();
                }
                return (int) mods;
            } catch (final XPathException | ParserConfigurationException e) {
                throw new EXistException(e);
            }
        });
    }

    @Override
    public int xupdateResource(final String resource, final byte[] xupdate, final String encoding) throws PermissionDeniedException, EXistException {
        try {
            return xupdateResource(XmldbURI.xmldbUriFor(resource), new String(xupdate, Charset.forName(encoding)));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    private int xupdateResource(final XmldbURI docUri, final String xupdate) throws PermissionDeniedException, EXistException {
        return withDb((broker, transaction) -> {
            final DocumentImpl documentRef = this.<DocumentImpl>readDocument(docUri).apply((document, broker1, transaction1) -> document);
            //TODO : register a lock (which one ?) within the transaction ?
            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(documentRef);
            try {
                final XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
                final Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
                long mods = 0;
                for (final Modification modification : modifications) {
                    mods += modification.process(transaction);
                    broker.flush();
                }
                return (int) mods;
            } catch (final XPathException | ParserConfigurationException e) {
                throw new EXistException(e);
            }
        });
    }

    @Override
    public boolean sync() {
        try {
            return withDbAsSystem((broker, transaction) -> {
                broker.sync(Sync.MAJOR);
                return true;
            });
        } catch (final EXistException | PermissionDeniedException e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean dataBackup(final String dest) {
        factory.getBrokerPool().triggerSystemTask(new DataBackup(Paths.get(dest)));
        return true;
    }

    @Override
    public List<String> getDocumentListing() throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> {
            final DocumentSet docs = broker.getAllXMLResources(new DefaultDocumentSet());
            final XmldbURI names[] = docs.getNames();
            final List<String> list = new ArrayList<>();
            for (final XmldbURI name : names) {
                list.add(name.toString());
            }
            return list;
        });
    }

    @Override
    public List<String> getCollectionListing(final String collName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return getCollectionListing(XmldbURI.xmldbUriFor(collName));
    }

    private List<String> getCollectionListing(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        try {
            return this.<List<String>>readCollection(collUri).apply((collection, broker, transaction) -> {
                final List<String> list = new ArrayList<>();
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
                    list.add(i.next().toString());
                }
                return list;
            });
        } catch (final EXistException e) {
            return Collections.EMPTY_LIST;
        }
    }

    @Override
    public List<String> getDocumentListing(final String collName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getDocumentListing(XmldbURI.xmldbUriFor(collName));
    }

    private List<String> getDocumentListing(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        try {
            return this.<List<String>>readCollection(collUri).apply((collection, broker, transaction) -> {
                final List<String> list = new ArrayList<>();
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    list.add(i.next().getFileURI().toString());
                }
                return list;
            });
        } catch (final EXistException e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug(e);
            }
            return Collections.EMPTY_LIST;
        }
    }

    @Override
    public int getResourceCount(final String collectionName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return getResourceCount(XmldbURI.xmldbUriFor(collectionName));
    }

    private int getResourceCount(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        return this.<Integer>readCollection(collUri).apply((collection, broker, transaction) -> collection.getDocumentCount(broker));
    }

    @Override
    public String createResourceId(final String collectionName)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return createResourceId(XmldbURI.xmldbUriFor(collectionName));
    }

    /**
     * Creates a unique name for a database resource Uniqueness is only
     * guaranteed within the eXist instance
     * <p>
     * The name is based on a hex encoded string of a random integer and will
     * have the format xxxxxxxx.xml where x is in the range 0 to 9 and a to f
     *
     * @return the unique resource name
     */
    private String createResourceId(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        return this.<String>readCollection(collUri).apply((collection, broker, transaction) -> {
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

                if (collection.hasChildCollection(broker, id)) {
                    ok = false;
                }

            } while (!ok);
            return id.toString();
        });
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
        return withDb((broker, transaction) -> {
            Collection collection = null;
            try {
                collection = broker.openCollection(uri, LockMode.READ_LOCK);
                final Permission perm;
                if (collection == null) {
                    DocumentImpl doc = null;
                    try {
                        doc = broker.getXMLResource(uri, LockMode.READ_LOCK);
                        if (doc == null) {
                            throw new EXistException("document or collection " + uri + " not found");
                        }
                        perm = doc.getPermissions();
                    } finally {
                        if (doc != null) {
                            doc.getUpdateLock().release(LockMode.READ_LOCK);
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
                    collection.release(LockMode.READ_LOCK);
                }
            }
        });
    }

    @Override
    public Map<String, Object> getSubCollectionPermissions(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {

        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        final Permission perm = this.<Permission>readCollection(uri).apply((collection, broker, transaction) -> collection.getChildCollectionEntry(broker, name).getPermissions());

        final Map<String, Object> result = new HashMap<>();
        result.put("owner", perm.getOwner().getName());
        result.put("group", perm.getGroup().getName());
        result.put("permissions", perm.getMode());

        if (perm instanceof ACLPermission) {
            result.put("acl", getACEs(perm));
        }
        return result;
    }

    @Override
    public Map<String, Object> getSubResourcePermissions(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        final Permission perm = this.<Permission>readCollection(uri).apply((collection, broker, transaction) -> collection.getResourceEntry(broker, name).getPermissions());

        final Map<String, Object> result = new HashMap<>();
        result.put("owner", perm.getOwner().getName());
        result.put("group", perm.getGroup().getName());
        result.put("permissions", perm.getMode());

        if (perm instanceof ACLPermission) {
            result.put("acl", getACEs(perm));
        }
        return result;
    }

    @Override
    public long getSubCollectionCreationTime(final String parentPath, final String name) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(parentPath);
        return this.<Long>readCollection(uri).apply((collection, broker, transaction) -> collection.getChildCollectionEntry(broker, name).getCreated());
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
        return this.<Map<String, List>>readCollection(collUri).apply((collection, broker, transaction) -> {
            final Map<String, List> result = new HashMap<>(collection.getDocumentCount(broker));
            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
                final Permission perm = doc.getPermissions();

                final List tmp = new ArrayList(4);
                tmp.add(perm.getOwner().getName());
                tmp.add(perm.getGroup().getName());
                tmp.add(perm.getMode());
                if (perm instanceof ACLPermission) {
                    tmp.add(getACEs(perm));
                }
                result.put(doc.getFileURI().toString(), tmp);
            }
            return result;
        });
    }

    @Override
    public Map<XmldbURI, List> listCollectionPermissions(final String name)
            throws EXistException, PermissionDeniedException, URISyntaxException {
        return listCollectionPermissions(XmldbURI.xmldbUriFor(name));
    }

    private Map<XmldbURI, List> listCollectionPermissions(final XmldbURI collUri)
            throws EXistException, PermissionDeniedException {
        return this.<Map<XmldbURI, List>>readCollection(collUri).apply((collection, broker, transaction) -> {
            final Map<XmldbURI, List> result = new HashMap<>(collection.getChildCollectionCount(broker));
            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
                final XmldbURI child = i.next();
                final XmldbURI path = collUri.append(child);
                final Collection childColl = broker.getCollection(path);
                final Permission perm = childColl.getPermissionsNoLock();
                final List tmp = new ArrayList(4);
                tmp.add(perm.getOwner().getName());
                tmp.add(perm.getGroup().getName());
                tmp.add(perm.getMode());
                if (perm instanceof ACLPermission) {
                    tmp.add(getACEs(perm));
                }
                result.put(child, tmp);
            }
            return result;
        });
    }

    @Override
    public Date getCreationDate(final String collectionPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getCreationDate(XmldbURI.xmldbUriFor(collectionPath));
    }

    private Date getCreationDate(final XmldbURI collUri) throws PermissionDeniedException, EXistException {
        return this.<Date>readCollection(collUri).apply((collection, broker, transaction) -> new Date(collection.getCreationTime()));
    }

    @Override
    public List<Date> getTimestamps(final String documentPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getTimestamps(XmldbURI.xmldbUriFor(documentPath));
    }

    private List<Date> getTimestamps(final XmldbURI docUri) throws PermissionDeniedException, EXistException {
        return this.<List<Date>>readDocument(docUri).apply((document, broker, transaction) -> {
            final DocumentMetadata metadata = document.getMetadata();
            final List<Date> list = new ArrayList<>(2);
            list.add(new Date(metadata.getCreated()));
            list.add(new Date(metadata.getLastModified()));
            return list;
        });
    }

    @Override
    public boolean setLastModified(final String documentPath, final long lastModified) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeDocument(XmldbURI.create(documentPath)).apply((document, broker, transaction) -> {
            //TODO : register the lock within the transaction ?
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + documentPath);
            }

            document.getMetadata().setLastModified(lastModified);
            broker.storeXMLResource(transaction, document);
            return true;
        });
    }

    @Override
    public Map<String, Object> getAccount(final String name) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> {
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
        });
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
        return withDb((broker, transaction) -> {
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
        });
    }

    @Override
    public void removeGroup(final String name) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> broker.getBrokerPool().getSecurityManager().deleteGroup(name));
    }

    @Override
    public boolean hasDocument(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return hasDocument(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean hasDocument(final XmldbURI docUri) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> broker.getXMLResource(docUri) != null);
    }

    @Override
    public boolean hasCollection(final String collectionName) throws EXistException, URISyntaxException, PermissionDeniedException {
        return hasCollection(XmldbURI.xmldbUriFor(collectionName));
    }

    private boolean hasCollection(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> broker.getCollection(collUri) != null);
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

        return this.<Boolean>writeCollection(docUri.removeLastSegment()).apply((collection, broker, transaction) -> {
            if (overwrite == 0) {
                final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                if (old != null) {
                    throw new PermissionDeniedException("Document exists and overwrite is not allowed");
                }
            }

            try (final InputStream is = new FastByteArrayInputStream(xml)) {

                final InputSource source = new InputSource(is);

                final long startTime = System.currentTimeMillis();

                final IndexInfo info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
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

                collection.store(transaction, broker, info, source);

                if(LOG.isDebugEnabled()) {
                    LOG.debug("parsing " + docUri + " took " + (System.currentTimeMillis() - startTime) + "ms.");
                }
                return true;
            }
        });
    }

    /**
     * Parse a file previously uploaded with upload.
     * <p>
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
                              final int overwrite, final String mimeType) throws EXistException, PermissionDeniedException, URISyntaxException {
        return parseLocal(localFile, documentPath, overwrite, mimeType, null, null);
    }

    /**
     * Parse a file previously uploaded with upload, forcing it to XML or
     * Binary.
     * <p>
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
                                 final int overwrite, final String mimeType, final int isXML) throws EXistException, PermissionDeniedException, URISyntaxException {
        return parseLocalExt(localFile, documentPath, overwrite, mimeType, isXML, null, null);
    }

    @SuppressWarnings("unused")
    private boolean parseLocal(final String localFile, final XmldbURI docUri,
                               final int overwrite, final String mimeType) throws EXistException, PermissionDeniedException {
        return parseLocal(localFile, docUri, overwrite, mimeType, null, null, null);
    }

    @SuppressWarnings("unused")
    private boolean parseLocalExt(final String localFile, final XmldbURI docUri,
                                  final int overwrite, final String mimeType, final int isXML) throws EXistException, PermissionDeniedException {
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
        return this.<Boolean>writeCollection(docUri.removeLastSegment()).apply((collection, broker, transaction) -> {

            if (overwrite == 0) {
                final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                if (old != null) {
                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
                }
            }


            // get the source for parsing
            SupplierE<FileInputSource, IOException> sourceSupplier;
            try {
                final int handle = Integer.parseInt(localFile);
                final SerializedResult sr = factory.resultSets.getSerializedResult(handle);
                if (sr == null) {
                    throw new EXistException("Invalid handle specified");
                }

                sourceSupplier = () -> {
                    final FileInputSource source = new FileInputSource(sr.result);
                    sr.result = null; // de-reference the temp file in the SerializeResult, so it is not re-claimed before we need it
                    factory.resultSets.remove(handle);
                    return source;
                };
            } catch (final NumberFormatException nfe) {

                // As this file can be a non-temporal one, we should not
                // blindly erase it!
                final Path path = Paths.get(localFile);
                if (!Files.isReadable(path)) {
                    throw new EXistException("unable to read file " + path.toAbsolutePath().toString());
                }

                sourceSupplier = () -> new FileInputSource(path);
            }

            // parse the source
            try (final FileInputSource source = sourceSupplier.get()) {
                final MimeType mime = Optional.ofNullable(MimeTable.getInstance().getContentType(mimeType)).orElse(MimeType.BINARY_TYPE);
                final boolean treatAsXML = (isXML != null && isXML) || (isXML == null && mime.isXMLType());

                if (treatAsXML) {
                    final IndexInfo info = collection.validateXMLResource(transaction, broker, docUri.lastSegment(), source);
                    if (created != null) {
                        info.getDocument().getMetadata().setCreated(created.getTime());
                    }
                    if (modified != null) {
                        info.getDocument().getMetadata().setLastModified(modified.getTime());
                    }
                    collection.store(transaction, broker, info, source);
                } else {
                    try (final InputStream is = source.getByteStream()) {
                        final DocumentImpl doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), is, mime.getName(), source.getByteStreamLength());
                        if (created != null) {
                            doc.getMetadata().setCreated(created.getTime());
                        }
                        if (modified != null) {
                            doc.getMetadata().setLastModified(modified.getTime());
                        }
                    }
                }

                return true;
            }
        });
    }

    public boolean storeBinary(final byte[] data, final String documentPath, final String mimeType,
                               final int overwrite) throws EXistException, PermissionDeniedException, URISyntaxException {
        return storeBinary(data, documentPath, mimeType, overwrite, null, null);
    }

    @SuppressWarnings("unused")
    private boolean storeBinary(final byte[] data, final XmldbURI docUri, final String mimeType,
                                final int overwrite) throws EXistException, PermissionDeniedException {
        return storeBinary(data, docUri, mimeType, overwrite, null, null);
    }

    public boolean storeBinary(final byte[] data, final String documentPath, final String mimeType,
                               final int overwrite, final Date created, final Date modified) throws URISyntaxException, EXistException, PermissionDeniedException {
        return storeBinary(data, XmldbURI.xmldbUriFor(documentPath), mimeType, overwrite, created, modified);
    }

    private boolean storeBinary(final byte[] data, final XmldbURI docUri, final String mimeType,
                                final int overwrite, final Date created, final Date modified) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeCollection(docUri.removeLastSegment()).apply((collection, broker, transaction) -> {
            // keep a write lock in the transaction
            transaction.acquireLock(collection.getLock(), LockMode.WRITE_LOCK);
            if (overwrite == 0) {
                final DocumentImpl old = collection.getDocument(broker, docUri.lastSegment());
                if (old != null) {
                    throw new PermissionDeniedException("Old document exists and overwrite is not allowed");
                }
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("Storing binary resource to collection " + collection.getURI());
            }

            final DocumentImpl doc = collection.addBinaryResource(transaction, broker, docUri.lastSegment(), data, mimeType);
            if (created != null) {
                doc.getMetadata().setCreated(created.getTime());
            }
            if (modified != null) {
                doc.getMetadata().setLastModified(modified.getTime());
            }

            return doc != null;
        });
    }

    public String upload(final byte[] chunk, final int length, @Nullable String fileName, final boolean compressed)
            throws EXistException, IOException {
        final OpenOption[] openOptions;
        final Path tempFile;
        if (fileName == null || fileName.length() == 0) {
            // no fileName, so new file
            openOptions = new OpenOption[] { CREATE, TRUNCATE_EXISTING, WRITE };

            // create temporary file
            tempFile = TemporaryFileManager.getInstance().getTemporaryFile();
            final int handle = factory.resultSets.add(new SerializedResult(tempFile));
            fileName = Integer.toString(handle);
        } else {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Appending to file " + fileName);
            }

            // fileName was specified so this is an append
            openOptions = new OpenOption[] { CREATE, APPEND, WRITE };

            try {
                final int handle = Integer.parseInt(fileName);
                final SerializedResult sr = factory.resultSets.getSerializedResult(handle);
                if (sr == null) {
                    throw new EXistException("Invalid handle specified");
                }
                // This will keep the serialized result in the cache
                sr.touch();
                tempFile = sr.result;
            } catch (final NumberFormatException nfe) {
                throw new EXistException("Syntactically invalid handle specified");
            }
        }

        try (final OutputStream os = Files.newOutputStream(tempFile, openOptions)) {
            if (compressed) {
                final int uncompressedLen = Compressor.uncompress(chunk, os);
                if (uncompressedLen != length) {
                    throw new IOException("Expected " + length + " bytes of uncompressed data, but actually " + uncompressedLen);
                }
            } else {
                os.write(chunk, 0, length);
            }
        }

        return fileName;
    }

    protected String printAll(final DBBroker broker, final Sequence resultSet, int howmany,
                              int start, final Map<String, Object> properties, final long queryTime) throws EXistException, SAXException, XPathException, IOException {
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

        final Properties serializationProps = toProperties(properties);

        Item item;
        for (int i = --start; i < start + howmany; i++) {
            item = resultSet.itemAt(i);
            if (item == null) {
                continue;
            }
            if (item.getType() == Type.ELEMENT) {
                final NodeValue node = (NodeValue) item;
                serialize(broker, serializationProps, saxSerializer -> saxSerializer.toSAX(node), writer);
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
        final Source source = new StringSource(query);

        return withDb((broker, transaction) -> {
            final Map<String, Object> ret = new HashMap<>();
            try {
                compileQuery(broker, transaction, source, parameters).apply(compiledQuery -> null);
            } catch (final XPathException e) {
                ret.put(RpcAPI.ERROR, e.getMessage());
                if (e.getLine() != 0) {
                    ret.put(RpcAPI.LINE, e.getLine());
                    ret.put(RpcAPI.COLUMN, e.getColumn());
                }
            }
            return ret;
        });
    }

    public String query(final String xpath, final int howmany, final int start,
                        final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {

        final Source source = new StringSource(xpath);

        return withDb((broker, transaction) -> {
            final long startTime = System.currentTimeMillis();

            try {
                final QueryResult qr = this.<QueryResult>compileQuery(broker, transaction, source, parameters).apply(compiled -> doQuery(broker, compiled, null, parameters));
                if (qr == null) {
                    return "<?xml version=\"1.0\"?>\n"
                            + "<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" "
                            + "hitCount=\"0\"/>";
                }
                if (qr.hasErrors()) {
                    throw qr.getException();
                }

                return printAll(broker, qr.result, howmany, start, parameters, (System.currentTimeMillis() - startTime));
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    /**
     * @deprecated Use {@link #queryPT(String, XmldbURI, String, Map)} instead.
     */
    public Map<String, Object> queryP(final String xpath, final String documentPath,
                                      final String s_id, final Map<String, Object> parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
        return queryP(xpath,
                (documentPath == null) ? null : XmldbURI.xmldbUriFor(documentPath),
                s_id, parameters);
    }

    /**
     * @deprecated Use {@link #queryPT(String, XmldbURI, String, Map)} instead.
     */
    private Map<String, Object> queryP(final String xpath, final XmldbURI docUri,
                                       final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {

        final Source source = new StringSource(xpath);
        final Optional<String> sortBy = Optional.ofNullable(parameters.get(RpcAPI.SORT_EXPR)).map(Object::toString);

        return withDb((broker, transaction) -> {
            final long startTime = System.currentTimeMillis();

            final NodeSet nodes;

            if (docUri != null && s_id != null) {
                nodes = this.<NodeSet>readDocument(broker, transaction, docUri).apply((document, broker1, transaction1) -> {

                    final Object[] docs = new Object[1];
                    docs[0] = docUri.toString();
                    parameters.put(RpcAPI.STATIC_DOCUMENTS, docs);

                    if (s_id.length() > 0) {
                        final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
                        final NodeProxy node = new NodeProxy(document, nodeId);
                        final NodeSet nodeSet = new ExtArrayNodeSet(1);
                        nodeSet.add(node);
                        return nodeSet;
                    } else {
                        return null;
                    }
                });
            } else {
                nodes = null;
            }

            try {
                final Map<String, Object> rpcResponse = this.<Map<String, Object>>compileQuery(broker, transaction, source, parameters)
                        .apply(compiledQuery -> queryResultToRpcResponse(startTime, doQuery(broker, compiledQuery, nodes, parameters), sortBy));
                return rpcResponse;
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    private Map<String, Object> queryResultToRpcResponse(final long startTime, final QueryResult queryResult, final Optional<String> sortBy) throws XPathException {
        final Map<String, Object> ret = new HashMap<>();
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

        Sequence resultSeq = queryResult.result;
        if (LOG.isDebugEnabled()) {
            LOG.debug("found " + resultSeq.getItemCount());
        }

        if (sortBy.isPresent()) {
            final SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user, sortBy.get());
            sorted.addAll(resultSeq);
            resultSeq = sorted;
        }

        final List<Object> result = new ArrayList<>();
        if (resultSeq != null) {
            final SequenceIterator i = resultSeq.iterate();
            if (i != null) {
                while (i.hasNext()) {
                    final Item next = i.nextItem();
                    if (Type.subTypeOf(next.getType(), Type.NODE)) {
                        final List<String> entry = new ArrayList<>();
                        if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                            final NodeProxy p = (NodeProxy) next;
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
                if(LOG.isDebugEnabled()) {
                    LOG.debug("sequence iterator is null. Should not");
                }
            }
        } else {
            if(LOG.isDebugEnabled()) {
                LOG.debug("result sequence is null. Skipping it...");
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
    public Map<String, Object> queryPT(final byte[] xquery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return queryPT(new String(xquery, DEFAULT_ENCODING), null, null, parameters);
    }

    @Override
    public Map<String, Object> queryPT(final byte[] xquery, @Nullable final String docName, @Nullable final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException, URISyntaxException {
        return queryPT(new String(xquery, DEFAULT_ENCODING), docName == null ? null : XmldbURI.create(docName), s_id, parameters);
    }

    private Map<String, Object> queryPT(final String xquery, final XmldbURI docUri,
                                        final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {

        final Source source = new StringSource(xquery);
        final Optional<String> sortBy = Optional.ofNullable(parameters.get(RpcAPI.SORT_EXPR)).map(Object::toString);

        return withDb((broker, transaction) -> {
            final long startTime = System.currentTimeMillis();

            final NodeSet nodes;

            if (docUri != null && s_id != null) {
                nodes = this.<NodeSet>readDocument(broker, transaction, docUri).apply((document, broker1, transaction1) -> {

                    final Object[] docs = new Object[1];
                    docs[0] = docUri.toString();
                    parameters.put(RpcAPI.STATIC_DOCUMENTS, docs);

                    if (s_id.length() > 0) {
                        final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
                        final NodeProxy node = new NodeProxy(document, nodeId);
                        final NodeSet nodeSet = new ExtArrayNodeSet(1);
                        nodeSet.add(node);
                        return nodeSet;
                    } else {
                        return null;
                    }
                });
            } else {
                nodes = null;
            }

            try {
                final Map<String, Object> rpcResponse = this.<Map<String, Object>>compileQuery(broker, transaction, source, parameters)
                        .apply(compiledQuery -> queryResultToTypedRpcResponse(startTime, doQuery(broker, compiledQuery, nodes, parameters), sortBy));
                return rpcResponse;
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    private Map<String, Object> queryResultToTypedRpcResponse(final long startTime, final QueryResult queryResult, final Optional<String> sortBy) throws XPathException {
        final Map<String, Object> ret = new HashMap<>();
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

        Sequence resultSeq = queryResult.result;
        if (LOG.isDebugEnabled()) {
            LOG.debug("found " + resultSeq.getItemCount());
        }

        if (sortBy.isPresent()) {
            final SortedNodeSet sorted = new SortedNodeSet(factory.getBrokerPool(), user, sortBy.get());
            sorted.addAll(resultSeq);
            resultSeq = sorted;
        }

        final List<Map<String, String>> result = new ArrayList<>();
        if (resultSeq != null) {
            final SequenceIterator i = resultSeq.iterate();
            if (i != null) {
                while (i.hasNext()) {
                    final Item next = i.nextItem();
                    final Map<String, String> entry;
                    if (Type.subTypeOf(next.getType(), Type.NODE)) {
                        entry = nodeMap(next);
                    } else {
                        entry = atomicMap(next);
                    }

                    if(entry != null) {
                        result.add(entry);
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("sequence iterator is null. Should not");
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("result sequence is null. Skipping it...");
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

    private @Nullable Map<String, String> nodeMap(final Item item) {
        final Map<String, String> result;

        if(item instanceof NodeValue &&
                ((NodeValue)item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
            final NodeProxy p = (NodeProxy) item;

            result = new HashMap<>();
            result.put("type", Type.getTypeName(p.getType()));
            result.put("docUri", p.getOwnerDocument().getURI().toString());
            result.put("nodeId", p.getNodeId().toString());

        } else if(item instanceof org.exist.dom.memtree.NodeImpl) {
            final NodeImpl ni = (NodeImpl)item;

            result = new HashMap<>();
            result.put("type", Type.getTypeName(ni.getType()));
            result.put("docUri", "temp_xquery/" + item.hashCode());
            result.put("nodeId", String.valueOf(ni.getNodeNumber()));
        } else {
            LOG.error("Omitting from results, unsure how to process: " + item.getClass());
            result = null;
        }

        return result;
    }

    private Map<String, String> atomicMap(final Item item) throws XPathException {
        final Map<String, String> result = new HashMap<>();

        final int type = item.getType();
        result.put("type", Type.getTypeName(type));
        result.put("value", item.getStringValue());

        return result;
    }

    @Deprecated
    @Override
    public Map<String, Object> execute(final String pathToQuery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();

        final Optional<String> sortBy = Optional.ofNullable(parameters.get(RpcAPI.SORT_EXPR)).map(Object::toString);

        return this.<Map<String, Object>>readDocument(XmldbURI.createInternal(pathToQuery)).apply((document, broker, transaction) -> {
            final BinaryDocument xquery = (BinaryDocument) document;
            if (xquery.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new EXistException("Document " + pathToQuery + " is not a binary resource");
            }

            if (!xquery.getPermissions().validate(user, Permission.READ | Permission.EXECUTE)) {
                throw new PermissionDeniedException("Insufficient privileges to access resource");
            }

            final Source source = new DBSource(broker, xquery, true);

            try {
                final Map<String, Object> rpcResponse = this.<Map<String, Object>>compileQuery(broker, transaction, source, parameters)
                        .apply(compiledQuery -> queryResultToRpcResponse(startTime, doQuery(broker, compiledQuery, null, parameters), sortBy));
                return rpcResponse;
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    @Override
    public Map<String, Object> executeT(final String pathToQuery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        final long startTime = System.currentTimeMillis();

        final Optional<String> sortBy = Optional.ofNullable(parameters.get(RpcAPI.SORT_EXPR)).map(Object::toString);

        return this.<Map<String, Object>>readDocument(XmldbURI.createInternal(pathToQuery)).apply((document, broker, transaction) -> {
            final BinaryDocument xquery = (BinaryDocument) document;
            if (xquery.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new EXistException("Document " + pathToQuery + " is not a binary resource");
            }

            if (!xquery.getPermissions().validate(user, Permission.READ | Permission.EXECUTE)) {
                throw new PermissionDeniedException("Insufficient privileges to access resource");
            }

            final Source source = new DBSource(broker, xquery, true);

            try {
                final Map<String, Object> rpcResponse = this.<Map<String, Object>>compileQuery(broker, transaction, source, parameters)
                        .apply(compiledQuery -> queryResultToTypedRpcResponse(startTime, doQuery(broker, compiledQuery, null, parameters), sortBy));
                return rpcResponse;
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
    }

    @Override
    public boolean releaseQueryResult(final int handle) {
        factory.resultSets.remove(handle);
        if(LOG.isDebugEnabled()) {
            LOG.debug("removed query result with handle " + handle);
        }
        return true;
    }

    @Override
    public boolean releaseQueryResult(final int handle, final int hash) {
        factory.resultSets.remove(handle, hash);
        if(LOG.isDebugEnabled()) {
            LOG.debug("removed query result with handle " + handle);
        }
        return true;
    }

    @Override
    public boolean remove(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return remove(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean remove(final XmldbURI docUri) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeCollection(docUri.removeLastSegment()).apply((collection, broker, transaction) -> {
            // keep a write lock in the transaction
            transaction.acquireLock(collection.getLock(), LockMode.WRITE_LOCK);

            final DocumentImpl doc = collection.getDocument(broker, docUri.lastSegment());
            if (doc == null) {
                throw new EXistException("Document " + docUri + " not found");
            }

            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                collection.removeBinaryResource(transaction, broker, doc);
            } else {
                collection.removeXMLResource(transaction, broker, docUri.lastSegment());
            }
            return true;
        });
    }

    @Override
    public boolean removeCollection(final String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
        return removeCollection(XmldbURI.xmldbUriFor(collectionName));
    }

    private boolean removeCollection(final XmldbURI collURI) throws EXistException, PermissionDeniedException {
        try {
            return this.<Boolean>writeCollection(collURI).apply((collection, broker, transaction) -> {
                // keep a write lock in the transaction
                transaction.acquireLock(collection.getLock(), LockMode.WRITE_LOCK);
                if(LOG.isDebugEnabled()) {
                    LOG.debug("removing collection " + collURI);
                }
                return broker.removeCollection(transaction, collection);
            });
        } catch (final EXistException e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug(e);
            }
            return false;
        }
    }

    @Override
    public boolean removeAccount(final String name) throws EXistException, PermissionDeniedException {
        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasAdminPrivileges(user)) {
            throw new PermissionDeniedException("you are not allowed to remove users");
        }

        withDb((broker, transaction) -> manager.deleteAccount(name));

        return true;
    }

    @Override
    public byte[] retrieve(final String doc, final String id, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        try {
            final String xml = retrieveAsString(doc, id, parameters);
            return xml.getBytes(getEncoding(parameters));
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    @Override
    public String retrieveAsString(final String documentPath, final String s_id,
                                   final Map<String, Object> parameters) throws URISyntaxException, EXistException, PermissionDeniedException {
        return retrieve(XmldbURI.xmldbUriFor(documentPath), s_id, parameters);
    }

    private String retrieve(final XmldbURI docUri, final String s_id,
                            final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return this.<String>readDocument(docUri).apply((document, broker, transaction) -> {
            final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(s_id);
            final NodeProxy node = new NodeProxy(document, nodeId);

            try (final StringWriter writer = new StringWriter()) {
                serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.serialize(node), writer);
                return writer.toString();
            }
        });
    }

    @Override
    public Map<String, Object> retrieveFirstChunk(final String docName, final String id, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        final boolean compression = useCompression(parameters);

        final XmldbURI docUri;
        try {
            docUri = XmldbURI.xmldbUriFor(docName);
        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }

        return this.<Map<String, Object>>readDocument(docUri).apply((document, broker, transaction) -> {
            final NodeId nodeId = factory.getBrokerPool().getNodeFactory().createFromString(id);
            final NodeProxy node = new NodeProxy(document, nodeId);

            final Map<String, Object> result = new HashMap<>();
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = temporaryFileManager.getTemporaryFile();

            if (compression && LOG.isDebugEnabled()) {
                LOG.debug("retrieveFirstChunk with compression");
            }

            try (final OutputStream os = compression ? new DeflaterOutputStream(Files.newOutputStream(tempFile)) : Files.newOutputStream(tempFile);
                    final Writer writer = new OutputStreamWriter(os, getEncoding(parameters))) {
                serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.toSAX(node), writer);
            }

            final byte[] firstChunk = getChunk(tempFile, 0);
            result.put("data", firstChunk);
            int offset = 0;
            if (Files.size(tempFile) > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(tempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                temporaryFileManager.returnTemporaryFile(tempFile);
            }
            result.put("offset", offset);
            return result;
        });
    }

    @Override
    public byte[] retrieve(final int resultId, final int num, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        final Charset encoding = getEncoding(parameters);
        final boolean compression = useCompression(parameters);

        final String xml = retrieveAsString(resultId, num, parameters);


        if (compression) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("retrieve with compression");
            }
            try {
                return Compressor.compress(xml.getBytes(encoding));
            } catch (final IOException ioe) {
                throw new EXistException(ioe);
            }
        } else {
            return xml.getBytes(encoding);
        }
    }

    private String retrieveAsString(final int resultId, final int num,
                                    final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> {
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
                for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                    parameters.put(entry.getKey().toString(), entry.getValue().toString());
                }
                try (final StringWriter writer = new StringWriter()) {
                  serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.toSAX(nodeValue), writer);
                  return writer.toString();
                }
            } else {
                try {
                    return item.getStringValue();
                } catch (final XPathException e) {
                    throw new EXistException(e);
                }
            }
        });
    }

    @Override
    public Map<String, Object> retrieveFirstChunk(final int resultId, final int num, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        final boolean compression = useCompression(parameters);


        return withDb((broker, transaction) -> {
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
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = temporaryFileManager.getTemporaryFile();

            if (compression && LOG.isDebugEnabled()) {
                LOG.debug("retrieveFirstChunk with compression");
            }

            try (final OutputStream os = compression ? new DeflaterOutputStream(Files.newOutputStream(tempFile)) : Files.newOutputStream(tempFile);
                    final Writer writer = new OutputStreamWriter(os, getEncoding(parameters))) {
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue nodeValue = (NodeValue) item;
                    for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                        parameters.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                    serialize(broker, toProperties(parameters), saxSerializer -> saxSerializer.toSAX(nodeValue), writer);
                } else {
                    writer.write(item.getStringValue());
                }
            } catch (final XPathException e) {
                throw new EXistException(e);
            }

            final byte[] firstChunk = getChunk(tempFile, 0);
            result.put("data", firstChunk);
            int offset = 0;
            if (Files.size(tempFile) > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(tempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                temporaryFileManager.returnTemporaryFile(tempFile);
            }
            result.put("offset", offset);
            return result;
        });
    }

    @Override
    public byte[] retrieveAll(final int resultId, final Map<String, Object> parameters) throws EXistException,
            PermissionDeniedException {
        final String xml = retrieveAllAsString(resultId, parameters);
        return xml.getBytes(getEncoding(parameters));
    }

    private String retrieveAllAsString(final int resultId, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> {
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out");
            }
            qr.touch();

            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            final StringWriter writer = new StringWriter();
            handler.setOutput(writer, toProperties(parameters));

//			serialize results
            handler.startDocument();
            handler.startPrefixMapping("exist", Namespaces.EXIST_NS);
            handler.startPrefixMapping("xs", Namespaces.SCHEMA_NS);
            final AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute("", "hitCount", "hitCount", "CDATA", Integer.toString(qr.result.getItemCount()));
            handler.startElement(Namespaces.EXIST_NS, "result", "exist:result", attribs);
            Item current;
            char[] value;
            try {
                for (final SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
                    current = i.nextItem();

                    if (Type.subTypeOf(current.getType(), Type.NODE)) {
                        current.toSAX(broker, handler, null);
                    } else {

                        final AttributesImpl typeAttr = new AttributesImpl();
                        typeAttr.addAttribute("", "type", "type", "CDATA", Type.getTypeName(current.getType()));
                        handler.startElement(Namespaces.EXIST_NS, "value", "exist:value", typeAttr);

                        value = current.toString().toCharArray();
                        handler.characters(value, 0, value.length);

                        handler.endElement(Namespaces.EXIST_NS, "value", "exist:value");
                    }
                }
            } catch (final XPathException e) {
                throw new EXistException(e);
            }
            handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
            handler.endPrefixMapping("xs");
            handler.endPrefixMapping("exist");
            handler.endDocument();
            SerializerPool.getInstance().returnObject(handler);
            return writer.toString();
        });
    }

    @Override
    public Map<String, Object> retrieveAllFirstChunk(final int resultId, final Map<String, Object> parameters)
            throws EXistException, PermissionDeniedException {
        final boolean compression = useCompression(parameters);
        return withDb((broker, transaction) -> {
            final QueryResult qr = factory.resultSets.getResult(resultId);
            if (qr == null) {
                throw new EXistException("result set unknown or timed out");
            }
            qr.touch();
            for (final Map.Entry<Object, Object> entry : qr.serialization.entrySet()) {
                parameters.put(entry.getKey().toString(), entry.getValue().toString());
            }
            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            final Map<String, Object> result = new HashMap<>();
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = temporaryFileManager.getTemporaryFile();

            if (compression && LOG.isDebugEnabled()) {
                LOG.debug("retrieveAllFirstChunk with compression");
            }

            try (final OutputStream os = compression ? new DeflaterOutputStream(Files.newOutputStream(tempFile)) : Files.newOutputStream(tempFile);
                 final Writer writer = new OutputStreamWriter(os, getEncoding(parameters))) {
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
                try {
                    for (final SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
                        current = i.nextItem();
                        if (Type.subTypeOf(current.getType(), Type.NODE)) {
                            ((NodeValue) current).toSAX(broker, handler, null);
                        } else {
                            value = current.toString().toCharArray();
                            handler.characters(value, 0, value.length);
                        }
                    }
                } catch (final XPathException e) {
                    throw new EXistException(e);
                }
                handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
                handler.endPrefixMapping("exist");
                handler.endDocument();
                SerializerPool.getInstance().returnObject(handler);
            }


            final byte[] firstChunk = getChunk(tempFile, 0);
            result.put("data", firstChunk);
            int offset = 0;
            if (Files.size(tempFile) > MAX_DOWNLOAD_CHUNK_SIZE) {
                offset = firstChunk.length;

                final int handle = factory.resultSets.add(new SerializedResult(tempFile));
                result.put("handle", Integer.toString(handle));
                result.put("supports-long-offset", Boolean.TRUE);
            } else {
                temporaryFileManager.returnTemporaryFile(tempFile);
            }
            result.put("offset", offset);
            return result;
        });
    }

    @Override
    public boolean chgrp(final String resource, final String ownerGroup) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> permission.setGroup(ownerGroup));
            return true;
        });
    }

    @Override
    public boolean chown(final String resource, final String owner) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> permission.setOwner(owner));
            return true;
        });
    }

    @Override
    public boolean chown(final String resource, final String owner, final String ownerGroup) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> {
                permission.setOwner(owner);
                permission.setGroup(ownerGroup);
            });
            return true;
        });
    }

    @Override
    public boolean setPermissions(final String resource, final int permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> permission.setMode(permissions));
            return true;
        });
    }

    @Override
    public boolean setPermissions(final String resource, final String permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> {
                try {
                    permission.setMode(permissions);
                } catch (final SyntaxException se) {
                    throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
                }
            });
            return true;
        });
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String ownerGroup, final String permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> {
                permission.setOwner(owner);
                permission.setGroup(ownerGroup);
                try {
                    permission.setMode(permissions);
                } catch (final SyntaxException se) {
                    throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
                }
            });
            return true;
        });
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String ownerGroup, final int permissions) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> {
                permission.setOwner(owner);
                permission.setGroup(ownerGroup);
                permission.setMode(permissions);
            });
            return true;
        });
    }

    @Override
    public boolean setPermissions(final String resource, final String owner, final String group, final int mode, final List<ACEAider> aces) throws EXistException, PermissionDeniedException, URISyntaxException {
        final XmldbURI uri = XmldbURI.xmldbUriFor(resource);
        return withDb((broker, transaction) -> {
            PermissionFactory.updatePermissions(broker, uri, permission -> {
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
            });
            return true;
        });
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
            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    u.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }
        }

        withDb((broker, transaction) -> manager.addAccount(u));

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
            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    account.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    account.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }
        }

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
        withDb((broker, transaction) -> manager.updateAccount(account));
        return true;
    }

    @Override
    public boolean addGroup(final String name, final Map<String, String> metadata) throws EXistException, PermissionDeniedException {

        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasGroup(name)) {

            if (!manager.hasAdminPrivileges(user)) {
                throw new PermissionDeniedException("Not allowed to create group");
            }

            final Group role = new GroupAider(name);

            for (final Map.Entry<String, String> m : metadata.entrySet()) {
                if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                    role.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                    role.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                }
            }

            withDb((broker, transaction) -> manager.addGroup(broker, role));

            return true;
        }

        return false;
    }

    @Override
    public boolean setUserPrimaryGroup(final String username, final String groupName) throws EXistException, PermissionDeniedException {
        final SecurityManager manager = factory.getBrokerPool().getSecurityManager();

        if (!manager.hasGroup(groupName)) {
            throw new EXistException("Group '" + groupName + "' does not exist!");
        }

        if (!manager.hasAdminPrivileges(user)) {
            throw new PermissionDeniedException("Not allowed to modify user");
        }


        withDb((broker, transaction) -> {
            final Account account = manager.getAccount(username);
            final Group group = manager.getGroup(groupName);
            account.setPrimaryGroup(group);
            manager.updateAccount(account);
            return null;
        });
        return true;
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
                for (final Map.Entry<String, String> m : metadata.entrySet()) {
                    if (AXSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(AXSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    } else if (EXistSchemaType.valueOfNamespace(m.getKey()) != null) {
                        group.setMetadataValue(EXistSchemaType.valueOfNamespace(m.getKey()), m.getValue());
                    }
                }
            }

            withDb((broker, transaction) -> manager.updateGroup(group));
            return true;

        } else {
            return false;
        }
    }

    @Override
    public List<String> getGroupMembers(final String groupName) throws EXistException, PermissionDeniedException {
        return withDb((broker, transaction) -> broker.getBrokerPool().getSecurityManager().findAllGroupMembers(groupName));
    }

    @Override
    public void addAccountToGroup(final String accountName, final String groupName) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Account account = sm.getAccount(accountName);
            account.addGroup(groupName);
            sm.updateAccount(account);
            return null;
        });
    }

    @Override
    public void addGroupManager(final String manager, final String groupName) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Account account = sm.getAccount(manager);
            final Group group = sm.getGroup(groupName);
            group.addManager(account);
            sm.updateGroup(group);

            return null;
        });
    }

    @Override
    public void removeGroupManager(final String groupName, final String manager) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
            final Group group = sm.getGroup(groupName);
            final Account account = sm.getAccount(manager);

            group.removeManager(account);
            sm.updateGroup(group);

            return null;
        });
    }

    @Override
    public void removeGroupMember(final String group, final String member) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            final SecurityManager sm = broker.getBrokerPool().getSecurityManager();

            final Account account = sm.getAccount(member);
            account.remGroup(group);
            sm.updateAccount(account);

            return null;
        });
    }

    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     * <p>
     * modified by Chris Tomlinson based on above updateAccount - it appears
     * that this code can rely on the SecurityManager to enforce policy about
     * whether user is or is not permitted to update the Account with name.
     * <p>
     * This is called via RemoteUserManagementService.addUserGroup(Account)
     *
     * @param name
     * @return
     * @throws org.exist.security.PermissionDeniedException
     */
    @Override
    public boolean updateAccount(final String name, final List<String> groups) throws EXistException, PermissionDeniedException {
        try {
            return withDb((broker, transaction) -> {
                final SecurityManager manager = broker.getBrokerPool().getSecurityManager();

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

            });
        } catch (final EXistException | PermissionDeniedException e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("addUserGroup encountered error", e);
            }
            return false;
        }
    }

    /**
     * Added by {Marco.Tampucci, Massimo.Martinelli} @isti.cnr.it
     * <p>
     * modified by Chris Tomlinson based on above updateAccount - it appears
     * that this code can rely on the SecurityManager to enforce policy about
     * whether user is or is not permitted to update the Account with name.
     * <p>
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

        try {
            return withDb((broker, transaction) -> {
                final SecurityManager manager = broker.getBrokerPool().getSecurityManager();

                final Account u = manager.getAccount(name);

                for (final String g : groups) {
                    if (g.equals(rgroup)) {
                        u.remGroup(g);
                    }
                }

                return manager.updateAccount(u);
            });

        } catch (final EXistException | PermissionDeniedException ex) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("removeGroup encountered error", ex);
            }
            return false;
        }
    }

    @Override
    public boolean lockResource(final String documentPath, final String userName) throws EXistException, PermissionDeniedException, URISyntaxException {
        return lockResource(XmldbURI.xmldbUriFor(documentPath), userName);
    }

    private boolean lockResource(final XmldbURI docURI, final String userName) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeDocument(docURI).apply((document, broker, transaction) -> {
            //TODO : register the lock within the transaction ?
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            }
            final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
            if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("User " + user.getName() + " is not allowed "
                        + "to lock the resource for user " + userName);
            }
            final Account lockOwner = document.getUserLock();
            if (lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("Resource is already locked by user "
                        + lockOwner.getName());
            }
            document.setUserLock(user);
            broker.storeXMLResource(transaction, document);
            return true;
        });
    }

    @Override
    public String hasUserLock(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return hasUserLock(XmldbURI.xmldbUriFor(documentPath));
    }

    private String hasUserLock(final XmldbURI docURI) throws EXistException, PermissionDeniedException {
        return this.<String>readDocument(docURI).apply((document, broker, transaction) -> {
            if (!document.getPermissions().validate(user, Permission.READ)) {
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            }
            final Account u = document.getUserLock();
            return u == null ? "" : u.getName();
        });
    }

    @Override
    public boolean unlockResource(final String documentPath) throws URISyntaxException, EXistException, PermissionDeniedException {
        return unlockResource(XmldbURI.xmldbUriFor(documentPath));
    }

    private boolean unlockResource(final XmldbURI docURI) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeDocument(docURI).apply((document, broker, transaction) -> {
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + docURI);
            }
            final SecurityManager manager = factory.getBrokerPool().getSecurityManager();
            final Account lockOwner = document.getUserLock();
            if (lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user))) {
                throw new PermissionDeniedException("Resource is already locked by user "
                        + lockOwner.getName());
            }
            document.setUserLock(null);
            broker.storeXMLResource(transaction, document);
            return true;
        });
    }

    public Map<String, Object> summary(final String xpath) throws EXistException, PermissionDeniedException {
        final Source source = new StringSource(xpath);

        return this.<Map<String, Object>>withDb((broker, transaction) -> {
            final long startTime = System.currentTimeMillis();

            final Map<String, Object> parameters = new HashMap<>();
            try {
                final QueryResult qr = this.<QueryResult>compileQuery(broker, transaction, source, parameters).apply(compiledQuery -> doQuery(broker, compiledQuery, null, parameters));
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
                for (final SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
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
                                doctypeCounter = doctypes.get(doctype.getName());
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
                    final List<Object> hitsByDoc = new ArrayList<>();
                    hitsByDoc.add(nodeCounter.doc.getFileURI().toString());
                    hitsByDoc.add(nodeCounter.doc.getDocId());
                    hitsByDoc.add(nodeCounter.count);
                    documents.add(hitsByDoc);
                }
                result.put("documents", documents);

                final List<List> dtypes = new ArrayList<>();
                for (final DoctypeCount docTemp : doctypes.values()) {
                    final List<Object> hitsByType = new ArrayList<>();
                    hitsByType.add(docTemp.doctype.getName());
                    hitsByType.add(docTemp.count);
                    dtypes.add(hitsByType);
                }
                result.put("doctypes", dtypes);
                return result;

            } catch (final XPathException e) {
                throw new EXistException(e);
            }
        });
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
        final Map<String, NodeCount> map = new HashMap<>();
        final Map<String, DoctypeCount> doctypes = new HashMap<>();
        NodeProxy p;
        String docName;
        DocumentType doctype;
        NodeCount counter;
        DoctypeCount doctypeCounter;
        for (final SequenceIterator i = qr.result.iterate(); i.hasNext(); ) {
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
            final List<Object> hitsByDoc = new ArrayList<>();
            hitsByDoc.add(nodeCounter.doc.getFileURI().toString());
            hitsByDoc.add(nodeCounter.doc.getDocId());
            hitsByDoc.add(nodeCounter.count);
            documents.add(hitsByDoc);
        }
        result.put("documents", documents);

        final List<List> dtypes = new ArrayList<>();
        for (final DoctypeCount docTemp : doctypes.values()) {
            final List<Object> hitsByType = new ArrayList<>();
            hitsByType.add(docTemp.doctype.getName());
            hitsByType.add(docTemp.count);
            dtypes.add(hitsByType);
        }
        result.put("doctypes", dtypes);
        return result;
    }

    @Override
    public List<List> getIndexedElements(final String collectionName,
                                         final boolean inclusive) throws EXistException, PermissionDeniedException, URISyntaxException {
        return getIndexedElements(XmldbURI.xmldbUriFor(collectionName), inclusive);
    }

    private List<List> getIndexedElements(final XmldbURI collUri,
                                          final boolean inclusive) throws EXistException, PermissionDeniedException {
        return this.<List<List>>readCollection(collUri).apply((collection, broker, transaction) -> {
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
        });
    }

    public void synchronize() {
    }

    private Properties toProperties(final Map<String, Object> parameters) {
        final Properties properties = new Properties();
        properties.putAll(parameters);
        return properties;
    }

    static class CachedQuery {

        final PathExpr expression;
        final String queryString;
        final long timestamp;

        public CachedQuery(final PathExpr expr, final String query) {
            this.expression = expr;
            this.queryString = query;
            this.timestamp = System.currentTimeMillis();
        }
    }

    static class DoctypeCount {

        int count = 1;
        final DocumentType doctype;

        public DoctypeCount(final DocumentType doctype) {
            this.doctype = doctype;
        }

        public void inc() {
            count++;
        }
    }

    static class NodeCount {

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
        final Path file = Paths.get(System.getProperty("java.io.tmpdir")).resolve(name);
        if (!Files.isReadable(file)) {
            throw new EXistException("unable to read file " + name);
        }
        if (FileUtils.sizeQuietly(file) < start + len) {
            throw new EXistException("address too big " + name);
        }
        final byte buffer[] = new byte[len];
        try (final RandomAccessFile os = new RandomAccessFile(file.toFile(), "r")) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Read from: " + start + " to: " + (start + len));
            }
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

        // use WRITE_LOCK if moving or if src and dest collection are the same
        final LockMode srcCollectionMode = move
                || docUri.removeLastSegment().equals(destUri) ? LockMode.WRITE_LOCK : LockMode.READ_LOCK;

        return withDb((broker, transaction) ->
                this.<Boolean>withCollection(srcCollectionMode, broker, transaction, docUri.removeLastSegment()).apply((source, broker1, transaction1) ->
                        this.<Boolean>writeDocument(broker1, transaction1, source, docUri).apply((document, broker2, transaction2) ->
                                this.<Boolean>writeCollection(broker2, transaction2, destUri).apply((destination, broker3, transaction3) -> {
                                    if (move) {
                                        broker3.moveResource(transaction3, document, destination, newName);
                                    } else {
                                        broker3.copyResource(transaction3, document, destination, newName);
                                    }
                                    return true;
                                })
                        )

                )
        );
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

        // use WRITE_LOCK if moving or if src and dest collection are the same
        final LockMode srcCollectionMode = move
                || collUri.equals(destUri) ? LockMode.WRITE_LOCK : LockMode.READ_LOCK;

        return withDb((broker, transaction) ->
                this.<Boolean>withCollection(srcCollectionMode, broker, transaction, collUri).apply((source, broker1, transaction1) ->
                        this.<Boolean>writeCollection(broker1, transaction1, destUri).apply((destination, broker2, transaction2) -> {
                            if (move) {
                                broker2.moveCollection(transaction2, source, destination, newName);
                            } else {
                                broker2.copyCollection(transaction2, source, destination, newName);
                            }
                            return true;
                        })
                )
        );
    }

    @Override
    public boolean reindexCollection(final String collectionName) throws URISyntaxException, EXistException, PermissionDeniedException {
        reindexCollection(XmldbURI.xmldbUriFor(collectionName));
        return true;
    }

    private void reindexCollection(final XmldbURI collUri) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            broker.reindexCollection(collUri);
            if(LOG.isDebugEnabled()) {
                LOG.debug("collection " + collUri + " and sub-collections reindexed");
            }
            return null;
        });
    }

    @Override
    public boolean reindexDocument(final String docUri) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            DocumentImpl doc = null;
            try {
                doc = broker.getXMLResource(XmldbURI.create(docUri), LockMode.READ_LOCK);
                broker.reindexXMLResource(transaction, doc, DBBroker.IndexMode.STORE);
                if(LOG.isDebugEnabled()) {
                    LOG.debug("document " + docUri + " reindexed");
                }
                return null;
            } finally {
                if (doc != null) {
                    doc.getUpdateLock().release(LockMode.READ_LOCK);
                }
            }
        });
        return true;
    }

    @Override
    public boolean backup(final String userbackup, final String password,
                          final String destcollection, final String collection) throws EXistException, PermissionDeniedException {
        try {
            final Backup backup = new Backup(
                    userbackup,
                    password,
                    Paths.get(destcollection + "-backup"),
                    XmldbURI.xmldbUriFor(XmldbURI.EMBEDDED_SERVER_URI.toString() + collection));
            backup.backup(false, null);

        } catch (final URISyntaxException | IOException | SAXException | XMLDBException e) {
            throw new EXistException(e);
        }
        return true;
    }

    /**
     * Validate if specified document is Valid.
     *
     * @param documentPath Path to XML document in database
     * @return TRUE if document is valid, FALSE if not or errors or.....
     * @throws java.net.URISyntaxException
     * @throws PermissionDeniedException   User is not allowed to perform action.
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
        } catch (final IOException e) {
            throw new EXistException(e);
        }
    }

    @Override
    public List<String> getDocType(final String documentPath)
            throws PermissionDeniedException, EXistException, URISyntaxException {
        return getDocType(XmldbURI.xmldbUriFor(documentPath));
    }

    private List<String> getDocType(final XmldbURI docUri)
            throws PermissionDeniedException, EXistException {
        return this.<List<String>>readDocument(docUri).apply((document, broker, transaction) -> {
            final List<String> list = new ArrayList<>(3);

            if (document.getDoctype() != null) {
                list.add(document.getDoctype().getName());

                if (document.getDoctype().getPublicId() != null) {
                    list.add(document.getDoctype().getPublicId());
                } else {
                    list.add("");
                }

                if (document.getDoctype().getSystemId() != null) {
                    list.add(document.getDoctype().getSystemId());
                } else {
                    list.add("");
                }
            } else {
                list.add("");
                list.add("");
                list.add("");
            }
            return list;
        });
    }

    @Override
    public boolean setDocType(final String documentPath, final String doctypename, final String publicid, final String systemid) throws
            URISyntaxException, EXistException, PermissionDeniedException {
        return setDocType(XmldbURI.xmldbUriFor(documentPath), doctypename, publicid, systemid);
    }

    private boolean setDocType(final XmldbURI docUri, final String doctypename, final String publicid, final String systemid) throws EXistException, PermissionDeniedException {
        return this.<Boolean>writeDocument(docUri).apply((document, broker, transaction) -> {
            //TODO : register the lock within the transaction ?
            if (!document.getPermissions().validate(user, Permission.WRITE)) {
                throw new PermissionDeniedException("User is not allowed to lock resource " + docUri);
            }

            DocumentType result = null;
            if (!"".equals(doctypename)) {
                result = new DocumentTypeImpl(doctypename,
                        "".equals(publicid) ? null : publicid,
                        "".equals(systemid) ? null : systemid);
            }

            document.setDocumentType(result);
            broker.storeXMLResource(transaction, document);
            return true;
        });
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
        final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
        final Path file = temporaryFileManager.getTemporaryFile();
        try (final OutputStream os = Files.newOutputStream(file)) {
            os.write(getDocument(name, parameters));
        }
        result.add(FileUtils.fileName(file));
        result.add(Long.toString(Files.size(file)));
        return result;
    }

    @Override
    public boolean copyCollection(final String name, final String namedest) throws PermissionDeniedException, EXistException {
        try {
            createCollection(namedest);

            final Map<String, Object> parameters = new HashMap<>();
            parameters.put(OutputKeys.INDENT, "no");
            parameters.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
            parameters.put(OutputKeys.ENCODING, DEFAULT_ENCODING);

            final Map<String, Object> desc = getCollectionDesc(name);
            final Object[] collections = (Object[]) desc.get("collections");
            final Object[] documents = (Object[]) desc.get("documents");

            //recurse the collection
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

                final byte[] xml = getDocument(name + "/" + nome, parameters);
                parse(xml, namedest + "/" + nome);
            }

            return true;

        } catch (final URISyntaxException e) {
            throw new EXistException(e);
        }
    }

    @Override
    public int xupdateResource(final String resource, final byte[] xupdate) throws PermissionDeniedException, EXistException, SAXException {
        return xupdateResource(resource, xupdate, DEFAULT_ENCODING.name());
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
        return parse(xml.getBytes(DEFAULT_ENCODING), docName, 0);
    }

    @Override
    public boolean parse(final String xml, final String docName, final int overwrite) throws EXistException, PermissionDeniedException, URISyntaxException {
        return parse(xml.getBytes(DEFAULT_ENCODING), docName, overwrite);
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
        final String result = query(new String(xquery, DEFAULT_ENCODING), howmany, start, parameters);
        return result.getBytes(getEncoding(parameters));
    }

    @Override
    @Deprecated
    public Map<String, Object> queryP(final byte[] xpath, final String docName, final String s_id, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException, URISyntaxException {
        return queryP(new String(xpath, DEFAULT_ENCODING), docName, s_id, parameters);
    }

    @Override
    @Deprecated
    public Map<String, Object> queryP(final byte[] xpath, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return queryP(new String(xpath, DEFAULT_ENCODING), (XmldbURI) null, null, parameters);
    }

    @Override
    public Map<String, Object> compile(final byte[] xquery, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return compile(new String(xquery, DEFAULT_ENCODING), parameters);
    }

    @Override
    public byte[] retrieve(final String doc, final String id) throws EXistException, PermissionDeniedException {
        return retrieve(doc, id, null);
    }

    @Override
    public String getDocumentAsString(final String name, final int prettyPrint, final String stylesheet) throws EXistException, PermissionDeniedException {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(OutputKeys.INDENT, prettyPrint > 0 ? "yes" : "no");
        if (stylesheet != null) {
            parameters.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }

        return getDocumentAsString(name, parameters);
    }

    @Override
    public String getDocumentAsString(final String name, final int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocumentAsString(name, prettyPrint, null);
    }

    @Override
    public byte[] getDocument(final String name, final String encoding, final int prettyPrint, final String stylesheet) throws EXistException, PermissionDeniedException {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(OutputKeys.INDENT, prettyPrint > 0 ? "yes" : "no");
        if (stylesheet != null) {
            parameters.put(EXistOutputKeys.STYLESHEET, stylesheet);
        }
        parameters.put(OutputKeys.ENCODING, encoding);

        return getDocument(name, parameters);
    }

    @Override
    public byte[] getDocument(final String name, final String encoding, final int prettyPrint) throws EXistException, PermissionDeniedException {
        return getDocument(name, encoding, prettyPrint, null);
    }

    @Override
    public boolean setTriggersEnabled(final String path, final String value) throws EXistException, PermissionDeniedException {
        final boolean triggersEnabled = Boolean.parseBoolean(value);
        return withDb((broker, transaction) -> {
            final Collection collection = broker.getCollection(XmldbURI.create(path));
            if (collection == null) {
                return false;
            }
            collection.setTriggersEnabled(triggersEnabled);
            return true;
        });
    }

    @Override
    public boolean shutdown() throws PermissionDeniedException {
        factory.getBrokerPool().shutdown();
        return true;
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

        final SystemTaskJob shutdownJob = new SystemTaskJobImpl("rpc-api.shutdown", new ShutdownTask());
        return factory.getBrokerPool().getScheduler().createPeriodicJob(0, shutdownJob, delay, new Properties(), 0);
    }

    @Override
    public boolean enterServiceMode() throws PermissionDeniedException {
        final BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.enterServiceMode(user);
        return true;
    }

    @Override
    public void exitServiceMode() throws PermissionDeniedException {
        final BrokerPool brokerPool = factory.getBrokerPool();
        brokerPool.exitServiceMode(user);
    }

    @Override
    public void runCommand(final XmldbURI collectionURI, final List<String> params) throws EXistException, PermissionDeniedException {
        withDb((broker, transaction) -> {
            org.exist.plugin.command.Commands.command(collectionURI, params.toArray(new String[params.size()]));
            return null;
        });
    }

    /**
     * Gets the encoding parameter or returns the default encoding
     */
    private Charset getEncoding(final Map<String, Object> parameters) {
        return Optional.ofNullable(parameters.get(OutputKeys.ENCODING)).map(p -> Charset.forName(p.toString())).orElse(DEFAULT_ENCODING);
    }

    /**
     * Determines if compression is switched on in the parameters
     */
    private boolean useCompression(final Map<String, Object> parameters) {
        return Optional.ofNullable(parameters.get(EXistOutputKeys.COMPRESS_OUTPUT)).map(c -> c.toString().equalsIgnoreCase("yes")).orElse(false);
    }

    /**
     * Takes a query from the pool or compiles a new one
     */
    private <R> Function3E<XmlRpcCompiledXQueryFunction<R>, R, EXistException, PermissionDeniedException, XPathException> compileQuery(final DBBroker broker, final Txn transaction, final Source source, final Map<String, Object> parameters) throws EXistException, PermissionDeniedException {
        return compiledOp -> {
            final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
            CompiledXQuery compiled = null;
            try {
                compiled = compile(broker, source, parameters);
                return compiledOp.apply(compiled);
            } catch (final IOException e) {
                throw new EXistException(e);
            } finally {
                if (compiled != null) {
                    compiled.getContext().runCleanupTasks();
                    pool.returnCompiledXQuery(source, compiled);
                }
            }
        };
    }

    /**
     * Higher-order function for performing read locked operations on a collection
     *
     * @param uri The full XmldbURI of the collection
     * @return A function to receive an operation to perform on the locked database collection
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcCollectionFunction<R>, R, EXistException, PermissionDeniedException> readCollection(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return readOp -> withDb((broker, transaction) -> this.<R>readCollection(broker, transaction, uri).apply(readOp));
    }

    /**
     * Higher-order function for performing read locked operations on a collection
     *
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the collection
     * @return A function to receive an operation to perform on the locked database collection
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcCollectionFunction<R>, R, EXistException, PermissionDeniedException> readCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withCollection(LockMode.READ_LOCK, broker, transaction, uri);
    }

    /**
     * Higher-order function for performing write locked operations on a collection
     *
     * @param uri The full XmldbURI of the collection
     * @return A function to receive an operation to perform on the locked database collection
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcCollectionFunction<R>, R, EXistException, PermissionDeniedException> writeCollection(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return writeOp -> withDb((broker, transaction) -> this.<R>writeCollection(broker, transaction, uri).apply(writeOp));
    }

    /**
     * Higher-order function for performing write locked operations on a collection
     *
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the collection
     * @return A function to receive an operation to perform on the locked database collection
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcCollectionFunction<R>, R, EXistException, PermissionDeniedException> writeCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withCollection(LockMode.WRITE_LOCK, broker, transaction, uri);
    }

    /**
     * Higher-order function for performing lockable operations on a collection
     *
     * @param lockMode
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the collection
     * @return A function to receive an operation to perform on the locked database collection
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcCollectionFunction<R>, R, EXistException, PermissionDeniedException> withCollection(final LockMode lockMode, final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return readOp -> {
            Collection collection = null;
            try {
                collection = broker.openCollection(uri, lockMode);
                if (collection == null) {
                    final String msg = "collection " + uri + " not found!";
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(msg);
                    }
                    throw new EXistException(msg);
                }
                return readOp.apply(collection, broker, transaction);
            } finally {
                if (collection != null) {
                    collection.release(lockMode);
                }
            }
        };
    }

    /**
     * Higher-order function for performing read locked operations on a document
     *
     * @param uri The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> readDocument(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return readOp -> withDb((broker, transaction) -> this.<R>readDocument(broker, transaction, uri).apply(readOp));
    }

    /**
     * Higher-order function for performing read locked operations on a document
     *
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> readDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withDocument(LockMode.READ_LOCK, broker, transaction, uri);
    }

    /**
     * Higher-order function for performing write locked operations on a document
     *
     * @param uri The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> writeDocument(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return writeOp -> withDb((broker, transaction) -> this.<R>writeDocument(broker, transaction, uri).apply(writeOp));
    }

    /**
     * Higher-order function for performing write locked operations on a document
     *
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> writeDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withDocument(LockMode.WRITE_LOCK, broker, transaction, uri);
    }

    /**
     * Higher-order function for performing write locked operations on a document
     *
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param collection  The collection in which the document resides
     * @param uri         The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> writeDocument(final DBBroker broker, final Txn transaction, final Collection collection, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withDocument(LockMode.WRITE_LOCK, broker, transaction, collection, uri);
    }

    //TODO(AR) consider interleaving the collection and document access, i.e. we could be finished with (and release the lock on) the collection once we have access to a handle to the document

    /**
     * Higher-order function for performing lockable operations on a document
     *
     * @param lockMode
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param uri         The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> withDocument(final LockMode lockMode, final DBBroker broker, final Txn transaction, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return withOp -> this.<R>readCollection(broker, transaction, uri.removeLastSegment()).apply((collection, broker1, transaction1) -> this.<R>withDocument(lockMode, broker1, transaction1, collection, uri).apply(withOp));
    }

    /**
     * Higher-order function for performing lockable operations on a document
     *
     * @param lockMode
     * @param broker      The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param collection  The collection in which the document resides
     * @param uri         The full XmldbURI of the document
     * @return A function to receive an operation to perform on the locked database document
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> Function2E<XmlRpcDocumentFunction<R>, R, EXistException, PermissionDeniedException> withDocument(final LockMode lockMode, final DBBroker broker, final Txn transaction, final Collection collection, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        return readOp -> {
            DocumentImpl document = null;
            try {
                document = collection.getDocumentWithLock(broker, uri.lastSegment(), lockMode);
                if (document == null) {
                    final String msg = "document " + uri + " not found!";
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(msg);
                    }
                    throw new EXistException(msg);
                }
                return readOp.apply(document, broker, transaction);
            } catch (final LockException e) {
                throw new EXistException(e);
            } finally {
                if (document != null) {
                    collection.releaseDocument(document, lockMode);
                }
            }
        };
    }

    /**
     * Higher-order-function for performing an XMLDB operation on
     * the database as the SYSTEM_USER.
     *
     * @param dbOperation The operation to perform on the database
     * @param <R>         The return type of the operation
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> R withDbAsSystem(final XmlRpcFunction<R> dbOperation) throws EXistException, PermissionDeniedException {
        return withDb(factory.getBrokerPool().getSecurityManager().getSystemSubject(), dbOperation);
    }

    /**
     * Higher-order-function for performing an XMLDB operation on
     * the database.
     * <p>
     * Performs the operation as the current user of the RpcConnection
     *
     * @param dbOperation The operation to perform on the database
     * @param <R>         The return type of the operation
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> R withDb(final XmlRpcFunction<R> dbOperation) throws EXistException, PermissionDeniedException {
        return withDb(user, dbOperation);
    }

    /**
     * Higher-order-function for performing an XMLDB operation on
     * the database
     *
     * @param user        The user to execute the operation as
     * @param dbOperation The operation to perform on the database
     * @param <R>         The return type of the operation
     * @throws org.exist.EXistException
     * @throws org.exist.security.PermissionDeniedException
     */
    private <R> R withDb(final Subject user, final XmlRpcFunction<R> dbOperation) throws EXistException, PermissionDeniedException {
        try (final DBBroker broker = factory.getBrokerPool().get(Optional.of(user));
             final Txn transaction = factory.getBrokerPool().getTransactionManager().beginTransaction()) {
            final R result = dbOperation.apply(broker, transaction);
            transaction.commit();
            return result;
        }
    }
}
