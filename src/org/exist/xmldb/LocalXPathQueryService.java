package org.exist.xmldb;

import java.io.StringReader;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xpath.PathExpr;
import org.exist.xpath.Value;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class LocalXPathQueryService implements XPathQueryServiceImpl {

	private static Category LOG = Category.getInstance(LocalXPathQueryService.class.getName());
	protected BrokerPool brokerPool;
	protected LocalCollection collection;
	protected String encoding = "UTF-8";

	protected boolean indentXML = false;
	protected boolean saxDocumentEvents = true;
	protected boolean createContainerElements = true;
	protected boolean matchTagging = true;
	protected User user;

	public LocalXPathQueryService(User user, BrokerPool pool, LocalCollection collection) {
		this.user = user;
		this.collection = collection;
		this.brokerPool = pool;
	}

	public void clearNamespaces() throws XMLDBException {
	}

	public String getName() throws XMLDBException {
		return "XPathQueryService";
	}

	public String getNamespace(String prefix) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	public String getProperty(String property) throws XMLDBException {
		if (property.equals("pretty"))
			return indentXML ? "true" : "false";
		if (property.equals("encoding"))
			return encoding;
		if (property.equals("create-container-elements"))
			return createContainerElements ? "true" : "false";
		if (property.equals("match-tagging"))
			return matchTagging ? "true" : "false";
		return null;
	}

	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	public ResourceSet query(String query) throws XMLDBException {
		return query(query, null);
	}
	
	public ResourceSet query(XMLResource res, String query) 
	throws XMLDBException {
		return query(res, query, null);
	}
	
	public ResourceSet query(String query, String sortBy) throws XMLDBException {
		if (!(query.startsWith("document(") || query.startsWith("collection(") ||
			query.startsWith("xcollection("))) {
			if (collection.getName().equals("/db") || collection.getName().equals("/"))
				query = "document(*)" + query;
			else
				query = "collection('" + collection.getPath() + "')" + query;
		}
		return doQuery(query, null, null, sortBy);
	}

	public ResourceSet query(XMLResource res, String query, String sortBy) throws XMLDBException {
		NodeProxy node = ((LocalXMLResource) res).getNode();
		if (node == null) {
			// resource is a document
			if (!(query.startsWith("document(") || query.startsWith("collection(")))
				query = "document('" + res.getDocumentId() + "')" + query;
		}
		NodeSet set = new ArraySet(1);
		set.add(node);
		DocumentSet docs = new DocumentSet();
		docs.add(node.getDoc());
		return doQuery(query, docs, set, sortBy);
	}

	protected ResourceSet doQuery(String query, DocumentSet docs, 
		NodeSet context, String sortExpr)
		throws XMLDBException {
		try {
			XPathLexer lexer = new XPathLexer(new StringReader(query));
			XPathParser parser = new XPathParser(brokerPool, user, lexer);
			PathExpr expr = new PathExpr(brokerPool);
			parser.expr(expr);
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			if (parser.foundErrors())
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, parser.getErrorMsg());
			docs = (docs == null ? expr.preselect() : expr.preselect(docs));
			if (docs.getLength() == 0)
				return null;
			Value resultValue = expr.eval(docs, context, null);
			LOG.info(
				expr.pprint()
					+ " found: "
					+ resultValue.getLength()
					+ " in "
					+ (System.currentTimeMillis() - start)
					+ "ms.");
			LocalResourceSet result =
				new LocalResourceSet(
					user,
					brokerPool,
					collection,
					resultValue,
					indentXML,
					encoding,
					saxDocumentEvents,
					createContainerElements,
					matchTagging,
					sortExpr);
			return result;
		} catch (antlr.RecognitionException re) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, re.getMessage(), re);
		} catch (antlr.TokenStreamException te) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, te.getMessage(), te);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
	}
 
	/**
	 *  Description of the Method
	 *
	 *@param  resource            Description of the Parameter
	 *@param  query               Description of the Parameter
	 *@return                     Description of the Return Value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public ResourceSet queryResource(String resource, String query) throws XMLDBException {
		query = "document('" + collection.getPath() + '/' + resource + "')" + query;
		return query(query);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  ns                  Description of the Parameter
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void removeNamespace(String ns) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	/**
	 *  Sets the collection attribute of the LocalXPathQueryService object
	 *
	 *@param  col                 The new collection value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setCollection(Collection col) throws XMLDBException {
	}

	/**
	 *  Sets the namespace attribute of the LocalXPathQueryService object
	 *
	 *@param  prefix              The new namespace value
	 *@param  namespace           The new namespace value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setNamespace(String prefix, String namespace) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	/**
	 *  Sets the property attribute of the LocalXPathQueryService object
	 *
	 *@param  property            The new property value
	 *@param  value               The new property value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setProperty(String property, String value) throws XMLDBException {
		if (property.equals("pretty"))
			indentXML = value.equals("true");

		if (property.equals("encoding")) {
			encoding = value;
			LOG.debug("encoding = " + encoding);
		}
		if (property.equals("sax-document-events"))
			saxDocumentEvents = value.equals("true");
		if (property.equals("create-container-elements"))
			createContainerElements = value.equals("true");
		if (property.equals("match-tagging"))
			matchTagging = value.equals("true");
	}

}
