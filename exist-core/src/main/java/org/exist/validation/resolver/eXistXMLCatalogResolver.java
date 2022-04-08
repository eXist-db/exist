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

package org.exist.validation.resolver;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.util.XMLCatalogResolver;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.exist.util.FileUtils;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Wrapper around xerces2's
 * <a href="http://xerces.apache.org/xerces2-j/javadocs/xerces2/org/apache/xerces/util/XMLCatalogResolver.html"
 * >XMLCatalogresolver</a>
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class eXistXMLCatalogResolver extends XMLCatalogResolver {

    //TODO(AR) refactor to org.apache.xml.resolver.tools.CatalogResolver which is reused within here for Xerces
    // see https://github.com/eXist-db/exist/issues/350
    // see https://www.saxonica.com/html/documentation/sourcedocs/xml-catalogs.html

    public eXistXMLCatalogResolver() {
        super();
        LOG.debug("Initializing");
    }

    public eXistXMLCatalogResolver(java.lang.String[] catalogs) {
        super(catalogs);
        LOG.debug("Initializing using catalogs");
    }

    eXistXMLCatalogResolver(java.lang.String[] catalogs, boolean preferPublic) {
        super(catalogs, preferPublic);
        LOG.debug("Initializing using catalogs, preferPublic={}", preferPublic);
    }

    private final static Logger LOG = LogManager.getLogger(eXistXMLCatalogResolver.class);

    /**
     * Constructs a catalog resolver with the given list of entry files.
     *
     * @param catalogs List of Strings
     *
     *                 TODO: check for non-String and NULL values.
     */
    public void setCatalogs(List<String> catalogs) {

        if (catalogs != null && catalogs.size() > 0) {
            final String[] allCatalogs = new String[catalogs.size()];
            int counter = 0;
            for (String element : catalogs) {
                allCatalogs[counter] = element;
                counter++;
            }
            super.setCatalogList(allCatalogs);
        }
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        LOG.debug("Resolving publicId='{}', systemId='{}'", publicId, systemId);
        InputSource retValue = super.resolveEntity(publicId, systemId);

        if (retValue == null) {
            retValue = resolveEntityFallback(publicId, systemId);
        }

        LOG.debug("Resolved {}", retValue != null);
        if (retValue != null) {
            LOG.debug("PublicId='{}' SystemId={}", retValue.getPublicId(), retValue.getSystemId());
        }
        return retValue;
    }

    /**
     * moved from Collection.resolveEntity() revision 6144
     */
    private InputSource resolveEntityFallback(String publicId, String systemId) throws SAXException, IOException {
        //if resolution failed and publicId == null,
        // try to make absolute file names relative and retry
        LOG.debug("Resolve failed, fallback scenario");
        if (publicId != null) {
            return null;
        }

        final URL url = new URL(systemId);
        if ("file".equals(url.getProtocol())) {
            final String path = url.getPath();
            final Path f = Paths.get(path).normalize();
            if (!Files.isReadable(f)) {
                return resolveEntity(null, FileUtils.fileName(f));
            } else {
                return new InputSource(f.toAbsolutePath().toString());
            }
        } else {
            return new InputSource(url.openStream());       //TODO(AR) stream is never closed!
        }
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        LOG.debug("Resolving type='{}', namespaceURI='{}', publicId='{}', systemId='{}', baseURI='{}'", type, namespaceURI, publicId, systemId, baseURI);
        final LSInput retValue = super.resolveResource(type, namespaceURI, publicId, systemId, baseURI);

        LOG.debug("Resolved {}", retValue != null);
        if (retValue != null) {
            LOG.debug("PublicId='{}' SystemId='{}' BaseURI='{}'", retValue.getPublicId(), retValue.getSystemId(), retValue.getBaseURI());
        }

        return retValue;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        LOG.debug("Resolving name='{}', publicId='{}', baseURI='{}', systemId='{}'", name, publicId, baseURI, systemId);
        final InputSource retValue = super.resolveEntity(name, publicId, baseURI, systemId);

        LOG.debug("Resolved {}", retValue != null);
        if (retValue != null) {
            LOG.debug("PublicId='{}' SystemId='{}'", retValue.getPublicId(), retValue.getSystemId());
        }

        return retValue;
    }

    @Override
    public String resolveIdentifier(XMLResourceIdentifier xri) throws IOException, XNIException {

        if (xri.getExpandedSystemId() == null && xri.getLiteralSystemId() == null &&
                xri.getNamespace() == null && xri.getPublicId() == null) {

            // quick fail
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving XMLResourceIdentifier: {}", getXriDetails(xri));
        }

        final String retValue = super.resolveIdentifier(xri);
        LOG.debug("Resolved {}", retValue != null);
        if (retValue != null) {
            LOG.debug("Identifier='{}'", retValue);
        }
        return retValue;
    }

    @Override
    public XMLInputSource resolveEntity(XMLResourceIdentifier xri) throws XNIException, IOException {
        if (xri.getExpandedSystemId() == null && xri.getLiteralSystemId() == null &&
                xri.getNamespace() == null && xri.getPublicId() == null) {

            // quick fail
            return null;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving XMLResourceIdentifier: {}", getXriDetails(xri));
        }
        final XMLInputSource retValue = super.resolveEntity(xri);


        LOG.debug("Resolved {}", retValue != null);
        if (retValue != null) {
            LOG.debug("PublicId='{}' SystemId='{}' BaseSystemId={}", retValue.getPublicId(), retValue.getSystemId(), retValue.getBaseSystemId());
        }

        return retValue;
    }

    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        LOG.debug("name='{}' baseURI='{}'", name, baseURI);
        return super.getExternalSubset(name, baseURI);
    }

    private String getXriDetails(XMLResourceIdentifier xrid) {
        return "PublicId='" + xrid.getPublicId() + "' " + "BaseSystemId='" + xrid.getBaseSystemId() + "' " + "ExpandedSystemId='" + xrid.getExpandedSystemId() + "' " + "LiteralSystemId='" + xrid.getLiteralSystemId() + "' " + "Namespace='" + xrid.getNamespace() + "' ";
    }

}
