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
package org.exist.resolver;

import org.apache.xerces.impl.xs.XSDDescription;
import org.apache.xerces.util.SAXInputSource;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlresolver.Resolver;

import java.io.IOException;

/**
 * Adapts an {@link org.xmlresolver.Resolver} for use
 * with Xerces SAX Parser by implementing {@link org.apache.xerces.xni.parser.XMLEntityResolver}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class XercesXmlResolverAdapter implements XMLEntityResolver {
    private final Resolver resolver;

    public XercesXmlResolverAdapter(final Resolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public XMLInputSource resolveEntity(final XMLResourceIdentifier xmlResourceIdentifier) throws XNIException, IOException {

        try {
            final org.apache.xerces.xni.QName triggeringComponent = ((XSDDescription)xmlResourceIdentifier).getTriggeringComponent();

            // get the name
            final String name;
            if (triggeringComponent != null) {
                name = triggeringComponent.localpart;
            } else {
                name = null;
            }

            // get the systemId
            final String systemId;
            if (xmlResourceIdentifier.getExpandedSystemId() !=  null) {
                systemId = xmlResourceIdentifier.getExpandedSystemId();
            } else {
                systemId = xmlResourceIdentifier.getNamespace();
            }

//            System.out.println(String.format("xri=(name=%s publicId=%s baseSystemId=%s systemId=%s)", name, xmlResourceIdentifier.getPublicId(), xmlResourceIdentifier.getBaseSystemId(), systemId));

            // resolve the entity via an org.xmlresolver.Resolver
            final InputSource src = resolver.resolveEntity(name, xmlResourceIdentifier.getPublicId(), xmlResourceIdentifier.getBaseSystemId(), systemId);
            if (src == null) {
                return null;
            }

            return new SAXInputSource(src);

        } catch (final SAXException e) {
            throw new XNIException(e);
        }
    }
}
