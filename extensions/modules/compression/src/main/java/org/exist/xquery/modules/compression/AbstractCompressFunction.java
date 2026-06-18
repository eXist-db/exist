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
package org.exist.xquery.modules.compression;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FunSerialize;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;

/**
 * Compresses a sequence of resources and/or collections
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:ljo@exist-db.org">Leif-Jöran Olsson</a>
 * @version 1.0
 */
public abstract class AbstractCompressFunction extends BasicFunction {
    protected final static SequenceType SOURCES_PARAM = new FunctionParameterSequenceType("sources", Type.ANY_TYPE, Cardinality.ONE_OR_MORE,
            "The sequence of URI's and/or Entrys. If an URI points to a collection then the collection, its resources and sub-collections are zipped recursively. " +
                    "If URI points to file (available only to the DBA role.) then file or directory are zipped. " +
                    "An Entry takes the format <entry name=\"filename.ext\" type=\"collection|uri|binary|xml|text\" method=\"deflate|store\">data</entry>. The method attribute is only effective for the compression:zip function.");
    protected final static SequenceType COLLECTION_HIERARCHY_PARAM = new FunctionParameterSequenceType("use-collection-hierarchy", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Indicates whether the Collection hierarchy (if any) should be preserved in the zip file.");
    protected final static SequenceType STRIP_PREFIX_PARAM = new FunctionParameterSequenceType("strip-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "This prefix is stripped from the Entrys name");
    protected final static SequenceType ENCODING_PARAM = new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "This encoding to be used for filenames inside the compressed file");
    protected final static SequenceType SERIALIZATION_OPTIONS_PARAM = new FunctionParameterSequenceType("serialization-options", Type.ITEM, Cardinality.ZERO_OR_ONE,
            "Serialization options applied to XML resources written to the archive, as either an " +
                    "output:serialization-parameters element or a map(*) (e.g. map { \"indent\": false() }). " +
                    "Standard W3C parameters use their string key; eXist extension parameters use their exist-namespace QName key. " +
                    "Takes precedence over any prolog declare option exist:serialize.");
    private final static Logger logger = LogManager.getLogger(AbstractCompressFunction.class);
    public static final String METHOD_STORE = "store";

    /** Serialization options from the optional map argument; reset on each eval and applied to XML
     *  entries after (so overriding) the prolog {@code declare option exist:serialize}. */
    private Properties serializationProperties = new Properties();

    public AbstractCompressFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    private String removeLeadingOffset(String uri, final String stripOffset) {
        // remove leading offset
        if (uri.startsWith(stripOffset)) {
            uri = uri.substring(stripOffset.length());
        }
        // remove leading /
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        return uri;
    }

    /**
     * Parses the optional serialization-options map argument (the 5th argument, if present) into the
     * serialization properties applied to XML entries written to the archive.
     *
     * @param args the function arguments
     * @return the supplied serialization properties, or an empty {@link Properties} when no map was given
     */
    private Properties parseSerializationOptions(final Sequence[] args) throws XPathException {
        if (args.length >= 5 && !args[4].isEmpty()) {
            return FunSerialize.getSerializationProperties(this, args[4].itemAt(0));
        }
        return new Properties();
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        // are there some uri's to tar?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // serialization options for XML entries (optional map argument); reset on each call
        serializationProperties = parseSerializationOptions(args);

        // use a hierarchy in the tar file?
        final boolean useHierarchy = args[1].effectiveBooleanValue();

        // Get offset
        String stripOffset = "";
        if (args.length == 3) {
            stripOffset = args[2].getStringValue();
        }

        // Get encoding
        try {
            final Charset encoding;
            if ((args.length >= 4) && !args[3].isEmpty()) {
                encoding = Charset.forName(args[3].getStringValue());
            } else {
                encoding = StandardCharsets.UTF_8;
            }

            try (final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().get();
                 final OutputStream os = stream(baos, encoding)) {

                // iterate through the argument sequence
                for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();

                    if (item instanceof final Element element) {
                        compressElement(os, element, useHierarchy, stripOffset);
                    } else {
                        compressFromUri(os, ((AnyURIValue) item).toURI(), useHierarchy, stripOffset, "", null);
                    }
                }

                os.flush();

                if (os instanceof final DeflaterOutputStream dos) {
                    dos.finish();
                }

                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), UnsynchronizedByteArrayInputStream.builder().setByteArray(baos.toByteArray()).get(), this);
            }
        } catch (final UnsupportedCharsetException | IOException e) {
            throw new XPathException(this, e.getMessage(), e);
        }
    }

    private void compressFromUri(final OutputStream os, final URI uri, final boolean useHierarchy, final String stripOffset, final String method, final String resourceName) throws XPathException {
        try {
            if ("file".equals(uri.getScheme())) {

                if (!context.getSubject().hasDbaRole()) {
                    final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
                    logger.error("Invalid user", xPathException);
                    throw xPathException;
                }

                // got a file
                final Path file = Path.of(uri.getPath());
                compressFile(os, file, useHierarchy, stripOffset, method, resourceName);

            } else {

                final XmldbURI xmldburi = XmldbURI.create(uri);

                // try for a collection
                try (final Collection collection = context.getBroker().openCollection(xmldburi, LockMode.READ_LOCK)) {
                    if (collection != null) {
                        compressCollection(os, collection, useHierarchy, stripOffset);
                        return;
                    }
                } catch (final PermissionDeniedException | LockException | SAXException | IOException pde) {
                    throw new XPathException(this, pde.getMessage());
                }


                // otherwise, try for a doc
                try (final Collection collection = context.getBroker().openCollection(xmldburi.removeLastSegment(), LockMode.READ_LOCK)) {
                    if (collection == null) {
                        throw new XPathException(this, "Invalid URI: " + uri);
                    }

                    try (final LockedDocument doc = collection.getDocumentWithLock(context.getBroker(), xmldburi.lastSegment(), LockMode.READ_LOCK)) {

                        // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                        collection.close();

                        if (doc == null) {
                            throw new XPathException(this, "Invalid URI: " + uri);
                        }

                        compressResource(os, doc.getDocument(), useHierarchy, stripOffset, method, resourceName);
                    }
                } catch (final PermissionDeniedException | LockException | SAXException | IOException pde) {
                    throw new XPathException(this, pde.getMessage());
                }
            }

        } catch (final IOException e) {
            throw new XPathException(this, e.getMessage());
        }

    }

    /**
     * Adds a element to a archive
     *
     * @param os           The Output Stream to add the element to
     * @param file         The file to add to the archive
     * @param useHierarchy Whether to use a folder hierarchy in the archive file that
     *                     reflects the collection hierarchy
     */
    private void compressFile(final OutputStream os, final Path file, final boolean useHierarchy, final String stripOffset, final String method, final String name) throws IOException {

        if (!Files.isDirectory(file)) {

            // create an entry in the Tar for the document
            final Object entry;
            if (name != null) {
                entry = newEntry(name);
            } else if (useHierarchy) {
                entry = newEntry(removeLeadingOffset(file.toAbsolutePath().toString(), stripOffset));
            } else {
                entry = newEntry(file.getFileName().toString());
            }

            final byte[] value = Files.readAllBytes(file);

            // close the entry
            final CRC32 chksum = new CRC32();
            if (entry instanceof final ZipEntry zipEntry && METHOD_STORE.equals(method)) {
                zipEntry.setMethod(ZipEntry.STORED);
                chksum.update(value);
                zipEntry.setCrc(chksum.getValue());
                zipEntry.setSize(value.length);
            }

            putEntry(os, entry);
            os.write(value);
            closeEntry(os);

        } else {

            for (final Path child : FileUtils.list(file)) {
                compressFile(os, file.resolve(child), useHierarchy, stripOffset, method, null);
            }

        }

    }

    /**
     * Adds a element to a archive
     *
     * @param os           The Output Stream to add the element to
     * @param element      The element to add to the archive
     * @param useHierarchy Whether to use a folder hierarchy in the archive file that
     *                     reflects the collection hierarchy
     */
    private void compressElement(final OutputStream os, final Element element, final boolean useHierarchy,
                                 final String stripOffset) throws XPathException {

        final String ns = element.getNamespaceURI();
        if ((ns != null && !ns.isEmpty()) || !"entry".equals(element.getNodeName())) {
            throw new XPathException(this, "Item must be type of xs:anyURI or element entry.");
        }

        if (element.getChildNodes().getLength() > 1) {
            throw new XPathException(this, "Entry content is not valid XML fragment.");
        }

        String name = element.getAttribute("name");
//            if(name == null)
//                throw new XPathException(this, "Entry must have name attribute.");

        final String type = element.getAttribute("type");

        if ("uri".equals(type)) {
            compressFromUri(os, URI.create(element.getFirstChild().getNodeValue()), useHierarchy, stripOffset, element.getAttribute("method"), name);
            return;
        }

        if (useHierarchy) {
            name = removeLeadingOffset(name, stripOffset);
        } else {
            name = name.substring(name.lastIndexOf("/") + 1);
        }

        if ("collection".equals(type)) {
            name += "/";
        }

        Object entry = null;
        try {
            entry = newEntry(name);

            if (!"collection".equals(type)) {
                byte[] value;
                final CRC32 chksum = new CRC32();
                final Node content = element.getFirstChild();

                if (content == null) {
                    value = new byte[0];
                } else {
                    if (content.getNodeType() == Node.TEXT_NODE) {
                        final String text = content.getNodeValue();
                        if ("binary".equals(type)) {
                            //base64 binary
                            value = Base64.decodeBase64(text);
                        } else {
                            //text
                            value = text.getBytes();
                        }
                    } else {
                        //xml
                        final Serializer serializer = context.getBroker().borrowSerializer();
                        try {
                            serializer.setUser(context.getSubject());
                            serializer.setProperty("omit-xml-declaration", "no");
                            getDynamicSerializerOptions(serializer);
                            value = serializer.serialize((NodeValue) content).getBytes();
                        } finally {
                            context.getBroker().returnSerializer(serializer);
                        }
                    }
                }

                if (entry instanceof final ZipEntry zipEntry && METHOD_STORE.equals(element.getAttribute("method"))) {
                    zipEntry.setMethod(ZipEntry.STORED);
                    chksum.update(value);
                    zipEntry.setCrc(chksum.getValue());
                    zipEntry.setSize(value.length);
                }
                putEntry(os, entry);

                os.write(value);
            }
        } catch (final IOException | SAXException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        } finally {
            if (entry != null) {
                try {
                    closeEntry(os);
                } catch (final IOException ioe) {
                    throw new XPathException(this, ioe.getMessage(), ioe);
                }
            }
        }
    }

    private void getDynamicSerializerOptions(final Serializer serializer) throws SAXException {
        final Option option = context.getOption(Option.SERIALIZE_QNAME);
        if (option != null) {
            final String[] params = option.tokenizeContents();
            for (final String param : params) {
                // OutputKeys.INDENT
                final String[] kvp = Option.parseKeyValuePair(param);
                serializer.setProperty(kvp[0], kvp[1]);
            }
        }
        // an explicit serialization-options map argument takes precedence over the prolog option
        for (final String key : serializationProperties.stringPropertyNames()) {
            serializer.setProperty(key, serializationProperties.getProperty(key));
        }
    }

    /**
     * Adds a document to a archive
     *
     * @param os           The Output Stream to add the document to
     * @param doc          The document to add to the archive
     * @param useHierarchy Whether to use a folder hierarchy in the archive file that
     *                     reflects the collection hierarchy
     */
    private void compressResource(final OutputStream os, final DocumentImpl doc, final boolean useHierarchy, final String stripOffset, final String method, final String name) throws IOException, SAXException {
        // create an entry in the Tar for the document
        final Object entry;
        if (name != null) {
            entry = newEntry(name);
        } else if (useHierarchy) {
            final String docCollection = doc.getCollection().getURI().toString();
            final XmldbURI collection = XmldbURI.create(removeLeadingOffset(docCollection, stripOffset));
            entry = newEntry(collection.append(doc.getFileURI()).toString());
        } else {
            entry = newEntry(doc.getFileURI().toString());
        }

        final byte[] value;
        if (doc.getResourceType() == DocumentImpl.XML_FILE) {
            // xml file
            final Serializer serializer = context.getBroker().borrowSerializer();
            try {
                serializer.setUser(context.getSubject());
                serializer.setProperty("omit-xml-declaration", "no");
                getDynamicSerializerOptions(serializer);
                final String strDoc = serializer.serialize(doc);
                value = strDoc.getBytes();
            } finally {
                context.getBroker().returnSerializer(serializer);
            }
        } else if (doc.getResourceType() == DocumentImpl.BINARY_FILE && doc.getContentLength() > 0) {
            // binary file
            try (final InputStream is = context.getBroker().getBinaryResource((BinaryDocument) doc);

                 final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().setBufferSize(doc.getContentLength() == -1 ? 1024 : (int) doc.getContentLength()).get()) {
                baos.write(is);
                value = baos.toByteArray();
            }
        } else {
            value = new byte[0];
        }

        // close the entry
        final CRC32 chksum = new CRC32();
        if (entry instanceof final ZipEntry zipEntry && METHOD_STORE.equals(method)) {
            zipEntry.setMethod(ZipEntry.STORED);
            chksum.update(value);
            zipEntry.setCrc(chksum.getValue());
            zipEntry.setSize(value.length);
        }

        putEntry(os, entry);
        os.write(value);
        closeEntry(os);
    }

    /**
     * Adds a Collection and its child collections and resources recursively to
     * a archive
     *
     * @param os           The Output Stream to add the document to
     * @param col          The Collection to add to the archive
     * @param useHierarchy Whether to use a folder hierarchy in the archive file that
     *                     reflects the collection hierarchy
     */
    private void compressCollection(final OutputStream os, final Collection col, final boolean useHierarchy, final String stripOffset) throws IOException, SAXException, LockException, PermissionDeniedException {
        // iterate over child documents
        final DBBroker broker = context.getBroker();
        final LockManager lockManager = broker.getBrokerPool().getLockManager();
        final MutableDocumentSet childDocs = new DefaultDocumentSet();
        col.getDocuments(broker, childDocs);
        for (final Iterator<DocumentImpl> itChildDocs = childDocs.getDocumentIterator(); itChildDocs.hasNext(); ) {
            final DocumentImpl childDoc = itChildDocs.next();
            try (final ManagedDocumentLock updateLock = lockManager.acquireDocumentReadLock(childDoc.getURI())) {
                compressResource(os, childDoc, useHierarchy, stripOffset, "", null);
            }
        }
        // iterate over child collections
        for (final Iterator<XmldbURI> itChildCols = col.collectionIterator(broker); itChildCols.hasNext(); ) {
            // get the child collection
            final XmldbURI childColURI = itChildCols.next();
            final Collection childCol = broker.getCollection(col.getURI().append(childColURI));
            // recurse
            compressCollection(os, childCol, useHierarchy, stripOffset);
        }
    }

    protected abstract OutputStream stream(UnsynchronizedByteArrayOutputStream baos, Charset encoding);

    protected abstract Object newEntry(String name);

    protected abstract void putEntry(Object os, Object entry) throws IOException;

    protected abstract void closeEntry(Object os) throws IOException;
}
