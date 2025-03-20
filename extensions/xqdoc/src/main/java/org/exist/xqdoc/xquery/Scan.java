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
package org.exist.xqdoc.xquery;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xqdoc.XQDocHelper;
import org.exist.xquery.*;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xqdoc.conversion.XQDocException;

public class Scan extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("scan", XQDocModule.NAMESPACE_URI, XQDocModule.PREFIX),
            "Scan and extract function documentation from an external XQuery function module according to the" +
            "XQDoc specification. The single argument URI may either point to an XQuery module stored in the " +
            "db (URI starts with xmldb:exist:...) or a module in the file system. A file system module is " +
            "searched in the same way as if it were loaded through an \"import module\" statement. Static " +
            "mappings defined in conf.xml are searched first.",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                    "The URI from which to load the function module")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "the function docs.")
        ),
        new FunctionSignature(
            new QName("scan", XQDocModule.NAMESPACE_URI, XQDocModule.PREFIX),
            "Scan and extract function documentation from an external XQuery function module according to the " +
            "XQDoc specification. The two parameter version of the function expects to get the source code of " +
            "the module in the first argument and a name for the module in the second.",
            new SequenceType[] {
                new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE,
                    "The base64 encoded source data of the module"),
                new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The name of the module")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "the function docs.")
        )
    };

    private final static Pattern NAME_PATTERN = Pattern.compile("([^/\\.]+)\\.?[^\\.]*$");

    private final static String NORMALIZE_XQUERY = "resource:org/exist/xqdoc/xquery/normalize.xql";

    private CompiledXQuery normalizeXQuery = null;

    public Scan(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    //TODO ideally should be replaced by changing BinarySource to a streaming approach
    private byte[] binaryValueToByteArray(BinaryValue binaryValue) throws IOException {
        try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            binaryValue.streamBinaryTo(baos);
            return baos.toByteArray();
        }
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Source source = null;
        String name;
        if (getArgumentCount() == 2) {
            
            byte data[];
            try{
                data = binaryValueToByteArray((BinaryValue)args[0].itemAt(0));
            } catch(IOException ioe) {
                throw new XPathException(this, ioe.getMessage(), ioe);
            }
            name = args[1].getStringValue();
            source = new BinarySource(data, true);
        } else {
            String uri = args[0].getStringValue();
            if (uri.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                try {
                    XmldbURI resourceURI = XmldbURI.xmldbUriFor(uri);
                    try (final Collection collection = context.getBroker().openCollection(resourceURI.removeLastSegment(), LockMode.READ_LOCK)) {
                        if (collection == null) {
                            LOG.warn("collection not found: {}", resourceURI.getCollectionPath());
                            return Sequence.EMPTY_SEQUENCE;
                        }

                        try(final LockedDocument lockedDoc = collection.getDocumentWithLock(context.getBroker(), resourceURI.lastSegment(), LockMode.READ_LOCK)) {

                            // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                            collection.close();

                            final DocumentImpl doc = lockedDoc == null ?  null : lockedDoc.getDocument();
                            if (doc == null) {
                                return Sequence.EMPTY_SEQUENCE;
                            }
                            if (doc.getResourceType() != DocumentImpl.BINARY_FILE ||
                                    !"application/xquery".equals(doc.getMimeType())) {
                                throw new XPathException(this, "XQuery resource: " + uri + " is not an XQuery or " +
                                        "declares a wrong mime-type");
                            }
                            source = new DBSource(context.getBroker().getBrokerPool(), (BinaryDocument) doc, false);
                            name = doc.getFileURI().toString();
                        }
                    } catch (LockException e) {
                        throw new XPathException(this, "internal lock error: " + e.getMessage());
                    } catch (PermissionDeniedException pde) {
                        throw new XPathException(this, pde.getMessage(), pde);
                    }
                } catch (URISyntaxException e) {
                    throw new XPathException(this, "invalid module uri: " + uri + ": " + e.getMessage(), e);
                }
            } else {
                // first check if the URI points to a registered module
                String location = context.getModuleLocation(uri);
                if (location != null)
                    uri = location;
                try {
                    source = SourceFactory.getSource(context.getBroker(), context.getModuleLoadPath(), uri, false);
                    if (source == null) {
                        throw new XPathException(this, "failed to read module " + uri);
                    }
                    name = extractName(uri);
                } catch (IOException e) {
                    throw new XPathException(this, "failed to read module " + uri, e);
                } catch (PermissionDeniedException e) {
                    throw new XPathException(this, "permission denied to read module " + uri, e);
                }
            }
        }
        try {
            XQDocHelper helper = new XQDocHelper();
            String xml = helper.scan(source, name);
            NodeValue root = ModuleUtils.stringToXML(context, xml, this);
            if (root == null)
                return Sequence.EMPTY_SEQUENCE;
            return normalize((NodeValue) ((Document) root).getDocumentElement());
        } catch (XQDocException | SAXException e) {
            throw new XPathException(this, "error while scanning module: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "IO error while scanning module: " + e.getMessage(), e);
        }
    }

    private String extractName(String uri) {
        Matcher matcher = NAME_PATTERN.matcher(uri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return uri;
    }

    private Sequence normalize(NodeValue input) throws IOException, XPathException {
        XQuery xquery = context.getBroker().getBrokerPool().getXQueryService();
        if (normalizeXQuery == null) {
            Source source = new ClassLoaderSource(NORMALIZE_XQUERY);
            XQueryContext xc = new XQueryContext(context.getBroker().getBrokerPool());
            try {
                normalizeXQuery = xquery.compile(xc, source);
            } catch(final PermissionDeniedException e) {
                throw new XPathException(this, e);
            }
        }
        
        try {
            normalizeXQuery.getContext().declareVariable("xqdoc:doc", input);
            return xquery.execute(context.getBroker(), normalizeXQuery, Sequence.EMPTY_SEQUENCE);
        } catch(final PermissionDeniedException e) {
            throw new XPathException(this, e);
        }
    }
}
