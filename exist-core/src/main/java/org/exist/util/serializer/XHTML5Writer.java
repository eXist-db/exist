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

import java.io.Writer;
import javax.xml.transform.TransformerException;

import org.exist.storage.serializers.EXistOutputKeys;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/**
 * A writer which produces well-formed XHTML5.
 */
public class XHTML5Writer extends XHTMLWriter {

    protected static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
    static {
        //https://www.w3.org/TR/html5/syntax.html#void-elements
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

    protected static final ObjectSet<String> XHTML_INLINE_TAGS = new ObjectOpenHashSet<>(31);
    
    static {
    	XHTML_INLINE_TAGS.add("a");
    	XHTML_INLINE_TAGS.add("abbr");
    	XHTML_INLINE_TAGS.add("area");
    	XHTML_INLINE_TAGS.add("audio");
    	XHTML_INLINE_TAGS.add("b");
    	XHTML_INLINE_TAGS.add("bdi");
    	XHTML_INLINE_TAGS.add("bdo");
    	XHTML_INLINE_TAGS.add("br");
    	XHTML_INLINE_TAGS.add("button");
    	XHTML_INLINE_TAGS.add("canvas");
    	XHTML_INLINE_TAGS.add("cite");
    	XHTML_INLINE_TAGS.add("code");
    	XHTML_INLINE_TAGS.add("command");
    	XHTML_INLINE_TAGS.add("datalist");
    	XHTML_INLINE_TAGS.add("del");
    	XHTML_INLINE_TAGS.add("dfn");
    	XHTML_INLINE_TAGS.add("em");
    	XHTML_INLINE_TAGS.add("embed");
    	XHTML_INLINE_TAGS.add("i");
    	XHTML_INLINE_TAGS.add("img");
    	XHTML_INLINE_TAGS.add("ins");
    	XHTML_INLINE_TAGS.add("input");
    	XHTML_INLINE_TAGS.add("kbd");
    	XHTML_INLINE_TAGS.add("keygen");
    	XHTML_INLINE_TAGS.add("label");
    	XHTML_INLINE_TAGS.add("map");
    	XHTML_INLINE_TAGS.add("mark");
    	XHTML_INLINE_TAGS.add("math");
    	XHTML_INLINE_TAGS.add("meter");
    	XHTML_INLINE_TAGS.add("object");
    	XHTML_INLINE_TAGS.add("output");
    	XHTML_INLINE_TAGS.add("progress");
    	XHTML_INLINE_TAGS.add("q");
    	XHTML_INLINE_TAGS.add("ruby");
    	XHTML_INLINE_TAGS.add("s");
    	XHTML_INLINE_TAGS.add("samp");
    	XHTML_INLINE_TAGS.add("select");
    	XHTML_INLINE_TAGS.add("small");
    	XHTML_INLINE_TAGS.add("span");
    	XHTML_INLINE_TAGS.add("strong");
    	XHTML_INLINE_TAGS.add("sub");
    	XHTML_INLINE_TAGS.add("sup");
    	XHTML_INLINE_TAGS.add("svg");
    	XHTML_INLINE_TAGS.add("textarea");
    	XHTML_INLINE_TAGS.add("time");
    	XHTML_INLINE_TAGS.add("tt");
    	XHTML_INLINE_TAGS.add("u");
    	XHTML_INLINE_TAGS.add("var");
    	XHTML_INLINE_TAGS.add("video");
    }
    
    public XHTML5Writer() {
        this(EMPTY_TAGS, XHTML_INLINE_TAGS);
    }

    public XHTML5Writer(final Writer writer) {
        this(writer, EMPTY_TAGS, XHTML_INLINE_TAGS);
    }

    public XHTML5Writer(ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super(emptyTags, inlineTags);
    }

    public XHTML5Writer(Writer writer, ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super(writer, emptyTags, inlineTags);
    }

    // PMD.NPathComplexity: emits the XSLT/XQuery Serialization 3.1 doctype with
    // public/system/internal-subset/version permutations and attribute-value-
    // quote escaping; branches map to the W3C HTML5/XHTML5 doctype emission rules.
    @SuppressWarnings("PMD.NPathComplexity")
    @Override
    protected void writeDoctype(String rootElement) throws TransformerException {
        if (doctypeWritten) {
            return;
        }

        // Canonical serialization: never output DOCTYPE
        final String canonicalProp = outputProperties != null
                ? outputProperties.getProperty(EXistOutputKeys.CANONICAL) : null;
        if ("yes".equals(canonicalProp) || "true".equals(canonicalProp) || "1".equals(canonicalProp)) {
            doctypeWritten = true;
            return;
        }

        // Only output DOCTYPE when the root element is <html> (case-insensitive)
        // Per W3C Serialization: DOCTYPE is for the html element only, not fragments
        final String localName = rootElement.contains(":") ? rootElement.substring(rootElement.indexOf(':') + 1) : rootElement;
        if (!"html".equalsIgnoreCase(localName)) {
            doctypeWritten = true;  // suppress future attempts
            return;
        }

        final String publicId = outputProperties != null
                ? outputProperties.getProperty(javax.xml.transform.OutputKeys.DOCTYPE_PUBLIC) : null;
        final String systemId = outputProperties != null
                ? outputProperties.getProperty(javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM) : null;
        final String method = outputProperties != null
                ? outputProperties.getProperty(javax.xml.transform.OutputKeys.METHOD, "xhtml") : "xhtml";

        if ("xhtml".equalsIgnoreCase(method)) {
            // XHTML: per W3C spec section 5.2, only output doctype-public when
            // doctype-system is also present
            if (systemId != null) {
                documentType("html", publicId, systemId);
            } else if (publicId == null) {
                // Neither set — simple DOCTYPE
                documentType("html", null, null);
            } else {
                // doctype-public without doctype-system — suppress DOCTYPE for XHTML
                doctypeWritten = true;
            }
        } else {
            // HTML method: pass through doctype-public and doctype-system as set
            documentType("html", publicId, systemId);
        }
        doctypeWritten = true;
    }
}
