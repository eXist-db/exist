/*
 * XMLDBTransformer.java - Mar 7, 2003
 * 
 * @author wolf
 */
package org.exist.cocoon;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.avalon.excalibur.pool.Poolable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractSAXTransformer;
import org.apache.cocoon.xml.dom.DOMStreamer;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.w3c.dom.DocumentFragment;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * Transformer component for querying an XML database using the
 * XMLDB API.
 * 
 * This component provides a limited set of tags to query collections
 * in the database.
 * 
 * @author wolf
 *
 */
public class XMLDBTransformer extends AbstractSAXTransformer implements Poolable {

	public String DEFAULT_DRIVER = "org.exist.xmldb.DatabaseImpl";
	public String DEFAULT_USER = "guest";
	public String DEFAULT_PASSWORD = "guest";

	public static final String NAMESPACE = "http://exist-db.org/transformer/1.0";
	public static final String COLLECTION_ELEMENT = "collection";
	public static final String FOR_EACH_ELEMENT = "for-each";
	public static final String CURRENT_NODE_ELEMENT = "current-node";
	public static final String SELECT_NODE = "select-node";
	public static final String RESULT_SET_ELEMENT = "result-set";
  public static final String XUPDATE_ELEMENT = "update";

	public static final String ERROR_ELEMENT = "error";
	public static final String ERRMSG_ELEMENT = "message";
	public static final String STACKTRACE_ELEMENT = "stacktrace";

	public static final String PREFIX = "xdb:";

	public static final String FATAL_ERROR = "fatal";
	public static final String WARNING = "warn";
	public static final String INFO= "info";

	public static final int IN_COLLECTION = 1;
	public static final int IN_QUERY = 2;

	private String driver = null;
	private String user = null;
	private String password = null;
	private String xpath = null;
	private Collection collection = null;
	private Stack commandStack = new Stack();
	private boolean isRecording = false;
	private int nesting = 0;
	private int mode = 0;
	private XMLResource currentResource = null;
	private HashMap namespaces = new HashMap(20);
	private String prefix = null;


  private StringWriter queryWriter;
  private TransformerHandler queryHandler;

   /** The trax <code>TransformerFactory</code> used by this transformer. */
    private SAXTransformerFactory tfactory = null;

	
	/**
	 * Setup the component. Accepts parameters "driver", "user" and
	 * "password". If specified, those parameters override the default-
	 * settings or the settings specified during component setup.
	 * 
	 * Example:
	 * 
	 * &lt;map:transform type="xmldb"&gt;
	 *     &lt;map:parameter name="driver" value="org.exist.xmldb.DatabaseImpl"/&gt;
	 *     &lt;map:parameter name="user" value="guest"/&gt;
	 *     &lt;map:parameter name="password" value="guest"/&gt;
	 * &lt;/map:transform&gt;
	 * 
	 * @see org.apache.cocoon.sitemap.SitemapModelComponent#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
	 */
	public void setup(SourceResolver resolver, Map map, String src, Parameters parameters)
		throws ProcessingException, SAXException, IOException {
		super.setup(resolver, map, src, parameters);
		driver = parameters.getParameter("driver", DEFAULT_DRIVER);
		user = parameters.getParameter("user", DEFAULT_USER);
		password = parameters.getParameter("password", DEFAULT_PASSWORD);
		if (request == null) {
			throw new ProcessingException("no request object found");
		}
		setupDatabase();
	}

