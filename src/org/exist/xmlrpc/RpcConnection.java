package org.exist.xmlrpc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.ArraySet;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SortedNodeSet;
import org.exist.memtree.NodeImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.util.SyntaxException;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.DOMSerializerPool;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import antlr.collections.AST;

/**
 * This class implements the actual methods defined by
 * {@link org.exist.xmlrpc.RpcAPI}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RpcConnection extends Thread {

	private final static Logger LOG = Logger.getLogger(RpcConnection.class);

	public final static String EXIST_NS = "http://exist.sourceforge.net/NS/exist";

	protected BrokerPool brokerPool;
	protected WeakHashMap documentCache = new WeakHashMap();
	protected boolean terminate = false;
	protected List cachedExpressions = new LinkedList();

	protected RpcServer.ConnectionPool connectionPool;

	public RpcConnection(Configuration conf, RpcServer.ConnectionPool pool)
			throws EXistException {
		super();
		connectionPool = pool;
		brokerPool = BrokerPool.getInstance();
	}

	public void createCollection(User user, String name) throws Exception,
			PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Collection current = broker.getOrCreateCollection(name);
			LOG.debug("creating collection " + name);
			broker.saveCollection(current);
			broker.flush();
			//broker.sync();
			LOG.debug("collection " + name + " has been created");
		} catch (Exception e) {
			LOG.debug(e);
			throw e;
		} finally {
			brokerPool.release(broker);
		}
	}

	public String createId(User user, String collName) throws EXistException {
		DBBroker broker = brokerPool.get(user);
		try {
			Collection collection = broker.getCollection(collName);
			if (collection == null)
				throw new EXistException("collection " + collName
						+ " not found!");
			String id;
			Random rand = new Random();
			boolean ok;
			do {
				ok = true;
				id = Integer.toHexString(rand.nextInt()) + ".xml";
				// check if this id does already exist
				if (collection.hasDocument(id))
					ok = false;

				if (collection.hasSubcollection(id))
					ok = false;

			} while (!ok);
			return id;
		} finally {
			brokerPool.release(broker);
		}
	}

	protected PathExpr compile(User user, DBBroker broker, String xquery,
			Hashtable parameters) throws Exception {
		String baseURI = (String) parameters.get(RpcAPI.BASE_URI);
		XQueryContext context = new XQueryContext(broker);
		context.setBaseURI(baseURI);
		Hashtable namespaces = (Hashtable)parameters.get(RpcAPI.NAMESPACES);
		if(namespaces != null && namespaces.size() > 0) {
			context.declareNamespaces(namespaces);
		}
		LOG.debug("compiling " + xquery);
		XQueryLexer lexer = new XQueryLexer(new StringReader(xquery));
		XQueryParser parser = new XQueryParser(lexer);
		XQueryTreeParser treeParser = new XQueryTreeParser(context);

		parser.xpath();
		if (parser.foundErrors()) {
			throw new EXistException(parser.getErrorMessage());
		}

		AST ast = parser.getAST();

		PathExpr expr = new PathExpr(context);
		treeParser.xpath(ast, expr);
		if (treeParser.foundErrors()) {
			throw new EXistException(treeParser.getErrorMessage());
		}
		return expr;
	}

	protected Sequence doQuery(User user, DBBroker broker, String xpath,
			DocumentSet docs, NodeSet contextSet, Hashtable parameters)
			throws Exception {
		String baseURI = (String) parameters.get(RpcAPI.BASE_URI);
		if(docs == null) {
			if(baseURI == null)
				docs = broker.getAllDocuments(new DocumentSet());
			else {
				docs = new DocumentSet();
				Collection root = broker.getCollection(baseURI);
				root.allDocs(broker, docs, true);
			}
		}
		CachedQuery cached = getCachedQuery(xpath);
		PathExpr expr = null;
		if (cached == null) {
			expr = compile(user, broker, xpath, parameters);
			cached = new CachedQuery(expr, xpath);
			cachedExpressions.add(cached);
		} else {
			LOG.debug("reusing compiled expression");
			expr = cached.expression;
		}
		XQueryContext context = expr.getContext();
		context.setBaseURI(baseURI);
		context.setStaticallyKnownDocuments(docs);
		Hashtable namespaces = (Hashtable)parameters.get(RpcAPI.NAMESPACES);
		if(namespaces != null && namespaces.size() > 0) {
			context.declareNamespaces(namespaces);
		}
		// set the current broker object when reusing a compiled query:
		context.setBroker(broker);
		long start = System.currentTimeMillis();
		Sequence result = expr.eval(contextSet, null);
		LOG.info("query took " + (System.currentTimeMillis() - start) + "ms.");
		expr.reset();
		return result;
	}

	public int executeQuery(User user, String xpath) throws Exception {
		long startTime = System.currentTimeMillis();
		LOG.debug("query: " + xpath);
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Sequence resultValue = doQuery(user, broker, xpath, null, null,
					new Hashtable());
			QueryResult qr = new QueryResult(resultValue, (System
					.currentTimeMillis() - startTime));
			connectionPool.resultSets.put(qr.hashCode(), qr);
			return qr.hashCode();
		} finally {
			brokerPool.release(broker);
		}
	}

	protected String formatErrorMsg(String message) {
		return formatErrorMsg("error", message);
	}

	protected String formatErrorMsg(String type, String message) {
		StringBuffer buf = new StringBuffer();
		buf
				.append("<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
		buf.append("hitCount=\"0\">");
		buf.append('<');
		buf.append(type);
		buf.append('>');
		buf.append(message);
		buf.append("</");
		buf.append(type);
		buf.append("></exist:result>");
		return buf.toString();
	}

	public Hashtable getCollectionDesc(User user, String rootCollection)
			throws Exception {
		DBBroker broker = brokerPool.get(user);
		try {
			if (rootCollection == null)
				rootCollection = "/db";

			Collection collection = broker.getCollection(rootCollection);
			if (collection == null)
				throw new EXistException("collection " + rootCollection
						+ " not found!");
			Hashtable desc = new Hashtable();
			Vector docs = new Vector();
			Vector collections = new Vector();
			if (collection.getPermissions().validate(user, Permission.READ)) {
				DocumentImpl doc;
				Hashtable hash;
				Permission perms;
				for (Iterator i = collection.iterator(); i.hasNext(); ) {
					doc = (DocumentImpl) i.next();
					perms = doc.getPermissions();
					hash = new Hashtable(4);
					hash.put("name", doc.getFileName());
					hash.put("owner", perms.getOwner());
					hash.put("group", perms.getOwnerGroup());
					hash
							.put("permissions", new Integer(perms
									.getPermissions()));
					hash.put("type",
							doc.getResourceType() == DocumentImpl.BINARY_FILE
									? "BinaryResource"
									: "XMLResource");
					docs.addElement(hash);
				}
				for (Iterator i = collection.collectionIterator(); i.hasNext(); )
					collections.addElement((String) i.next());
			}
			Permission perms = collection.getPermissions();
			desc.put("collections", collections);
			desc.put("documents", docs);
			desc.put("name", collection.getName());
			desc.put("created", Long.toString(collection.getCreationTime()));
			desc.put("owner", perms.getOwner());
			desc.put("group", perms.getOwnerGroup());
			desc.put("permissions", new Integer(perms.getPermissions()));
			return desc;
		} finally {
			brokerPool.release(broker);
		}
	}

	public String getDocument(User user, String name, Hashtable parametri)
			throws Exception {
		long start = System.currentTimeMillis();
		DBBroker broker = null;

		String stylesheet = null;
		String encoding = "UTF-8";
		String processXSL = "yes";
		Hashtable styleparam = null;

		try {
			broker = brokerPool.get(user);
			Configuration config = broker.getConfiguration();
			String option = (String) config
					.getProperty("serialization.enable-xinclude");
			String prettyPrint = (String) config
					.getProperty("serialization.indent");

			DocumentImpl doc = (DocumentImpl) broker.getDocument(name);
			if (doc == null) {
				LOG.debug("document " + name + " not found!");
				throw new EXistException("document not found");
			}
			Serializer serializer = broker.getSerializer();

			if (parametri != null) {

				for (Enumeration en = parametri.keys(); en.hasMoreElements(); ) {

					String param = (String) en.nextElement();
					String paramvalue = parametri.get(param).toString();
					//LOG.debug("-------Parametri passati:"+param+":
					// "+paramvalue);

					if (param.equals(EXistOutputKeys.EXPAND_XINCLUDES)) {
						option = (paramvalue.equals("yes")) ? "true" : "false";
					}

					if (param.equals(OutputKeys.INDENT)) {
						prettyPrint = paramvalue;
					}

					if (param.equals(OutputKeys.ENCODING)) {
						encoding = paramvalue;
					}

					if (param.equals(EXistOutputKeys.STYLESHEET)) {
						stylesheet = paramvalue;
					}

					if (param.equals(EXistOutputKeys.STYLESHEET_PARAM)) {
						styleparam = (Hashtable) parametri.get(param);
					}

					if (param.equals(OutputKeys.DOCTYPE_SYSTEM)) {
						serializer.setProperty(OutputKeys.DOCTYPE_SYSTEM,
								paramvalue);
					}

					if(param.equals(EXistOutputKeys.PROCESS_XSL_PI)) {
						serializer.setProperty(EXistOutputKeys.PROCESS_XSL_PI, paramvalue);
					}
				}

			}

			if (option.equals("true")) {
				serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
			} else {
				serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
			}

			serializer.setProperty(OutputKeys.ENCODING, encoding);
			serializer.setProperty(OutputKeys.INDENT, prettyPrint);
			if (stylesheet != null) {
				if (stylesheet.indexOf(":") < 0) {
					if (!stylesheet.startsWith("/")) {
						// make path relative to current collection
						String collection;
						if (doc.getCollection() != null)
							collection = doc.getCollection().getName();
						else {
							int cp = doc.getFileName().lastIndexOf("/");
							collection = (cp > 0) ? doc.getFileName()
									.substring(0, cp) : "/";
						}
						stylesheet = (collection.equals("/")
								? '/' + stylesheet
								: collection + '/' + stylesheet);
					}

				}
				serializer.setStylesheet(stylesheet);

				// set stylesheet param if present
				if (styleparam != null) {
					for (Enumeration en1 = styleparam.keys(); en1
							.hasMoreElements(); ) {
						String param1 = (String) en1.nextElement();
						String paramvalue1 = styleparam.get(param1).toString();
						// System.out.println("-->"+param1+"--"+paramvalue1);
						serializer
								.setStylesheetParamameter(param1, paramvalue1);
					}
				}
			}
			String xml = serializer.serialize(doc);

			return xml;
		} catch (NoSuchMethodError nsme) {
			nsme.printStackTrace();
			return null;
		} finally {
			brokerPool.release(broker);
		}
	}

	public byte[] getBinaryResource(User user, String name)
			throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl doc = (DocumentImpl) broker.getDocument(name);
			if (doc == null)
				throw new EXistException("Resource " + name + " not found");
			if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
				throw new EXistException("Document " + name
						+ " is not a binary resource");
			return broker.getBinaryResourceData((BinaryDocument) doc);
		} finally {
			brokerPool.release(broker);
		}
	}

	public int xupdate(User user, String collectionName, String xupdate)
			throws SAXException, PermissionDeniedException, EXistException,
			XPathException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName
						+ " not found");
			DocumentSet docs = collection.allDocs(broker, new DocumentSet(),
					true);
			XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
			Modification modifications[] = processor.parse(new InputSource(
					new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process();
				broker.flush();
			}
			return (int) mods;
		} catch (ParserConfigurationException e) {
			throw new EXistException(e.getMessage());
		} catch (IOException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	public int xupdateResource(User user, String resource, String xupdate)
			throws SAXException, PermissionDeniedException, EXistException,
			XPathException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Document doc = broker.getDocument(resource);
			if (doc == null)
				throw new EXistException("document " + resource + " not found");
			DocumentSet docs = new DocumentSet();
			docs.add(doc);
			XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
			Modification modifications[] = processor.parse(new InputSource(
					new StringReader(xupdate)));
			long mods = 0;
			for (int i = 0; i < modifications.length; i++) {
				mods += modifications[i].process();
				broker.flush();
			}
			return (int) mods;
		} catch (ParserConfigurationException e) {
			throw new EXistException(e.getMessage());
		} catch (IOException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean sync() {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			broker.sync();
		} catch (EXistException e) {
		} finally {
			brokerPool.release(broker);
		}
		return true;
	}

	/**
	 * Gets the documentListing attribute of the RpcConnection object
	 * 
	 * @param user
	 *                   Description of the Parameter
	 * @return The documentListing value
	 * @exception EXistException
	 *                         Description of the Exception
	 */
	public Vector getDocumentListing(User user) throws EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentSet docs = broker.getAllDocuments(new DocumentSet());
			String names[] = docs.getNames();
			Vector vec = new Vector();
			for (int i = 0; i < names.length; i++)
				vec.addElement(names[i]);

			return vec;
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 * Gets the documentListing attribute of the RpcConnection object
	 * 
	 * @param collection
	 *                   Description of the Parameter
	 * @param user
	 *                   Description of the Parameter
	 * @return The documentListing value
	 * @exception EXistException
	 *                         Description of the Exception
	 * @exception PermissionDeniedException
	 *                         Description of the Exception
	 */
	public Vector getDocumentListing(User user, String name)
			throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			Vector vec = new Vector();
			if (collection == null)
				return vec;
			String resource;
			int p;
			for (Iterator i = collection.iterator(); i.hasNext(); ) {
				resource = ((DocumentImpl) i.next()).getFileName();
				p = resource.lastIndexOf('/');
				vec.addElement(p < 0 ? resource : resource.substring(p + 1));
			}
			return vec;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Hashtable listDocumentPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			if (!collection.getPermissions().validate(user, Permission.READ))
				throw new PermissionDeniedException(
						"not allowed to read collection " + name);
			Hashtable result = new Hashtable(collection.getDocumentCount());
			if (collection == null)
				return result;
			DocumentImpl doc;
			Permission perm;
			Vector tmp;
			String docName;
			for (Iterator i = collection.iterator(); i.hasNext(); ) {
				doc = (DocumentImpl) i.next();
				perm = doc.getPermissions();
				tmp = new Vector(3);
				tmp.addElement(perm.getOwner());
				tmp.addElement(perm.getOwnerGroup());
				tmp.addElement(new Integer(perm.getPermissions()));
				docName = doc.getFileName().substring(
						doc.getFileName().lastIndexOf('/') + 1);
				result.put(docName, tmp);
			}
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Hashtable listCollectionPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			if (!collection.getPermissions().validate(user, Permission.READ))
				throw new PermissionDeniedException(
						"not allowed to read collection " + name);
			Hashtable result = new Hashtable(collection
					.getChildCollectionCount());
			if (collection == null)
				return result;
			String child, path;
			Collection childColl;
			Permission perm;
			Vector tmp;
			for (Iterator i = collection.collectionIterator(); i.hasNext(); ) {
				child = (String) i.next();
				path = name + '/' + child;
				childColl = broker.getCollection(path);
				perm = childColl.getPermissions();
				tmp = new Vector(3);
				tmp.addElement(perm.getOwner());
				tmp.addElement(perm.getOwnerGroup());
				tmp.addElement(new Integer(perm.getPermissions()));
				result.put(child, tmp);
			}
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public int getHits(User user, int resultId) throws EXistException {
		QueryResult qr = (QueryResult) connectionPool.resultSets.get(resultId);
		if (qr == null)
			throw new EXistException("result set unknown or timed out");
		qr.timestamp = System.currentTimeMillis();
		if (qr.result == null)
			return 0;
		return qr.result.getLength();
	}

	public Hashtable getPermissions(User user, String name)
			throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			Permission perm = null;
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(name);
				if (doc == null)
					throw new EXistException("document or collection " + name
							+ " not found");
				perm = doc.getPermissions();
			} else {
				perm = collection.getPermissions();
			}
			Hashtable result = new Hashtable();
			result.put("owner", perm.getOwner());
			result.put("group", perm.getOwnerGroup());
			result.put("permissions", new Integer(perm.getPermissions()));
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Date getCreationDate(User user, String collectionPath)
			throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!collectionPath.startsWith("/"))
				collectionPath = '/' + collectionPath;
			if (!collectionPath.startsWith("/db"))
				collectionPath = "/db" + collectionPath;
			Collection collection = broker.getCollection(collectionPath);
			if (collection == null)
				throw new EXistException("collection " + collectionPath
						+ " not found");
			return new Date(collection.getCreationTime());
		} finally {
			brokerPool.release(broker);
		}
	}

	public Vector getTimestamps(User user, String documentPath)
			throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (!documentPath.startsWith("/"))
				documentPath = '/' + documentPath;
			if (!documentPath.startsWith("/db"))
				documentPath = "/db" + documentPath;
			DocumentImpl doc = (DocumentImpl) broker.getDocument(documentPath);
			if (doc == null) {
				LOG.debug("document " + documentPath + " not found!");
				throw new EXistException("document not found");
			}
			Vector vector = new Vector(2);
			vector.addElement(new Date(doc.getCreated()));
			vector.addElement(new Date(doc.getLastModified()));
			return vector;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Hashtable getUser(User user, String name) throws EXistException,
			PermissionDeniedException {
		User u = brokerPool.getSecurityManager().getUser(name);
		if (u == null)
			throw new EXistException("user " + name + " does not exist");
		Hashtable tab = new Hashtable();
		tab.put("name", u.getName());
		Vector groups = new Vector();
		for (Iterator i = u.getGroups(); i.hasNext(); )
			groups.addElement(i.next());
		tab.put("groups", groups);
		if (u.getHome() != null)
			tab.put("home", u.getHome());
		return tab;
	}

	public Vector getUsers(User user) throws EXistException,
			PermissionDeniedException {
		User users[] = brokerPool.getSecurityManager().getUsers();
		Vector r = new Vector();
		for (int i = 0; i < users.length; i++) {
			final Hashtable tab = new Hashtable();
			tab.put("name", users[i].getName());
			Vector groups = new Vector();
			for (Iterator j = users[i].getGroups(); j.hasNext(); )
				groups.addElement(j.next());
			tab.put("groups", groups);
			if (users[i].getHome() != null)
				tab.put("home", users[i].getHome());
			r.addElement(tab);
		}
		return r;
	}

	public Vector getGroups(User user) throws EXistException,
			PermissionDeniedException {
		String[] groups = brokerPool.getSecurityManager().getGroups();
		Vector v = new Vector(groups.length);
		for (int i = 0; i < groups.length; i++) {
			v.addElement(groups[i]);
		}
		return v;
	}

	public boolean hasDocument(User user, String name) throws Exception {
		DBBroker broker = brokerPool.get(user);
		boolean r = (broker.getDocument(name) != null);
		brokerPool.release(broker);
		return r;
	}

	public boolean parse(User user, byte[] xml, String docName, 
			boolean replace) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			int p = docName.lastIndexOf('/');
			if (p < 0 || p == docName.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = docName.substring(0, p);
			docName = docName.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("Collection " + collectionName
						+ " not found");
			if (!replace) {
				DocumentImpl old = collection.getDocument(docName);
				if (old != null)
					throw new PermissionDeniedException(
							"Document exists and overwrite is not allowed");
			}
			long startTime = System.currentTimeMillis();
			InputStream is = new ByteArrayInputStream(xml);
			DocumentImpl doc = collection.addDocument(broker, docName,
					new InputSource(is));
			LOG.debug("parsing " + docName + " took "
					+ (System.currentTimeMillis() - startTime) + "ms.");
			return doc != null;
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw e;
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 * Parse a file previously uploaded with upload.
	 * 
	 * The temporary file will be removed.
	 * 
	 * @param user
	 * @param localFile
	 * @throws EXistException
	 * @throws IOException
	 */
	public boolean parseLocal(User user, String localFile, String docName,
			boolean replace) throws EXistException, PermissionDeniedException, LockException,
			SAXException, TriggerException {
		File file = new File(localFile);
		if (!file.canRead())
			throw new EXistException("unable to read file " + localFile);
		DBBroker broker = null;
		DocumentImpl doc = null;
		try {
			broker = brokerPool.get(user);
			int p = docName.lastIndexOf('/');
			if (p < 0 || p == docName.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = docName.substring(0, p);
			docName = docName.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("Collection " + collectionName
						+ " not found");
			if (!replace) {
				DocumentImpl old = collection.getDocument(docName);
				if (old != null)
					throw new PermissionDeniedException(
							"Old document exists and overwrite is not allowed");
			}
			String uri = file.toURI().toASCIIString();
			collection.addDocument(broker, docName, new InputSource(uri));
		} finally {
			brokerPool.release(broker);
		}
		file.delete();
		return doc != null;
	}

	public boolean storeBinary(User user, byte[] data, String docName,
		boolean replace) throws EXistException, PermissionDeniedException, LockException {
		DBBroker broker = null;
		DocumentImpl doc = null;
		try {
			broker = brokerPool.get(user);
		} finally {
			brokerPool.release(broker);
			int p = docName.lastIndexOf('/');
			if (p < 0 || p == docName.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = docName.substring(0, p);
			docName = docName.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("Collection " + collectionName
						+ " not found");
			if (!replace) {
				DocumentImpl old = collection.getDocument(docName);
				if (old != null)
					throw new PermissionDeniedException(
							"Old document exists and overwrite is not allowed");
			}
			doc = collection.addBinaryResource(broker, docName, data);
		}
		return doc != null;
	}

	public String upload(User user, byte[] chunk, int length, String fileName)
			throws EXistException, IOException {
		File file;
		if (fileName == null || fileName.length() == 0) {
			// create temporary file
			file = File.createTempFile("rpc", "xml");
			fileName = file.getAbsolutePath();
			LOG.debug("created temporary file " + file.getAbsolutePath());
		} else {
			LOG.debug("appending to file " + fileName);
			file = new File(fileName);
		}
		if (!file.canWrite())
			throw new EXistException("cannot write to file " + fileName);
		FileOutputStream os = new FileOutputStream(file, true);
		os.write(chunk, 0, length);
		os.close();
		return fileName;
	}

	protected String printAll(DBBroker broker, Sequence resultSet, int howmany,
			int start, Hashtable properties, long queryTime) throws Exception {
		if (resultSet.getLength() == 0)
			return "<?xml version=\"1.0\"?>\n"
					+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
					+ "hitCount=\"0\"/>";
		if (howmany > resultSet.getLength() || howmany == 0)
			howmany = resultSet.getLength();

		if (start < 1 || start > resultSet.getLength())
			throw new EXistException("start parameter out of range");

		StringWriter writer = new StringWriter();
		writer.write("<exist:result xmlns:exist=\"");
		writer.write(EXIST_NS);
		writer.write("\" hits=\"");
		writer.write(Integer.toString(resultSet.getLength()));
		writer.write("\" start=\"");
		writer.write(Integer.toString(start));
		writer.write("\" count=\"");
		writer.write(Integer.toString(howmany));
		writer.write("\">\n");

		Serializer serializer = broker.getSerializer();
		serializer.reset();
		serializer.setProperties(properties);

		Item item;
		for (int i = --start; i < start + howmany; i++) {
			item = resultSet.itemAt(i);
			if (item == null)
				continue;
			if (item.getType() == Type.ELEMENT) {
				NodeValue node = (NodeValue) item;
				writer.write(serializer.serialize(node));
			} else {
				writer.write("<exist:value type=\"");
				writer.write(Type.getTypeName(item.getType()));
				writer.write("\">");
				writer.write(item.getStringValue());
				writer.write("</exist:value>");
			}
		}
		writer.write("\n</exist:result>");
		return writer.toString();
	}

	public String query(User user, String xpath, int howmany, int start,
			Hashtable parameters) throws Exception {
		long startTime = System.currentTimeMillis();
		String result;
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Sequence resultSeq = doQuery(user, broker, xpath, null, null, parameters);
			if (resultSeq == null)
				return "<?xml version=\"1.0\"?>\n"
						+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
						+ "hitCount=\"0\"/>";

			result = printAll(broker, resultSeq, howmany, start, parameters,
					(System.currentTimeMillis() - startTime));
		} finally {
			brokerPool.release(broker);
		}
		return result;
	}

	public Hashtable queryP(User user, String xpath, String docName,
			String s_id, Hashtable parameters) throws Exception {
		long startTime = System.currentTimeMillis();
		String sortBy = (String) parameters.get(RpcAPI.SORT_EXPR);

		Hashtable ret = new Hashtable();
		Vector result = new Vector();
		NodeSet nodes = null;
		DocumentSet docs = null;
		Sequence resultSeq = null;
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (docName != null && s_id != null) {
				long id = Long.parseLong(s_id);
				DocumentImpl doc;
				if (!documentCache.containsKey(docName)) {
					doc = (DocumentImpl) broker.getDocument(docName);
					documentCache.put(docName, doc);
				} else
					doc = (DocumentImpl) documentCache.get(docName);
				NodeProxy node = new NodeProxy(doc, id);
				nodes = new ArraySet(1);
				nodes.add(node);
				docs = new DocumentSet();
				docs.add(node.doc);
			}
			resultSeq = doQuery(user, broker, xpath, docs, nodes, parameters);
			if (resultSeq == null)
				return ret;
			LOG.debug("found " + resultSeq.getLength());
			
			if (sortBy != null) {
				SortedNodeSet sorted = new SortedNodeSet(brokerPool, user,
						sortBy);
				sorted.addAll(resultSeq);
				resultSeq = sorted;
			}
			NodeProxy p;
			Vector entry;
			if (resultSeq != null) {
				SequenceIterator i = resultSeq.iterate();
				if (i != null) {
					Item next;
					while (i.hasNext()) {
						next = i.nextItem();
						if (Type.subTypeOf(next.getType(), Type.NODE)) {
							entry = new Vector();
							if (((NodeValue) next).getImplementationType() == NodeValue.PERSISTENT_NODE) {
								p = (NodeProxy) next;
								entry.addElement(p.doc.getFileName());
								entry.addElement(Long.toString(p.getGID()));
							} else {
								entry.addElement("temp_xquery/"
										+ next.hashCode());
								entry.addElement(String
										.valueOf(((NodeImpl) next)
												.getNodeNumber()));
							}
							result.addElement(entry);
						} else
							result.addElement(next.getStringValue());
					}
				} else {
					LOG.debug("sequence iterator is null. Should not");
				}
			} else
				LOG.debug("result sequence is null. Skipping it...");
		} finally {
			brokerPool.release(broker);
		}
		QueryResult qr = new QueryResult(resultSeq,
				(System.currentTimeMillis() - startTime));
		connectionPool.resultSets.put(qr.hashCode(), qr);
		ret.put("id", new Integer(qr.hashCode()));
		ret.put("results", result);
		return ret;
	}

	public void releaseQueryResult(int handle) {
		connectionPool.resultSets.remove(handle);
		LOG.debug("removed query result with handle " + handle);
	}

	public void remove(User user, String docName) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			int p = docName.lastIndexOf('/');
			if (p < 0 || p == docName.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = docName.substring(0, p);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("Collection " + collectionName
						+ " not found");
			DocumentImpl doc = collection.getDocument(docName);
			if(doc == null)
				throw new EXistException("Document " + docName + " not found");
			docName = docName.substring(p + 1);
			if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
				collection.removeBinaryResource(broker, doc);
			else
				collection.removeDocument(broker, docName);
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean removeCollection(User user, String name) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (broker.getCollection(name) == null)
				return false;
			LOG.debug("removing collection " + name);
			return broker.removeCollection(name);
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean removeUser(User user, String name) throws EXistException,
			PermissionDeniedException {
		org.exist.security.SecurityManager manager = brokerPool
				.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new PermissionDeniedException(
					"you are not allowed to remove users");

		manager.deleteUser(name);
		return true;
	}

	public String retrieve(User user, String docName, String s_id,
			Hashtable parameters) throws Exception {
		DBBroker broker = brokerPool.get(user);
		try {
			long id = Long.parseLong(s_id);
			DocumentImpl doc;
			if (!documentCache.containsKey(docName)) {
				doc = (DocumentImpl) broker.getDocument(docName);
				documentCache.put(docName, doc);
			} else
				doc = (DocumentImpl) documentCache.get(docName);

			NodeProxy node = new NodeProxy(doc, id);
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			serializer.setProperties(parameters);
			return serializer.serialize(node);
		} finally {
			brokerPool.release(broker);
		}
	}

	public String retrieve(User user, int resultId, int num,
			Hashtable parameters) throws Exception {
		DBBroker broker = brokerPool.get(user);
		try {
			QueryResult qr = (QueryResult) connectionPool.resultSets
					.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out");
			qr.timestamp = System.currentTimeMillis();
			Item item = qr.result.itemAt(num);
			if (item == null)
				throw new EXistException("index out of range");

			if (item instanceof NodeProxy) {
				NodeProxy proxy = (NodeProxy) item;
				Serializer serializer = broker.getSerializer();
				serializer.reset();
				serializer.setProperties(parameters);
				return serializer.serialize(proxy);
			} else if (item instanceof Node) {
				StringWriter writer = new StringWriter();
				Properties properties = getProperties(parameters);
				DOMSerializer serializer = DOMSerializerPool.getInstance()
						.borrowDOMSerializer();
				serializer.setWriter(writer);
				serializer.setOutputProperties(properties);
				serializer.serialize((Node) item);
				DOMSerializerPool.getInstance().returnDOMSerializer(serializer);
				return writer.toString();
			} else {
				return item.getStringValue();
			}
		} finally {
			brokerPool.release(broker);
		}
	}

	public void run() {
		synchronized (this) {
			while (!terminate)
				try {
					this.wait(500);
				} catch (InterruptedException inte) {
				}

		}
		// broker.shutdown();
	}

	public boolean setPermissions(User user, String resource, String owner,
			String ownerGroup, String permissions) throws EXistException,
			PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			org.exist.security.SecurityManager manager = brokerPool
					.getSecurityManager();
			Collection collection = broker.getCollection(resource);
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(resource);
				if (doc == null)
					throw new EXistException("document or collection "
							+ resource + " not found");
				LOG.debug("changing permissions on document " + resource);
				Permission perm = doc.getPermissions();
				if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					if (permissions != null && permissions.length() > 0)
						perm.setPermissions(permissions);
					broker.saveCollection(doc.getCollection());
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException(
							"not allowed to change permissions");
			} else {
				LOG.debug("changing permissions on collection " + resource);
				Permission perm = collection.getPermissions();
				if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
					if (permissions != null)
						perm.setPermissions(permissions);
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					broker.saveCollection(collection);
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException(
							"not allowed to change permissions");
			}
		} catch (SyntaxException e) {
			throw new EXistException(e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean setPermissions(User user, String resource, String owner,
			String ownerGroup, int permissions) throws EXistException,
			PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			org.exist.security.SecurityManager manager = brokerPool
					.getSecurityManager();
			Collection collection = broker.getCollection(resource);
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(resource);
				if (doc == null)
					throw new EXistException("document or collection "
							+ resource + " not found");
				LOG.debug("changing permissions on document " + resource);
				Permission perm = doc.getPermissions();
				if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					perm.setPermissions(permissions);
					broker.saveCollection(doc.getCollection());
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException(
							"not allowed to change permissions");
			} else {
				LOG.debug("changing permissions on collection " + resource);
				Permission perm = collection.getPermissions();
				if (perm.getOwner().equals(user.getName())
						|| manager.hasAdminPrivileges(user)) {
					perm.setPermissions(permissions);
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					broker.saveCollection(collection);
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException(
							"not allowed to change permissions");
			}
		} catch (PermissionDeniedException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean setUser(User user, String name, String passwd,
			Vector groups, String home) throws EXistException,
			PermissionDeniedException {
		org.exist.security.SecurityManager manager = brokerPool
				.getSecurityManager();
		if(name.equals(org.exist.security.SecurityManager.GUEST_USER) &&
				(!manager.hasAdminPrivileges(user)))
			throw new PermissionDeniedException(
				"guest user cannot be modified");
		User u;
		if (!manager.hasUser(name)) {
			if (!manager.hasAdminPrivileges(user))
				throw new PermissionDeniedException(
						"not allowed to create user");
			u = new User(name);
			u.setPasswordDigest(passwd);
		} else {
			u = manager.getUser(name);
			if (!(u.getName().equals(user.getName()) || manager
					.hasAdminPrivileges(user)))
				throw new PermissionDeniedException(
						"you are not allowed to change this user");
			u.setPasswordDigest(passwd);
		}
		String g;
		for (Iterator i = groups.iterator(); i.hasNext(); ) {
			g = (String) i.next();
			if (!u.hasGroup(g)) {
				if(!manager.hasAdminPrivileges(user))
					throw new PermissionDeniedException(
							"User is not allowed to add groups");
				u.addGroup(g);
			}
		}
		if (home != null)
			u.setHome(home);
		manager.setUser(u);
		return true;
	}

	public boolean lockResource(User user, String path, String userName) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl doc = (DocumentImpl) broker.getDocument(path);
				if (doc == null)
					throw new EXistException("Resource "
							+ path + " not found");
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("User is not allowed to lock resource " + path);
			org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
			if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user)))
				throw new PermissionDeniedException("User " + user.getName() + " is not allowed " +
						"to lock the resource for user " + userName);
			User lockOwner = doc.getUserLock();
			if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
				throw new PermissionDeniedException("Resource is already locked by user " +
						lockOwner.getName());
			doc.setUserLock(user);
			broker.saveCollection(doc.getCollection());
			return true;
		} finally {
			brokerPool.release(broker);
		}
	}
	
	public String hasUserLock(User user, String path) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl doc = (DocumentImpl) broker.getDocument(path);
			if (doc == null)
				throw new EXistException("Resource " + path + " not found");
			User u = doc.getUserLock();
			return u == null ? "" : u.getName();
		} finally {
			brokerPool.release(broker);
		}
	}
	
	public boolean unlockResource(User user, String path) throws Exception {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			DocumentImpl doc = (DocumentImpl) broker.getDocument(path);
			if (doc == null)
				throw new EXistException("Resource "
						+ path + " not found");
			if (!doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("User is not allowed to lock resource " + path);
			org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
			User lockOwner = doc.getUserLock();
			if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
				throw new PermissionDeniedException("Resource is already locked by user " +
						lockOwner.getName());
			doc.setUserLock(null);
			broker.saveCollection(doc.getCollection());
			return true;
		} finally {
			brokerPool.release(broker);
		}
	}
	
	public Hashtable summary(User user, String xpath) throws Exception {
		long startTime = System.currentTimeMillis();
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Sequence resultSeq = doQuery(user, broker, xpath, null, null, null);
			if (resultSeq == null)
				return new Hashtable();
			NodeList resultSet = (NodeList) resultSeq;
			HashMap map = new HashMap();
			HashMap doctypes = new HashMap();
			NodeProxy p;
			String docName;
			DocumentType doctype;
			NodeCount counter;
			DoctypeCount doctypeCounter;
			for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext(); ) {
				p = (NodeProxy) i.next();
				docName = p.doc.getFileName();
				doctype = p.doc.getDoctype();
				if (map.containsKey(docName)) {
					counter = (NodeCount) map.get(docName);
					counter.inc();
				} else {
					counter = new NodeCount(p.doc);
					map.put(docName, counter);
				}
				if (doctype == null)
					continue;
				if (doctypes.containsKey(doctype.getName())) {
					doctypeCounter = (DoctypeCount) doctypes.get(doctype
							.getName());
					doctypeCounter.inc();
				} else {
					doctypeCounter = new DoctypeCount(doctype);
					doctypes.put(doctype.getName(), doctypeCounter);
				}
			}
			Hashtable result = new Hashtable();
			result.put("queryTime", new Integer((int) (System
					.currentTimeMillis() - startTime)));
			result.put("hits", new Integer(resultSet.getLength()));
			Vector documents = new Vector();
			Vector hitsByDoc;
			for (Iterator i = map.values().iterator(); i.hasNext(); ) {
				counter = (NodeCount) i.next();
				hitsByDoc = new Vector();
				hitsByDoc.addElement(counter.doc.getFileName());
				hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
				hitsByDoc.addElement(new Integer(counter.count));
				documents.addElement(hitsByDoc);
			}
			result.put("documents", documents);
			Vector dtypes = new Vector();
			Vector hitsByType;
			DoctypeCount docTemp;
			for (Iterator i = doctypes.values().iterator(); i.hasNext(); ) {
				docTemp = (DoctypeCount) i.next();
				hitsByType = new Vector();
				hitsByType.addElement(docTemp.doctype.getName());
				hitsByType.addElement(new Integer(docTemp.count));
				dtypes.addElement(hitsByType);
			}
			result.put("doctypes", dtypes);
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 * Description of the Method
	 * 
	 * @param resultId
	 *                   Description of the Parameter
	 * @param user
	 *                   Description of the Parameter
	 * @return Description of the Return Value
	 * @exception EXistException
	 *                         Description of the Exception
	 */
	public Hashtable summary(User user, int resultId) throws EXistException {
		long startTime = System.currentTimeMillis();
		QueryResult qr = (QueryResult) connectionPool.resultSets.get(resultId);
		if (qr == null)
			throw new EXistException("result set unknown or timed out");
		qr.timestamp = System.currentTimeMillis();
		Hashtable result = new Hashtable();
		result.put("queryTime", new Integer((int) qr.queryTime));
		if (qr.result == null) {
			result.put("hits", new Integer(0));
			return result;
		}
		DBBroker broker = brokerPool.get(user);
		try {
			NodeList resultSet = (NodeList) qr.result;
			HashMap map = new HashMap();
			HashMap doctypes = new HashMap();
			NodeProxy p;
			String docName;
			DocumentType doctype;
			NodeCount counter;
			DoctypeCount doctypeCounter;
			for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext(); ) {
				p = (NodeProxy) i.next();
				docName = p.doc.getFileName();
				doctype = p.doc.getDoctype();
				if (map.containsKey(docName)) {
					counter = (NodeCount) map.get(docName);
					counter.inc();
				} else {
					counter = new NodeCount(p.doc);
					map.put(docName, counter);
				}
				if (doctype == null)
					continue;
				if (doctypes.containsKey(doctype.getName())) {
					doctypeCounter = (DoctypeCount) doctypes.get(doctype
							.getName());
					doctypeCounter.inc();
				} else {
					doctypeCounter = new DoctypeCount(doctype);
					doctypes.put(doctype.getName(), doctypeCounter);
				}
			}
			result.put("hits", new Integer(resultSet.getLength()));
			Vector documents = new Vector();
			Vector hitsByDoc;
			for (Iterator i = map.values().iterator(); i.hasNext(); ) {
				counter = (NodeCount) i.next();
				hitsByDoc = new Vector();
				hitsByDoc.addElement(counter.doc.getFileName());
				hitsByDoc.addElement(new Integer(counter.doc.getDocId()));
				hitsByDoc.addElement(new Integer(counter.count));
				documents.addElement(hitsByDoc);
			}
			result.put("documents", documents);
			Vector dtypes = new Vector();
			Vector hitsByType;
			DoctypeCount docTemp;
			for (Iterator i = doctypes.values().iterator(); i.hasNext(); ) {
				docTemp = (DoctypeCount) i.next();
				hitsByType = new Vector();
				hitsByType.addElement(docTemp.doctype.getName());
				hitsByType.addElement(new Integer(docTemp.count));
				dtypes.addElement(hitsByType);
			}
			result.put("doctypes", dtypes);
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Vector getIndexedElements(User user, String collectionName,
			boolean inclusive) throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName
						+ " not found");
			Occurrences occurrences[] = broker.scanIndexedElements(collection,
					inclusive);
			Vector result = new Vector(occurrences.length);
			for (int i = 0; i < occurrences.length; i++) {
				QName qname = (QName)occurrences[i].getTerm();
				Vector temp = new Vector(4);
				temp.addElement(qname.getLocalName());
				temp.addElement(qname.getNamespaceURI());
				temp.addElement(qname.getPrefix() == null ? "" : qname.getPrefix());
				temp.addElement(new Integer(occurrences[i].getOccurrences()));
				result.addElement(temp);
			}
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Vector scanIndexTerms(User user, String collectionName,
			String start, String end, boolean inclusive)
			throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName
						+ " not found");
			Occurrences occurrences[] = broker.getTextEngine().scanIndexTerms(
					user, collection, start, end, inclusive);
			Vector result = new Vector(occurrences.length);
			Vector temp;
			for (int i = 0; i < occurrences.length; i++) {
				temp = new Vector(2);
				temp.addElement(occurrences[i].getTerm().toString());
				temp.addElement(new Integer(occurrences[i].getOccurrences()));
				result.addElement(temp);
			}
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public void synchronize() {
		documentCache.clear();
	}

	public void terminate() {
		terminate = true;
	}

	private Properties getProperties(Hashtable parameters) {
		Properties properties = new Properties();
		for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry) i.next();
			properties.setProperty((String) entry.getKey(), (String) entry
					.getValue());
		}
		return properties;
	}

	private CachedQuery getCachedQuery(String query) {
		CachedQuery found = null;
		CachedQuery cached;
		for (Iterator i = cachedExpressions.iterator(); i.hasNext(); ) {
			cached = (CachedQuery) i.next();
			if (cached.queryString.equals(query)) {
				found = cached;
				found.expression.reset();
				cached.timestamp = System.currentTimeMillis();
			} else {
				// timeout: release the compiled expression
				if (System.currentTimeMillis() - cached.timestamp > 120000) {
					LOG.debug("Releasing compiled expression");
					i.remove();
				}
			}
		}
		return found;
	}

	class CachedQuery {

		PathExpr expression;
		String queryString;
		long timestamp;

		public CachedQuery(PathExpr expr, String query) {
			this.expression = expr;
			this.queryString = query;
			this.timestamp = System.currentTimeMillis();
		}
	}

	class DoctypeCount {
		int count = 1;
		DocumentType doctype;

		/**
		 * Constructor for the DoctypeCount object
		 * 
		 * @param doctype
		 *                   Description of the Parameter
		 */
		public DoctypeCount(DocumentType doctype) {
			this.doctype = doctype;
		}

		public void inc() {
			count++;
		}
	}

	class NodeCount {
		int count = 1;
		DocumentImpl doc;

		/**
		 * Constructor for the NodeCount object
		 * 
		 * @param doc
		 *                   Description of the Parameter
		 */
		public NodeCount(DocumentImpl doc) {
			this.doc = doc;
		}

		public void inc() {
			count++;
		}
	}

}
