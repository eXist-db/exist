package org.exist.soap;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.soap.Session.QueryResult;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import antlr.collections.AST;

/**
 *  Provides the actual implementations for the methods defined in
 * {@link org.exist.soap.Query}.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class QuerySoapBindingImpl implements org.exist.soap.Query {

	private static Category LOG = Category.getInstance("QueryService");

	private BrokerPool pool;

	public QuerySoapBindingImpl() {
		try {
			if (!BrokerPool.isConfigured())
				configure();
			pool = BrokerPool.getInstance();
		} catch (Exception e) {
			throw new RuntimeException("failed to initialize broker pool");
		}
	}

	private QueryResponseCollection[] collectQueryInfo(TreeMap collections) {
		QueryResponseCollection c[] = new QueryResponseCollection[collections.size()];
		QueryResponseDocument doc;
		QueryResponseDocument docs[];
		String docId;
		int k = 0;
		int l;
		TreeMap documents;
		for (Iterator i = collections.entrySet().iterator(); i.hasNext(); k++) {
			Map.Entry entry = (Map.Entry) i.next();
			c[k] = new QueryResponseCollection();
			c[k].setCollectionName((String) entry.getKey());
			documents = (TreeMap) entry.getValue();
			docs = new QueryResponseDocument[documents.size()];
			c[k].setDocuments(docs);
			l = 0;
			for (Iterator j = documents.entrySet().iterator(); j.hasNext(); l++) {
				Map.Entry docEntry = (Map.Entry) j.next();
				doc = new QueryResponseDocument();
				docId = (String) docEntry.getKey();
				if (docId.indexOf('/') > -1)
					docId = docId.substring(docId.lastIndexOf('/') + 1);
				doc.setDocumentName(docId);
				doc.setHitCount(((Integer) docEntry.getValue()).intValue());
				docs[l] = doc;
			}
		}
		return c;
	}

	private void configure() throws Exception {
		String pathSep = System.getProperty("file.separator", "/");
		String home = System.getProperty("exist.home");
		if (home == null)
			home = System.getProperty("user.dir");
		Configuration config = new Configuration(home + pathSep + "conf.xml");
		BrokerPool.configure(1, 5, config);
	}

	private Session getSession(String id) throws java.rmi.RemoteException {
		Session session = SessionManager.getInstance().getSession(id);
		if (session == null)
			throw new java.rmi.RemoteException("Session is invalid or timed out");
		return session;
	}

	public String connect(String user, String password) throws java.rmi.RemoteException {
		User u = pool.getSecurityManager().getUser(user);
		if (u == null)
			throw new RemoteException("user " + user + " does not exist");
		if (!u.validate(password))
			throw new RemoteException("the supplied password is invalid");
		LOG.debug("user " + user + " connected");
		return SessionManager.getInstance().createSession(u);
	}

	public void disconnect(String id) throws RemoteException {
		SessionManager manager = SessionManager.getInstance();
		Session session = manager.getSession(id);
		if (session != null) {
			LOG.debug("disconnecting session " + id);
			manager.disconnect(id);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.soap.Query#getResourceData(java.lang.String, byte[], boolean, boolean)
	 */
	public byte[] getResourceData(String sessionId, String path,
			boolean indent, boolean xinclude, boolean processXSLPI) throws RemoteException {
		Properties outputProperties = new Properties();
		outputProperties.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
		outputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
		outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI,
				processXSLPI ? "yes" : "no");
		String xml = getResource(sessionId, path, outputProperties);
		try {
			return xml.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			return xml.getBytes();
		}
	}
	
	public String getResource(String sessionId, String name, boolean indent, boolean xinclude)
	throws java.rmi.RemoteException {
		Properties outputProperties = new Properties();
		outputProperties.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
		outputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
		return getResource(sessionId, name, outputProperties);
	}
	
	protected String getResource(String sessionId, String name, Properties outputProperties)
		throws java.rmi.RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			DocumentImpl document = (DocumentImpl) broker.getDocument(name);
			if (document == null)
				throw new RemoteException("resource " + name + " not found");
			if(!document.getPermissions().validate(broker.getUser(), Permission.READ))
				throw new PermissionDeniedException("Not allowed to read resource");
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			serializer.setProperties(outputProperties);
			return serializer.serialize(document);

			//			if (xml != null)
			//				try {
			//					return xml.getBytes("UTF-8");
			//				} catch (java.io.UnsupportedEncodingException e) {
			//					return xml.getBytes();
			//				}
			//
			//			return null;
		} catch (SAXException saxe) {
			saxe.printStackTrace();
			throw new RemoteException(saxe.getMessage());
		} catch (EXistException e) {
			throw new RemoteException(e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new RemoteException(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	public Collection listCollection(String sessionId, String path) throws RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			if (path == null)
				path = "/db";
			org.exist.collections.Collection collection = broker.getCollection(path);
			if (collection == null)
				throw new RemoteException("collection " + path + " not found");
			if (!collection.getPermissions().validate(session.getUser(), Permission.READ))
				throw new RemoteException("permission denied");
			Collection c = new Collection();

			// Sub-collections
			String childCollections[] = new String[collection.getChildCollectionCount()];
			int j = 0;
			for (Iterator i = collection.collectionIterator(); i.hasNext(); j++)
				childCollections[j] = (String) i.next();

			// Resources
			String[] resources = new String[collection.getDocumentCount()];
			j = 0;
			int p;
			String resource;
			for (Iterator i = collection.iterator(broker); i.hasNext(); j++) {
				resource = ((DocumentImpl) i.next()).getFileName();
				p = resource.lastIndexOf('/');
				resources[j] = p < 0 ? resource : resource.substring(p + 1);
			}
			c.setResources(resources);
			c.setCollections(childCollections);
			return c;
		} catch (EXistException e) {
			throw new RemoteException(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.soap.Query#xquery(java.lang.String, byte[])
	 */
	public QueryResponse xquery(String sessionId, byte[] xquery)
			throws RemoteException {
		String query;
		try {
			query = new String(xquery, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			query = new String(xquery);
		}
		return query(sessionId, query);
	}
	
	public org.exist.soap.QueryResponse query(String sessionId, java.lang.String query)
		throws java.rmi.RemoteException {
		Session session = getSession(sessionId);

		QueryResponse resp = new QueryResponse();
		resp.setHits(0);
		DBBroker broker = null;
		try {
			query = StringValue.expand(query);
			LOG.debug("query: " + query);
			broker = pool.get(session.getUser());
			XQueryContext context = new XQueryContext(broker);
			
			XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new RemoteException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				LOG.debug(treeParser.getErrorMessage());
				throw new EXistException(treeParser.getErrorMessage());
			}
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			Sequence seq= expr.eval(null, null);

			QueryResponseCollection[] collections = null;
			if (seq.getLength() > 0 && Type.subTypeOf(seq.getItemType(), Type.NODE))
				collections = collectQueryInfo(scanResults(seq));
			session.addQueryResult(seq);
			resp.setCollections(collections);
			resp.setHits(seq.getLength());
			resp.setQueryTime(System.currentTimeMillis() - start);
			expr.reset();
			context.reset();
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new RemoteException("query execution failed: " + e.getMessage());
		} finally {
			pool.release(broker);
		}
		return resp;
	}

	/* (non-Javadoc)
	 * @see org.exist.soap.Query#retrieveData(java.lang.String, int, int, boolean, boolean, java.lang.String)
	 */
	public byte[][] retrieveData(String sessionId, int start, int howmany,
			boolean indent, boolean xinclude, String highlight)
			throws RemoteException {
		String[] results = retrieve(sessionId, start, howmany, indent, xinclude, highlight);
		byte[][] data = new byte[results.length][];
		for(int i = 0; i < results.length; i++) {
			try {
				data[i] = results[i].getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
			}
		}
		return data;
	}
	
	public String[] retrieve(
		String sessionId,
		int start,
		int howmany,
		boolean indent,
		boolean xinclude,
		String highlight)
		throws java.rmi.RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			QueryResult queryResult = session.getQueryResult();
			if (queryResult == null)
				throw new RemoteException("result set unknown or timed out");
			Sequence seq = (Sequence) queryResult.result;
			if (start < 1 || start > seq.getLength())
				throw new RuntimeException(
						"index " + start + " out of bounds (" + seq.getLength() + ")");
			if (start + howmany > seq.getLength() || howmany == 0)
				howmany = seq.getLength();
			
			String xml[] = new String[howmany];
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
			serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
			serializer.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlight);

			Item item;
			int j = 0;
			for (int i = --start; i < start + howmany; i++, j++) {
				item = seq.itemAt(i);
				if (item == null)
					continue;
				if (item.getType() == Type.ELEMENT) {
					NodeValue node = (NodeValue) item;
					xml[j] = serializer.serialize(node);
				} else {
					xml[j] = item.getStringValue();
				}
			}
			return xml;
		} catch (Exception e) {
			LOG.warn(e);
			e.printStackTrace();
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	public String[] retrieveByDocument(
		String sessionId,
		int start,
		int howmany,
		String docPath,
		boolean indent,
		boolean xinclude,
		String highlight)
		throws RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			Sequence qr = (Sequence) session.getQueryResult().result;
			if (qr == null)
				throw new RemoteException("result set unknown or timed out");
			String xml[] = null;
			switch (qr.getItemType()) {
				case Type.NODE :
					NodeList resultSet = (NodeSet)qr;
					ArraySet hitsByDoc = new ArraySet(50);
					NodeProxy p;
					String path;
					for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
						p = (NodeProxy) i.next();
						path = p.getDocument().getCollection().getName() + '/' + p.getDocument().getFileName();
						if (path.equals(docPath))
							hitsByDoc.add(p);
					}
					--start;
					if (start < 0 || start > hitsByDoc.getLength())
						throw new RemoteException(
							"index " + start + "out of bounds (" + hitsByDoc.getLength() + ")");
					if (start + howmany >= hitsByDoc.getLength())
						howmany = hitsByDoc.getLength() - start;
					Serializer serializer = broker.getSerializer();
					serializer.reset();
					serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
					serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
					serializer.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlight);

					xml = new String[howmany];
					for (int i = 0; i < howmany; i++) {
						NodeProxy proxy = ((NodeSet) hitsByDoc).get(start);
						if (proxy == null)
							throw new RuntimeException("not found: " + start);
						xml[i] = serializer.serialize(proxy);
					}
					break;
				default :
					throw new RemoteException("result set is not a node list");
			}
			return xml;
		} catch (Exception e) {
			LOG.warn(e);
			e.printStackTrace();
			throw new RemoteException(e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	private TreeMap scanResults(Sequence results) {
		TreeMap collections = new TreeMap();
		TreeMap documents;
		Integer hits;
		for (SequenceIterator i = results.iterate(); i.hasNext(); ) {
			Item item = i.nextItem();
			if(Type.subTypeOf(item.getType(), Type.NODE)) {
				NodeValue node = (NodeValue)item;
				if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
					NodeProxy p = (NodeProxy)node;
					if ((documents = (TreeMap) collections.get(p.getDocument().getCollection().getName())) == null) {
						documents = new TreeMap();
						collections.put(p.getDocument().getCollection().getName(), documents);
					}
					if ((hits = (Integer) documents.get(p.getDocument().getFileName())) == null)
						documents.put(p.getDocument().getFileName(), new Integer(1));
					else
						documents.put(p.getDocument().getFileName(), new Integer(hits.intValue() + 1));
				}
			}
		}
		return collections;
	}
}
