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
package org.exist.util.serializer;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import com.evolvedbinary.j8fu.lazy.LazyVal;
import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.CharSlice;
import org.exist.util.serializer.encodings.CharacterSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Write XML to a writer. This class defines methods similar to SAX. It deals
 * with opening and closing tags, writing attributes and so on.
 * 
 * @author wolf
 */
public class XMLWriter implements SerializerWriter {

    private final static IllegalStateException EX_CHARSET_NULL = new IllegalStateException("Charset should never be null!");
    
    protected final static Properties defaultProperties = new Properties();
    static {
        defaultProperties.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "no");
        defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        defaultProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "no");
    }

    private static final String DEFAULT_XML_VERSION = "1.0";
    private static final String DEFAULT_XML_ENCODING = UTF_8.name();

    protected Writer writer = null;

    protected CharacterSet charSet;

    protected boolean tagIsOpen = false;

    protected boolean tagIsEmpty = true;

    protected boolean declarationWritten = false;

    protected boolean doctypeWritten = false;
    
    protected Properties outputProperties;

    private final char[] charref = new char[10];

    private static final boolean[] textSpecialChars;

    private static final boolean[] attrSpecialChars;

    private String defaultNamespace = "";

    /**
     * When serializing an XDM this should be true,
     * otherwise false.
     *
     * XDM has different serialization rules
     * compared to retrieving resources from the database.
     */
    private boolean xdmSerialization = false;

    private final Deque<QName> elementName = new ArrayDeque<>();
    private LazyVal<Set<QName>> cdataSectionElements = new LazyVal<>(this::parseCdataSectionElementNames);
    private boolean cdataSetionElement = false;

    static {
        textSpecialChars = new boolean[128];
        Arrays.fill(textSpecialChars, false);
        textSpecialChars['<'] = true;
        textSpecialChars['>'] = true;
        // textSpecialChars['\r'] = true;
        textSpecialChars['&'] = true;

        attrSpecialChars = new boolean[128];
        Arrays.fill(attrSpecialChars, false);
        attrSpecialChars['<'] = true;
        attrSpecialChars['>'] = true;
        attrSpecialChars['\r'] = true;
        attrSpecialChars['\n'] = true;
        attrSpecialChars['\t'] = true;
        attrSpecialChars['&'] = true;
        attrSpecialChars['"'] = true;
    }

    @Nullable private XMLDeclaration originalXmlDecl;

    public XMLWriter() {
        charSet = CharacterSet.getCharacterSet(UTF_8.name());
        if(charSet == null) {
            throw EX_CHARSET_NULL;
        }
    }

    public XMLWriter(final Writer writer) {
        this();
        this.writer = writer;
    }

    /**
     * Set the output properties.
     * 
     * @param properties outputProperties
     */
    public void setOutputProperties(final Properties properties) {
        outputProperties = Objects.requireNonNullElseGet(properties, () -> new Properties(defaultProperties));

        final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, DEFAULT_XML_ENCODING);
        this.charSet = CharacterSet.getCharacterSet(encoding);
        if(this.charSet == null) {
            throw EX_CHARSET_NULL;
        }

        this.xdmSerialization = outputProperties.getProperty(EXistOutputKeys.XDM_SERIALIZATION, "no").equals("yes");
    }

    private Set<QName> parseCdataSectionElementNames() {
        final String s = outputProperties.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (s == null || s.isEmpty()) {
            return Collections.EMPTY_SET;
        }

        final Set<QName> qnames = new HashSet<>();
        for (final String uriQualifiedName : s.split("\\s")) {
            qnames.add(QName.fromURIQualifiedName(uriQualifiedName));
        }
        return qnames;
    }

    public void reset() {
        writer = null;
        resetObjectState();
    }

    protected void resetObjectState() {
        tagIsOpen = false;
        tagIsEmpty = true;
        declarationWritten = false;
        originalXmlDecl = null;
        doctypeWritten = false;
        defaultNamespace = "";
        cdataSectionElements = new LazyVal<>(this::parseCdataSectionElementNames);
    }

    /**
     * Set a new writer. Calling this method will reset the state of the object.
     * 
     * @param writer the writer
     */
    public void setWriter(final Writer writer) {
        this.writer = writer;
        resetObjectState();
    }
    
    public Writer getWriter() {
        return writer;
    }

    public String getDefaultNamespace() {
        return defaultNamespace.isEmpty() ? null : defaultNamespace;
    }

    public void setDefaultNamespace(final String namespace) {
        defaultNamespace = namespace == null ? "" : namespace;
    }
	
    public void startDocument() throws TransformerException {
        resetObjectState();
    }

    @Override
    public void declaration(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) throws TransformerException {
        this.originalXmlDecl = new XMLDeclaration(version, encoding, standalone);
    }

    public void endDocument() throws TransformerException {
    }

    public void startElement(final String namespaceUri, final String localName, final String qname) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }
        
        if(!doctypeWritten) {
            writeDoctype(qname);
        }
        
        try {
            if(tagIsOpen) {
                closeStartTag(false);
            }
            writer.write('<');
            writer.write(qname);
            tagIsOpen = true;
            try {
                elementName.push(QName.parse(namespaceUri, qname));
            } catch (final QName.IllegalQNameException e) {
                throw new TransformerException(e.getMessage(), e);
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void startElement(final QName qname) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }
        
        if(!doctypeWritten) {
            writeDoctype(qname.getStringValue());
        }
        
        try {
            if(tagIsOpen) {
                closeStartTag(false);
            }
            writer.write('<');
            if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                writer.write(qname.getPrefix());
                writer.write(':');
            }
            
            writer.write(qname.getLocalPart());
            tagIsOpen = true;
            elementName.push(qname);
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        try {
            if (tagIsOpen) {
                closeStartTag(true);
            } else {
                writer.write("</");
                writer.write(qname);
                writer.write('>');
            }
            elementName.pop();
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void endElement(final QName qname) throws TransformerException {
        try {
            if(tagIsOpen) {
                closeStartTag(true);
            } else {
                writer.write("</");
                if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                    writer.write(qname.getPrefix());
                    writer.write(':');
                }
                writer.write(qname.getLocalPart());
                writer.write('>');
            }
            elementName.pop();
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        if((nsURI == null) && (prefix == null || prefix.isEmpty())) {
            return;
        }

        try {						
            if(!tagIsOpen) {
                throw new TransformerException("Found a namespace declaration outside an element");
            }

            if(prefix != null && !prefix.isEmpty()) {
                writer.write(' ');
                writer.write("xmlns");
                writer.write(':');
                writer.write(prefix);
                writer.write("=\"");
                writeChars(nsURI, true);
                writer.write('"');
            } else {
                if(defaultNamespace.equals(nsURI)) {
                    return;	
                }
                writer.write(' ');
                writer.write("xmlns");
                writer.write("=\"");
                writeChars(nsURI, true);
                writer.write('"');
                defaultNamespace= nsURI;				
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void attribute(String qname, CharSequence value) throws TransformerException {
        try {
            if(!tagIsOpen) {
                    characters(value);
                    return;
                    // throw new TransformerException("Found an attribute outside an
                    // element");
            }
            writer.write(' ');
            writer.write(qname);
            writer.write("=\"");
            writeChars(value, true);
            writer.write('"');
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void attribute(final QName qname, final CharSequence value) throws TransformerException {
        try {
            if(!tagIsOpen) {
                characters(value);
                return;
                // throw new TransformerException("Found an attribute outside an
                // element");
            }
            writer.write(' ');
            if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                writer.write(qname.getPrefix());
                writer.write(':');
            }
            writer.write(qname.getLocalPart());
            writer.write("=\"");
            writeChars(value, true);
            writer.write('"');
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void characters(final CharSequence chars) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }

        try {
            if(tagIsOpen) {
                closeStartTag(false);
            }
            writeChars(chars, false);
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void characters(final char[] ch, final int start, final int len) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }
        if (cdataSetionElement) {
            try {
                writer.write(ch, start, len);
            } catch (final IOException e) {
                throw new TransformerException(e.getMessage(), e);
            }
        } else {
            characters(new CharSlice(ch, start, len));
        }
    }

    public void processingInstruction(final String target, final String data) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }
        
        try {
            if(tagIsOpen) {
                    closeStartTag(false);
            }
            writer.write("<?");
            writer.write(target);
            if(data != null && !data.isEmpty()) {
                writer.write(' ');
                writer.write(data);
            }
            writer.write("?>");
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void comment(final CharSequence data) throws TransformerException {
        if (!declarationWritten) {
            writeDeclaration();
        }
            
        try {
            if(tagIsOpen) {
                closeStartTag(false);
            }

            writer.write("<!--");
            writer.write(data.toString());
            writer.write("-->");
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void startCdataSection() throws TransformerException {
        if(tagIsOpen) {
            closeStartTag(false);
        }

        if ((!xdmSerialization) || cdataSectionElements.get().contains(elementName.peek())) {
            try {
                writer.write("<![CDATA[");
                this.cdataSetionElement = true;
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
        }
    }

    public void endCdataSection() throws TransformerException {
        if ((!xdmSerialization) || cdataSectionElements.get().contains(elementName.peek())) {
            try {
                writer.write("]]>");
                this.cdataSetionElement = false;
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
        }
    }

    public void cdataSection(final char[] ch, final int start, final int len) throws TransformerException {
        startCdataSection();
        try {
            writer.write(ch, start, len);
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
        endCdataSection();
    }

    public void startDocumentType(final String name, final String publicId, final String systemId) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }

        try {
            writer.write("<!DOCTYPE ");
            writer.write(name);
            if(publicId != null) {
                //writer.write(" PUBLIC \"" + publicId + "\"");
                writer.write(" PUBLIC \"" + publicId.replaceAll("&#160;", " ") + "\"");	//workaround for XHTML doctype, declare does not allow spaces so use &#160; instead and then replace each &#160; with a space here - Adam
            }

            if(systemId != null) {
                if(publicId == null) {
                    writer.write(" SYSTEM");
                }
                writer.write(" \"" + systemId + "\"");
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void endDocumentType() throws TransformerException {
        try {
            writer.write(">");
            doctypeWritten = true;
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void documentType(final String name, final String publicId, final String systemId) throws TransformerException {
        startDocumentType(name, publicId, systemId);
        endDocumentType();
    }

    protected void closeStartTag(final boolean isEmpty) throws TransformerException {
        try {
            if(tagIsOpen) {
                if(isEmpty) {
                    writer.write("/>");
                } else {
                    writer.write('>');
                }
                tagIsOpen = false;
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    protected void writeDeclaration() throws TransformerException {
        if(declarationWritten) {
            return;
        }

        if(outputProperties == null) {
            outputProperties = new Properties(defaultProperties);
        }
        declarationWritten = true;

        final String omitOriginalXmlDecl = outputProperties.getProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "yes");
        if (originalXmlDecl != null && "no".equals(omitOriginalXmlDecl)) {
            // get the fields of the persisted xml declaration, but overridden with any properties from the serialization properties
            final String version = outputProperties.getProperty(OutputKeys.VERSION, (originalXmlDecl.version != null ? originalXmlDecl.version : DEFAULT_XML_VERSION));
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, (originalXmlDecl.encoding != null ? originalXmlDecl.encoding : DEFAULT_XML_ENCODING));
            @Nullable final String standalone = outputProperties.getProperty(OutputKeys.STANDALONE, originalXmlDecl.standalone);

            writeDeclaration(version, encoding, standalone);

            return;
        }

        final String omitXmlDecl = outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        if ("no".equals(omitXmlDecl)) {
            // get the fields of the declaration from the serialization properties
            final String version = outputProperties.getProperty(OutputKeys.VERSION, DEFAULT_XML_VERSION);
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, DEFAULT_XML_ENCODING);
            @Nullable final String standalone = outputProperties.getProperty(OutputKeys.STANDALONE);

            writeDeclaration(version, encoding, standalone);
        }
    }

    private void writeDeclaration(final String version, final String encoding, @Nullable final String standalone) throws TransformerException {
        try {
            writer.write("<?xml version=\"");
            writer.write(version);
            writer.write("\" encoding=\"");
            writer.write(encoding);
            writer.write('"');
            if(standalone != null) {
                writer.write(" standalone=\"");
                writer.write(standalone);
                writer.write('"');
            }
            writer.write("?>\n");
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    protected void writeDoctype(final String rootElement) throws TransformerException {
        if(doctypeWritten) {
            return;
        }
        final String publicId = outputProperties.getProperty(OutputKeys.DOCTYPE_PUBLIC);
        final String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);

        if(publicId != null || systemId != null) {
            documentType(rootElement, publicId, systemId);
        }
        doctypeWritten = true;
    }
    
    protected boolean needsEscape(final char ch) {
    	return true;
    }
    
    protected void writeChars(final CharSequence s, final boolean inAttribute) throws IOException {
        final boolean[] specialChars = inAttribute ? attrSpecialChars : textSpecialChars;
        char ch = 0;
        final int len = s.length();
        int pos = 0, i;
        while(pos < len) {
            i = pos;
            while(i < len) {
                ch = s.charAt(i);
                if(ch < 128) {
                    if(specialChars[ch]) {
                        break;
                    } else {
                        i++;
                    }
                } else if(!charSet.inCharacterSet(ch)) {
                    break;
                } else {
                    i++;
                }
            }
            writeCharSeq(s, pos, i);
            // writer.write(s.subSequence(pos, i).toString());
            
            if (i >= len) {
                return;
            }
            
            if(needsEscape(ch)) {
                switch(ch) {
                    case '<':
                        writer.write("&lt;");
                        break;
                    case '>':
                        writer.write("&gt;");
                        break;
                    case '&':
                        writer.write("&amp;");
                        break;
                    case '\r':
                        writer.write("&#xD;");
                        break;
                    case '\n':
                        writer.write("&#xA;");
                        break;
                    case '\t':
                        writer.write("&#x9;");
                        break;
                    case '"':
                        writer.write("&#34;");
                        break;
                    default:
                        writeCharacterReference(ch);
                }
            } else {
                writer.write(ch);
            }
            
            pos = ++i;
        }
    }

    private void writeCharSeq(final CharSequence ch, final int start, final int end) throws IOException {
        for(int i = start; i < end; i++) {
            writer.write(ch.charAt(i));
        }
    }

    protected void writeCharacterReference(final char charval) throws IOException {
        int o = 0;
        charref[o++] = '&';
        charref[o++] = '#';
        charref[o++] = 'x';
        final String code = Integer.toHexString(charval);
        final int len = code.length();
        for(int k = 0; k < len; k++) {
            charref[o++] = code.charAt(k);
        }
        charref[o++] = ';';
        writer.write(charref, 0, o);
    }

    private static class XMLDeclaration {
        @Nullable final String version;
        @Nullable final String encoding;
        @Nullable final String standalone;

        private XMLDeclaration(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) {
            this.version = version;
            this.encoding = encoding;
            this.standalone = standalone;
        }
    }
}
