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

    // Namespace stack (BaseX-style): flat list of (prefix, uri) pairs for all in-scope bindings.
    // nstack records the list size at each startElement so endElement can roll back declarations.
    private final List<String> nspaces = new ArrayList<>();
    private final Deque<Integer> nstack = new ArrayDeque<>();

    /**
     * When serializing an XDM this should be true,
     * otherwise false.
     *
     * XDM has different serialization rules
     * compared to retrieving resources from the database.
     */
    private boolean xdmSerialization = false;
    private boolean xml11 = false;
    @Nullable private java.text.Normalizer.Form normalizationForm = null;

    private final Deque<QName> elementName = new ArrayDeque<>();

    /**
     * Returns true if cdata-section-elements should be applied.
     * Subclasses (e.g., XHTMLWriter for HTML method) can override
     * to suppress CDATA sections.
     */
    protected boolean shouldUseCdataSections() {
        return xdmSerialization;
    }

    /**
     * Returns the namespace URI of the current (innermost) element,
     * or null if no element is on the stack.
     */
    protected String currentElementNamespaceURI() {
        final QName top = elementName.peek();
        return top != null ? top.getNamespaceURI() : null;
    }
    private LazyVal<Set<QName>> cdataSectionElements = new LazyVal<>(this::parseCdataSectionElementNames);
    private boolean cdataSetionElement = false;

    static {
        textSpecialChars = new boolean[128];
        Arrays.fill(textSpecialChars, false);
        textSpecialChars['<'] = true;
        textSpecialChars['>'] = true;
                textSpecialChars['\r'] = true;
        textSpecialChars['&'] = true;
        textSpecialChars[0x7F] = true; // DEL must be escaped as &#x7F;

        attrSpecialChars = new boolean[128];
        Arrays.fill(attrSpecialChars, false);
        attrSpecialChars['<'] = true;
        attrSpecialChars['>'] = true;
        attrSpecialChars['\r'] = true;
        attrSpecialChars['\n'] = true;
        attrSpecialChars['\t'] = true;
        attrSpecialChars['&'] = true;
        attrSpecialChars['"'] = true;
        attrSpecialChars[0x7F] = true; // DEL must be escaped as &#x7F;
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

        this.xdmSerialization = "yes".equals(outputProperties.getProperty(EXistOutputKeys.XDM_SERIALIZATION, "no"));
        this.xml11 = "1.1".equals(outputProperties.getProperty(OutputKeys.VERSION));
        this.normalizationForm = parseNormalizationForm(outputProperties.getProperty("normalization-form", "none"));
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
        nspaces.clear();
        nstack.clear();
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
        final String fromStack = nsLookup("");
        return (fromStack == null || fromStack.isEmpty()) ? null : fromStack;
    }

    public void setDefaultNamespace(final String namespace) {
        // Keep the baseline field in sync; nsLookup() falls back to it when the
        // namespace stack has no in-scope binding for the default prefix.
        defaultNamespace = namespace == null ? "" : namespace;
    }

    /**
     * Looks up the currently in-scope URI for {@code prefix} by scanning the flat
     * namespace list from innermost to outermost scope.
     * For the default-namespace prefix ({@code ""}), falls back to the
     * {@link #defaultNamespace} baseline field when the stack has no binding.
     *
     * @return the in-scope URI, or {@code null} if {@code prefix} is unbound
     */
    private String nsLookup(final String prefix) {
        for (int i = nspaces.size() - 2; i >= 0; i -= 2) {
            if (nspaces.get(i).equals(prefix)) {
                return nspaces.get(i + 1);
            }
        }
        if (prefix.isEmpty()) {
            return defaultNamespace.isEmpty() ? null : defaultNamespace;
        }
        return null;
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
            nstack.push(nspaces.size());
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
            nstack.push(nspaces.size());
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
            if (!nstack.isEmpty()) {
                nspaces.subList(nstack.pop(), nspaces.size()).clear();
            }
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
            if (!nstack.isEmpty()) {
                nspaces.subList(nstack.pop(), nspaces.size()).clear();
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        final String normPrefix = prefix != null ? prefix : "";
        final String normUri = nsURI != null ? nsURI : "";

        // The xml namespace is implicitly declared and never needs explicit serialization
        if ("xml".equals(normPrefix)) {
            return;
        }

        try {
            if (!tagIsOpen) {
                // An xmlns="" outside a start tag is harmless — just skip it
                if (normUri.isEmpty() && normPrefix.isEmpty()) {
                    return;
                }
                throw new TransformerException("Found a namespace declaration outside an element");
            }

            // Look up what is currently in scope for this prefix.
            // nsLookup scans nspaces from innermost to outermost and falls back to the
            // defaultNamespace baseline field for the default-namespace prefix.
            final String inScope = nsLookup(normPrefix);
            final String effective = inScope != null ? inScope : "";
            if (normUri.equals(effective)) {
                return;  // Binding unchanged — no declaration needed
            }

            // Record the new binding so descendants can see it via nsLookup
            nspaces.add(normPrefix);
            nspaces.add(normUri);

            // Write the namespace declaration
            writer.write(' ');
            if (normPrefix.isEmpty()) {
                writer.write("xmlns=\"");
            } else {
                writer.write("xmlns:");
                writer.write(normPrefix);
                writer.write("=\"");
            }
            writeChars(normUri, true);
            writer.write('"');
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    public void attribute(String qname, CharSequence value) throws TransformerException {
        try {
            if(!tagIsOpen) {
                    characters(value);
                    return;
            }
            // Coalesce ' ' + qname + '="' into a single bulk write when the
            // qname fits in the scratch buffer (typical case for short HTML
            // attribute names like class, href, style).
            writeAttributePrefix(qname);
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
            }
            final String prefix = qname.getPrefix();
            final String localPart = qname.getLocalPart();
            if (prefix != null && !prefix.isEmpty()) {
                writePrefixedAttributePrefix(prefix, localPart);
            } else {
                writeAttributePrefix(localPart);
            }
            writeChars(value, true);
            writer.write('"');
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    /**
     * Write {@code ' ' + qname + '="'} as a single {@code Writer.write(char[],
     * int, int)} call when {@code qname} fits in the scratch buffer. Reduces
     * 3 writer calls per attribute to 1.
     */
    private void writeAttributePrefix(final String qname) throws IOException {
        final int qlen = qname.length();
        final int needed = qlen + 3;  // ' ' + qname + '="'
        if (needed <= ATTR_SCRATCH_LEN) {
            attrScratch[0] = ' ';
            qname.getChars(0, qlen, attrScratch, 1);
            attrScratch[qlen + 1] = '=';
            attrScratch[qlen + 2] = '"';
            writer.write(attrScratch, 0, needed);
        } else {
            writer.write(' ');
            writer.write(qname);
            writer.write("=\"");
        }
    }

    /**
     * Write {@code ' ' + prefix + ':' + localPart + '="'} as a single bulk
     * write when it fits the scratch buffer.
     */
    private void writePrefixedAttributePrefix(final String prefix, final String localPart) throws IOException {
        final int plen = prefix.length();
        final int llen = localPart.length();
        final int needed = plen + llen + 4;  // ' ' + prefix + ':' + localPart + '="'
        if (needed <= ATTR_SCRATCH_LEN) {
            attrScratch[0] = ' ';
            prefix.getChars(0, plen, attrScratch, 1);
            attrScratch[plen + 1] = ':';
            localPart.getChars(0, llen, attrScratch, plen + 2);
            attrScratch[plen + llen + 2] = '=';
            attrScratch[plen + llen + 3] = '"';
            writer.write(attrScratch, 0, needed);
        } else {
            writer.write(' ');
            writer.write(prefix);
            writer.write(':');
            writer.write(localPart);
            writer.write("=\"");
        }
    }

    private static final int ATTR_SCRATCH_LEN = 96;
    private final char[] attrScratch = new char[ATTR_SCRATCH_LEN];

    public void characters(final CharSequence chars) throws TransformerException {
        if(!declarationWritten) {
            writeDeclaration();
        }

        try {
            if(tagIsOpen) {
                closeStartTag(false);
            }
            // When xdmSerialization is active and current element is in cdata-section-elements,
            // wrap text content in CDATA instead of escaping it (per W3C Serialization 3.1)
            if (shouldUseCdataSections() && !elementName.isEmpty()
                    && cdataSectionElements.get().contains(elementName.peek())) {
                writeCdataContent(chars);
            } else {
                writeChars(chars, false);
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    private void writeCdataContent(final CharSequence chars) throws IOException {
        // CDATA sections must be split when:
        // 1. The content contains "]]>" (which would end the CDATA prematurely)
        // 2. A character cannot be represented in the output encoding (must be escaped as &#xNN;)
        final String s = normalize(chars).toString();
        boolean inCdata = false;
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            final int cpLen = Character.charCount(cp);

            // Check for "]]>" sequence
            if (cp == ']' && i + 2 < s.length() && s.charAt(i + 1) == ']' && s.charAt(i + 2) == '>') {
                if (!inCdata) {
                    writer.write("<![CDATA[");
                    inCdata = true;
                }
                writer.write("]]");
                writer.write("]]>");
                inCdata = false;
                i += 2; // skip "]]", the ">" will be picked up next
                continue;
            }

            // Check if character is encodable in the output charset
            if (!charSet.inCharacterSet((char) cp)) {
                // Close any open CDATA section
                if (inCdata) {
                    writer.write("]]>");
                    inCdata = false;
                }
                // Write as character reference
                writer.write("&#x");
                writer.write(Integer.toHexString(cp));
                writer.write(';');
            } else {
                // Encodable character — write inside CDATA
                if (!inCdata) {
                    writer.write("<![CDATA[");
                    inCdata = true;
                }
                writer.write(s, i, cpLen);
            }
            i += cpLen;
        }
        if (inCdata) {
            writer.write("]]>");
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
            if (tagIsOpen) {
                if (isEmpty) {
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
            @Nullable final String standaloneOrig = outputProperties.getProperty(OutputKeys.STANDALONE, originalXmlDecl.standalone);
            // "omit" means standalone should be absent from the declaration
            @Nullable final String standalone = (standaloneOrig != null && "omit".equalsIgnoreCase(standaloneOrig.trim())) ? null : standaloneOrig;

            writeDeclaration(version, encoding, standalone);

            return;
        }

        final String omitXmlDecl = outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        @Nullable final String standaloneRaw = outputProperties.getProperty(OutputKeys.STANDALONE);
        // "omit" means standalone should be absent from the declaration
        @Nullable final String standalone = (standaloneRaw != null && "omit".equalsIgnoreCase(standaloneRaw.trim())) ? null : standaloneRaw;
        // Per W3C Serialization 3.1: output declaration if omit-xml-declaration is false/no/0,
        // or if standalone is explicitly set (the declaration is required to carry standalone)
        if (isBooleanFalse(omitXmlDecl) || standalone != null) {
            // get the fields of the declaration from the serialization properties
            final String version = outputProperties.getProperty(OutputKeys.VERSION, DEFAULT_XML_VERSION);
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, DEFAULT_XML_ENCODING);

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
                // Normalize boolean values to yes/no for XML declaration
                final String standaloneVal = standalone.trim();
                if ("true".equals(standaloneVal) || "1".equals(standaloneVal)) {
                    writer.write("yes");
                } else if ("false".equals(standaloneVal) || "0".equals(standaloneVal)) {
                    writer.write("no");
                } else {
                    writer.write(standaloneVal);
                }
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

    /**
     * Whether &amp; before { should be escaped. HTML output returns false
     * per W3C HTML serialization spec. XML output returns true (always escape &amp;).
     */
    protected boolean escapeAmpersandBeforeBrace() {
        return true;
    }

    /**
     * Check if a serialization boolean parameter value is false.
     * W3C Serialization 3.1 accepts "no", "false", "0" (with optional whitespace) as false.
     */
    protected static boolean isBooleanFalse(final String value) {
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim();
        return "no".equals(trimmed) || "false".equals(trimmed) || "0".equals(trimmed);
    }

    /**
     * Whether the given character needs escaping. Subclasses can override
     * to suppress escaping for specific contexts (e.g., HTML raw text elements).
     *
     * @param ch the character to check
     * @param inAttribute true if we're writing an attribute value
     */
    protected boolean needsEscape(final char ch, final boolean inAttribute) {
        return needsEscape(ch);
    }

    /**
     * Whether the current context requires character escaping at all.
     * Subclasses (e.g., HTML5Writer for {@code <script>} / {@code <style>}
     * raw-text content) override to return {@code false} when the entire
     * text can be written verbatim, letting {@link #writeChars} skip the
     * per-character specialChars check and emit one bulk write.
     *
     * @param inAttribute true if we're writing an attribute value
     */
    protected boolean needsEscaping(final boolean inAttribute) {
        return true;
    }

    protected void writeChars(final CharSequence s, final boolean inAttribute) throws IOException {
        // Apply Unicode normalization if configured
        final CharSequence text = normalize(s);
        final boolean[] specialChars = inAttribute ? attrSpecialChars : textSpecialChars;
        // In raw-text contexts (HTML5 script/style) no escaping is needed, so
        // skip the per-char specialChars check — only encoding-incompatible
        // chars need to break the bulk run.
        final boolean checkSpecials = needsEscaping(inAttribute);
        char ch = 0;
        final int len = text.length();
        int pos = 0, i;
        while(pos < len) {
            i = pos;
            while(i < len) {
                ch = text.charAt(i);
                if (ch < 128) {
                    if (checkSpecials && specialChars[ch]) {
                        break;
                    } else if (xml11 && ch >= 0x01 && ch <= 0x1F
                            && ch != 0x09 && ch != 0x0A && ch != 0x0D) {
                        // XML 1.1: C0 control chars (except TAB, LF, CR) must be escaped
                        break;
                    } else {
                        i++;
                    }
                } else if (!charSet.inCharacterSet(ch)) {
                    break;
                } else if (ch >= 0x7F && ch <= 0x9F) {
                    // Control chars 0x7F-0x9F must be serialized as character references
                    break;
                } else if (ch == 0x2028) {
                    // LINE SEPARATOR must be serialized as character reference
                    break;
                } else {
                    i++;
                }
            }
            writeCharSeq(text, pos, i);
            // writer.write(s.subSequence(pos, i).toString());
            
            if (i >= len) {
                return;
            }
            
            if (needsEscape(ch, inAttribute)) {
                switch(ch) {
                    case '<' -> writer.write("&lt;");
                    case '>' -> writer.write("&gt;");
                    case '&' -> {
                        // HTML spec: & before { in attribute values should not be escaped
                        if (inAttribute && i + 1 < len && text.charAt(i + 1) == '{' && !escapeAmpersandBeforeBrace()) {
                            writer.write('&');
                        } else {
                            writer.write("&amp;");
                        }
                    }
                    case '\r' -> writer.write("&#xD;");
                    case '\n' -> writer.write("&#xA;");
                    case '\t' -> writer.write("&#x9;");
                    case '"' -> writer.write("&#34;");
                    default -> writeCharacterReference(ch);
                }
            } else {
                writer.write(ch);
            }
            
            pos = ++i;
        }
    }

    protected void writeCharSeq(final CharSequence ch, final int start, final int end) throws IOException {
        final int len = end - start;
        if (len <= 0) {
            return;
        }
        switch (ch) {
            case String s -> writer.write(s, start, len);
            case CharSlice cs -> cs.write(writer, start, len);
            case StringBuilder sb -> {
                sb.getChars(start, end, ensureCharBuffer(len), 0);
                writer.write(charBuffer, 0, len);
            }
            case StringBuffer sb -> {
                sb.getChars(start, end, ensureCharBuffer(len), 0);
                writer.write(charBuffer, 0, len);
            }
            default -> {
                // Generic CharSequence — copy then bulk-write
                final char[] buf = ensureCharBuffer(len);
                for (int i = 0; i < len; i++) {
                    buf[i] = ch.charAt(start + i);
                }
                writer.write(buf, 0, len);
            }
        }
    }

    /**
     * Scratch buffer for bulk character writes. Grows as needed and is
     * reused across serializations within the same pool-borrow cycle.
     * Thread-safe: XMLWriter instances are borrowed exclusively from
     * {@link SerializerPool} (Commons Pool2), guaranteeing no concurrent
     * access to the same instance.
     */
    private char[] charBuffer;

    private char[] ensureCharBuffer(final int minLen) {
        if (charBuffer == null || charBuffer.length < minLen) {
            // Grow to a power-of-two-ish size to amortize re-allocation
            int n = charBuffer == null ? 256 : charBuffer.length;
            while (n < minLen) {
                n <<= 1;
            }
            charBuffer = new char[n];
        }
        return charBuffer;
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

    @Nullable
    private static java.text.Normalizer.Form parseNormalizationForm(final String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "NFC" -> java.text.Normalizer.Form.NFC;
            case "NFD" -> java.text.Normalizer.Form.NFD;
            case "NFKC" -> java.text.Normalizer.Form.NFKC;
            case "NFKD" -> java.text.Normalizer.Form.NFKD;
            case "NONE", "" -> null;
            default -> null;  // "fully-normalized" or unknown — treated as none
        };
    }

    /**
     * Apply Unicode normalization if a normalization-form is set.
     */
    protected CharSequence normalize(final CharSequence text) {
        if (normalizationForm == null) return text;
        final String s = text.toString();
        if (java.text.Normalizer.isNormalized(s, normalizationForm)) return text;
        return java.text.Normalizer.normalize(s, normalizationForm);
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
