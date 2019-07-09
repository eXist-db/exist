/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.storage.report;

import java.nio.file.Path;
import java.util.Optional;

import org.exist.collections.CollectionCache;
import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.NativeValueIndex;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;
import org.exist.storage.index.CollectionStore;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/** generate statistics about the XML storage - 
 * used by org.apache.cocoon.generation.StatusGenerator
 * @author jmv
 */
public class XMLStatistics {

    public final static String NAMESPACE = "http://exist.sourceforge.net/generators/status";
    public final static String PREFIX = "status";
    public ContentHandler contentHandler;

    public XMLStatistics(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    public void genInstanceStatus() throws SAXException {
        final AttributesImpl atts = new AttributesImpl();
        //TODO : find a way to retrieve the actual instance's name !
        atts.addAttribute("", "default", "default", "CDATA", "exist");
        this.contentHandler.startElement(NAMESPACE, "database-instances", 
            PREFIX + ":database-instances", atts);
        atts.clear();
        BrokerPool.readInstances(brokerPool -> getInstanceStatus(atts, brokerPool));
        this.contentHandler.endElement(NAMESPACE, "database-instances",
            PREFIX + ":database-instances");
    }

    private void getInstanceStatus(final AttributesImpl atts, final BrokerPool instance) throws SAXException {
        atts.addAttribute("", "name", "name", "CDATA", instance.getId());
        this.contentHandler.startElement(NAMESPACE, "database-instance",
                PREFIX + ":database-instance", atts);
        atts.clear();
        final Optional<Path> configPath = instance.getConfiguration().getConfigFilePath();
        if(configPath.isPresent()) {
            addValue("configuration", configPath.get().toAbsolutePath().toString());
        }
        addValue("data-directory", ((Path)instance.getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR)).toAbsolutePath().toString());
        addValue("cache-size", String.valueOf(instance.getConfiguration().getInteger("db-connection.cache-size")));
        addValue("page-size", String.valueOf(instance.getConfiguration().getInteger("db-connection.page-size")));
        addValue("collection-cache-mem", String.valueOf(instance.getConfiguration().getInteger(CollectionCache.PROPERTY_CACHE_SIZE_BYTES)));
        this.contentHandler.startElement(NAMESPACE, "pool", PREFIX + ":pool", atts);
        addValue("max", String.valueOf(instance.getMax()));
        addValue("active", String.valueOf(instance.countActiveBrokers()));
        addValue("available", String.valueOf(instance.available()));
        this.contentHandler.endElement(NAMESPACE, "pool", PREFIX + ":pool");
        genBufferStatus(instance);
        this.contentHandler.endElement(NAMESPACE, "database-instance",
                PREFIX + ":database-instance");
    }

    private void genBufferStatus(BrokerPool instance) throws SAXException {
        final AttributesImpl atts = new AttributesImpl();
        this.contentHandler.startElement(NAMESPACE, "buffers", PREFIX + ":buffers", atts);
        final Configuration conf = instance.getConfiguration();
        BFile db;
        db = (BFile) conf.getProperty(CollectionStore.FILE_KEY_IN_CONFIG);
        genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Collections storage ("+ FileUtils.fileName(db.getFile()) + ")");
        final DOMFile dom = (DOMFile) conf.getProperty(DOMFile.CONFIG_KEY_FOR_FILE);
        genBufferDetails(dom.getIndexBufferStats(), dom.getDataBufferStats(), "Resource storage ("+ FileUtils.fileName(dom.getFile()) + ")");
        db = (BFile) conf.getProperty(NativeValueIndex.FILE_KEY_IN_CONFIG);
        if (db != null)
            {genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Values index ("+ FileUtils.fileName(db.getFile()) + ")");}
        this.contentHandler.endElement(NAMESPACE, "buffers", PREFIX + ":buffers");
    }

    private void genBufferDetails(BufferStats index, BufferStats data, String name) throws SAXException {
        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "name", "name", "CDATA", name);
        this.contentHandler.startElement(NAMESPACE, "file", PREFIX + ":file", atts);
        atts.clear();
        atts.addAttribute("", "type", "type", "CDATA", "btree");
        this.contentHandler.startElement(NAMESPACE, "buffer", PREFIX + ":buffer", atts);
        atts.clear();
        addValue("size", String.valueOf(index.getSize()));
        addValue("used", String.valueOf(index.getUsed()));
        addValue("hits", String.valueOf(index.getPageHits()));
        addValue("fails", String.valueOf(index.getPageFails()));
        this.contentHandler.endElement(NAMESPACE, "buffer", PREFIX + ":buffer");
        atts.addAttribute("", "type", "type", "CDATA", "data");
        this.contentHandler.startElement(NAMESPACE, "buffer", PREFIX + ":buffer", atts);
        atts.clear();
        addValue("size", String.valueOf(data.getSize()));
        addValue("used", String.valueOf(data.getUsed()));
        addValue("hits", String.valueOf(data.getPageHits()));
        addValue("fails", String.valueOf(data.getPageFails()));
        this.contentHandler.endElement(NAMESPACE, "buffer", PREFIX + ":buffer");
        this.contentHandler.endElement(NAMESPACE, "file", PREFIX + ":file");
    }

    public void addValue(String elem, String value) throws SAXException {
        final AttributesImpl atts = new AttributesImpl();
        this.contentHandler.startElement(NAMESPACE, elem, PREFIX + ':' + elem, atts);
        this.contentHandler.characters(value.toCharArray(), 0, value.length());
        this.contentHandler.endElement(NAMESPACE, elem, PREFIX + ':' + elem);
    }

    public void setContentHandler(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

}
