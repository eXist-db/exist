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
package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Properties;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.EXistURISchemeURIResolver;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.exist.xslt.EXistURIResolver;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class RenderXXepProcessorAdapter implements ProcessorAdapter {

    private Object formatter = null;

    static {
        System.setProperty("com.renderx.jaxp.uriresolver","org.exist.xquery.modules.xslfo.RenderXXepProcessorAdapter.EXistURISchemeAndURIResolver");
    }

    public static class EXistURISchemeAndURIResolver implements URIResolver {
        private static final String DEFAULT_BASE_URI = "exist://localhost/db/";

        private static URIResolver existUriSchemeAndURIResolver = null;

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {
            synchronized (EXistURISchemeAndURIResolver.class) {
                if (existUriSchemeAndURIResolver == null) {
                    try {
                        existUriSchemeAndURIResolver = init();
                    } catch (final EXistException e) {
                        throw new TransformerException(e.getMessage(), e);
                    }
                }
            }
            return existUriSchemeAndURIResolver.resolve(href, base);
        }

        private static URIResolver init() throws EXistException {
            final BrokerPool brokerPool = BrokerPool.getInstance();
            return new EXistURISchemeURIResolver(new EXistURIResolver(brokerPool, DEFAULT_BASE_URI));
        }
    }


    @Override
    public ContentHandler getContentHandler(DBBroker broker, NodeValue configFile, Properties parameters, String mimeType, OutputStream os) throws XPathException, SAXException {

        if(configFile == null) {
            throw new XPathException("XEP requires a configuration file");
        }

        try {

            Class formatterImplClazz = Class.forName("com.renderx.xep.FormatterImpl");

            if(parameters == null) {
                Constructor formatterImplCstr = formatterImplClazz.getConstructor(Source.class);
                formatter = formatterImplCstr.newInstance(new DOMSource(configFile.getNode()));
            } else {
                Constructor formatterImplCstr = formatterImplClazz.getConstructor(Source.class, Properties.class);
                formatter = formatterImplCstr.newInstance(new DOMSource(configFile.getNode()), parameters);
            }
            String backendType = mimeType.substring(mimeType.indexOf("/")+1).toUpperCase();

            Class foTargetClazz = Class.forName("com.renderx.xep.FOTarget");
            Constructor foTargetCstr = foTargetClazz.getConstructor(OutputStream.class, String.class);
            Object foTarget = foTargetCstr.newInstance(os, backendType);

            Method createContentHandlerMethod = formatterImplClazz.getMethod("createContentHandler", String.class, foTargetClazz);

            return (ContentHandler) createContentHandlerMethod.invoke(formatter, null, foTarget);
        } catch (Exception e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void cleanup() {
        if(formatter != null) {
            try{
                Class formatterImplClazz = Class.forName("com.renderx.xep.FormatterImpl");
                Method cleanupMethod = formatterImplClazz.getMethod("cleanup");
                cleanupMethod.invoke(formatter);
            } catch(Exception e) {
                // do nothing
            }
        }
     }
}
