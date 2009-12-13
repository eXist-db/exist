/*
 * StatusGenerator.java - May 17, 2003
 * 
 * @author wolf
 */
package org.exist.cocoon;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.generation.AbstractGenerator;
import org.exist.storage.report.XMLStatistics;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A Cocoon Generator which generates status information about running database instances,
 * buffer usage and the like.
 * 
 */
public class StatusGenerator extends AbstractGenerator {

	public final static String NAMESPACE = "http://exist.sourceforge.net/generators/status";
	public final static String PREFIX = "status";
	XMLStatistics stats;
	
	public StatusGenerator() {
		super();
		stats = new XMLStatistics(contentHandler);
	}

	/**
	 * @see org.apache.cocoon.generation.Generator#generate()
	 */
	public void generate() throws IOException, SAXException, ProcessingException {
		stats.setContentHandler(contentHandler);
		this.contentHandler.startDocument();
		this.contentHandler.startPrefixMapping(PREFIX, NAMESPACE);
		this.contentHandler.startElement(NAMESPACE, "status", PREFIX + ":status", new AttributesImpl());
		genVMStatus();
		stats.genInstanceStatus();
		this.contentHandler.endElement(NAMESPACE, "status", PREFIX + ":status");
		this.contentHandler.endPrefixMapping(PREFIX);
		this.contentHandler.endDocument();
	}
	
	private void genVMStatus() throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		this.contentHandler.startElement(NAMESPACE, "system", PREFIX + ":system", atts);
		
		this.contentHandler.startElement(NAMESPACE, "memory", PREFIX + ":memory", atts);
		addValue("total", String.valueOf(Runtime.getRuntime().totalMemory()));
		addValue("free", String.valueOf(Runtime.getRuntime().freeMemory()));
		addValue("max", String.valueOf(Runtime.getRuntime().maxMemory()));
		this.contentHandler.endElement(NAMESPACE, "memory", PREFIX + ":memory");
		
		this.contentHandler.startElement(NAMESPACE, "jvm", PREFIX + ":jvm", atts);
		addValue("version", System.getProperty("java.version"));
		addValue("vendor", System.getProperty("java.vendor"));
		
		Locale locale = Locale.getDefault();
		addValue("locale", locale.toString());
		
		InputStreamReader is = new InputStreamReader(System.in);
		addValue("charset", is.getEncoding());
		this.contentHandler.endElement(NAMESPACE, "jvm", PREFIX + ":jvm");
		
		this.contentHandler.startElement(NAMESPACE, "os", PREFIX + ":os", atts);
		addValue("name", System.getProperty("os.name"));
		addValue("architecture", System.getProperty("os.arch"));
		addValue("version", System.getProperty("os.version"));
		this.contentHandler.endElement(NAMESPACE, "os", PREFIX + ":os");
		
		this.contentHandler.endElement(NAMESPACE, "system", PREFIX + ":system");
	}
	/* ===================
	private void genInstanceStatus() throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		atts.addAttribute("", "default", "default", "CDATA", BrokerPool.DEFAULT_INSTANCE);
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
			addValue("data-directory", (String)instance.getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR));
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
		BFile db = (BFile) conf.getProperty("db-connection.elements");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "elements.dbx");
		db = (BFile) conf.getProperty("db-connection.collections");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "collections.dbx");
		db = (BFile) conf.getProperty("db-connection.words");
		genBufferDetails(db.getIndexBufferStats(), db.getDataBufferStats(), "words.dbx");
		DOMFile dom = (DOMFile) conf.getProperty("db-connection.dom");
		genBufferDetails(dom.getIndexBufferStats(), dom.getDataBufferStats(), "dom.dbx");
		
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
	
	private void addValue(String elem, String value) throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		this.contentHandler.startElement(NAMESPACE, elem, PREFIX + ':' + elem, atts);
		this.contentHandler.characters(value.toCharArray(), 0, value.length());
		this.contentHandler.endElement(NAMESPACE, elem, PREFIX + ':' + elem);
	}
*/

	/**
	 * @param elem
	 * @param value
	 * @throws SAXException
	 */
	private void addValue(String elem, String value) throws SAXException {
		stats.addValue(elem, value);		
	}
}
