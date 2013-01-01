/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
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
package org.exist.collections.triggers;

import java.util.HashMap;
import java.util.Map;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

/**
 * Cache for STX Templates
 *
 * @author aretter
 */
public class STXTemplatesCache {

    protected Logger LOG = Logger.getLogger(getClass());

    private Map<XmldbURI, CachedTemplate> cache = new HashMap<XmldbURI, CachedTemplate>();

    private final SAXTransformerFactory factory = (SAXTransformerFactory)TransformerFactory.newInstance("net.sf.joost.trax.TransformerFactoryImpl", getClass().getClassLoader());

    /**
     * Will get the compiled stylesheet from the cache
     *
     * If the stylesheet is not present in the cache it will be compiled and added to the cache
     *
     * If the stylesheet is in the cache but older than the current stylesheet it will be re-compiled and cached
     *
     * @param broker Database broker for accessing the serializer pool
     * @return The compiled stylesheet
     */
    public synchronized Templates getOrUpdateTemplate(DBBroker broker, DocumentImpl stylesheet) throws TransformerConfigurationException, SAXException {
        //is it already in the cache
        XmldbURI stylesheetUri = stylesheet.getURI();
        long lastModified = stylesheet.getMetadata().getLastModified();
        CachedTemplate cachedTemplate = cache.get(stylesheetUri);
        if(cachedTemplate == null) {
             cachedTemplate = storeInCache(broker, stylesheetUri, stylesheet, lastModified);
        } else {
            //has it been modified since it was cached?
            if(lastModified > cachedTemplate.getLastUpdated()) {
                //refresh the entry in the cache
                cachedTemplate = storeInCache(broker, stylesheetUri, stylesheet, lastModified);
            }
        }
        LOG.debug("Retrieved STX Template '" + stylesheetUri.toString() + "' from cache.");
        return cachedTemplate.getTemplate();
    }

    private CachedTemplate storeInCache(DBBroker broker, XmldbURI stylesheetUri, DocumentImpl stylesheet, long lastModified) throws TransformerConfigurationException, SAXException {
        Templates compiled = compileTemplate(broker, stylesheet);
        CachedTemplate cachedTemplate = new CachedTemplate(compiled, lastModified);
        cache.put(stylesheetUri, cachedTemplate);
        LOG.debug("Compiled and Stored STX Template '" + stylesheetUri.toString() + "' in cache.");
        return cachedTemplate;
    }

    private Templates compileTemplate(DBBroker broker, DocumentImpl stylesheet) throws TransformerConfigurationException, SAXException {
        Serializer serializer = broker.getSerializer();
        TemplatesHandler thandler = factory.newTemplatesHandler();
        serializer.setSAXHandlers(thandler, null);
        serializer.toSAX(stylesheet);
        return thandler.getTemplates();
    }

    private class CachedTemplate {

        private final Templates templates;
        private final long lastUpdated;

        public CachedTemplate(Templates templates, long lastUpdated) {
            this.templates = templates;
            this.lastUpdated = lastUpdated;
        }

        private long getLastUpdated() {
            return lastUpdated;
        }

        private Templates getTemplate() {
            return templates;
        }
    }
}