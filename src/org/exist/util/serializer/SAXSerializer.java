/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.util.serializer;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import org.exist.Namespaces;
import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.XMLString;
import org.exist.util.serializer.json.JSONWriter;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.NamespaceSupport;

public class SAXSerializer implements ContentHandler, LexicalHandler, Receiver {

    private final static String XHTML_NS = "http://www.w3.org/1999/xhtml";

    private final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.INDENT, "false");
    }

    private final static int XML_WRITER = 0;
    private final static int XHTML_WRITER = 1;
    private final static int TEXT_WRITER = 2;
    private final static int JSON_WRITER = 3;
    private final static int XHTML5_WRITER = 4;
    private final static int MICRO_XML_WRITER = 5;
    private final static int HTML5_WRITER = 6;
    
    private XMLWriter writers[] = {
        new IndentingXMLWriter(),
        new XHTMLWriter(), 
        new TEXTWriter(),
        new JSONWriter(),
        new XHTML5Writer(),
        new MicroXmlWriter(),
        new HTML5Writer()
    };


    protected XMLWriter receiver;
    protected Properties outputProperties = defaultProperties;
    protected NamespaceSupport nsSupport = new NamespaceSupport();
    protected HashMap<String, String> namespaceDecls = new HashMap<String, String>();
    protected HashMap<String, String> optionalNamespaceDecls = new HashMap<String, String>();
    protected boolean enforceXHTML = false;

    public SAXSerializer() {
        super();
        receiver = writers[XML_WRITER];
    }

    public SAXSerializer(final Writer writer, final Properties outputProperties) {
        super();
        setOutput(writer, outputProperties);
    }

    public final void setOutput(final Writer writer, final Properties properties) {
        if (properties == null) {
            outputProperties = defaultProperties;
        } else {
            outputProperties = properties;
        }
        final String method = outputProperties.getProperty("method", "xml");

        if ("xhtml".equalsIgnoreCase(method)) {
            receiver = writers[XHTML_WRITER];
        } else if("text".equalsIgnoreCase(method)) {
            receiver = writers[TEXT_WRITER];
        } else if ("json".equalsIgnoreCase(method)) {
        	receiver = writers[JSON_WRITER];
        } else if ("xhtml5".equalsIgnoreCase(method)) {
        	receiver = writers[XHTML5_WRITER];
        } else if ("html5".equalsIgnoreCase(method)) {
            receiver = writers[HTML5_WRITER];
        } else if("microxml".equalsIgnoreCase(method)) {
            receiver = writers[MICRO_XML_WRITER];
        } else {
            receiver = writers[XML_WRITER];
        }


        // if set, enforce XHTML namespace on elements with no namespace
        final String xhtml = outputProperties.getProperty(EXistOutputKeys.ENFORCE_XHTML, "no");
        enforceXHTML = xhtml.equalsIgnoreCase("yes");

        receiver.setWriter(writer);
        receiver.setOutputProperties(outputProperties);
    }

    public Writer getWriter() {
        return receiver.getWriter();
    }

    public void setReceiver(XMLWriter receiver) {
        this.receiver = receiver;
    }

    public void reset() {
        nsSupport.reset();
        namespaceDecls.clear();
        optionalNamespaceDecls.clear();
        for (int i = 0; i < writers.length; i++) {
            writers[i].reset();
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
     */
    @Override
    public void setDocumentLocator(Locator locator) {
        //Nothing to do ?
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        try {
            receiver.startDocument();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        try {
            receiver.endDocument();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (namespaceURI.equals(Namespaces.XML_NS)) {
            return;
        }
        if(prefix == null) {
            prefix = XMLConstants.DEFAULT_NS_PREFIX;
        }
        final String ns = nsSupport.getURI(prefix);
        if (enforceXHTML && !XHTML_NS.equals(namespaceURI)) {
            namespaceURI = XHTML_NS;
        }
        if(ns == null || (!ns.equals(namespaceURI))) {
            optionalNamespaceDecls.put(prefix, namespaceURI);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        optionalNamespaceDecls.remove(prefix);
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String namespaceURI, final String localName, final String qname, final Attributes attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            receiver.startElement(namespaceURI, localName, qname);
            String elemPrefix = XMLConstants.DEFAULT_NS_PREFIX;
            int p = qname.indexOf(':');
            if (p > 0) {
                elemPrefix = qname.substring(0, p);
            }
            if (namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            if (enforceXHTML && elemPrefix.length() == 0 && namespaceURI.length() == 0) {
                namespaceURI = XHTML_NS;
            }
            if (nsSupport.getURI(elemPrefix) == null) {
                namespaceDecls.put(elemPrefix, namespaceURI);
                nsSupport.declarePrefix(elemPrefix, namespaceURI);
            }
            // check attributes for required namespace declarations
            String attrName;
            String uri;
            if(attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    attrName = attribs.getQName(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrName)) {
                        if (nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX) == null) {
                            uri = attribs.getValue(i);
                            if (enforceXHTML && !XHTML_NS.equals(uri)) {
                                uri = XHTML_NS;
                            }
                            namespaceDecls.put(XMLConstants.DEFAULT_NS_PREFIX, uri);
                            nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, uri);
                        }
                    } else if (attrName.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                        final String prefix = attrName.substring(6);
                        if (nsSupport.getURI(prefix) == null) {
                            uri = attribs.getValue(i);
                            namespaceDecls.put(prefix, uri);
                            nsSupport.declarePrefix(prefix, uri);
                        }
                    } else if ((p = attrName.indexOf(':')) > 0) {
                        final String prefix = attrName.substring(0, p);
                        uri = attribs.getURI(i);
                        if (nsSupport.getURI(prefix) == null) {
                            namespaceDecls.put(prefix, uri);
                            nsSupport.declarePrefix(prefix, uri);
                        }
                    }
                }
            }
            for (final Map.Entry<String, String> nsEntry : optionalNamespaceDecls.entrySet()) {
                final String prefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                receiver.namespace(prefix, uri);
                nsSupport.declarePrefix(prefix, uri); //nsSupport.declarePrefix(prefix, namespaceURI);
            }
            // output all namespace declarations
            for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
                final String prefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                if(!optionalNamespaceDecls.containsKey(prefix)) {
                    receiver.namespace(prefix, uri);
                }
            }
            //cancels current xmlns if relevant
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(elemPrefix) && !namespaceURI.equals(receiver.getDefaultNamespace())) {
                receiver.namespace(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
                nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
            }
            optionalNamespaceDecls.clear();
            // output attributes
            for (int i = 0; i < attribs.getLength(); i++) {
                if (!attribs.getQName(i).startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                    receiver.attribute(attribs.getQName(i), attribs.getValue(i));
                }
            }
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.Receiver#startElement(org.exist.dom.QName)
     */
    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            String prefix = qname.getPrefix();
            String namespaceURI = qname.getNamespaceURI();
            if(prefix == null) {
                prefix = XMLConstants.DEFAULT_NS_PREFIX;
            }
            if(namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            if(enforceXHTML && prefix.length() == 0 && namespaceURI.length() == 0) {
                namespaceURI = XHTML_NS;
                receiver.startElement(new QName(qname.getLocalPart(), namespaceURI, qname.getPrefix()));
            } else {
                receiver.startElement(qname);
            }
            if (nsSupport.getURI(prefix) == null) {
                namespaceDecls.put(prefix, namespaceURI);
                nsSupport.declarePrefix(prefix, namespaceURI);
            }
            // check attributes for required namespace declarations
            QName attrQName;
            String uri;
            if(attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    attrQName = attribs.getQName(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrQName.getLocalPart())) {
                        if (nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX) == null) {
                            uri = attribs.getValue(i);
                            if (enforceXHTML && !XHTML_NS.equals(uri)) {
                                uri = XHTML_NS;
                            }
                            namespaceDecls.put(XMLConstants.DEFAULT_NS_PREFIX, uri);
                            nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, uri);
                        }
                    } else if (attrQName.getPrefix() != null && attrQName.getPrefix().length() > 0) {
                        prefix = attrQName.getPrefix();
                        if((XMLConstants.XMLNS_ATTRIBUTE + ":").equals(prefix)) {
                            if (nsSupport.getURI(prefix) == null) {
                                uri = attribs.getValue(i);
                                prefix = attrQName.getLocalPart();
                                namespaceDecls.put(prefix, uri);
                                nsSupport.declarePrefix(prefix, uri);
                            }
                        } else {
                            if (nsSupport.getURI(prefix) == null) {
                                uri = attrQName.getNamespaceURI();
                                namespaceDecls.put(prefix, uri);
                                nsSupport.declarePrefix(prefix, uri);
                            }
                        }
                    }
                }
            }
            String optPrefix;
            for (final Map.Entry<String, String> nsEntry : optionalNamespaceDecls.entrySet()) {
                optPrefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                receiver.namespace(optPrefix, uri);
                nsSupport.declarePrefix(optPrefix, uri);
            }
            // output all namespace declarations
            for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
                optPrefix = nsEntry.getKey();
                if (XMLConstants.XMLNS_ATTRIBUTE.equals(optPrefix)) {
                    continue;
                }
                uri = nsEntry.getValue(); 
                if(!optionalNamespaceDecls.containsKey(optPrefix)) {
                    receiver.namespace(optPrefix, uri);
                }
            }
            optionalNamespaceDecls.clear();
            //cancels current xmlns if relevant
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix) && !namespaceURI.equals(receiver.getDefaultNamespace())) {
                receiver.namespace(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
                nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
            }
            if(attribs != null) {
                // output attributes
                for (int i = 0; i < attribs.getLength(); i++) {
                    if (!attribs.getQName(i).getLocalPart().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                        receiver.attribute(attribs.getQName(i), attribs.getValue(i));
                    }
                }
            }
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {
        try {
            nsSupport.popContext();
            receiver.endElement(namespaceURI, localName, qname);
            receiver.setDefaultNamespace(nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.Receiver#endElement(org.exist.dom.QName)
     */
    @Override
    public void endElement(final QName qname) throws SAXException {
        try {
            nsSupport.popContext();
            String prefix = qname.getPrefix();
            String namespaceURI = qname.getNamespaceURI();
            if(prefix == null) {
                prefix = XMLConstants.DEFAULT_NS_PREFIX;
            }
            
            if(namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            
            if(enforceXHTML && prefix.length() == 0 && namespaceURI.length() == 0) {
                namespaceURI = XHTML_NS;
                receiver.endElement(new QName(qname.getLocalPart(), namespaceURI, qname.getPrefix()));
            } else {
                receiver.endElement(qname);
            }
            receiver.setDefaultNamespace(nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.Receiver#attribute(org.exist.dom.QName, java.lang.String)
     */
    @Override
    public void attribute(final QName qname, final String value) throws SAXException {
        // ignore namespace declaration attributes
        if((qname.getPrefix() != null && XMLConstants.XMLNS_ATTRIBUTE.equals(qname.getPrefix())) || XMLConstants.XMLNS_ATTRIBUTE.equals(qname.getLocalPart())) {
            return;
        }
        
        try {
            receiver.attribute(qname, value);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.characters(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void characters(final CharSequence seq) throws SAXException {
        try {
            receiver.characters(seq);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.characters(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        try {
            receiver.processingInstruction(target, data);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.cdataSection(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endDTD()
     */
    @Override
    public void endDTD() throws SAXException {
        //Nothing to do
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws SAXException {
        try {
            receiver.documentType(name, publicId, systemId);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void highlightText(final CharSequence seq) {
        // not supported with this receiver
    }

    /* (non-Javadoc)
      * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
      */
    @Override
    public void startEntity(final String name) throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
     */
    @Override
    public void endEntity(final String name) throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startCDATA()
     */
    @Override
    public void startCDATA() throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endCDATA()
     */
    @Override
    public void endCDATA() throws SAXException {
        //Nothing to do
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
     */
    @Override
    public void comment(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.comment(new XMLString(ch, start, len));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void setCurrentNode(final INodeHandle node) {
        // just ignore.
    }

    @Override
    public Document getDocument() {
        //just ignore.
        return null;
    }
}
