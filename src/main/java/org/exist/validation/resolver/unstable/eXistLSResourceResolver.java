/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.embedded.EmbeddedInputStream;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.protocolhandler.xmlrpc.XmlrpcInputStream;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * eXistLSResourceResolver provides a way for applications to redirect
 * references to external resource.
 *
 * To be used by @see javax.xml.validation.Validator
 * 
 * @author Dizzzz (dizzzz@exist-db.org)
 */
public class eXistLSResourceResolver implements LSResourceResolver {
    private static final Logger LOG = LogManager.getLogger(eXistLSResourceResolver.class);
    private static final ThreadGroup threadGroup = new ThreadGroup("exist.ls-resolver");

    public LSInput resolveResource(String type, String namespaceURI,
            String publicId, String systemId, String baseURI) {

        LOG.debug("type=" + type + " namespaceURI=" + namespaceURI
                + " publicId=" + publicId + " systemId=" + systemId
                + " baseURI=" + baseURI);

        LSInput lsInput = new eXistLSInput();

        try {
            final InputStream is = getInputStream(systemId);
            lsInput.setByteStream(is);

        } catch (final Exception ex) {
            LOG.error(ex.getMessage());
            lsInput=null;            
        } 

        return lsInput;
    }

    private InputStream getInputStream(String resourcePath) throws MalformedURLException, IOException{

        if(resourcePath.startsWith("/db")){
            resourcePath="xmldb:exist://"+resourcePath;
        }

        InputStream is = null;
        if (resourcePath.startsWith("xmldb:")) {
            final XmldbURL xmldbURL = new XmldbURL(resourcePath);
            if (xmldbURL.isEmbedded()) {
                is = new EmbeddedInputStream(xmldbURL);

            } else {
                is = new XmlrpcInputStream(threadGroup, xmldbURL);
            }

        } else {
            is = new URL(resourcePath).openStream();
        }
        return is;
    }
}
