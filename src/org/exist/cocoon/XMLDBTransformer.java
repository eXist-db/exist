/*
 * XMLDBTransformer.java - Mar 7, 2003
 * 
 * @author wolf
 */
package org.exist.cocoon;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.avalon.excalibur.pool.Poolable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractSAXTransformer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 * @author wolf
 *
 */
public class XMLDBTransformer
	extends AbstractSAXTransformer
	implements Poolable {

	public static final String DEFAULT_DRIVER = "org.exist.xmldb.DatabaseImpl";
	public static final String DEFAULT_USER = "guest";

	public static final String NAMESPACE = "http://exist-db/transformer/1.0";
	public static final String COLLECTION_ELEMENT = "collection";
	public static final String EXEC_QUERY_ELEMENT = "execute-query";
	public static final String QUERY_ELEMENT = "query";
	public static final String RESULT_ELEMENT = "result-set";
	public static final String ERROR_ELEMENT = "error";
	public static final String ERRMSG_ELEMENT = "message";
	public static final String STACKTRACE_ELEMENT = "stacktrace";

	public static final String FATAL_ERROR = "fatal";
	public static final String WARNING = "warn";

	public static final int IN_COLLECTION = 1;
	public static final int IN_QUERY = 2;

	private String driver = null;
	private String user = null;
	private String password = null;
	private String xpath = null;
	private StringBuffer buffer = new StringBuffer();
	private Collection collection = null;
	private int mode = 0;
	private boolean inElement = false;

	/* (non-Javadoc)
	 * @see org.apache.cocoon.sitemap.SitemapModelComponent#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
	 */
	public void setup(
		SourceResolver resolver,
		Map map,
		String src,
		Parameters parameters)
		throws ProcessingException, SAXException, IOException {
		driver = parameters.getParameter("driver", DEFAULT_DRIVER);
		user = parameters.getParameter("user", DEFAULT_USER);
		password = parameters.getParameter("password", DEFAULT_USER);
		setupDatabase();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
		String uri,
		String localName,
		String qname,
		Attributes attribs)
		throws SAXException {
		if (NAMESPACE.equals(uri)) {
			if (COLLECTION_ELEMENT.equals(localName))
				startCollection(attribs);
			else if (EXEC_QUERY_ELEMENT.equals(localName))
				startQuery(attribs);
			else if (QUERY_ELEMENT.equals(localName))
				inElement = true;
		} else
			super.startElement(uri, localName, qname, attribs);
	}

	protected void startCollection(Attributes attribs) throws SAXException {
		String uri = attribs.getValue("uri");
		if (uri == null) {
			reportError(
				FATAL_ERROR,
				"element collection requires an uri-attribute");
			return;
		}
		try {
			collection = DatabaseManager.getCollection(uri, user, password);
			if (collection == null) {
				reportError(WARNING, "collection " + uri + " not found");
				return;
			}
		} catch (XMLDBException e) {
			reportError(WARNING, "failed to retrieve collection", e);
		}
		mode = IN_COLLECTION;
	}

	protected void startQuery(Attributes attribs) throws SAXException {
		if (mode != IN_COLLECTION)
			return;
		xpath = attribs.getValue("query");
		mode = IN_QUERY;
	}

	protected void setupDatabase() throws ProcessingException {
		try {
			Class clazz = Class.forName(driver);
			Database database = (Database) clazz.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
		} catch (Exception e) {
			throw new ProcessingException("failed to setup database", e);
		}
	}

	protected void reportError(String type, String message)
		throws SAXException {
		reportError(type, message, null);
	}

	protected void reportError(String type, String message, Exception cause)
		throws SAXException {
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "type", "type", "CDATA", type);
		super.startElement(NAMESPACE, ERROR_ELEMENT, ERROR_ELEMENT, attribs);
		super.startElement(
			NAMESPACE,
			ERRMSG_ELEMENT,
			ERRMSG_ELEMENT,
			new AttributesImpl());
		super.characters(message.toCharArray(), 0, message.length());
		super.endElement(NAMESPACE, ERRMSG_ELEMENT, ERRMSG_ELEMENT);
		if (cause != null) {
			PrintWriter writer = new PrintWriter(new StringWriter());
			cause.printStackTrace(writer);
			String trace = cause.toString();
			super.startElement(
				NAMESPACE,
				STACKTRACE_ELEMENT,
				STACKTRACE_ELEMENT,
				new AttributesImpl());
			super.characters(trace.toCharArray(), 0, trace.length());
			super.endElement(NAMESPACE, STACKTRACE_ELEMENT, STACKTRACE_ELEMENT);
		}
		super.endElement(NAMESPACE, ERROR_ELEMENT, ERROR_ELEMENT);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String uri, String loc, String raw)
		throws SAXException {
		if (NAMESPACE.equals(uri)) {
			if (COLLECTION_ELEMENT.equals(loc)) {
				collection = null;
				mode = 0;
			} else if (EXEC_QUERY_ELEMENT.equals(loc)) {
				try {
					XPathQueryService service =
						(XPathQueryService) collection.getService(
							"XPathQueryService",
							"1.0");
					service.setProperty("sax-document-events", "false");
					System.out.println("Query: " + xpath);
					ResourceSet result = service.query(xpath);
					if (result == null) {
						reportError(WARNING, "query returned null as result");
						return;
					}
					AttributesImpl at = new AttributesImpl();
					at.addAttribute(
						"",
						"count",
						"count",
						"CDATA",
						Long.toString(result.getSize()));
					at.addAttribute("", "xpath", "xpath", "CDATA", xpath);
					super.startElement(
						NAMESPACE,
						RESULT_ELEMENT,
						RESULT_ELEMENT,
						at);
					XMLResource resource;
					for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
						resource = (XMLResource)i.nextResource();
						resource.getContentAsSAX(this);
					}
					super.endElement(NAMESPACE, RESULT_ELEMENT, RESULT_ELEMENT);
					mode = IN_COLLECTION;
				} catch (XMLDBException e) {
					reportError(WARNING, "database error", e);
				}
			} else if (QUERY_ELEMENT.equals(loc)) {
				xpath = buffer.toString();
				inElement = false;
			}
			return;
		} else
			super.endElement(uri, loc, raw);
	}

	/* (non-Javadoc)
	 * @see org.apache.avalon.excalibur.pool.Recyclable#recycle()
	 */
	public void recycle() {
		collection = null;
		mode = 0;
		inElement = false;
		xpath = null;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] p0, int p1, int p2) throws SAXException {
		super.characters(p0, p1, p2);
	}

}