	public void startElement(String uri, String localName, String qname, Attributes attribs)
		throws SAXException {
    if(queryHandler!=null) {
      this.queryHandler.startElement(uri, localName, qname, attribs);
    }
    else
    {
    
		if (isRecording) {
			if (NAMESPACE.equals(uri) && FOR_EACH_ELEMENT.equals(localName))
				++nesting;
			super.startElement(uri, localName, qname, attribs);
		} else if (NAMESPACE.equals(uri)) {
			if (COLLECTION_ELEMENT.equals(localName)) {
				prefix = ( qname.endsWith(localName) ? qname.substring(0, qname.length() - localName.length())
					: PREFIX );
				startCollection(attribs);
			} else if (FOR_EACH_ELEMENT.equals(localName))
				startForEach(attribs);
			else if (CURRENT_NODE_ELEMENT.equals(localName))
				startCurrent(attribs);
			else if (SELECT_NODE.equals(localName))
				startSelectNode(attribs);
      else if(XUPDATE_ELEMENT.equals(localName))
        startXUpdate(attribs);
		} else {
			if (currentResource != null) {
				try {
					AttributesImpl a = new AttributesImpl(attribs);
					a.addAttribute(
						NAMESPACE,
						"document-id",
						prefix + "document-id",
						"CDATA",
						currentResource.getDocumentId());
					a.addAttribute(
						NAMESPACE,
						"collection",
						prefix + "collection",
						"CDATA",
						currentResource.getParentCollection().getName());
					super.startElement(uri, localName, qname, a);
					currentResource = null;
				} catch (XMLDBException e) {
				}
			} else
				super.startElement(uri, localName, qname, attribs);
		}
    }
	}

	protected void startCollection(Attributes attribs) throws SAXException {
		String uri = attribs.getValue("uri");
		if (uri == null) {
			reportError(FATAL_ERROR, "element collection requires an uri-attribute");
			return;
		}
		String pUser = attribs.getValue("user");
		String pPassword = attribs.getValue("password");
		// use default user and password if not specified
		if (pUser == null)
			pUser = user;
		if (pPassword == null)
			pPassword = password;
		try {
			collection = DatabaseManager.getCollection(uri, pUser, pPassword);
			if (collection == null) {
				reportError(WARNING, "collection " + uri + " not found");
				return;
			}
		} catch (XMLDBException e) {
			reportError(WARNING, "failed to retrieve collection", e);
		}
		mode = IN_COLLECTION;
	}

	protected void startCurrent(Attributes attribs) throws SAXException {
		if (commandStack.isEmpty())
			return;
		ForEach each = (ForEach) commandStack.peek();
		try {
			if (each.currentResource != null)
				each.currentResource.getContentAsSAX(this);
		} catch (XMLDBException e) {
		}
	}

  /**
   * Helper for TransformerFactory.
   */
    protected SAXTransformerFactory getTransformerFactory() {
        if (tfactory == null)  {
            tfactory = (SAXTransformerFactory) TransformerFactory.newInstance();
            //tfactory.setErrorListener(new TraxErrorHandler(getLogger()));
        }
        return tfactory;
    }


