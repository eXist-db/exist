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
import javax.xml.transform.stream.StreamSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
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

/**
 * @author Adam Retter <adam@existsolutions.com>
 * @version EXPath Zip Client Module Candidate 12 October 2010 http://expath.org/spec/zip/20101012
 */
public class ZipEntryFunctions extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(ZipEntryFunctions.class);

    private final static FunctionParameterSequenceType HREF_PARAM = new FunctionParameterSequenceType("href", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI for locating the Zip file");
    private final static FunctionParameterSequenceType ENTRY_PARAM = new FunctionParameterSequenceType("entry", Type.STRING, Cardinality.EXACTLY_ONE, "The entry within the Zip file to address");

    private final static String BINARY_ENTRY_NAME = "binary-entry";
    private final static String HTML_ENTRY_NAME = "html-entry";
    private final static String TEXT_ENTRY_NAME = "text-entry";
    private final static String XML_ENTRY_NAME = "xml-entry";

    public final static FunctionSignature signatures[] = {
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
     * @param context	The Context of the calling XQuery
     * @param signature The actual signature of the function
     */
    public ZipEntryFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        XmldbURI uri = ((AnyURIValue)args[0].itemAt(0)).toXmldbURI();
        String entryName = args[1].itemAt(0).getStringValue();

        ZipFileSource zipFileSource =  new ZipFileFromDb(uri);
        ZipInputStream zis = null;

        boolean mustClose = true;
        Sequence result = Sequence.EMPTY_SEQUENCE;

        try {
            zis = zipFileSource.getStream();
            ZipEntry zipEntry;
            while((zipEntry = zis.getNextEntry()) != null) {
                try {
                    if(zipEntry.getName().equals(entryName)) {
                        //process
                        if(isCalledAs(BINARY_ENTRY_NAME)) {
                            result = extractBinaryEntry(zis, zipEntry);
                            mustClose = false;
                        } else if(isCalledAs(HTML_ENTRY_NAME)) {
                            result = extractHtmlEntry(zis, zipEntry);
                        } else if(isCalledAs(TEXT_ENTRY_NAME)) {
                            result = extractStringEntry(zis, zipEntry);
                        } else if(isCalledAs(XML_ENTRY_NAME)) {
                            result = extractXmlEntry(zis, zipEntry);
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
        } catch(IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            throw new XPathException(ioe.getMessage(), ioe);
        } catch(PermissionDeniedException pde) {
            LOG.error(pde.getMessage(), pde);
            throw new XPathException(pde.getMessage(), pde);
        } finally {
            if(zis != null && mustClose) {
                try { zis.close(); } catch (IOException ioe) { LOG.warn(ioe.getMessage(), ioe); }
            }
            zipFileSource.close();
        }

        return result;
    }

    private BinaryValue extractBinaryEntry(ZipInputStream zis, ZipEntry zipEntry) throws XPathException {
        return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), zis);
    }

    private StringValue extractStringEntry(ZipInputStream zis, ZipEntry zipEntry) throws XPathException, IOException{
        Reader reader = new InputStreamReader(zis);
        char buf[] = new char[1024];
        StringBuilder builder = new StringBuilder();
        int read = -1;
        try {
            while((read = reader.read(buf)) > -1) {
                builder.append(buf, 0, read);
            }
        } finally {
            reader.close();
        }
        return new StringValue(builder.toString());
    }

    private org.exist.dom.memtree.DocumentImpl extractHtmlEntry(ZipInputStream zis, ZipEntry zipEntry) throws XPathException {
        try {
            return ModuleUtils.htmlToXHtml(context, zipEntry.getName(), new StreamSource(zis), null, null);
        } catch(SAXException saxe) {
            throw new XPathException(saxe.getMessage(), saxe);
        } catch(IOException ioe) {
            throw new XPathException(ioe.getMessage(), ioe);
        }
    }

    private NodeValue extractXmlEntry(ZipInputStream zis, ZipEntry zipEntry) throws XPathException {
        try {
            return ModuleUtils.streamToXML(context, zis);
        } catch(SAXException saxe) {
            throw new XPathException(saxe.getMessage(), saxe);
        } catch(IOException ioe) {
            throw new XPathException(ioe.getMessage(), ioe);
        }
    }

    public interface ZipFileSource {
        public ZipInputStream getStream() throws IOException, PermissionDeniedException;
        public void close();
    }

    private class ZipFileFromDb implements ZipFileSource {
        private BinaryDocument binaryDoc = null;
        private final XmldbURI uri;

        public ZipFileFromDb(XmldbURI uri) {
            this.uri = uri;
        }

        @Override
        public ZipInputStream getStream() throws IOException, PermissionDeniedException {

            if(binaryDoc == null) {
                binaryDoc = getDoc();
            }

            return new ZipInputStream(context.getBroker().getBinaryResource(binaryDoc));
        }

        @Override
        public void close() {
            if(binaryDoc != null) {
               binaryDoc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        private BinaryDocument getDoc() throws PermissionDeniedException {

            DocumentImpl doc = context.getBroker().getXMLResource(uri, Lock.READ_LOCK);
            if(doc == null || doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                return null;
            }

            return (BinaryDocument)doc;
        }
    }
}