package org.exist.xmldb;

import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

public class LocalXPathQueryService implements XPathQueryServiceImpl, XQueryService {

	private final static Logger LOG = Logger.getLogger(LocalXPathQueryService.class);

	protected BrokerPool brokerPool;
	protected LocalCollection collection;
	protected User user;
	protected TreeMap namespaceDecls = new TreeMap();
	protected TreeMap variableDecls = new TreeMap();
	protected boolean xpathCompatible = true;
	protected String moduleLoadPath = null;
	
	public LocalXPathQueryService(
		User user,
		BrokerPool pool,
		LocalCollection collection) {
		this.user = user;
		this.collection = collection;
		this.brokerPool = pool;
	}

	public void clearNamespaces() throws XMLDBException {
		namespaceDecls.clear();
	}

	public String getName() throws XMLDBException {
		return "XPathQueryService";
	}

	public String getNamespace(String prefix) throws XMLDBException {
		return (String)namespaceDecls.get(prefix);
	}
	
	public String getProperty(String property) throws XMLDBException {
		return collection.properties.getProperty(property);
	}

	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	public ResourceSet query(String query) throws XMLDBException {
		return query(query, null);
	}

	public ResourceSet query(XMLResource res, String query) throws XMLDBException {
		return query(res, query, null);
	}

	public ResourceSet query(String query, String sortBy) throws XMLDBException {
		DocumentSet docs = null;
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			docs = collection.collection.allDocs(broker, new DocumentSet(), true);
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"error while loading documents: " + e.getMessage(),
				e);
		} finally {
			brokerPool.release(broker);
		}
		return doQuery(query, docs, null, sortBy);
	}

	public ResourceSet query(XMLResource res, String query, String sortBy)
		throws XMLDBException {
		NodeProxy node = ((LocalXMLResource) res).getNode();
		if (node == null) {
			// resource is a document
			if (!(query.startsWith("document(") || query.startsWith("collection(")))
				query = "document('" + res.getDocumentId() + "')" + query;
			return doQuery(query, null, null, sortBy);
		} else {
			NodeSet set = new ArraySet(1);
			set.add(node);
			DocumentSet docs = new DocumentSet();
			docs.add(node.getDoc());
			return doQuery(query, docs, set, sortBy);
		}
	}

	public ResourceSet execute(CompiledExpression expression) throws XMLDBException {
		DBBroker broker = null;
		DocumentSet docs = null;
		try {
			broker = brokerPool.get(user);
			docs = collection.collection.allDocs(broker, new DocumentSet(), true);
		} catch (EXistException e) {
			throw new XMLDBException(
				ErrorCodes.UNKNOWN_ERROR,
				"error while loading documents: " + e.getMessage(),
				e);
		} finally {
			brokerPool.release(broker);
		}
		expression.reset();
		XQueryContext context = ((PathExpr)expression).getContext();
		context.setBackwardsCompatibility(xpathCompatible);
		context.setStaticallyKnownDocuments(docs);
		
		Map.Entry entry;
		// declare namespace/prefix mappings
		for (Iterator i = namespaceDecls.entrySet().iterator(); i.hasNext();) {
			entry = (Map.Entry) i.next();
			context.declareNamespace((String) entry.getKey(), (String) entry.getValue());
		}
		// declare static variables
		for (Iterator i = variableDecls.entrySet().iterator(); i.hasNext();) {
			entry = (Map.Entry) i.next();
			try {
				context.declareVariable((String) entry.getKey(), entry.getValue());
			} catch (XPathException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			}
		}
		return doQuery(expression, docs, null, null);
	}

	public CompiledExpression compile(String query) throws XMLDBException {
		//LOG.debug("compiling " + query);
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			XQueryContext context = new XQueryContext(broker);
			context.setBaseURI(collection.properties.getProperty("base-uri", collection.getPath()));
			if(moduleLoadPath != null)
				context.setModuleLoadPath(moduleLoadPath);
			Map.Entry entry;
			// declare namespace/prefix mappings
			for (Iterator i = namespaceDecls.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				LOG.debug("prefix " + entry.getKey() + " = " + entry.getValue());
				context.declareNamespace(
					(String) entry.getKey(),
					(String) entry.getValue());
			}
			// declare static variables
			for (Iterator i = variableDecls.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				context.declareVariable((String) entry.getKey(), entry.getValue());
			}
			context.setBackwardsCompatibility(xpathCompatible);
			XQueryLexer lexer = new XQueryLexer(new StringReader(query));
			XQueryParser parser = new XQueryParser(lexer, false);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringList());

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new XMLDBException(
					ErrorCodes.UNKNOWN_ERROR,
					treeParser.getErrorMessage(),
					treeParser.getLastException());
			}
			LOG.debug("compiled: " + expr.pprint());
			return expr;
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (RecognitionException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (TokenStreamException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (XPathException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
	}

	protected ResourceSet doQuery(
		String query,
		DocumentSet docs,
		NodeSet contextSet,
		String sortExpr)
		throws XMLDBException {
		CompiledExpression expr = compile(query);
		return doQuery(expr, docs, contextSet, sortExpr);
	}

	protected ResourceSet doQuery(
		CompiledExpression compiled,
		DocumentSet docs,
		NodeSet contextSet,
		String sortExpr)
		throws XMLDBException {
		long start = System.currentTimeMillis();
		PathExpr expr = ((PathExpr) compiled);
		expr.getContext().setStaticallyKnownDocuments(docs);
		try {
			Sequence result = expr.eval(contextSet, null);
			LOG.info(
				expr.pprint()
					+ " found: "
					+ result.getLength()
					+ " in "
					+ (System.currentTimeMillis() - start)
					+ "ms.");
			LocalResourceSet resultSet =
				new LocalResourceSet(user, brokerPool, collection, result, sortExpr);
			expr.reset();
			return resultSet;
		} catch (XPathException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
	}

	public ResourceSet queryResource(String resource, String query)
		throws XMLDBException {
		DocumentSet docs = new DocumentSet();
		LocalXMLResource res = (LocalXMLResource) collection.getResource(resource);
		if (res == null)
			throw new XMLDBException(
				ErrorCodes.INVALID_RESOURCE,
				"resource " + resource + " not found");
		docs.add(res.getDocument());
		return doQuery(query, docs, null, null);
	}

	public void removeNamespace(String ns) throws XMLDBException {
		for (Iterator i = namespaceDecls.values().iterator(); i.hasNext();) {
			if (((String) i.next()).equals(ns)) {
				i.remove();
			}
		}
	}

	public void setCollection(Collection col) throws XMLDBException {
	}

	public void setNamespace(String prefix, String namespace) throws XMLDBException {
		namespaceDecls.put(prefix, namespace);
	}

	public void setProperty(String property, String value) throws XMLDBException {
		collection.properties.setProperty(property, value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XPathQueryServiceImpl#declareVariable(java.lang.String, java.lang.Object)
	 */
	public void declareVariable(String qname, Object initialValue)
		throws XMLDBException {
		variableDecls.put(qname, initialValue);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setXPathCompatibility(boolean)
	 */
	public void setXPathCompatibility(boolean backwardsCompatible) {
		this.xpathCompatible = backwardsCompatible;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setModuleLoadPath(java.lang.String)
	 */
	public void setModuleLoadPath(String path) {
		moduleLoadPath = path;		
	}

}
