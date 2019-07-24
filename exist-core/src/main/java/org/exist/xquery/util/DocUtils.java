/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2017 The eXist Project
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
package org.exist.xquery.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Optional;
import java.util.regex.Pattern;

import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.URLSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.Try.Try;

/**
 * Utilities for XPath doc related functions
 *
 * @author <a href="mailto:pierrick.brihaye@free.fr">wolf
 * @author Pierrick Brihaye</a>
 */
//TODO : many more improvements to handle efficiently any URI
public class DocUtils {

    private static final Pattern PTN_PROTOCOL_PREFIX = Pattern.compile("^[a-z]+:.*");

    public static Sequence getDocument(final XQueryContext context, final String path) throws XPathException, PermissionDeniedException {
        return getDocumentByPath(context, path);
    }

    //TODO(AR) could make this much more efficient... it doesn't need to actually retrieve the document!
    public static boolean isDocumentAvailable(final XQueryContext context, final String path) throws XPathException {
        try {
            final Sequence seq = getDocumentByPath(context, path);
            return (seq != null && seq.effectiveBooleanValue());
        } catch (final PermissionDeniedException e) {
            return false;
        }

    }

    private static Sequence getDocumentByPath(final XQueryContext context, final String path) throws XPathException, PermissionDeniedException {
        Sequence doc = getFromDynamicallyAvailableDocuments(context, path);
        if (doc == null) {
            if (PTN_PROTOCOL_PREFIX.matcher(path).matches() && !path.startsWith("xmldb:")) {
                /* URL */
                doc = getDocumentByPathFromURL(context, path);
            } else {
                /* Database documents */
                doc = getDocumentByPathFromDB(context, path);
            }
        }

        return doc;
    }

    private static @Nullable Sequence getFromDynamicallyAvailableDocuments(final XQueryContext context, final String path) throws XPathException {
        try {
            URI uri = new URI(path);
            if (!uri.isAbsolute()) {
                final AnyURIValue baseXdmUri = context.getBaseURI();
                if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                    URI baseUri = baseXdmUri.toURI();
                    if (!baseUri.toString().endsWith("/")) {
                        baseUri = new URI(baseUri.toString() + '/');
                    }
                    uri = baseUri.resolve(uri);
                }
            }
            return context.getDynamicallyAvailableDocument(uri.toString());
        } catch (final URISyntaxException e) {
            throw new XPathException(context.getRootExpression(), ErrorCodes.FODC0005, e);
        }
    }

    private static Sequence getDocumentByPathFromURL(final XQueryContext context, final String path) throws XPathException, PermissionDeniedException {
        try {
            final Source source = SourceFactory.getSource(context.getBroker(), "", path, false);
            if (source == null) {
                return Sequence.EMPTY_SEQUENCE;
            }

            try (final InputStream is = source.getInputStream()) {
                if (source instanceof URLSource) {
                    final int responseCode = ((URLSource) source).getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        // Special case: '404'
                        return Sequence.EMPTY_SEQUENCE;
                    } else if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new PermissionDeniedException("Server returned code " + responseCode);
                    }
                }

                final org.exist.dom.memtree.DocumentImpl memtreeDoc = parse(
                        context.getBroker().getBrokerPool(),
                        context,
                        is);
                memtreeDoc.setDocumentURI(path);
                return memtreeDoc;
            }
        } catch (final ConnectException e) {
            // prevent long stack traces
            throw new XPathException(e.getMessage() + " (" + path + ")");
        } catch (final MalformedURLException e) {
            throw new XPathException(e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException("An error occurred while parsing " + path + ": " + e.getMessage(), e);
        }
    }

    private static Sequence getDocumentByPathFromDB(final XQueryContext context, final String path) throws XPathException, PermissionDeniedException {
        // check if the loaded documents should remain locked
        final LockMode lockType = context.lockDocumentsOnLoad() ? LockMode.WRITE_LOCK : LockMode.READ_LOCK;
        try {
            final XmldbURI baseURI = context.getBaseURI().toXmldbURI();
            final XmldbURI pathUri;
            if (baseURI != null && !(baseURI.equals("") || baseURI.equals("/db"))) {
                // relative collection Path: add the current base URI
                pathUri = baseURI.resolveCollectionPath(XmldbURI.xmldbUriFor(path, false));
            } else {
                pathUri = XmldbURI.xmldbUriFor(path, false);
            }

            // relative collection Path: add the current module call URI if applicable
            final XmldbURI resourceUri = Optional.ofNullable(context.getModuleLoadPath())
                    .filter(moduleLoadPath -> !moduleLoadPath.isEmpty())
                    .flatMap(moduleLoadPath -> Try(() -> XmldbURI.xmldbUriFor(moduleLoadPath)).toOption())
                    .map(moduleLoadPath -> moduleLoadPath.resolveCollectionPath(pathUri))
                    .orElse(pathUri);

            // try to open the document and acquire a lock
            try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(resourceUri, lockType)){
                if (lockedDoc == null) {
                    return Sequence.EMPTY_SEQUENCE;
                } else {
                    final DocumentImpl doc = lockedDoc.getDocument();
                    if (!doc.getPermissions().validate(context.getSubject(), Permission.READ)) {
                        throw new PermissionDeniedException("Insufficient privileges to read resource " + path);
                    }

                    if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                        throw new XPathException("Document " + path + " is a binary resource, not an XML document. Please consider using the function util:binary-doc() to retrieve a reference to it.");
                    }

                    return new NodeProxy(doc);
                }
            }
        } catch (final URISyntaxException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param context The XQuery context
     * @param is      The input stream to parse from
     * @return document The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final XQueryContext context, final InputStream is) throws XPathException {
        return parse(context.getBroker().getBrokerPool(), context, is);
    }

    /**
     * Utility function to parse an input stream into an in-memory DOM document.
     *
     * @param pool    The broker pool
     * @param context The XQuery context
     * @param is      The input stream to parse from
     * @return document The document that was parsed
     * @throws XPathException in case of dynamic error
     */
    public static org.exist.dom.memtree.DocumentImpl parse(final BrokerPool pool, final XQueryContext context,
            final InputStream is) throws XPathException {
        // we use eXist's in-memory DOM implementation
        final XMLReaderPool parserPool = pool.getParserPool();
        XMLReader reader = null;
        try {
            reader = pool.getParserPool().borrowXMLReader();
            final InputSource src = new InputSource(is);
            final SAXAdapter adapter = new SAXAdapter(context);
            reader.setContentHandler(adapter);
            try {
                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                reader.parse(src);
            } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
                throw new XPathException("Error creating XML parser: " + e.getMessage(), e);
            } catch (final IOException | SAXException e) {
                throw new XPathException("Error while parsing XML: " + e.getMessage(), e);
            }

            return adapter.getDocument();

        } finally {
            if (reader != null) {
                parserPool.returnXMLReader(reader);
            }
        }
    }
}
