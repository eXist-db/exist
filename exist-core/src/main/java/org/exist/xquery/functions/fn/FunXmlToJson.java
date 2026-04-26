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
package org.exist.xquery.functions.fn;

import io.lacuna.bifurcan.IEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Set;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Implements fn:xml-to-json per XPath/XQuery 3.1 §17.5.2 with XQuery 4.0 extensions.
 *
 * Walks the input element tree (which must conform to the JSON-XML schema) and
 * emits the corresponding JSON text. Validates namespace, element names,
 * attributes, and JSON value lexical forms.
 */
public class FunXmlToJson extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(FunXmlToJson.class);

    private static final String JSON_NS = Namespaces.XPATH_FUNCTIONS_NS;

    private static final String FS_XML_TO_JSON_NAME = "xml-to-json";
    private static final FunctionParameterSequenceType FS_XML_TO_JSON_OPT_PARAM_NODE = optParam("node", Type.NODE, "The input node");
    private static final FunctionParameterSequenceType FS_XML_TO_JSON_OPT_PARAM_OPTIONS = param("options", Type.MAP_ITEM, "The options map");

    static final FunctionSignature[] FS_XML_TO_JSON = functionSignatures(
            new QName(FS_XML_TO_JSON_NAME, Function.BUILTIN_FUNCTION_NS),
            "Converts an XML tree (in XPath 3.1 'XML Representation of JSON' format) into a string conforming to the JSON grammar.",
            returnsOpt(Type.STRING, "The JSON representation of the input node"),
            arities(
                    arity(FS_XML_TO_JSON_OPT_PARAM_NODE),
                    arity(FS_XML_TO_JSON_OPT_PARAM_NODE, FS_XML_TO_JSON_OPT_PARAM_OPTIONS)
            )
    );

    public FunXmlToJson(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence seq = (getArgumentCount() > 0) ? args[0] : Sequence.EMPTY_SEQUENCE;
        if (seq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item item = seq.itemAt(0);
        if (item.getType() != Type.DOCUMENT && item.getType() != Type.ELEMENT) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:xml-to-json: input must be a document or element node");
        }

        final Options options = parseOptions(args);

        final Element rootElement;
        if (item.getType() == Type.DOCUMENT) {
            final Node node = ((NodeValue) item).getNode();
            final Document doc = (node instanceof Document) ? (Document) node : node.getOwnerDocument();
            rootElement = doc.getDocumentElement();
            if (rootElement == null) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Empty document node passed to fn:xml-to-json");
            }
        } else {
            rootElement = (Element) ((NodeValue) item).getNode();
        }

        final StringBuilder out = new StringBuilder();
        emitElement(rootElement, out, options, 0, /* isRoot */ true);

        final ValueSequence result = new ValueSequence();
        result.add(new StringValue(this, out.toString()));
        return result;
    }

    /** XQ4 options. */
    private static final class Options {
        boolean indent = false;
        boolean escapeSolidus = true;
    }

    private Options parseOptions(final Sequence[] args) throws XPathException {
        final Options opts = new Options();
        if (getArgumentCount() < 2 || args[1].isEmpty()) {
            return opts;
        }
        final AbstractMapType map = (AbstractMapType) args[1].itemAt(0);

        final Sequence indentSeq = map.get(new StringValue(this, "indent"));
        if (indentSeq != null && !indentSeq.isEmpty()) {
            if (indentSeq.getItemCount() != 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option 'indent' must be a single boolean");
            }
            final Item it = indentSeq.itemAt(0);
            if (it.getType() != Type.BOOLEAN) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option 'indent' must be a boolean");
            }
            opts.indent = ((BooleanValue) it).getValue();
        } else if (indentSeq != null) {
            // Empty sequence is invalid per spec
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option 'indent' must be a boolean, not empty sequence");
        }

        final Sequence escSeq = map.get(new StringValue(this, "escape-solidus"));
        if (escSeq != null && !escSeq.isEmpty()) {
            if (escSeq.getItemCount() != 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option 'escape-solidus' must be a single boolean");
            }
            final Item it = escSeq.itemAt(0);
            if (it.getType() != Type.BOOLEAN) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option 'escape-solidus' must be a boolean");
            }
            opts.escapeSolidus = ((BooleanValue) it).getValue();
        }

        return opts;
    }

    private void emitElement(final Element element, final StringBuilder out,
                             final Options opts, final int depth, final boolean isRoot) throws XPathException {
        final String ns = element.getNamespaceURI();
        if (ns == null || !JSON_NS.equals(ns)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Element must be in the namespace " + JSON_NS + ", got: " + ns);
        }

        final String localName = element.getLocalName();
        validateAttributes(element, localName, isRoot);

        switch (localName) {
            case "null":
                checkNoChildren(element, "null");
                out.append("null");
                break;
            case "boolean":
                emitBoolean(element, out);
                break;
            case "number":
                emitNumber(element, out);
                break;
            case "string":
                emitString(element, out, opts);
                break;
            case "array":
                emitArray(element, out, opts, depth);
                break;
            case "map":
                emitMap(element, out, opts, depth);
                break;
            default:
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Invalid element name in JSON-XML: '" + localName + "'");
        }
    }

    private void validateAttributes(final Element element, final String localName, final boolean isRoot) throws XPathException {
        final NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node attr = attrs.item(i);
            final String attrNs = attr.getNamespaceURI();
            // xmlns:* declarations - skip
            if (Namespaces.XMLNS_NS.equals(attrNs) || "xmlns".equals(attr.getNodeName())) {
                continue;
            }
            // Attributes in the JSON namespace are not allowed
            if (JSON_NS.equals(attrNs)) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Attribute '" + attr.getNodeName() + "' must not be in the JSON namespace");
            }
            // Attributes in any other (non-null, non-XMLNS) namespace are allowed and ignored
            if (attrNs != null && !attrNs.isEmpty()) {
                continue;
            }
            // Null-namespace attributes must be: key, escaped, escaped-key.
            // On the root element these are allowed but ignored (bug 29917).
            final String attrLocal = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            switch (attrLocal) {
                case "key":
                    // Allowed; semantic check happens in emitMap (key required on map members)
                    break;
                case "escaped":
                case "escaped-key":
                    validateBooleanAttr(attr.getNodeValue(), attrLocal);
                    break;
                default:
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Disallowed attribute '" + attrLocal + "' on JSON-XML element");
            }
        }
    }

    private void validateBooleanAttr(final String value, final String attrName) throws XPathException {
        if (value == null) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid value for '" + attrName + "': null");
        }
        final String trimmed = value.trim();
        if (!"true".equals(trimmed) && !"false".equals(trimmed)
                && !"1".equals(trimmed) && !"0".equals(trimmed)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid boolean value for '" + attrName + "': '" + value + "'");
        }
    }

    private boolean booleanAttrValue(final String value) {
        if (value == null) return false;
        final String t = value.trim();
        return "true".equals(t) || "1".equals(t);
    }

    private void checkNoChildren(final Element element, final String elementName) throws XPathException {
        Node child = element.getFirstChild();
        while (child != null) {
            final int t = child.getNodeType();
            if (t == Node.ELEMENT_NODE) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Element '" + elementName + "' must not have child elements");
            }
            if (t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE) {
                final String txt = child.getNodeValue();
                if (txt != null && !txt.isEmpty()) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Element '" + elementName + "' must have empty content");
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean isAllWhitespace(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    private void emitBoolean(final Element element, final StringBuilder out) throws XPathException {
        final String text = collectText(element, "boolean");
        final String trimmed = text.trim();
        if ("true".equals(trimmed) || "1".equals(trimmed)) {
            out.append("true");
        } else if ("false".equals(trimmed) || "0".equals(trimmed)) {
            out.append("false");
        } else {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid boolean content: '" + text + "'");
        }
    }

    private void emitNumber(final Element element, final StringBuilder out) throws XPathException {
        final String text = collectText(element, "number");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Number element must have non-empty content");
        }
        // Reject NaN/Infinity (they are valid xs:double but not valid JSON)
        if ("NaN".equals(trimmed) || "INF".equals(trimmed) || "-INF".equals(trimmed)
                || "Infinity".equals(trimmed) || "-Infinity".equals(trimmed)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Number must be finite, not NaN or Infinity");
        }
        // Strip optional leading +
        if (trimmed.startsWith("+")) {
            trimmed = trimmed.substring(1);
        }
        try {
            // BigDecimal preserves precision, normalizes leading zeros, and
            // produces canonical exponent form (E+/E-).
            final java.math.BigDecimal bd = new java.math.BigDecimal(trimmed);
            out.append(bd.toString());
        } catch (final NumberFormatException e) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid number: '" + text + "'");
        }
    }

    private void emitString(final Element element, final StringBuilder out, final Options opts) throws XPathException {
        final String text = collectText(element, "string");
        final boolean escaped = booleanAttrValue(getNullNsAttribute(element, "escaped"));
        out.append('"');
        if (escaped) {
            // Validate and copy escape sequences as-is, escaping only unescaped specials
            appendValidatedEscapedString(text, out, opts);
        } else {
            appendEscapedString(text, out, opts);
        }
        out.append('"');
    }

    private static String getNullNsAttribute(final Element element, final String name) {
        final NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node a = attrs.item(i);
            final String ns = a.getNamespaceURI();
            if (ns != null && !ns.isEmpty()) continue;
            final String ln = a.getLocalName() != null ? a.getLocalName() : a.getNodeName();
            if (name.equals(ln)) return a.getNodeValue();
        }
        return null;
    }

    /**
     * Collect the concatenated text content of a leaf element (boolean/number/string),
     * rejecting any element children. Text may be split by comments/PIs which are
     * silently dropped.
     */
    private String collectText(final Element element, final String elementName) throws XPathException {
        final StringBuilder sb = new StringBuilder();
        Node child = element.getFirstChild();
        while (child != null) {
            final int t = child.getNodeType();
            if (t == Node.ELEMENT_NODE) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Element '" + elementName + "' must not have child elements");
            }
            if (t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE) {
                final String v = child.getNodeValue();
                if (v != null) sb.append(v);
            }
            child = child.getNextSibling();
        }
        return sb.toString();
    }

    private void emitArray(final Element element, final StringBuilder out,
                           final Options opts, final int depth) throws XPathException {
        rejectStrayText(element, "array");
        // Count member elements to decide whether to indent (empty arrays stay inline)
        boolean hasChildren = false;
        Node c = element.getFirstChild();
        while (c != null) {
            if (c.getNodeType() == Node.ELEMENT_NODE) { hasChildren = true; break; }
            c = c.getNextSibling();
        }
        out.append('[');
        if (opts.indent && hasChildren) out.append('\n');
        Node child = element.getFirstChild();
        boolean first = true;
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!first) {
                    out.append(',');
                    if (opts.indent) out.append('\n');
                }
                if (opts.indent) appendIndent(out, depth + 1);
                emitElement((Element) child, out, opts, depth + 1, false);
                first = false;
            }
            child = child.getNextSibling();
        }
        if (opts.indent && hasChildren) {
            out.append('\n');
            appendIndent(out, depth);
        }
        out.append(']');
    }

    private void emitMap(final Element element, final StringBuilder out,
                         final Options opts, final int depth) throws XPathException {
        rejectStrayText(element, "map");
        boolean hasChildren = false;
        Node c = element.getFirstChild();
        while (c != null) {
            if (c.getNodeType() == Node.ELEMENT_NODE) { hasChildren = true; break; }
            c = c.getNextSibling();
        }
        out.append('{');
        if (opts.indent && hasChildren) out.append('\n');
        final Set<String> seenKeys = new HashSet<>();
        Node child = element.getFirstChild();
        boolean first = true;
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final Element member = (Element) child;
                final String key = getNullNsAttribute(member, "key");
                if (key == null) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Map member must have a 'key' attribute");
                }
                final boolean keyEscaped = booleanAttrValue(getNullNsAttribute(member, "escaped-key"));
                final String resolvedKey = keyEscaped ? unescapeJsonString(key) : key;
                if (!seenKeys.add(resolvedKey)) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Duplicate key in map: '" + resolvedKey + "'");
                }
                if (!first) {
                    out.append(',');
                    if (opts.indent) out.append('\n');
                }
                if (opts.indent) appendIndent(out, depth + 1);
                out.append('"');
                if (keyEscaped) {
                    appendValidatedEscapedString(key, out, opts);
                } else {
                    appendEscapedString(key, out, opts);
                }
                out.append('"').append(':');
                if (opts.indent) out.append(' ');
                emitElement(member, out, opts, depth + 1, false);
                first = false;
            }
            child = child.getNextSibling();
        }
        if (opts.indent && hasChildren) {
            out.append('\n');
            appendIndent(out, depth);
        }
        out.append('}');
    }

    private static void appendIndent(final StringBuilder out, final int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private void rejectStrayText(final Element element, final String elementName) throws XPathException {
        Node child = element.getFirstChild();
        while (child != null) {
            final int t = child.getNodeType();
            if ((t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE)) {
                final String v = child.getNodeValue();
                if (v != null && !isAllWhitespace(v)) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Element '" + elementName + "' must not contain non-whitespace text");
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Append a string to the output, applying full JSON escaping.
     * Special chars (", \\, control chars) are escaped per RFC 8259.
     * Solidus is escaped if escape-solidus option is true (default).
     */
    private static void appendEscapedString(final String s, final StringBuilder out, final Options opts) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '/':
                    if (opts.escapeSolidus) {
                        out.append("\\/");
                    } else {
                        out.append('/');
                    }
                    break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if ((c >= 1 && c <= 31) || (c >= 127 && c <= 159)) {
                        out.append("\\u").append(String.format("%04X", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
    }

    /**
     * Append a string that the input claims is already JSON-escaped (escaped="true").
     * Validate the escape sequences (raising FOJS0007 on bad escapes), pass valid
     * escapes through unchanged, and additionally escape any unescaped special chars.
     */
    private static void appendValidatedEscapedString(final String s, final StringBuilder out, final Options opts) throws XPathException {
        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c == '\\') {
                if (i + 1 >= s.length()) {
                    throw new XPathException(ErrorCodes.FOJS0007, "Trailing backslash in escaped string");
                }
                final char next = s.charAt(i + 1);
                switch (next) {
                    case '"': case '\\': case '/': case 'b': case 'f':
                    case 'n': case 'r': case 't':
                        out.append(c).append(next);
                        i += 2;
                        break;
                    case 'u':
                        if (i + 5 >= s.length()) {
                            throw new XPathException(ErrorCodes.FOJS0007,
                                    "Incomplete \\u escape sequence");
                        }
                        for (int j = i + 2; j < i + 6; j++) {
                            final char h = s.charAt(j);
                            if (!isHex(h)) {
                                throw new XPathException(ErrorCodes.FOJS0007,
                                        "Invalid hex digit in \\u escape: '" + h + "'");
                            }
                        }
                        out.append(s, i, i + 6);
                        i += 6;
                        break;
                    default:
                        throw new XPathException(ErrorCodes.FOJS0007,
                                "Invalid escape sequence: \\" + next);
                }
            } else if (c == '"') {
                // Unescaped quote → escape it
                out.append("\\\"");
                i++;
            } else if (c == '/') {
                if (opts.escapeSolidus) {
                    out.append("\\/");
                } else {
                    out.append('/');
                }
                i++;
            } else if (c == '\b') {
                out.append("\\b"); i++;
            } else if (c == '\f') {
                out.append("\\f"); i++;
            } else if (c == '\n') {
                out.append("\\n"); i++;
            } else if (c == '\r') {
                out.append("\\r"); i++;
            } else if (c == '\t') {
                out.append("\\t"); i++;
            } else if ((c >= 1 && c <= 31) || (c >= 127 && c <= 159)) {
                out.append("\\u").append(String.format("%04X", (int) c));
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private static boolean isHex(final char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Unescape a JSON-style escaped string for use as a map key when comparing
     * for duplicates. Validation is performed by appendValidatedEscapedString;
     * here we just decode known sequences.
     */
    private static String unescapeJsonString(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                final char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '/': sb.append('/'); i += 2; break;
                    case 'b': sb.append('\b'); i += 2; break;
                    case 'f': sb.append('\f'); i += 2; break;
                    case 'n': sb.append('\n'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                final int codePoint = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) codePoint);
                                i += 6;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                                i++;
                            }
                        } else {
                            sb.append(c);
                            i++;
                        }
                        break;
                    default:
                        sb.append(c);
                        i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
