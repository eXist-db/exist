package org.exist.xmldb;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.Value;
import org.exist.xpath.ValueNodeSet;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import antlr.collections.AST;

public class LocalXPathQueryService implements XPathQueryServiceImpl {

	private final static Logger LOG = 
		Logger.getLogger(LocalXPathQueryService.class);
	
	protected Map properties = new TreeMap();
	protected BrokerPool brokerPool;
	protected LocalCollection collection;
	protected User user;
	protected StaticContext context = null;
	
	public LocalXPathQueryService(User user, BrokerPool pool, LocalCollection collection) {
		this.user = user;
		this.collection = collection;
		this.brokerPool = pool;
		context = new StaticContext(user);
	}

	public void clearNamespaces() throws XMLDBException {
		context.clearNamespaces();
	}

	public String getName() throws XMLDBException {
		return "XPathQueryService";
	}

	public String getNamespace(String prefix) throws XMLDBException {
		throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
	}

	public String getProperty(String property) throws XMLDBException {
		return (String)properties.get(property);
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
		DocumentSet docs = null;
		if (!(query.startsWith("document(") || query.startsWith("collection(") ||
			query.startsWith("xcollection("))) {
			docs = collection.collection.allDocs(user, true);
		}
		return doQuery(query, docs, null, sortBy);
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
		NodeSet contextSet, String sortExpr)
		throws XMLDBException {
		try {
			XPathLexer2 lexer = new XPathLexer2(new StringReader(query));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(brokerPool, context);
			
			parser.xpath();
			if(parser.foundErrors()) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
					parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());
			
			PathExpr expr = new PathExpr(brokerPool);
			treeParser.xpath(ast, expr);
			if(treeParser.foundErrors()) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
					treeParser.getErrorMessage());
			}
						
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			//if (parser.foundErrors())
			//	throw new XMLDBException(ErrorCodes.VENDOR_ERROR, parser.getErrorMsg());
			Value resultValue = null;
			docs = (docs == null ? expr.preselect() : expr.preselect(docs));
			if (docs.getLength() == 0)
				resultValue = new ValueNodeSet(NodeSet.EMPTY_SET);
			else 
				resultValue = expr.eval(context, docs, contextSet, null);
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
					properties,
					sortExpr);
			return result;
		} catch (antlr.RecognitionException re) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, re.getMessage(), re);
		} catch (antlr.TokenStreamException te) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, te.getMessage(), te);
		} catch (IllegalArgumentException e) {
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
		context.removeNamespace(ns);
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
		context.declareNamespace(prefix, namespace);
	}

	/**
	 *  Sets the property attribute of the LocalXPathQueryService object
	 *
	 *@param  property            The new property value
	 *@param  value               The new property value
	 *@exception  XMLDBException  Description of the Exception
	 */
	public void setProperty(String property, String value) throws XMLDBException {
		properties.put(property, value);
	}

}
