/*
 *  eXist EXPath Zip Client Module zip file entry functions
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.exist;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 * @version EXPath Zip Client Module Candidate 12 October 2010 http://expath.org/spec/zip/20101012
 */
public class ZipEntryFunctions extends BasicFunction {

    protected static final Logger LOG = LogManager.getLogger(ZipEntryFunctions.class);

    private static final FunctionParameterSequenceType HREF_PARAM = new FunctionParameterSequenceType("href", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI for locating the Zip file");
    private static final FunctionParameterSequenceType ENTRY_PARAM = new FunctionParameterSequenceType("entry", Type.STRING, Cardinality.EXACTLY_ONE, "The entry within the Zip file to address");

    private static final String BINARY_ENTRY_NAME = "binary-entry";
    private static final String HTML_ENTRY_NAME = "html-entry";
    private static final String TEXT_ENTRY_NAME = "text-entry";
    private static final String XML_ENTRY_NAME = "xml-entry";

    public static final FunctionSignature signatures[] = {
            //zip:binary-entry($href as xs:anyURI, $entry as xs:string) as xs:base64Binary
            new FunctionSignature(
                    new QName(BINARY_ENTRY_NAME, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Extracts the binary stream from the file positioned at $entry within the ZIP file identified by $href and returns it as a Base64 item.",
                    new SequenceType[]{
                            HREF_PARAM,
                            ENTRY_PARAM
                    },
                    new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary representation of the entry from the Zip file.")
            ),
            //zip:html-entry($href as xs:anyURI, $entry as xs:string) as document-node()
            new FunctionSignature(
                    new QName(HTML_ENTRY_NAME, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Extracts the html file positioned at $entry within the ZIP file identified by $href, and returns a document node. Because an HTML document is not necessarily a well-formed XML document, an implementation may use a specific parser in order to produce an XDM document node, like [TagSoup] or [HTML Tidy]; the details of this process are implementation-defined.",
                    new SequenceType[]{
                            HREF_PARAM,
                            ENTRY_PARAM
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "The document-node of the entry from the Zip file.")
            ),
            //zip:text-entry($href as xs:anyURI, $entry as xs:string) as xs:string
            new FunctionSignature(
                    new QName(TEXT_ENTRY_NAME, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Extracts the contents of the text file positioned at entry within the ZIP file identified by $href and returns it as a string.",
                    new SequenceType[]{
                            HREF_PARAM,
                            ENTRY_PARAM
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The string value of the entry from the Zip file.")
            ),
            //zip:xml-entry($href as xs:anyURI, $entry as xs:string) as document-node()
            new FunctionSignature(
                    new QName(XML_ENTRY_NAME, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Extracts the content from the XML file positioned at $entry within the ZIP file identified by $href and returns it as a document-node.",
                    new SequenceType[]{
                            HREF_PARAM,
                            ENTRY_PARAM
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "The document-node of the entry from the Zip file.")
            )
    };

    /**
     * SendRequestFunction Constructor
     *
     * @param context   The Context of the calling XQuery
     * @param signature The actual signature of the function
     */
    public ZipEntryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final XmldbURI uri = ((AnyURIValue) args[0].itemAt(0)).toXmldbURI();
        final String entryName = args[1].itemAt(0).getStringValue();

        final ZipFileSource zipFileSource = new ZipFileFromDb(uri);
        ZipInputStream zis = null;

        boolean mustClose = true;
        Sequence result = Sequence.EMPTY_SEQUENCE;

        try {
            zis = zipFileSource.getStream(context.getBroker());
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                try {
                    if (zipEntry.getName().equals(entryName)) {
                        //process
                        if (isCalledAs(BINARY_ENTRY_NAME)) {
                            result = extractBinaryEntry(zis);
                            mustClose = false;
                        } else if (isCalledAs(HTML_ENTRY_NAME)) {
                            result = extractHtmlEntry(zis);
                        } else if (isCalledAs(TEXT_ENTRY_NAME)) {
                            result = extractStringEntry(zis);
                        } else if (isCalledAs(XML_ENTRY_NAME)) {
                            result = extractXmlEntry(zis);
                        }
                        break;
                    }
                } finally {
                    //DONT need to close as the extract functions
                    //close the stream on the zip entry
                    /*if(mustClose) {
                        zis.closeEntry();
                    }*/
                }
            }
        } catch (final IOException | PermissionDeniedException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            throw new XPathException(this, ioe.getMessage(), ioe);
        } finally {
            if (zis != null && mustClose) {
                try {
                    zis.close();
                } catch (final IOException ioe) {
                    LOG.warn(ioe.getMessage(), ioe);
                }
            }
            zipFileSource.close();
        }

        return result;
    }

    private BinaryValue extractBinaryEntry(final ZipInputStream zis) throws XPathException {
        return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), zis);
    }

    private StringValue extractStringEntry(final ZipInputStream zis) throws XPathException, IOException {
        final char buf[] = new char[4096];
        final StringBuilder builder = new StringBuilder();
        int read = -1;
        try (final Reader reader = new InputStreamReader(zis, UTF_8)) {
            while ((read = reader.read(buf)) > -1) {
                builder.append(buf, 0, read);
            }
        }
        return new StringValue(builder.toString());
    }

    private org.exist.dom.memtree.DocumentImpl extractHtmlEntry(final ZipInputStream zis) throws XPathException {
        try {
            return ModuleUtils.htmlToXHtml(context, new StreamSource(zis), null, null);
        } catch (final SAXException | IOException saxe) {
            throw new XPathException(this, saxe.getMessage(), saxe);
        }
    }

    private NodeValue extractXmlEntry(final ZipInputStream zis) throws XPathException {
        try {
            return ModuleUtils.streamToXML(context, zis);
        } catch (final SAXException | IOException saxe) {
            throw new XPathException(this, saxe.getMessage(), saxe);
        }
    }

    public interface ZipFileSource extends AutoCloseable {
        ZipInputStream getStream(final DBBroker broker) throws IOException, PermissionDeniedException;

        @Override
        void close();
    }

    protected static class ZipFileFromDb implements ZipFileSource {
        private LockedDocument binaryDoc = null;
        private final XmldbURI uri;

        public ZipFileFromDb(final XmldbURI uri) {
            this.uri = uri;
        }

        @Override
        public ZipInputStream getStream(final DBBroker broker) throws IOException, PermissionDeniedException {
            if (binaryDoc == null) {
                binaryDoc = getDoc(broker);
            }

            return new ZipInputStream(broker.getBinaryResource((BinaryDocument)binaryDoc.getDocument()));
        }

        @Override
        public void close() {
            if (binaryDoc != null) {
                binaryDoc.close();
            }
        }

        /**
         * @return only binary document otherwise null
         */
        @Nullable
        private LockedDocument getDoc(final DBBroker broker) throws PermissionDeniedException {
            final LockedDocument lockedDoc = broker.getXMLResource(uri, LockMode.READ_LOCK);
            if(lockedDoc == null) {
                return null;
            } else if (lockedDoc.getDocument().getResourceType() != DocumentImpl.BINARY_FILE) {
                lockedDoc.close();
                return null;
            }

            return lockedDoc;
        }
    }
}