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
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Sequence;
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
	protected TreeMap namespaceDecls = new TreeMap();
	
	public LocalXPathQueryService(User user, BrokerPool pool, LocalCollection collection) {
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
			DBBroker broker = null;
			try {
				broker = brokerPool.get(user);
				docs = collection.collection.allDocs(broker, true);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
					"error while loading documents: " + e.getMessage(), e);
			} finally {
				brokerPool.release(broker);
			}
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
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
            StaticContext context = new StaticContext(broker);
            Map.Entry entry;
            for(Iterator i = namespaceDecls.entrySet().iterator(); i.hasNext(); ) {
                entry = (Map.Entry)i.next();
                LOG.debug("prefix " + entry.getKey() + " = " + entry.getValue());
                context.declareNamespace((String)entry.getKey(), (String)entry.getValue());
            }
			XPathLexer2 lexer = new XPathLexer2(new StringReader(query));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(context);
			parser.xpath();
			if(parser.foundErrors()) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
					parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());
			
			PathExpr expr = new PathExpr();
			treeParser.xpath(ast, expr);
			if(treeParser.foundErrors()) {
				throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
					treeParser.getErrorMessage());
			}
						
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			//if (parser.foundErrors())
			//	throw new XMLDBException(ErrorCodes.VENDOR_ERROR, parser.getErrorMsg());
			Sequence result = null;
			docs = (docs == null ? expr.preselect(context) : expr.preselect(docs, context));
			if (docs.getLength() == 0)
				result = Sequence.EMPTY_SEQUENCE;
			else 
				result = expr.eval(context, docs, contextSet, null);
			LOG.info(
				expr.pprint()
					+ " found: "
					+ result.getLength()
					+ " in "
					+ (System.currentTimeMillis() - start)
					+ "ms.");
			LocalResourceSet resultSet =
				new LocalResourceSet(
					user,
					brokerPool,
					collection,
					result,
					properties,
					sortExpr);
			return resultSet;
		} catch (antlr.RecognitionException re) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, re.getMessage(), re);
		} catch (antlr.TokenStreamException te) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, te.getMessage(), te);
		} catch (IllegalArgumentException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (XPathException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
	}
 
	public ResourceSet queryResource(String resource, String query) throws XMLDBException {
		DocumentSet docs = new DocumentSet();
		LocalXMLResource res = (LocalXMLResource)collection.getResource(resource);
		if(res == null)
			throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource " + resource + " not found");
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
		properties.put(property, value);
	}

}
