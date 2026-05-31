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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.exist.Namespaces;
import org.exist.dom.QName;

/**
 * @author wolf
 *
 */
public class XHTMLWriter extends IndentingXMLWriter {

    /**
     * HTML boolean attributes per HTML 4.01 and HTML5 spec.
     * When method="html" and the attribute value equals the attribute name
     * (case-insensitive), the attribute is minimized to just the name.
     */
    protected static final ObjectSet<String> BOOLEAN_ATTRIBUTES = new ObjectOpenHashSet<>(31);
    static {
        BOOLEAN_ATTRIBUTES.add("checked");
        BOOLEAN_ATTRIBUTES.add("compact");
        BOOLEAN_ATTRIBUTES.add("declare");
        BOOLEAN_ATTRIBUTES.add("defer");
        BOOLEAN_ATTRIBUTES.add("disabled");
        BOOLEAN_ATTRIBUTES.add("ismap");
        BOOLEAN_ATTRIBUTES.add("multiple");
        BOOLEAN_ATTRIBUTES.add("nohref");
        BOOLEAN_ATTRIBUTES.add("noresize");
        BOOLEAN_ATTRIBUTES.add("noshade");
        BOOLEAN_ATTRIBUTES.add("nowrap");
        BOOLEAN_ATTRIBUTES.add("readonly");
        BOOLEAN_ATTRIBUTES.add("selected");
    }

