/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xmlrpc;

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.RecursiveTypeParserImpl;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.internal.aider.ACEAider;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * XML-RPC type parser for objects of
 * {@link org.exist.security.internal.aider.ACEAider}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class ACEAiderParser extends RecursiveTypeParserImpl {
    private int level = 0;
    private List<Object> list;

    /**
     * Creates a new instance.
     *
     * @param context The namespace context.
     * @param config  The request or response configuration.
     * @param factory The type factory.
     */
    ACEAiderParser(final XmlRpcStreamConfig config, final NamespaceContextImpl context, final TypeFactory factory) {
        super(config, context, factory);
    }

    @Override
    public void startDocument() throws SAXException {
        level = 0;
        list = new ArrayList<>();
        super.startDocument();
    }

    @Override
    protected void addResult(final Object value) {
        list.add(value);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qname) throws SAXException {
        switch (--level) {
            case 0:
                setResult(toAceAider(list));
                break;
            case 1:
                break;
            case 2:
                endValueTag();
                break;
            default:
                super.endElement(uri, localName, qname);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qname, final Attributes attrs) throws SAXException {
        switch (level++) {
            case 0:
                if (!XMLConstants.NULL_NS_URI.equals(uri) || !ACEAiderSerializer.ACEAIDER_TAG.equals(localName)) {
                    throw new SAXParseException("Expected aceAider element, got "
                            + new QName(uri, localName),
                            getDocumentLocator());
                }
                break;
            case 1:
                if (!XMLConstants.NULL_NS_URI.equals(uri) || !ACEAiderSerializer.DATA_TAG.equals(localName)) {
                    throw new SAXParseException("Expected data element, got "
                            + new QName(uri, localName),
                            getDocumentLocator());
                }
                break;
            case 2:
                if (!XMLConstants.NULL_NS_URI.equals(uri) || !ACEAiderSerializer.VALUE_TAG.equals(localName)) {
                    throw new SAXParseException("Expected value element, got "
                            + new QName(uri, localName),
                            getDocumentLocator());
                }
                startValueTag();
                break;
            default:
                super.startElement(uri, localName, qname, attrs);
                break;
        }
    }

    private static ACEAider toAceAider(final List<Object> list) throws SAXException {
        if (list.size() != 4) {
            throw new SAXException("Inavlis list size for ACEAider");
        }

        Object object = list.getFirst();
        final ACE_ACCESS_TYPE aceAccessType;
        if (object instanceof String) {
            try {
                aceAccessType = ACE_ACCESS_TYPE.valueOf((String) object);
            } catch (final IllegalArgumentException e) {
                throw new SAXException(e);
            }
        } else {
            throw new SAXException("Expected ACE_ACCESS_TYPE");
        }

        object = list.get(1);
        final ACE_TARGET aceTarget;
        if (object instanceof String) {
            try {
                aceTarget = ACE_TARGET.valueOf((String) object);
            } catch (final IllegalArgumentException e) {
                throw new SAXException(e);
            }
        } else {
            throw new SAXException("Expected ACE_TARGET");
        }

        object = list.get(2);
        final String aceWho;
        if (object instanceof String) {
            aceWho = (String) object;
        } else {
            throw new SAXException("Expected String");
        }

        object = list.get(3);
        final int aceMode;
        if (object instanceof Integer) {
            aceMode = (Integer) object;
        } else {
            throw new SAXException("Expected Integer");
        }

        return new ACEAider(aceAccessType, aceTarget, aceWho, aceMode);
    }
}