  protected void startXUpdate(Attributes attribs) throws SAXException {
    if (collection == null) {
      reportError(FATAL_ERROR, "no collection selected");
      return;
    }
    queryWriter = new StringWriter(256);
    try {
      this.queryHandler = getTransformerFactory().newTransformerHandler();
      this.queryHandler.setResult(new StreamResult(queryWriter));
      //this.queryHandler.getTransformer().setOutputProperties(format);
    } 
    catch (TransformerConfigurationException e) {
      throw new SAXException("Failed to get transformer handler", e);
    }
    // Start query document
    this.queryHandler.startDocument();

    Iterator i = namespaces.entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry entry = (Map.Entry)i.next();
      this.queryHandler.startPrefixMapping((String)entry.getKey(), (String)entry.getValue());
    }

  }

  protected void endXUpdate() throws SAXException 
  {
    try { 
      XUpdateQueryService service = (XUpdateQueryService) collection.getService("XUpdateQueryService", "1.0");
      long count = service.update(queryWriter.toString());
    }
    catch(XMLDBException e) {
      reportError(FATAL_ERROR, "Unable to perform update: "+e.getMessage() , e);
    }
  }

	protected void startSelectNode(Attributes attribs) throws SAXException {
		if (collection == null) {
			reportError(FATAL_ERROR, "no collection selected");
			return;
		}
		XMLResource resource = null;
		if (!commandStack.isEmpty()) {
			ForEach last = (ForEach) commandStack.peek();
			resource = last.currentResource;
		}
		xpath = attribs.getValue("query");
		if (xpath == null) {
			reportError(FATAL_ERROR, "attribute 'query' is missing");
			return;
		}
		String pHighlightElementMatches = attribs.getValue("match-tagging-elements");
		boolean highlightElementMatches = true;
		if (pHighlightElementMatches != null)
			highlightElementMatches = pHighlightElementMatches.equals("true");
		String pHighlightAttributeMatches = attribs.getValue("match-tagging-attributes");
		boolean highlightAttributeMatches = false;
		if (pHighlightAttributeMatches != null)
			highlightAttributeMatches = pHighlightAttributeMatches.equals("true");
		final long start = System.currentTimeMillis();
		try {
			XPathQueryServiceImpl service =
				(XPathQueryServiceImpl) collection.getService("XPathQueryService", "1.0");
			service.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			String highlighting = "none";
			if (highlightElementMatches && highlightAttributeMatches)
				highlighting = "both";
			else if (highlightElementMatches)
				highlighting = "elements";
			else if (highlightAttributeMatches)
				highlighting = "attributes";
			service.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlighting);
			setQueryContext(service);
			ResourceSet queryResult =
				(resource == null) ? service.query(xpath) : service.query(resource, xpath);
			if (queryResult == null) {
				reportError(WARNING, "query returned null");
				return;
			}
			long len = queryResult.getSize();
			for (long i = 0; i < len; i++) {
				XMLResource res = (XMLResource) queryResult.getResource(i);
				res.getContentAsSAX(this);
			}
		} catch (XMLDBException e) {
			reportError(WARNING, "error during query-execution", e);
		}
	}

	protected void startForEach(Attributes attribs) throws SAXException {
		if (collection == null) {
			reportError(FATAL_ERROR, "no collection selected");
			return;
		}
		ForEach each = new ForEach();
		XMLResource resource = null;
		boolean nested = !commandStack.isEmpty();
		if (nested) {
			ForEach last = (ForEach) commandStack.peek();
			resource = last.currentResource;
		}
		commandStack.push(each);

		// process attributes
		xpath = attribs.getValue("query");
		if (xpath == null) {
			reportError(FATAL_ERROR, "attribute 'query' is missing");
			return;
		}
		each.query = xpath;
		String sortExpr = attribs.getValue("sort-by");
		String pFrom = attribs.getValue("from");
		if (pFrom != null)
			try {
				each.from = Integer.parseInt(pFrom);
			} catch (NumberFormatException e) {
				reportError(WARNING, "attribute 'from' requires numeric value");
			}
		String pTo = attribs.getValue("to");
		if (pTo != null)
			try {
				each.to = Integer.parseInt(pTo);
			} catch (NumberFormatException e) {
				reportError(WARNING, "attribute 'to' requires numeric value");
			}
		String pHighlightElementMatches = attribs.getValue("match-tagging-elements");
		boolean highlightElementMatches = true;
		if (pHighlightElementMatches != null)
			highlightElementMatches = pHighlightElementMatches.equals("true");
		String pHighlightAttributeMatches = attribs.getValue("match-tagging-attributes");
		boolean highlightAttributeMatches = false;
		if (pHighlightAttributeMatches != null)
			highlightAttributeMatches = pHighlightAttributeMatches.equals("true");
		String pSession = attribs.getValue("use-session");
		boolean createSession = false;
		if (pSession != null)
			createSession = pSession.equals("true");
		Session session = null;
		if (createSession)
			session = request.getSession(true);
		final long start = System.currentTimeMillis();
		try {
			ResourceSet queryResult = null;
			XPathQueryServiceImpl service =
				(XPathQueryServiceImpl) collection.getService("XPathQueryService", "1.0");
			service.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			String highlighting = "none";
			if (highlightElementMatches && highlightAttributeMatches)
				highlighting = "both";
			else if (highlightElementMatches)
				highlighting = "elements";
			else if (highlightAttributeMatches)
				highlighting = "attributes";
			service.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlighting);
			setQueryContext(service);
			// check if query result is already stored in the session
			if (createSession && resource == null)
				queryResult = (ResourceSet) session.getAttribute(xpath);
			if (queryResult == null) {
				queryResult =
					(resource == null)
						? service.query(xpath, sortExpr)
						: service.query(resource, xpath, sortExpr);
				if (createSession)
					session.setAttribute(xpath, queryResult);
			}
			if (queryResult == null) {
				reportError(WARNING, "query returned null");
				return;
			}
			each.queryResult = queryResult;
			int size = (int) each.queryResult.getSize();
			if (each.from < 0)
				each.from = 0;
			if (each.to < 0 || each.to >= size)
				each.to = size - 1;
			if (!nested) {
				AttributesImpl atts = new AttributesImpl();
				atts.addAttribute(
					"",
					"count",
					"count",
					"CDATA",
					queryResult == null ? "0" : Long.toString(queryResult.getSize()));
				atts.addAttribute("", "xpath", "xpath", "CDATA", xpath);
				atts.addAttribute(
					"",
					"query-time",
					"query-time",
					"CDATA",
					Long.toString((System.currentTimeMillis() - start)));
				atts.addAttribute("", "from", "from", "CDATA", Integer.toString(each.from));
				atts.addAttribute("", "to", "to", "CDATA", Integer.toString(each.to));
				super.startElement(
					NAMESPACE,
					RESULT_SET_ELEMENT,
					prefix + RESULT_SET_ELEMENT,
					atts);
			}
		} catch (XMLDBException e) {
			reportError(FATAL_ERROR, e.getMessage(), e);
			commandStack.pop();
			return;
		}
		nesting++;
		isRecording = true;
		startRecording();
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

	protected void reportError(String type, String message) throws SAXException {
		reportError(type, message, null);
	}

	protected void reportError(String type, String message, Exception cause) throws SAXException {
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute("", "type", "type", "CDATA", type);
		super.startPrefixMapping(PREFIX, NAMESPACE);
		super.startElement(NAMESPACE, ERROR_ELEMENT, PREFIX + ERROR_ELEMENT, attribs);
		super.startElement(
			NAMESPACE,
			ERRMSG_ELEMENT,
			PREFIX + ERRMSG_ELEMENT,
			new AttributesImpl());
		super.characters(message.toCharArray(), 0, message.length());
		super.endElement(NAMESPACE, ERRMSG_ELEMENT, PREFIX + ERRMSG_ELEMENT);
		if (cause != null) {
			PrintWriter writer = new PrintWriter(new StringWriter());
			cause.printStackTrace(writer);
			String trace = cause.toString();
			super.startElement(
				NAMESPACE,
				STACKTRACE_ELEMENT,
				PREFIX + STACKTRACE_ELEMENT,
				new AttributesImpl());
			super.characters(trace.toCharArray(), 0, trace.length());
			super.endElement(NAMESPACE, STACKTRACE_ELEMENT, PREFIX + STACKTRACE_ELEMENT);
		}
		super.endElement(NAMESPACE, ERROR_ELEMENT, PREFIX + ERROR_ELEMENT);
		super.endPrefixMapping(PREFIX);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String uri, String loc, String raw) throws SAXException {
    if (this.queryHandler != null  && !(NAMESPACE.equals(uri) && XUPDATE_ELEMENT.equals(loc)) )  {
      this.queryHandler.endElement(uri, loc, raw);
    } else if(NAMESPACE.equals(uri) && XUPDATE_ELEMENT.equals(loc)) {
        Iterator i = namespaces.entrySet().iterator();
        while (i.hasNext()) {
          Map.Entry entry = (Map.Entry) i.next();
          this.queryHandler.endPrefixMapping((String)entry.getKey());
        }
        endXUpdate();
        this.queryHandler = null;
    }
    else
    {
		if (isRecording) {
			if (NAMESPACE.equals(uri) && FOR_EACH_ELEMENT.equals(loc) && --nesting == 0)
				endForEach();
			else
				super.endElement(uri, loc, raw);
		} else if (NAMESPACE.equals(uri)) {
			if (COLLECTION_ELEMENT.equals(loc)) {
				collection = null;
				mode = 0;
			} else if (FOR_EACH_ELEMENT.equals(loc)) {
				endForEach();
      }
			return;
		} else
			super.endElement(uri, loc, raw);
    }
	}

	protected void endForEach() throws SAXException {
		isRecording = false;
		if (commandStack.isEmpty())
			return;
		ForEach each = (ForEach) commandStack.peek();
		DocumentFragment fragment = endRecording();
		if (each.queryResult == null)
			return;
		DOMStreamer streamer = new DOMStreamer(this);
		for (each.current = each.from; each.current <= each.to; ++each.current) {
			try {
				each.currentResource = (XMLResource) each.queryResult.getResource(each.current);
				currentResource = each.currentResource;
				streamer.stream(fragment);
			} catch (XMLDBException e) {
				reportError(WARNING, "error while retrieving resource " + each.current, e);
			}
		}
		commandStack.pop();
		if (commandStack.isEmpty())
			super.endElement(NAMESPACE, RESULT_SET_ELEMENT, prefix + RESULT_SET_ELEMENT);
	}

	/* (non-Javadoc)
	 * @see org.apache.avalon.excalibur.pool.Recyclable#recycle()
	 */
	public void recycle() {
		collection = null;
		mode = 0;
		xpath = null;
		commandStack.clear();
		nesting = 0;
	}

	public void characters(char[] p0, int p1, int p2) throws SAXException {
    if(queryHandler!=null) {
      this.queryHandler.characters(p0,p1,p2);
    } else {
		  super.characters(p0, p1, p2);
    }
	}

	protected class ForEach {
		ResourceSet queryResult = null;
		String query = null;
		int from = -1;
		int to = -1;
		XMLResource currentResource = null;
		long current = 0;

		public ForEach() {
		}
	}

	/**
	 * Try to read configuration parameters from the component setup.
	 * 
	 * Example:
	 * 
	 * &lt;map:transformer name="xmldb" src="org.exist.cocoon.XMLDBTransformer"&gt;
	 * 	   &lt;driver&gt;org.exist.xmldb.DatabaseImpl&lt;/driver&gt;
	 *     &lt;user&gt;guest&lt;/user&gt;
	 *     &lt;password&gt;guest&lt;/password&gt;
	 * &lt;/map:transformer&gt;
	 * 
	 * will set the default driver, user and password. Note that these
	 * values may also be set as parameters in the pipeline.
	 * 
	 * @see org.apache.avalon.framework.configuration.Configurable#configure(org.apache.avalon.framework.configuration.Configuration)
	 */
	public void configure(Configuration configuration) throws ConfigurationException {
		super.configure(configuration);
		Configuration child = configuration.getChild("user", false);
		if (child != null)
			DEFAULT_USER = child.getValue();
		child = configuration.getChild("password", false);
		if (child != null)
			DEFAULT_PASSWORD = child.getValue();
		child = configuration.getChild("driver", false);
		if (child != null)
			DEFAULT_DRIVER = child.getValue();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
		super.endPrefixMapping(prefix);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String namespaceURI)
		throws SAXException {
		namespaces.put(prefix, namespaceURI);
		super.startPrefixMapping(prefix, namespaceURI);
	}

	private void setQueryContext(XPathQueryService service) {
		Map.Entry entry;
		for(Iterator i = namespaces.entrySet().iterator(); i.hasNext(); ) {
			entry = (Map.Entry)i.next();
            if(entry.getKey() == null || entry.getValue() == null)
                continue;
			try {
				service.setNamespace((String)entry.getKey(), (String)entry.getValue());
			} catch (XMLDBException e) {
			}
		}
	}
}
