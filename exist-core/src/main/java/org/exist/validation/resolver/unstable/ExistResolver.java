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
package org.exist.validation.resolver.unstable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 *
 * @author dizzzz@exist-db.org
 */
public class ExistResolver implements EntityResolver2, URIResolver {

    private final static Logger LOG = LogManager.getLogger(ExistResolver.class);
    private final static String LOCALURI = "xmldb:exist:///";
    private final static String SHORTLOCALURI = "xmldb:///";
    private BrokerPool brokerPool = null;

    public ExistResolver(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }

    /* ========================================== */
    /* SAX1: interface org.xml.sax.EntityResolver */
    /* ========================================== */
    public InputSource resolveEntity(final String publicId, final String systemId)
            throws SAXException, IOException {

        LOG.debug("publicId={} systemId={}", publicId, systemId);

        return resolveInputSource(brokerPool, systemId);
    }

    /*  =============================================== */
    /*  SAX2: interface org.xml.sax.ext.EntityResolver2 */
    /*  =============================================== */
    public InputSource getExternalSubset(final String name, final String baseURI)
            throws SAXException, IOException {

        LOG.debug("name={} baseURI={}", name, baseURI);

        return resolveInputSource(brokerPool, baseURI);
    }

    public InputSource resolveEntity(final String name, final String publicId,
                                     final String baseURI, final String systemId) throws SAXException, IOException {

        LOG.debug("name={} publicId={} baseURI={} systemId={}", name, publicId, baseURI, systemId);

        return resolveInputSource(brokerPool, systemId);
    }

    /* ================================================ */
    /* JAXP : interface javax.xml.transform.URIResolver */
    /* ================================================ */
    public Source resolve(String href, String base) throws TransformerException {

        LOG.debug("href={} base={}", href, base);

        if (base != null) {
            String sep = "/";
            if (base.startsWith("file:")) {
                sep = File.separator;
            }
            final int pos = base.lastIndexOf(sep);
            if (pos != -1) {
                base = base.substring(0, pos);
                href = base + sep + href;
            }
        }

        return resolveStreamSource(brokerPool, href);
    }

    /* ============== */
    /* Helper methods */
    /* ============== */
    private InputSource resolveInputSource(final BrokerPool bPool, final String path) throws IOException {

        LOG.debug("Resolving inputSource {}", path);

        final InputSource inputsource = new InputSource();

        if (path != null) {

            if (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI)) {
                final XmldbURL url = new XmldbURL(path);
                final EmbeddedInputStream eis = new EmbeddedInputStream(bPool, url);
                inputsource.setByteStream(eis);
                inputsource.setSystemId(path);

            } else {
                final InputStream is = URI.create(path).toURL().openStream();
                inputsource.setByteStream(is);
                inputsource.setSystemId(path);
            }
        }
        return inputsource;
    }

    private StreamSource resolveStreamSource(final BrokerPool bPool, final String path) throws TransformerException {

        LOG.debug("Resolving streamSource {}", path);

        final StreamSource streamsource = new StreamSource();

        try {
            if (path != null) {
                if (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI)) {
                    final XmldbURL url = new XmldbURL(path);
                    final EmbeddedInputStream eis = new EmbeddedInputStream(bPool, url);
                    streamsource.setInputStream(eis);
                    streamsource.setSystemId(path);

                } else {
                    final InputStream is = URI.create(path).toURL().openStream();
                    streamsource.setInputStream(is);
                    streamsource.setSystemId(path);
                }
            }

        } catch (final IOException ex) {
            throw new TransformerException(ex);
        }

        return streamsource;
    }
}
