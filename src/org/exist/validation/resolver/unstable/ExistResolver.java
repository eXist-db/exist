/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.validation.resolver.unstable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;

import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.BrokerPool;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 *
 * @author dizzzz@exist-db.org
 */
public class ExistResolver implements EntityResolver2, URIResolver {

    private final static Logger LOG = Logger.getLogger(EntityResolver2.class);
    private BrokerPool brokerPool = null;
    private final static String LOCALURI = "xmldb:exist:///";
    private final static String SHORTLOCALURI = "xmldb:///";

    public ExistResolver(BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }

    /* ========================================== */
    /* SAX1: interface org.xml.sax.EntityResolver */
    /* ========================================== */
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {

        LOG.debug("publicId=" + publicId + " systemId=" + systemId);

        return resolveInputSource(systemId);
    }

    /*  =============================================== */
    /*  SAX2: interface org.xml.sax.ext.EntityResolver2 */
    /*  =============================================== */
    public InputSource getExternalSubset(String name, String baseURI)
            throws SAXException, IOException {

        LOG.debug("name=" + name + " baseURI=" + baseURI);

        return resolveInputSource(baseURI);
    }

    public InputSource resolveEntity(String name, String publicId,
            String baseURI, String systemId) throws SAXException, IOException {

        LOG.debug("name=" + name + " publicId=" + publicId + " baseURI=" + baseURI + " systemId=" + systemId);

        return resolveInputSource(systemId);
    }

    /* ================================================ */
    /* JAXP : interface javax.xml.transform.URIResolver */
    /* ================================================ */
    public Source resolve(String href, String base) throws TransformerException {

        LOG.debug("href=" + href + " base=" + base);

        return resolveStreamSource(href);
    }

    /* ============== */
    /* Helper methods */
    /* ============== */
    private InputSource resolveInputSource(String path) throws IOException {
        InputSource inputsource = new InputSource();

        if (path != null &&
                (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI))) {

            XmldbURL url = new XmldbURL(path);
            EmbeddedInputStream eis = new EmbeddedInputStream(url);
            inputsource.setByteStream(eis);

        } else {
            InputStream is = new URL(path).openStream();
            inputsource.setByteStream(is);
        }
        return inputsource;
    }

    private StreamSource resolveStreamSource(String path) throws TransformerException {
        StreamSource streamsource = new StreamSource();

        try {
            if (path != null &&
                    (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI))) {

                XmldbURL url = new XmldbURL(path);
                EmbeddedInputStream eis = new EmbeddedInputStream(url);
                streamsource.setInputStream(eis);

            } else {
                InputStream is = new URL(path).openStream();
                streamsource.setInputStream(is);
            }
        } catch (IOException ex) {
            throw new TransformerException(ex);
        }
        return streamsource;
    }
}