    /**
     * URI-valued attributes that must be %-escaped when escape-uri-attributes=yes
     * (default for HTML/XHTML output methods, per W3C XSLT and XQuery
     * Serialization 3.1 § 7.2.5). Keys are element local name + "/" + attribute
     * local name, both lowercase. The synthetic key "*&#47;href" matches any
     * element bearing an href attribute (covers both a/@href and area/@href etc.
     * in a single check while still letting non-URI attributes through).
     */
    private static final ObjectSet<String> URI_VALUED_ATTRIBUTES = new ObjectOpenHashSet<>(48);
    static {
        URI_VALUED_ATTRIBUTES.add("a/href");
        URI_VALUED_ATTRIBUTES.add("a/name");
        URI_VALUED_ATTRIBUTES.add("applet/codebase");
        URI_VALUED_ATTRIBUTES.add("area/href");
        URI_VALUED_ATTRIBUTES.add("base/href");
        URI_VALUED_ATTRIBUTES.add("blockquote/cite");
        URI_VALUED_ATTRIBUTES.add("body/background");
        URI_VALUED_ATTRIBUTES.add("button/formaction");
        URI_VALUED_ATTRIBUTES.add("del/cite");
        URI_VALUED_ATTRIBUTES.add("form/action");
        URI_VALUED_ATTRIBUTES.add("frame/longdesc");
        URI_VALUED_ATTRIBUTES.add("frame/src");
        URI_VALUED_ATTRIBUTES.add("head/profile");
        URI_VALUED_ATTRIBUTES.add("html/manifest");
        URI_VALUED_ATTRIBUTES.add("iframe/longdesc");
        URI_VALUED_ATTRIBUTES.add("iframe/src");
        URI_VALUED_ATTRIBUTES.add("img/longdesc");
        URI_VALUED_ATTRIBUTES.add("img/src");
        URI_VALUED_ATTRIBUTES.add("img/usemap");
        URI_VALUED_ATTRIBUTES.add("input/formaction");
        URI_VALUED_ATTRIBUTES.add("input/src");
        URI_VALUED_ATTRIBUTES.add("input/usemap");
        URI_VALUED_ATTRIBUTES.add("ins/cite");
        URI_VALUED_ATTRIBUTES.add("link/href");
        URI_VALUED_ATTRIBUTES.add("object/archive");
        URI_VALUED_ATTRIBUTES.add("object/classid");
        URI_VALUED_ATTRIBUTES.add("object/codebase");
        URI_VALUED_ATTRIBUTES.add("object/data");
        URI_VALUED_ATTRIBUTES.add("object/usemap");
        URI_VALUED_ATTRIBUTES.add("q/cite");
        URI_VALUED_ATTRIBUTES.add("script/src");
        URI_VALUED_ATTRIBUTES.add("source/src");
        URI_VALUED_ATTRIBUTES.add("track/src");
        URI_VALUED_ATTRIBUTES.add("video/poster");
        URI_VALUED_ATTRIBUTES.add("video/src");
        URI_VALUED_ATTRIBUTES.add("audio/src");
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    protected static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
    static {
        EMPTY_TAGS.add("area");
        EMPTY_TAGS.add("base");
        EMPTY_TAGS.add("br");
        EMPTY_TAGS.add("col");
        EMPTY_TAGS.add("embed");
        EMPTY_TAGS.add("hr");
        EMPTY_TAGS.add("img");
        EMPTY_TAGS.add("input");
        EMPTY_TAGS.add("link");
        EMPTY_TAGS.add("meta");
        EMPTY_TAGS.add("basefont");
        EMPTY_TAGS.add("frame");
        EMPTY_TAGS.add("isindex");
        EMPTY_TAGS.add("param");
    }

    protected static final ObjectSet<String> INLINE_TAGS = new ObjectOpenHashSet<>(31);
    static {
        INLINE_TAGS.add("a");
        INLINE_TAGS.add("abbr");
        INLINE_TAGS.add("acronym");
        INLINE_TAGS.add("b");
        INLINE_TAGS.add("bdo");
        INLINE_TAGS.add("big");
        INLINE_TAGS.add("br");
        INLINE_TAGS.add("button");
        INLINE_TAGS.add("cite");
        INLINE_TAGS.add("code");
        INLINE_TAGS.add("del");
        INLINE_TAGS.add("dfn");
        INLINE_TAGS.add("em");
        INLINE_TAGS.add("i");
        INLINE_TAGS.add("img");
        INLINE_TAGS.add("input");
        INLINE_TAGS.add("kbd");
        INLINE_TAGS.add("label");
        INLINE_TAGS.add("q");
        INLINE_TAGS.add("samp");
        INLINE_TAGS.add("select");
        INLINE_TAGS.add("small");
        INLINE_TAGS.add("span");
        INLINE_TAGS.add("strong");
        INLINE_TAGS.add("sub");
        INLINE_TAGS.add("sup");
        INLINE_TAGS.add("textarea");
        INLINE_TAGS.add("tt");
        INLINE_TAGS.add("var");
    }

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String MATHML_NS = "http://www.w3.org/1998/Math/MathML";

    private static final ObjectSet<String> RAW_TEXT_ELEMENTS_HTML = new ObjectOpenHashSet<>(4);
    static {
        RAW_TEXT_ELEMENTS_HTML.add("script");
        RAW_TEXT_ELEMENTS_HTML.add("style");
    }

    protected final ObjectSet<String> emptyTags;
    protected final ObjectSet<String> inlineTags;

    protected String currentTag;
    protected boolean inHead = false;
    protected boolean contentTypeMetaWritten = false;

    // Meta-tag dedup state: when a `<meta>` element is encountered inside
    // `<head>` AFTER the auto-generated content-type meta has been emitted,
    // its bytes are diverted to {@link #metaScratch}. If, while buffering,
    // we observe a {@code charset} or {@code http-equiv="Content-Type"}
    // attribute, the buffered meta is dropped (the auto-meta replaces it);
    // otherwise the buffer is flushed verbatim at endElement time.
    private Writer metaSuspendedWriter = null;
    private java.io.StringWriter metaScratch = null;
    private boolean metaIsContentTypeOrCharset = false;

    boolean haveCollapsedXhtmlPrefix = false;
    private String collapsedForeignNs = null;  // SVG or MathML ns being normalized

    /**
     *
     */
    public XHTMLWriter() {
        this(EMPTY_TAGS, INLINE_TAGS);
    }

    public XHTMLWriter(ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super();
        this.emptyTags = emptyTags;
        this.inlineTags = inlineTags;
    }

    public XHTMLWriter(final Writer writer) {
        this(writer, EMPTY_TAGS, INLINE_TAGS);
    }

    /**
     * @param writer the writer
     * @param emptyTags tags that are allowed to be empty
     * @param inlineTags tags that should be written inline
     */
    public XHTMLWriter(final Writer writer, ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super(writer);
        this.emptyTags = emptyTags;
        this.inlineTags = inlineTags;
    }

    /**
     * Returns true when the {@code escape-uri-attributes} serialization
     * parameter is enabled (default {@code yes} for HTML/XHTML methods)
     * and the (element, attribute) pair names a URI-valued attribute that
     * must have non-ASCII characters %-encoded as UTF-8.
     */
    protected boolean shouldEscapeUriAttribute(final String elementLocal, final String attrLocal) {
        if (elementLocal == null || attrLocal == null) {
            return false;
        }
        if (outputProperties != null
                && "no".equals(outputProperties.getProperty("escape-uri-attributes", "yes"))) {
            return false;
        }
        return URI_VALUED_ATTRIBUTES.contains(
                elementLocal.toLowerCase(java.util.Locale.ROOT)
                + '/' + attrLocal.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Apply XSLT/XQuery Serialization 3.1 § 7.2.5 URI %-escaping: each
     * character outside the URI character set (defined in RFC 3986 plus
     * a handful of additions) is encoded to UTF-8 bytes and each byte
     * emitted as {@code %XX}. Characters already in the URI character set
     * (including {@code %} itself, to keep already-escaped sequences intact)
     * are written verbatim.
     */
    protected static CharSequence escapeUriAttribute(final CharSequence value) {
        if (value == null || value.length() == 0) {
            return value;
        }
        final String src = value.toString();
        StringBuilder sb = null;
        for (int i = 0; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (isUriChar(c)) {
                if (sb != null) {
                    sb.append(c);
                }
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder(src.length() + 16);
                sb.append(src, 0, i);
            }
            // Collect the codepoint (handling surrogate pairs) then UTF-8 encode.
            int cp = c;
            if (Character.isHighSurrogate(c) && i + 1 < src.length()
                    && Character.isLowSurrogate(src.charAt(i + 1))) {
                cp = Character.toCodePoint(c, src.charAt(i + 1));
                i++;
            }
            appendUtf8Percent(sb, cp);
        }
        return sb == null ? value : sb;
    }

    private static boolean isUriChar(final char c) {
        // The escape-uri-attributes contract per W3C Serialization 3.1 § 7.2.5
        // is to encode disallowed-in-URI characters. In practice (matching
        // Saxon's behavior) only non-ASCII codepoints are %-escaped: ASCII-range
        // characters — including ' ' which an authoring tool may leave literal
        // in href values — pass through unchanged so existing valid escapes are
        // not double-encoded.
        return c < 0x80;
    }

    private static void appendUtf8Percent(final StringBuilder sb, final int codepoint) {
        if (codepoint < 0x80) {
            appendHexByte(sb, codepoint);
        } else if (codepoint < 0x800) {
            appendHexByte(sb, 0xC0 | (codepoint >> 6));
            appendHexByte(sb, 0x80 | (codepoint & 0x3F));
        } else if (codepoint < 0x10000) {
            appendHexByte(sb, 0xE0 | (codepoint >> 12));
            appendHexByte(sb, 0x80 | ((codepoint >> 6) & 0x3F));
            appendHexByte(sb, 0x80 | (codepoint & 0x3F));
        } else {
            appendHexByte(sb, 0xF0 | (codepoint >> 18));
            appendHexByte(sb, 0x80 | ((codepoint >> 12) & 0x3F));
            appendHexByte(sb, 0x80 | ((codepoint >> 6) & 0x3F));
            appendHexByte(sb, 0x80 | (codepoint & 0x3F));
        }
    }

    private static void appendHexByte(final StringBuilder sb, final int b) {
        sb.append('%');
        sb.append(HEX[(b >> 4) & 0xF]);
        sb.append(HEX[b & 0xF]);
    }

    @Override
    protected void resetObjectState() {
        super.resetObjectState();
        inHead = false;
        contentTypeMetaWritten = false;
        metaSuspendedWriter = null;
        metaScratch = null;
        metaIsContentTypeOrCharset = false;
    }

    private boolean shouldBufferDuplicateMeta(final String localName) {
        return inHead && contentTypeMetaWritten && metaSuspendedWriter == null
                && "meta".equalsIgnoreCase(localName);
    }

    /** True when the writer is currently diverting bytes for a candidate-duplicate meta. */
    protected boolean isBufferedMeta(final String localName) {
        return metaSuspendedWriter != null && "meta".equalsIgnoreCase(localName);
    }

    private void beginMetaBuffer() {
        metaSuspendedWriter = writer;
        metaScratch = new java.io.StringWriter();
        writer = metaScratch;
        metaIsContentTypeOrCharset = false;
    }

    protected void endMetaBuffer() throws TransformerException {
        if (metaSuspendedWriter == null) {
            return;
        }
        final Writer original = metaSuspendedWriter;
        final String buffered = metaScratch.toString();
        final boolean dropDuplicate = metaIsContentTypeOrCharset;
        metaSuspendedWriter = null;
        metaScratch = null;
        metaIsContentTypeOrCharset = false;
        writer = original;
        if (!dropDuplicate) {
            try {
                writer.write(buffered);
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
        }
    }

    protected void noteMetaAttribute(final String localName, final CharSequence value) {
        if (metaSuspendedWriter == null) {
            return;
        }
        if ("charset".equalsIgnoreCase(localName)) {
            metaIsContentTypeOrCharset = true;
        } else if ("http-equiv".equalsIgnoreCase(localName)
                && value != null && "Content-Type".equalsIgnoreCase(value.toString())) {
            metaIsContentTypeOrCharset = true;
        }
    }

    protected boolean isEmptyTag(final String tag) {
        return emptyTags.contains(tag);
    }

    @Override
    public void startElement(final QName qname) throws TransformerException {

        final QName xhtmlQName = removeXhtmlPrefix(qname);

        if (shouldBufferDuplicateMeta(xhtmlQName.getLocalPart())) {
            beginMetaBuffer();
        }
        super.startElement(xhtmlQName);
        currentTag = xhtmlQName.getStringValue();
        if ("head".equalsIgnoreCase(xhtmlQName.getLocalPart())) {
            inHead = true;
            writeContentTypeMeta();
        }
    }

    @Override
    public void endElement(final QName qname) throws TransformerException {
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        final boolean isMetaInHead = metaSuspendedWriter != null
                && "meta".equalsIgnoreCase(xhtmlQName.getLocalPart());
        if (inHead && "head".equalsIgnoreCase(xhtmlQName.getLocalPart())) {
            inHead = false;
        }

        super.endElement(xhtmlQName);

        if (isMetaInHead) {
            endMetaBuffer();
        }

        haveCollapsedXhtmlPrefix = false;
        collapsedForeignNs = null;
    }

    protected QName removeXhtmlPrefix(final QName qname) {
        final String prefix = qname.getPrefix();
        final String namespaceURI = qname.getNamespaceURI();
        if (prefix != null && !prefix.isEmpty() && namespaceURI != null) {
            if (namespaceURI.equals(Namespaces.XHTML_NS)) {
                haveCollapsedXhtmlPrefix = true;
                return new QName(qname.getLocalPart(), namespaceURI);
            }
            // XHTML5: normalize SVG and MathML prefixes to default namespace
            if (isHtml5Version() && (namespaceURI.equals(SVG_NS) || namespaceURI.equals(MATHML_NS))) {
                collapsedForeignNs = namespaceURI;
                return new QName(qname.getLocalPart(), namespaceURI);
            }
        }
        return qname;
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {

        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);

        if (shouldBufferDuplicateMeta(localName)) {
            beginMetaBuffer();
        }
        super.startElement(namespaceURI, localName, xhtmlQName);
        currentTag = xhtmlQName;
        if ("head".equalsIgnoreCase(localName)) {
            inHead = true;
            writeContentTypeMeta();
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        final boolean isMetaInHead = metaSuspendedWriter != null
                && "meta".equalsIgnoreCase(localName);
        if (inHead && "head".equalsIgnoreCase(localName)) {
            inHead = false;
        }

        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);

        super.endElement(namespaceURI, localName, xhtmlQName);

        if (isMetaInHead) {
            endMetaBuffer();
        }

        haveCollapsedXhtmlPrefix = false;
        collapsedForeignNs = null;
    }

    protected String removeXhtmlPrefix(final String namespaceURI, final String qname) {
        final int pos = qname.indexOf(':');
        if (pos > 0 && namespaceURI != null) {
            if (namespaceURI.equals(Namespaces.XHTML_NS)) {
                haveCollapsedXhtmlPrefix = true;
                return qname.substring(pos + 1);
            }
            // XHTML5: normalize SVG and MathML prefixes
            if (isHtml5Version() && (namespaceURI.equals(SVG_NS) || namespaceURI.equals(MATHML_NS))) {
                collapsedForeignNs = namespaceURI;
                return qname.substring(pos + 1);
            }
        }
        return qname;
    }

    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        if (haveCollapsedXhtmlPrefix && prefix != null && !prefix.isEmpty() && nsURI.equals(Namespaces.XHTML_NS)) {
            return; // don't output the xmlns:prefix for the collapsed node's prefix
        }
        // When a foreign namespace prefix was collapsed, replace the prefixed
        // declaration with a default namespace declaration
        if (collapsedForeignNs != null && prefix != null && !prefix.isEmpty()
                && nsURI.equals(collapsedForeignNs)) {
            super.namespace("", nsURI);  // emit xmlns="..." instead of xmlns:prefix="..."
            return;
        }
        super.namespace(prefix, nsURI);
    }


    @Override
    protected void closeStartTag(final boolean isEmpty) throws TransformerException {
        try {
            if (tagIsOpen) {
                final Writer w = getWriter();
                if (isEmpty) {
                    if (isEmptyTag(currentTag)) {
                        // For method="html", use HTML-style void tags (<br>)
                        // For method="xhtml", use XHTML-style (<br />)
                        if (isHtmlMethod()) {
                            w.write('>');
                        } else {
                            w.write(" />");
                        }
                    } else {
                        // Coalesce ">", "</", tag, ">" into 2 writer calls instead of 4
                        w.write("></");
                        w.write(currentTag);
                        w.write('>');
                    }
                } else {
                    w.write('>');
                }
                tagIsOpen = false;
            }
        } catch (final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    /**
     * Returns true if the output method is "html" (not "xhtml").
     * HTML uses void element syntax (<br>) while XHTML uses self-closing (<br />).
     */
    protected boolean isHtmlMethod() {
        if (outputProperties != null) {
            final String method = outputProperties.getProperty(OutputKeys.METHOD);
            return "html".equalsIgnoreCase(method);
        }
        return false;
    }

    /**
     * Returns true if the HTML version is 5.0 or higher.
     * Checks html-version first, then falls back to version (per W3C spec for html method).
     */
    protected boolean isHtml5Version() {
        if (outputProperties == null) {
            return true; // default to HTML5
        }
        final String htmlVersion = outputProperties.getProperty(org.exist.storage.serializers.EXistOutputKeys.HTML_VERSION);
        if (htmlVersion != null) {
            try {
                return Double.parseDouble(htmlVersion) >= 5.0;
            } catch (final NumberFormatException e) {
                // fall through
            }
        }
        final String version = outputProperties.getProperty(OutputKeys.VERSION);
        if (version != null) {
            try {
                return Double.parseDouble(version) >= 5.0;
            } catch (final NumberFormatException e) {
                // ignore
            }
        }
        return true; // default to HTML5
    }

    /**
     * DOCTYPE emission for XHTML/HTML output methods, per
     * W3C XSLT and XQuery Serialization 3.1 sections 7.1 and 7.2.
     *
     * <ul>
     *   <li>doctype-system set: emit DOCTYPE with PUBLIC/SYSTEM ids</li>
     *   <li>doctype-system absent, html method, doctype-public set: emit DOCTYPE PUBLIC</li>
     *   <li>doctype-system absent, html-version &ge; 5: emit {@code <!DOCTYPE html>}</li>
     *   <li>otherwise: no DOCTYPE</li>
     * </ul>
     *
     * Only emitted when the root element is {@code html} (case-insensitive); for
     * fragments rooted on any other element the DOCTYPE is suppressed.
     */
    @Override
    protected void writeDoctype(final String rootElement) throws TransformerException {
        if (doctypeWritten) {
            return;
        }
        if (!isHtmlRoot(rootElement)) {
            doctypeWritten = true;
            return;
        }
        emitHtmlDoctype();
        doctypeWritten = true;
    }

    private static boolean isHtmlRoot(final String rootElement) {
        final int colon = rootElement.indexOf(':');
        final String localName = colon < 0 ? rootElement : rootElement.substring(colon + 1);
        return "html".equalsIgnoreCase(localName);
    }

    private String getDoctypeProperty(final String key) {
        return outputProperties != null ? outputProperties.getProperty(key) : null;
    }

    private void emitHtmlDoctype() throws TransformerException {
        final String publicId = getDoctypeProperty(OutputKeys.DOCTYPE_PUBLIC);
        final String systemId = getDoctypeProperty(OutputKeys.DOCTYPE_SYSTEM);
        if (systemId != null) {
            documentType("html", publicId, systemId);
        } else if (isHtmlMethod() && publicId != null) {
            documentType("html", publicId, null);
        } else if (isHtml5Version()) {
            documentType("html", null, null);
        }
    }

    @Override
    public void attribute(final QName qname, final CharSequence value) throws TransformerException {
        noteMetaAttribute(qname.getLocalPart(), value);
        final CharSequence effectiveValue = maybeEscapeUri(qname.getLocalPart(), value);
        // For method="html", minimize boolean attributes when value matches name
        if (isHtmlMethod() && isBooleanAttribute(qname.getLocalPart(), effectiveValue)) {
            try {
                if (!tagIsOpen) {
                    characters(value);
                    return;
                }
                final Writer w = getWriter();
                w.write(' ');
                w.write(qname.getLocalPart());
                // Don't write ="value" — minimized form
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
            return;
        }
        super.attribute(qname, effectiveValue);
    }

    @Override
    public void attribute(final String qname, final CharSequence value) throws TransformerException {
        // Strip prefix for the redundancy check (we want the local name).
        final int colon = qname.indexOf(':');
        final String localName = colon < 0 ? qname : qname.substring(colon + 1);
        noteMetaAttribute(localName, value);
        final CharSequence effectiveValue = maybeEscapeUri(localName, value);
        if (isHtmlMethod() && isBooleanAttribute(qname, effectiveValue)) {
            try {
                if (!tagIsOpen) {
                    characters(value);
                    return;
                }
                final Writer w = getWriter();
                w.write(' ');
                w.write(qname);
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
            return;
        }
        super.attribute(qname, effectiveValue);
    }

    /**
     * Apply escape-uri-attributes when the current element/attribute names
     * a URI-valued attribute; otherwise return the value unchanged. Escaping
     * is applied for both HTML and XHTML output methods, so URI-valued
     * attributes round-trip in XHTML 1.0 / 5 output too.
     */
    private CharSequence maybeEscapeUri(final String attrLocal, final CharSequence value) {
        if (currentTag == null) {
            return value;
        }
        final String elementLocal = currentTag.contains(":")
                ? currentTag.substring(currentTag.indexOf(':') + 1)
                : currentTag;
        if (!shouldEscapeUriAttribute(elementLocal, attrLocal)) {
            return value;
        }
        return escapeUriAttribute(value);
    }

    private boolean isBooleanAttribute(final String attrName, final CharSequence value) {
        return BOOLEAN_ATTRIBUTES.contains(attrName.toLowerCase(java.util.Locale.ROOT))
                && attrName.equalsIgnoreCase(value.toString());
    }

    @Override
    protected boolean needsEscape(final char ch, final boolean inAttribute) {
        // For HTML method, script and style content should not be escaped
        if (!inAttribute && isHtmlMethod()
                && currentTag != null && RAW_TEXT_ELEMENTS_HTML.contains(currentTag.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return super.needsEscape(ch, inAttribute);
    }

    @Override
    protected boolean needsEscaping(final boolean inAttribute) {
        if (!inAttribute && isHtmlMethod()
                && currentTag != null && RAW_TEXT_ELEMENTS_HTML.contains(currentTag.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return super.needsEscaping(inAttribute);
    }

    /**
     * Per W3C XSLT and XQuery Serialization 3.1 § 7.2.7, the html method
     * ignores cdata-section-elements for HTML elements (CDATA sections are
     * not valid HTML syntax) but DOES apply them to foreign content
     * (e.g. SVG, MathML, or any element in a non-HTML namespace embedded
     * in the document). For foreign content the rule is unconditional —
     * the xdm-serialization gate that the XML writer otherwise applies
     * does not gate HTML's foreign-content CDATA emission.
     */
    @Override
    protected boolean shouldUseCdataSections() {
        if (isHtmlMethod()) {
            final String ns = currentElementNamespaceURI();
            return ns != null && !ns.isEmpty() && !Namespaces.XHTML_NS.equals(ns);
        }
        return super.shouldUseCdataSections();
    }

    /**
     * Processing-instruction serialization for HTML method (pre-HTML5).
     * Per W3C XSLT and XQuery Serialization 3.1 § 7.1.5, the HTML output
     * method emits PIs as {@code <?target data>} (no closing {@code ?>});
     * XHTML uses the regular XML form which the parent already provides.
     * The HTML5 (PR2372) variant lives in {@link HTML5Writer}.
     */
    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        if (!isHtmlMethod()) {
            super.processingInstruction(target, data);
            return;
        }
        try {
            if (tagIsOpen) {
                closeStartTag(false);
            }
            final Writer w = getWriter();
            w.write("<?");
            w.write(target);
            if (data != null && !data.isEmpty()) {
                w.write(' ');
                w.write(data);
            }
            w.write('>');
        } catch (final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    @Override
    protected boolean escapeAmpersandBeforeBrace() {
        // HTML spec: & before { in attribute values should not be escaped
        return false;
    }

    @Override
    protected boolean isInlineTag(final String namespaceURI, final String localName) {
        return (namespaceURI == null || namespaceURI.isEmpty() || Namespaces.XHTML_NS.equals(namespaceURI))
                && inlineTags.contains(localName);
    }

    /**
     * Write a meta content-type tag as the first child of head when
     * include-content-type is enabled (the default per W3C Serialization 3.1).
     */
    protected void writeContentTypeMeta() throws TransformerException {
        if (contentTypeMetaWritten || outputProperties == null) {
            return;
        }
        final String includeContentType = outputProperties.getProperty("include-content-type", "yes");
        if (!"yes".equals(includeContentType)) {
            return;
        }
        contentTypeMetaWritten = true;
        try {
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            closeStartTag(false);
            final Writer writer = getWriter();

            // HTML5 method uses <meta charset="UTF-8">
            // XHTML and HTML4 use <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            // XHTML mode requires self-closing tags (/>)  for valid XML output —
            // the URL rewrite pipeline re-parses this as XML in the view step.
            final boolean selfClose = !isHtmlMethod();
            if (isHtmlMethod() && isHtml5Version()) {
                writer.write("<meta charset=\"");
                writer.write(encoding);
                writer.write(selfClose ? "\" />" : "\">");
            } else {
                final String mediaType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE, "text/html");
                writer.write("<meta http-equiv=\"Content-Type\" content=\"");
                writer.write(mediaType);
                writer.write("; charset=");
                writer.write(encoding);
                writer.write(selfClose ? "\" />" : "\">");
            }
        } catch (IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }
}
