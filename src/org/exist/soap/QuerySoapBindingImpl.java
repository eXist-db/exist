/**
 *  QueryServiceSoapBindingImpl.java This file was auto-generated from WSDL by
 *  the Apache Axis Wsdl2java emitter.
 */

package org.exist.soap;

import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import antlr.collections.AST;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    30. April 2002
 */
public class QuerySoapBindingImpl implements org.exist.soap.Query {

	private static Category LOG = Category.getInstance("QueryService");

	private BrokerPool pool;

	/**  Constructor for the QuerySoapBindingImpl object */
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

	/**
	 *  Gets the resource attribute of the QuerySoapBindingImpl object
	 *
	 *@param  name                          Description of the Parameter
	 *@param  prettyPrint                   Description of the Parameter
	 *@param  encoding                      Description of the Parameter
	 *@return                               The resource value
	 *@exception  java.rmi.RemoteException  Description of the Exception
	 */
	public String getResource(String sessionId, String name, boolean indent, boolean xinclude)
		throws java.rmi.RemoteException {
		Session session = getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			DocumentImpl document = (DocumentImpl) broker.getDocument(name);
			if (document == null)
				throw new RemoteException("resource " + name + " not found");
			Serializer serializer = broker.getSerializer();
			serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
			serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
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

	/**
	 *  Description of the Method
	 *
	 *@param  path                 Description of the Parameter
	 *@return                      Description of the Return Value
	 *@exception  RemoteException  Description of the Exception
	 */
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
			for (Iterator i = collection.iterator(); i.hasNext(); j++) {
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

	/**
	 *  Description of the Method
	 *
	 *@param  query                         Description of the Parameter
	 *@return                               Description of the Return Value
	 *@exception  java.rmi.RemoteException  Description of the Exception
	 */
	public org.exist.soap.QueryResponse query(String sessionId, java.lang.String query)
		throws java.rmi.RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		if (!(query.startsWith("document(") || query.startsWith("collection(")))
			query = "document()" + query;

		QueryResponse resp = new QueryResponse();
		resp.setHits(0);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			StaticContext context = new StaticContext(broker);
			XPathLexer2 lexer = new XPathLexer2(new StringReader(query));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new RemoteException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());

			PathExpr expr = new PathExpr();
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new EXistException(treeParser.getErrorMessage());
			}
			LOG.info("query: " + expr.pprint());
			long start = System.currentTimeMillis();
			DocumentSet ndocs = expr.preselect( context );
			if (ndocs.getLength() == 0)
				return resp;
			Sequence seq= expr.eval(context, ndocs, null, null);

			QueryResponseCollection[] collections = null;
			if (seq.getItemType() == Type.NODE)
				collections = collectQueryInfo(scanResults((NodeSet)seq));
			session.addQueryResult(seq);
			resp.setCollections(collections);
			resp.setHits(seq.getLength());
			resp.setQueryTime(System.currentTimeMillis() - start);
		} catch (Exception e) {
			throw new RemoteException("query execution failed: " + e.getMessage());
		} finally {
			pool.release(broker);
		}
		return resp;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  resultId                      Description of the Parameter
	 *@param  num                           Description of the Parameter
	 *@param  encoding                      Description of the Parameter
	 *@param  prettyPrint                   Description of the Parameter
	 *@return                               Description of the Return Value
	 *@exception  java.rmi.RemoteException  Description of the Exception
	 */
	public String[] retrieve(
		String sessionId,
		int start,
		int howmany,
		boolean indent,
		boolean xinclude,
		String highlight)
		throws java.rmi.RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
		DBBroker broker = null;
		try {
			broker = pool.get(session.getUser());
			Sequence qr = (Sequence) session.getQueryResult().result;
			if (qr == null)
				throw new RemoteException("result set unknown or timed out");
			String xml[] = null;
			switch (qr.getItemType()) {
				case Type.NODE :
					NodeSet resultSet = (NodeSet)qr;
					--start;
					if (start < 0 || start >= resultSet.getLength())
						throw new RuntimeException(
							"index " + start + " out of bounds (" + resultSet.getLength() + ")");
					if (start + howmany >= resultSet.getLength())
						howmany = resultSet.getLength() - start;
					Serializer serializer = broker.getSerializer();
					serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
					serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
					serializer.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlight);

					xml = new String[howmany];
					for (int i = 0; i < howmany; i++) {
						NodeProxy proxy = ((NodeSet) resultSet).get(start + i);
						if (proxy == null)
							throw new RuntimeException("not found: " + (start + i));

						xml[i] = serializer.serialize(proxy);
					}
					break;
				default :
					--start;
					if (start < 0 || start >= qr.getLength())
						throw new RemoteException("index " + start + " out of bounds");
					if (start + howmany >= qr.getLength())
						howmany = qr.getLength() - start;
					xml = new String[howmany];
					for (int i = 0; i < howmany; i++) {
						Item item = qr.itemAt(start);
						xml[i] = item.getStringValue();
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
		Session session = SessionManager.getInstance().getSession(sessionId);
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
					for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
						p = (NodeProxy) i.next();
						if (p.doc.getFileName().equals(docPath))
							hitsByDoc.add(p);
					}
					--start;
					if (start < 0 || start > hitsByDoc.getLength())
						throw new RemoteException(
							"index " + start + "out of bounds (" + hitsByDoc.getLength() + ")");
					if (start + howmany >= hitsByDoc.getLength())
						howmany = hitsByDoc.getLength() - start;
					Serializer serializer = broker.getSerializer();
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

	private TreeMap scanResults(NodeList results) {
		TreeMap collections = new TreeMap();
		TreeMap documents;
		NodeProxy p;
		Integer hits;
		for (Iterator i = ((NodeSet) results).iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			if ((documents = (TreeMap) collections.get(p.doc.getCollection().getName())) == null) {
				documents = new TreeMap();
				collections.put(p.doc.getCollection().getName(), documents);
				System.out.println("added " + p.doc.getCollection().getName());
			}
			if ((hits = (Integer) documents.get(p.doc.getFileName())) == null)
				documents.put(p.doc.getFileName(), new Integer(1));
			else
				documents.put(p.doc.getFileName(), new Integer(hits.intValue() + 1));
		}
		return collections;
	}
}
