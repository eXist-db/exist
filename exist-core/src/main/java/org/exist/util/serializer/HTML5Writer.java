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

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.exist.dom.QName;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;

/**
 * HTML5 writer which does not produce well-formed XHTML.
 *
 * @author Wolfgang
 */
public class HTML5Writer extends XHTML5Writer {

    /**
     * Holds the names of the attributes that are considered boolean
     * according to <a href="http://www.w3.org/TR/html51/single-page.html">HTML Standard</a>
     *
     * The value of these attributes are written if they equal the
     * name of the attribute. For example: checked="checked" will be
     * written as checked.
     *
     * See <a href="https://github.com/eXist-db/exist/issues/777">html5 method eating attribute value when value is eq to name</a> for details.
     */
    private static final ObjectSet<String> BOOLEAN_ATTRIBUTE_NAMES = new ObjectOpenHashSet<>(68);
    static {
        BOOLEAN_ATTRIBUTE_NAMES.add("allowFullscreen");
        BOOLEAN_ATTRIBUTE_NAMES.add("async");
        BOOLEAN_ATTRIBUTE_NAMES.add("autofocus");
        BOOLEAN_ATTRIBUTE_NAMES.add("autoplay");
        BOOLEAN_ATTRIBUTE_NAMES.add("badInput");
        BOOLEAN_ATTRIBUTE_NAMES.add("checked");
        BOOLEAN_ATTRIBUTE_NAMES.add("closed");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandChecked");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandDisabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandHidden");
        BOOLEAN_ATTRIBUTE_NAMES.add("compact");
        BOOLEAN_ATTRIBUTE_NAMES.add("complete");
        BOOLEAN_ATTRIBUTE_NAMES.add("controls");
        BOOLEAN_ATTRIBUTE_NAMES.add("cookieEnabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("customError");
        BOOLEAN_ATTRIBUTE_NAMES.add("declare");
        BOOLEAN_ATTRIBUTE_NAMES.add("default");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultChecked");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultMuted");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultSelected");
        BOOLEAN_ATTRIBUTE_NAMES.add("defer");
        BOOLEAN_ATTRIBUTE_NAMES.add("disabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("draggable");
        BOOLEAN_ATTRIBUTE_NAMES.add("enabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("ended");
        BOOLEAN_ATTRIBUTE_NAMES.add("formNoValidate");
        BOOLEAN_ATTRIBUTE_NAMES.add("hidden");
        BOOLEAN_ATTRIBUTE_NAMES.add("indeterminate");
        BOOLEAN_ATTRIBUTE_NAMES.add("isContentEditable");
        BOOLEAN_ATTRIBUTE_NAMES.add("isMap");
        BOOLEAN_ATTRIBUTE_NAMES.add("itemScope");
        BOOLEAN_ATTRIBUTE_NAMES.add("javaEnabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("loop");
        BOOLEAN_ATTRIBUTE_NAMES.add("multiple");
        BOOLEAN_ATTRIBUTE_NAMES.add("muted");
        BOOLEAN_ATTRIBUTE_NAMES.add("noHref");
        BOOLEAN_ATTRIBUTE_NAMES.add("noResize");
        BOOLEAN_ATTRIBUTE_NAMES.add("noShade");
        BOOLEAN_ATTRIBUTE_NAMES.add("noValidate");
        BOOLEAN_ATTRIBUTE_NAMES.add("noWrap");
        BOOLEAN_ATTRIBUTE_NAMES.add("onLine");
        BOOLEAN_ATTRIBUTE_NAMES.add("open");
        BOOLEAN_ATTRIBUTE_NAMES.add("patternMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("pauseOnExit");
        BOOLEAN_ATTRIBUTE_NAMES.add("paused");
        BOOLEAN_ATTRIBUTE_NAMES.add("persisted");
        BOOLEAN_ATTRIBUTE_NAMES.add("rangeOverflow");
        BOOLEAN_ATTRIBUTE_NAMES.add("rangeUnderflow");
        BOOLEAN_ATTRIBUTE_NAMES.add("readOnly");
        BOOLEAN_ATTRIBUTE_NAMES.add("required");
        BOOLEAN_ATTRIBUTE_NAMES.add("reversed");
        BOOLEAN_ATTRIBUTE_NAMES.add("scoped");
        BOOLEAN_ATTRIBUTE_NAMES.add("seamless");
        BOOLEAN_ATTRIBUTE_NAMES.add("seeking");
        BOOLEAN_ATTRIBUTE_NAMES.add("selected");
        BOOLEAN_ATTRIBUTE_NAMES.add("sortable");
        BOOLEAN_ATTRIBUTE_NAMES.add("spellcheck");
        BOOLEAN_ATTRIBUTE_NAMES.add("stepMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("tooLong");
        BOOLEAN_ATTRIBUTE_NAMES.add("tooShort");
        BOOLEAN_ATTRIBUTE_NAMES.add("translate");
        BOOLEAN_ATTRIBUTE_NAMES.add("trueSpeed");
        BOOLEAN_ATTRIBUTE_NAMES.add("typeMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("typeMustMatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("valid");
        BOOLEAN_ATTRIBUTE_NAMES.add("valueMissing");
        BOOLEAN_ATTRIBUTE_NAMES.add("visible");
        BOOLEAN_ATTRIBUTE_NAMES.add("willValidate");
    }

    private static final ObjectSet<String> BOOLEAN_ATTRIBUTE_NAMES_LOWER = new ObjectOpenHashSet<>(BOOLEAN_ATTRIBUTE_NAMES.size());
    static {
        for (final String n : BOOLEAN_ATTRIBUTE_NAMES) {
            BOOLEAN_ATTRIBUTE_NAMES_LOWER.add(n.toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
    static {
        EMPTY_TAGS.add("area");
        EMPTY_TAGS.add("base");
        EMPTY_TAGS.add("br");
        EMPTY_TAGS.add("col");
        EMPTY_TAGS.add("embed");
        EMPTY_TAGS.add("hr");
        EMPTY_TAGS.add("img");
        EMPTY_TAGS.add("input");
        EMPTY_TAGS.add("keygen");
        EMPTY_TAGS.add("link");
        EMPTY_TAGS.add("meta");
        EMPTY_TAGS.add("param");
        EMPTY_TAGS.add("source");
        EMPTY_TAGS.add("track");
        EMPTY_TAGS.add("wbr");
    }

    private static final ObjectSet<String> RAW_TEXT_ELEMENTS = new ObjectOpenHashSet<>(31);
    static {
        RAW_TEXT_ELEMENTS.add("script");
        RAW_TEXT_ELEMENTS.add("style");
    }

    public HTML5Writer() {
        super(EMPTY_TAGS, INLINE_TAGS);
    }

    public HTML5Writer(Writer writer) {
        super(writer, EMPTY_TAGS, INLINE_TAGS);
    }

    @Override
    public void endElement(QName qname) throws TransformerException {
        if (!isEmptyTag(qname.getLocalPart())) {
            super.endElement(qname);
        } else {
            // HTML5 omits the close tag for void elements; we still need to
            // honor the meta-in-head dedup that XHTMLWriter sets up at startElement
            // time. Capture the buffered-meta flag before closeStartTag flips state.
            final boolean wasBufferedMeta = isBufferedMeta(qname.getLocalPart());
            closeStartTag(true);
            endIndent(qname.getNamespaceURI(), qname.getLocalPart());
            if (wasBufferedMeta) {
                endMetaBuffer();
            }
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws TransformerException {
        if (!isEmptyTag(localName)) {
            super.endElement(namespaceURI, localName, qname);
        } else {
            final boolean wasBufferedMeta = isBufferedMeta(localName);
            closeStartTag(true);
            endIndent(namespaceURI, localName);
            if (wasBufferedMeta) {
                endMetaBuffer();
            }
        }
    }

    @Override
    public void attribute(String qname, CharSequence value) throws TransformerException {
        // Strip prefix for the meta-dedup redundancy check
        final int colon = qname.indexOf(':');
        final String localName = colon < 0 ? qname : qname.substring(colon + 1);
        noteMetaAttribute(localName, value);
        final CharSequence effectiveValue = maybeEscapeUriHtml5(localName, value);
        try {
            if(!tagIsOpen) {
                characters(effectiveValue);
                return;
            }
            final Writer writer = getWriter();
            writer.write(' ');
            writer.write(qname);
            if (!isBooleanAttributeMatch(qname, effectiveValue)) {
                writer.write("=\"");
                writeChars(effectiveValue, true);
                writer.write('"');
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    @Override
    public void attribute(QName qname, CharSequence value) throws TransformerException {
        noteMetaAttribute(qname.getLocalPart(), value);
        final String localPart = qname.getLocalPart();
        final CharSequence effectiveValue = maybeEscapeUriHtml5(localPart, value);
        try {
            if(!tagIsOpen) {
                characters(effectiveValue);
                return;
                // throw new TransformerException("Found an attribute outside an
                // element");
            }
            final Writer writer = getWriter();
            writer.write(' ');
            if(qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                writer.write(qname.getPrefix());
                writer.write(':');
            }
            writer.write(localPart);
            if (!isBooleanAttributeMatch(localPart, effectiveValue)) {
                writer.write("=\"");
                writeChars(effectiveValue, true);
                writer.write('"');
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    /**
     * URI-attribute escaping for the HTML5 writer. Mirrors
     * {@link XHTMLWriter#shouldEscapeUriAttribute(String, String)} but unwraps
     * the prefixed form of {@link #currentTag} so the (element, attribute)
     * lookup uses local names only.
     */
    private CharSequence maybeEscapeUriHtml5(final String attrLocal, final CharSequence value) {
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

    /**
     * HTML5 boolean attribute minimization: emit just the bare name when the
     * value is empty or matches the attribute name case-insensitively
     * (per W3C XSLT/XQuery Serialization 3.1, section 7.2.2).
     */
    private static boolean isBooleanAttributeMatch(final String name, final CharSequence value) {
        if (!BOOLEAN_ATTRIBUTE_NAMES_LOWER.contains(name.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        if (value == null || value.length() == 0) {
            return true;
        }
        return name.equalsIgnoreCase(value.toString());
    }

    @Override
    public void namespace(String prefix, String nsURI) throws TransformerException {
        // HTML5 elements never carry an explicit xmlns since the parser puts
        // them in the HTML namespace implicitly. Foreign content (anything
        // outside the XHTML namespace, e.g. SVG, MathML, custom XML) keeps
        // its namespace declarations so the receiver can re-parse it as XML.
        if (nsURI == null || nsURI.isEmpty()) {
            return;
        }
        if (org.exist.Namespaces.XHTML_NS.equals(nsURI)) {
            return;
        }
        super.namespace(prefix, nsURI);
    }

    @Override
    protected void closeStartTag(boolean isEmpty) throws TransformerException {
        try {
            if (tagIsOpen) {
                final Writer w = getWriter();
                if (isEmpty) {
                    if (isEmptyTag(currentTag)) {
                        w.write('>');
                    } else if (isForeignContent()) {
                        // Foreign content (SVG, MathML, custom XML namespace)
                        // embedded in HTML5 is serialized with XML self-close
                        // syntax so the receiver can re-parse it as XML.
                        w.write("/>");
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
     * The current element is "foreign content" when its namespace is neither
     * the XHTML namespace nor the empty (no-namespace) HTML namespace; that
     * is the trigger for XML-style self-closing per HTML5's foreign-content
     * serialization rule.
     */
    private boolean isForeignContent() {
        final String ns = currentElementNamespaceURI();
        return ns != null && !ns.isEmpty() && !org.exist.Namespaces.XHTML_NS.equals(ns);
    }

    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        // QT4 PR2372: HTML5 has no PI syntax, so the serializer renders
        // processing instructions as comments of the form `<!--?target data?-->`,
        // matching the HTML5 parser's coercion of `<?...?>` content.
        try {
            if (tagIsOpen) {
                closeStartTag(false);
            }
            final Writer writer = getWriter();
            writer.write("<!--?");
            writer.write(target);
            if (data != null && !data.isEmpty()) {
                writer.write(' ');
                writer.write(data);
            }
            writer.write("?-->");
        } catch (final IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean needsEscape(char ch) {
        if (RAW_TEXT_ELEMENTS.contains(currentTag)) {
            return false;
        }
        return super.needsEscape(ch);
    }

    @Override
    protected boolean needsEscape(final char ch, final boolean inAttribute) {
        // In raw text elements (script, style), suppress escaping for TEXT content only.
        // Attribute values must always be escaped, even on raw text elements.
        if (!inAttribute && RAW_TEXT_ELEMENTS.contains(currentTag)) {
            return false;
        }
        // For attributes, always return true (bypass the 1-arg override
        // which returns false for all script/style content)
        if (inAttribute) {
            return true;
        }
        return super.needsEscape(ch, inAttribute);
    }

    @Override
    protected boolean needsEscaping(final boolean inAttribute) {
        // Mirror the per-char rule above: TEXT content inside script/style is
        // raw text and never needs escaping. Lets writeChars() bulk-stream
        // the entire block in one Writer.write() call.
        if (!inAttribute && RAW_TEXT_ELEMENTS.contains(currentTag)) {
            return false;
        }
        return true;
    }

}
