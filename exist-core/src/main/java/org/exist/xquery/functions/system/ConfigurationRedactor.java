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
package org.exist.xquery.functions.system;

import java.util.regex.Pattern;

import javax.xml.XMLConstants;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Copies eXist's effective {@code conf.xml} into a {@link MemTreeBuilder},
 * replacing the value of any attribute or element whose local name looks
 * like a credential with {@code [redacted]}. Shared by
 * {@link GetConfiguration} and {@link GetConfigurationProperty} — see #6563:
 * {@code conf.xml} carries the admin password hash, LDAP/SMTP passwords, JDBC
 * connection strings, and arbitrary {@code <parameter name="password" .../>}
 * entries, none of which should be exposed even to a DBA-only XQuery function
 * without redaction.
 *
 * <p>The marker list ({@code password}, {@code passwd}, {@code secret},
 * {@code credential}) is a starting point, not a final allowlist —
 * {@code key} and {@code token} are intentionally excluded as too broad (they
 * also appear in non-sensitive contexts such as cache key sizes and MIME
 * type tokens).
 */
final class ConfigurationRedactor {

    static final String REDACTED = "[redacted]";

    private static final Pattern CREDENTIAL_MARKER =
            Pattern.compile("password|passwd|secret|credential", Pattern.CASE_INSENSITIVE);

    private ConfigurationRedactor() {
    }

    /**
     * Copies {@code source}'s document element into {@code builder}, redacting
     * credential-shaped attribute and element values along the way.
     *
     * @param source the effective configuration document
     * @param builder the target builder; caller owns {@code pushDocumentContext()}/
     *                {@code popDocumentContext()}
     */
    static void copyRedacted(final Document source, final MemTreeBuilder builder) {
        copyElement(source.getDocumentElement(), builder);
    }

    /**
     * Builds a redacted copy of the effective configuration as a queryable eXist node,
     * anchored in {@code context}'s own document set (so it can be used as the context
     * item for a further XQuery/XPath evaluation, e.g. in {@link GetConfigurationProperty}).
     *
     * @param context the query context whose broker's configuration document to redact
     * @return the redacted {@code <exist>} root element
     */
    static NodeValue buildRedactedRoot(final XQueryContext context) {
        final Document source = context.getBroker().getConfiguration().getConfigurationDocument();
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            copyRedacted(source, builder);
            return (NodeValue) builder.getDocument().getNode(1);
        } finally {
            context.popDocumentContext();
        }
    }

    static boolean isCredentialMarker(final String localName) {
        return localName != null && CREDENTIAL_MARKER.matcher(localName).find();
    }

    /**
     * Whether {@code attr}'s value should be redacted: either its own local name looks
     * like a credential, or it's the {@code value} attribute of eXist's
     * {@code <parameter name="password" value="..."/>} idiom (the secret lives in
     * {@code value}, not in an attribute literally named {@code password}).
     */
    static boolean isCredentialAttribute(final Attr attr) {
        final String localName = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
        if (isCredentialMarker(localName)) {
            return true;
        }
        if (!"value".equals(localName)) {
            return false;
        }
        final String nameAttr = attr.getOwnerElement().getAttribute("name");
        return !nameAttr.isEmpty() && isCredentialMarker(nameAttr);
    }

    private static void copyElement(final Element element, final MemTreeBuilder builder) {
        final QName qname = toQName(element);
        builder.startElement(qname, null);

        final NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Attr attr = (Attr) attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            builder.addAttribute(toQName(attr), isCredentialAttribute(attr) ? REDACTED : attr.getValue());
        }

        if (isCredentialMarker(element.getLocalName() != null ? element.getLocalName() : element.getTagName())) {
            builder.characters(REDACTED);
        } else {
            final NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final Node child = children.item(i);
                switch (child.getNodeType()) {
                    case Node.ELEMENT_NODE -> copyElement((Element) child, builder);
                    case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.characters(child.getNodeValue());
                    default -> { /* skip comments, PIs */ }
                }
            }
        }

        builder.endElement();
    }

    private static QName toQName(final Node node) {
        final String localName = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
        final String namespaceURI = node.getNamespaceURI() != null ? node.getNamespaceURI() : XMLConstants.NULL_NS_URI;
        final String prefix = node.getPrefix() != null ? node.getPrefix() : XMLConstants.DEFAULT_NS_PREFIX;
        return new QName(localName, namespaceURI, prefix);
    }
}
