/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private final static Logger LOG = LogManager.getLogger(ExistResolver.class);
    
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

        return resolveInputSource(brokerPool, systemId);
    }

    /*  =============================================== */
    /*  SAX2: interface org.xml.sax.ext.EntityResolver2 */
    /*  =============================================== */
    public InputSource getExternalSubset(String name, String baseURI)
            throws SAXException, IOException {

        LOG.debug("name=" + name + " baseURI=" + baseURI);

        return resolveInputSource(brokerPool, baseURI);
    }

    public InputSource resolveEntity(String name, String publicId,
            String baseURI, String systemId) throws SAXException, IOException {

        LOG.debug("name=" + name + " publicId=" + publicId + " baseURI=" + baseURI + " systemId=" + systemId);

        return resolveInputSource(brokerPool, systemId);
    }

    /* ================================================ */
    /* JAXP : interface javax.xml.transform.URIResolver */
    /* ================================================ */
    public Source resolve(String href, String base) throws TransformerException {

        LOG.debug("href=" + href + " base=" + base);

        if(base!=null){
        	String sep = "/"; 
        	if (base.startsWith("file:")) {
        		sep = File.separator;
        	}
            final int pos = base.lastIndexOf(sep);
            if(pos!=-1){
                base=base.substring(0, pos);
                href=base + sep + href;
            }
        }

        return resolveStreamSource(brokerPool, href);
    }

    /* ============== */
    /* Helper methods */
    /* ============== */
    private InputSource resolveInputSource(BrokerPool bPool, String path) throws IOException {

        LOG.debug("Resolving " + path);

        final InputSource inputsource = new InputSource();

        if (path != null) {

            if (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI)) {
                final XmldbURL url = new XmldbURL(path);
                final EmbeddedInputStream eis = new EmbeddedInputStream(bPool, url);
                inputsource.setByteStream(eis);
                inputsource.setSystemId(path);

            } else {
                final InputStream is = new URL(path).openStream();
                inputsource.setByteStream(is);
                inputsource.setSystemId(path);
            }
        }
        return inputsource;
    }

    private StreamSource resolveStreamSource(BrokerPool bPool, String path) throws TransformerException {

        LOG.debug("Resolving "+path);
        
        final StreamSource streamsource = new StreamSource();

        try {
            if (path != null) {
                if (path.startsWith(LOCALURI) || path.startsWith(SHORTLOCALURI)) {
                    final XmldbURL url = new XmldbURL(path);
                    final EmbeddedInputStream eis = new EmbeddedInputStream(bPool, url);
                    streamsource.setInputStream(eis);
                    streamsource.setSystemId(path);

                } else {
                    final InputStream is = new URL(path).openStream();
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
