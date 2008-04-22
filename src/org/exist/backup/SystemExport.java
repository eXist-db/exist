/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * $Id$
 */
package org.exist.backup;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.repair.ErrorReport;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class SystemExport {

    private final static Logger LOG = Logger.getLogger(SystemExport.class);
    
    public Properties defaultOutputProperties = new Properties();
	{
		defaultOutputProperties.setProperty(OutputKeys.INDENT, "no");
		defaultOutputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultOutputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
	}

    public Properties contentsOutputProps = new Properties();
	{
	    contentsOutputProps.setProperty(OutputKeys.INDENT, "yes");
    }

    private static final XmldbURI TEMP_COLLECTION = XmldbURI.createInternal(NativeBroker.TEMP_COLLECTION);
    private static final XmldbURI CONTENTS_URI = XmldbURI.createInternal("__contents__.xml");

    private static final int currVersion = 1;
    
    private DBBroker broker;
    private String target;
    private StatusCallback callback = null;

    public SystemExport(DBBroker broker, String target, StatusCallback callback) {
        this.broker = broker;
        this.target = target;
        this.callback = callback;
    }

    private void reportError(String message, Throwable e) {
        LOG.warn(message, e);
        if (callback != null)
            callback.error(message, e);
    }

    public void export(List errorList) {
        try {
            Collection root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
            BackupWriter output;
            if (target.endsWith(".zip"))
                output = new ZipWriter(target, "/db");
            else
                output = new FileSystemWriter(target + "/db");
            export(root, output, errorList);
            output.close();
        } catch (IOException e) {
            reportError("A write error occurred while exporting data: '" + e.getMessage() +
                    "'. Aborting export.", e);
        } catch (SAXException e) {
            reportError("A write error occurred while exporting data: '" + e.getMessage() +
                    "'. Aborting export.", e);
        }
    }

    private static boolean isDamaged(DocumentImpl doc, List errorList) {
        if (errorList == null)
            return false;
        ErrorReport report;
        for (int i = 0; i < errorList.size(); i++) {
            report = (ErrorReport) errorList.get(i);
            if (report.getErrcode() == ErrorReport.RESOURCE_ACCESS_FAILED &&
                    report.getDocumentId() == doc.getDocId())
                return true;
        }
        return false;
    }

    private void export(Collection current, BackupWriter output, List errorList) throws IOException, SAXException {
        if (callback != null)
            callback.startCollection(current.getURI().toString());
        Writer contents = output.newContents();
		// serializer writes to __contents__.xml
		SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
		serializer.setOutput(contents, contentsOutputProps);

		serializer.startDocument();
		serializer.startPrefixMapping("", Namespaces.EXIST_NS);
        XmldbURI uri = current.getURI();
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", uri.toString());
        attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf(currVersion));
        attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", current.getPermissions().getOwner());
		attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", current.getPermissions().getOwnerGroup());
        attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA",
                Integer.toOctalString(current.getPermissions().getPermissions()));
        try {
            attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA",
                    new DateTimeValue(new Date(current.getCreationTime())).getStringValue());
        } catch (XPathException e) {
            e.printStackTrace();
        }
        serializer.startElement(Namespaces.EXIST_NS, "collection", "collection", attr);

        OutputStream os;
        BufferedWriter writer;
        SAXSerializer contentSerializer;
        int docsCount = current.getDocumentCount();
        int count = 0;
        for (Iterator i = current.iterator(broker); i.hasNext(); count++) {
            DocumentImpl doc = (DocumentImpl) i.next();
            if (isDamaged(doc, errorList)) {
                reportError("Skipping damaged document " + doc.getFileURI(), null);
                continue;
            }
            if (doc.getFileURI().equalsInternal(CONTENTS_URI))
                continue; // skip __contents__.xml documents
            if (callback != null)
                callback.startDocument(doc.getFileURI().toString(), count, docsCount);
            os = output.newEntry(Backup.encode(URIUtils.urlDecodeUtf8(doc.getFileURI())));
            try {
                if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    broker.readBinaryResource((BinaryDocument) doc, os);
                } else {
                    writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    // write resource to contentSerializer
                    contentSerializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                    contentSerializer.setOutput(writer, defaultOutputProperties);
                    writeXML(doc, contentSerializer);
                    SerializerPool.getInstance().returnObject(contentSerializer);
                    writer.flush();
                }
            } catch (Exception e) {
                reportError("A write error occurred while exporting document: '" + doc.getFileURI() +
                            "'. Continuing with next document.", e);
                continue;
            } finally {
                output.closeEntry();
            }

            //store permissions
            attr.clear();
            attr.addAttribute(Namespaces.EXIST_NS, "type", "type", "CDATA",
                    doc.getResourceType() == DocumentImpl.BINARY_FILE ? "BinaryResource" : "XMLResource");
            attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", doc.getFileURI().toString());
            attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", doc.getPermissions().getOwner());
            attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", doc.getPermissions().getOwnerGroup());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA",
                    Integer.toOctalString(doc.getPermissions().getPermissions()));
            try {
                final String created = new DateTimeValue(new Date(doc.getMetadata().getCreated())).getStringValue();
                attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA", created);
                final String modified = new DateTimeValue(new Date(doc.getMetadata().getLastModified())).getStringValue();
                attr.addAttribute(Namespaces.EXIST_NS, "modified", "modified", "CDATA", modified);
            } catch (XPathException e) {
                LOG.warn(e.getMessage(), e);
            }

            attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA",
                    Backup.encode(URIUtils.urlDecodeUtf8(doc.getFileURI())));
            attr.addAttribute(Namespaces.EXIST_NS, "mimetype", "mimetype", "CDATA",
                    Backup.encode(doc.getMetadata().getMimeType()));
            if (doc.getResourceType() == DocumentImpl.XML_FILE && doc.getDoctype() != null) {
                if (doc.getDoctype().getName() != null)
                    attr.addAttribute(Namespaces.EXIST_NS, "namedoctype", "namedoctype", "CDATA", doc.getDoctype().getName());
                if (doc.getDoctype().getPublicId() != null)
                    attr.addAttribute(Namespaces.EXIST_NS, "publicid", "publicid", "CDATA", doc.getDoctype().getPublicId());
                if (doc.getDoctype().getSystemId() != null)
                    attr.addAttribute(Namespaces.EXIST_NS, "systemid", "systemid", "CDATA", doc.getDoctype().getSystemId());
            }
            serializer.startElement(Namespaces.EXIST_NS, "resource", "resource", attr);
            serializer.endElement(Namespaces.EXIST_NS, "resource", "resource");
        }

        for (Iterator i = current.collectionIterator(); i.hasNext(); ) {
            XmldbURI childUri = (XmldbURI) i.next();
            if (childUri.equalsInternal(TEMP_COLLECTION))
                continue;
            Collection child = broker.getCollection(uri.append(childUri));
			attr.clear();
			attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", childUri.toString());
			attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA",
                    Backup.encode(URIUtils.urlDecodeUtf8(childUri.toString())));
			serializer.startElement(Namespaces.EXIST_NS, "subcollection", "subcollection", attr);
			serializer.endElement(Namespaces.EXIST_NS, "subcollection", "subcollection");
		}
		// close <collection>
		serializer.endElement(Namespaces.EXIST_NS, "collection", "collection");
		serializer.endPrefixMapping("");
		serializer.endDocument();
		output.closeContents();

        for (Iterator i = current.collectionIterator(); i.hasNext(); ) {
            XmldbURI childUri = (XmldbURI) i.next();
            if (childUri.equalsInternal(TEMP_COLLECTION))
                continue;
            Collection child = broker.getCollection(uri.append(childUri));
            try {
                output.newCollection(Backup.encode(URIUtils.urlDecodeUtf8(childUri.toString())));
                export(child, output, errorList);
            } catch (Exception e) {
                reportError("An error occurred while writing collection " + child.getURI() +
                    ". Continuing with next collection.", e);
            } finally {
                output.closeCollection();
            }
        }
    }

    private void writeXML(DocumentImpl doc, Receiver receiver) {
        try {
            EmbeddedXMLStreamReader reader;
            char ch[];
            NodeList children = doc.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                reader = doc.getBroker().getXMLStreamReader((StoredNode) children.item(i), false);
                while (reader.hasNext()) {
                    int status = reader.next();
                    switch (status) {
                        case XMLStreamReader.START_DOCUMENT:
                        case XMLStreamReader.END_DOCUMENT:
                            break;
                        case XMLStreamReader.START_ELEMENT :
                            AttrList attribs = new AttrList();
                            for (int j = 0; j < reader.getAttributeCount(); j++) {
                                final QName qn = new QName(reader.getAttributeLocalName(j), reader.getAttributeNamespace(j),
                                        reader.getAttributePrefix(j));
                                attribs.addAttribute(qn, reader.getAttributeValue(j));
                            }
                            receiver.startElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()),
                                    attribs);
                            break;
                        case XMLStreamReader.END_ELEMENT :
                            receiver.endElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()));
                            break;
                        case XMLStreamReader.CHARACTERS :
                            receiver.characters(reader.getText());
                            break;
                        case XMLStreamReader.CDATA :
                            ch = reader.getTextCharacters();
                            receiver.cdataSection(ch, 0, ch.length);
                            break;
                        case XMLStreamReader.COMMENT :
                            ch = reader.getTextCharacters();
                            receiver.comment(ch, 0, ch.length);
                            break;
                        case XMLStreamReader.PROCESSING_INSTRUCTION :
                            receiver.processingInstruction(reader.getPITarget(), reader.getPIData());
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public static interface StatusCallback {

        public void startCollection(String path);

        public void startDocument(String name, int current, int count);
        
        public void error(String message, Throwable exception);
    }
}