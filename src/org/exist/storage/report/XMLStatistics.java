/*
 * Created on 5 sept. 2004
$Id$
 */
package org.exist.storage.report;

import java.util.Iterator;

import org.exist.storage.BrokerPool;
import org.exist.storage.BufferStats;
import org.exist.storage.NativeBroker;
import org.exist.storage.NativeElementIndex;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;
import org.exist.util.Configuration;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;


/** generate statistics about the XML storage - 
 * used by {@link org.apache.cocoon.generation.StatusGenerator}
 * @author jmv
 */
public class XMLStatistics {
	public final static String NAMESPACE = "http://exist.sourceforge.net/generators/status";
	public final static String PREFIX = "status";
	public ContentHandler contentHandler;

	/**
	 * @param contentHandler
	 */
	public XMLStatistics(ContentHandler contentHandler) {
		this.contentHandler = contentHandler;
	}

	public void genInstanceStatus() throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		//TODO : find a way to retrieve the actual instance's name !
		atts.addAttribute("", "default", "default", "CDATA", "exist");
		this.contentHandler.startElement(NAMESPACE, "database-instances", 
			PREFIX + ":database-instances", atts);
		atts.clear();
		
		BrokerPool instance;
		for(Iterator i = BrokerPool.getInstances(); i.hasNext(); ) {
			instance = (BrokerPool)i.next();
			atts.addAttribute("", "name", "name", "CDATA", instance.getId());
			this.contentHandler.startElement(NAMESPACE, "database-instance", 
				PREFIX + ":database-instance", atts);
			atts.clear();
			addValue("configuration", instance.getConfiguration().getPath());
			addValue("data-directory", (String)instance.getConfiguration().getProperty("db-connection.data-dir"));

            // values added for cache used % calc - Gary Larsen
            addValue("cache-size", String.valueOf(instance.getConfiguration().getInteger("db-connection.cache-size")));
            addValue("page-size", String.valueOf(instance.getConfiguration().getInteger("db-connection.page-size")));

            this.contentHandler.startElement(NAMESPACE, "pool", PREFIX + ":pool", atts);
			addValue("max", String.valueOf(instance.getMax()));
			addValue("active", String.valueOf(instance.active()));
			addValue("available", String.valueOf(instance.available()));
			this.contentHandler.endElement(NAMESPACE, "pool", PREFIX + ":pool");
			genBufferStatus(instance);
			this.contentHandler.endElement(NAMESPACE, "database-instance",
				PREFIX + ":database-instance");
		}
		
		this.contentHandler.endElement(NAMESPACE, "database-instances",
			PREFIX + "database-instances");
	}
	
	private void genBufferStatus(BrokerPool instance) throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		this.contentHandler.startElement(NAMESPACE, "buffers", PREFIX + ":buffers", atts);
		
		Configuration conf = instance.getConfiguration();
		BFile db;
		db = (BFile) conf.getProperty("db-connection.collections");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Collections storage ("+ NativeBroker.COLLECTIONS_DBX + ")");
		DOMFile dom = (DOMFile) conf.getProperty("db-connection.dom");
		genBufferDetails(dom.getIndexBufferStats(), dom.getDataBufferStats(), "Resource storage ("+ NativeBroker.DOM_DBX + ")");
		db = (BFile) conf.getProperty("db-connection.elements");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Structural index ("+ NativeElementIndex.ELEMENTS_DBX + ")");
		db = (BFile) conf.getProperty("db-connection.values");
		if (db != null)
			genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Values index ("+ NativeBroker.VALUES_DBX + ")");
		db = (BFile) conf.getProperty("db-connection2.values");
		if (db != null)
			genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "QName values index ("+ NativeBroker.VALUES_QNAME_DBX + ")");
		db = (BFile) conf.getProperty("db-connection.words");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "Fulltext index ("+ NativeBroker.WORDS_DBX + ")");		
		this.contentHandler.endElement(NAMESPACE, "buffers", PREFIX + ":buffers");
	}
	
	private void genBufferDetails(BufferStats index, BufferStats data, String name) throws SAXException {
		AttributesImpl atts = new AttributesImpl();
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
		AttributesImpl atts = new AttributesImpl();
		this.contentHandler.startElement(NAMESPACE, elem, PREFIX + ':' + elem, atts);
		this.contentHandler.characters(value.toCharArray(), 0, value.length());
		this.contentHandler.endElement(NAMESPACE, elem, PREFIX + ':' + elem);
	}

	/**
	 * @param contentHandler
	 */
	public void setContentHandler(ContentHandler contentHandler) {
		this.contentHandler = contentHandler;
		
	}

}
