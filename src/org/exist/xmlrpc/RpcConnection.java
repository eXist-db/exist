package org.exist.xmlrpc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.EXistException;
import org.exist.Parser;
import org.exist.dom.ArraySet;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SortedNodeSet;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.RelationalBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.exist.util.SyntaxException;
import org.exist.xpath.PathExpr;
import org.exist.xpath.Value;
import org.exist.xpath.ValueNodeSet;
import org.exist.xpath.ValueSet;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
public class RpcConnection extends Thread {

	private final static Logger LOG = Logger.getLogger(RpcConnection.class);
	protected BrokerPool brokerPool;
	protected WeakHashMap documentCache = new WeakHashMap();
	protected Parser parser = null;
	protected boolean terminate = false;
	protected DocumentBuilder docBuilder = null;
	protected RpcServer.ConnectionPool connectionPool;
	protected TreeMap tempFiles = new TreeMap();

	/**
	 *  Constructor for the RpcConnection object
	 *
	 *@param  conf                Description of the Parameter
	 *@exception  EXistException  Description of the Exception
	 */
	public RpcConnection(Configuration conf, RpcServer.ConnectionPool pool) throws EXistException {
		super();
		connectionPool = pool;
		brokerPool = BrokerPool.getInstance();
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOG.warn(e);
			throw new EXistException(e);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@exception  Exception                  Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public void createCollection(User user, String name)
		throws Exception, PermissionDeniedException {
		DBBroker broker = brokerPool.get();
		try {
			Collection current = broker.getOrCreateCollection(user, name);
			LOG.debug("creating collection " + name);
			broker.saveCollection(current);
			broker.flush();
			broker.sync();
			LOG.debug("collection " + name + " has been created");
		} catch (Exception e) {
			LOG.debug(e);
			throw e;
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  collName            Description of the Parameter
	 *@param  user                Description of the Parameter
	 *@return                     Description of the Return Value
	 *@exception  EXistException  Description of the Exception
	 */
	public String createId(User user, String collName) throws EXistException {
		DBBroker broker = brokerPool.get();
		try {
			Collection collection = broker.getCollection(collName);
			if (collection == null)
				throw new EXistException("collection " + collName + " not found!");
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

	/**
	 *  Description of the Method
	 *
	 *@param  xpath          Description of the Parameter
	 *@param  user           Description of the Parameter
	 *@return                Description of the Return Value
	 *@exception  Exception  Description of the Exception
	 */
	protected Value doQuery(User user, String xpath, DocumentSet docs, NodeSet context)
		throws Exception {
		XPathLexer lexer = new XPathLexer(new StringReader(xpath));
		XPathParser parser = new XPathParser(brokerPool, user, lexer);
		PathExpr expr = new PathExpr(brokerPool);
		parser.expr(expr);
		LOG.info("query: " + expr.pprint());
		long start = System.currentTimeMillis();
		if (parser.foundErrors())
			throw new EXistException(parser.getErrorMsg());
		DocumentSet ndocs = (docs == null ? expr.preselect() : expr.preselect(docs));
		if (ndocs.getLength() == 0)
			return null;
		LOG.info("pre-select took " + (System.currentTimeMillis() - start) + "ms.");
		Value result = expr.eval(ndocs, context, null);
		LOG.info("query took " + (System.currentTimeMillis() - start) + "ms.");
		return result;
	}

	public int executeQuery(User user, String xpath) throws Exception {
		long startTime = System.currentTimeMillis();
		LOG.debug("query: " + xpath);
		Value resultValue = doQuery(user, xpath, null, null);
		QueryResult qr = new QueryResult(resultValue, (System.currentTimeMillis() - startTime));
		connectionPool.resultSets.put(qr.hashCode(), qr);
		return qr.hashCode();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  message  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected String formatErrorMsg(String message) {
		return formatErrorMsg("error", message);
	}

	protected String formatErrorMsg(String type, String message) {
		StringBuffer buf = new StringBuffer();
		buf.append("<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
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

	public Hashtable getCollectionDesc(User user, String rootCollection) throws Exception {
		DBBroker broker = brokerPool.get();
		try {
			if (rootCollection == null)
				rootCollection = "/";

			Collection collection = broker.getCollection(rootCollection);
			if (collection == null)
				throw new EXistException("collection " + rootCollection + " not found!");
			Hashtable desc = new Hashtable();
			Vector docs = new Vector();
			Vector collections = new Vector();
			if (collection.getPermissions().validate(user, Permission.READ)) {
				DocumentImpl doc;
				for (Iterator i = collection.iterator(); i.hasNext();) {
					doc = (DocumentImpl) i.next();
					docs.addElement(doc.getFileName());
				}
				for (Iterator i = collection.collectionIterator(); i.hasNext();)
					collections.addElement((String) i.next());
			}
			desc.put("collections", collections);
			desc.put("documents", docs);
			desc.put("name", collection.getName());
			return desc;
		} finally {
			brokerPool.release(broker);
		}
	}

	public String getDocument(
		User user,
		String name,
		boolean prettyPrint,
		String encoding,
		String stylesheet)
		throws Exception {
		long start = System.currentTimeMillis();
		DBBroker broker = brokerPool.get();
		try {
			DocumentImpl doc = (DocumentImpl) broker.getDocument(user, name);
			if (doc == null) {
				LOG.debug("document " + name + " not found!");
				throw new EXistException("document not found");
			}
			broker.setRetrvMode(RelationalBroker.PRELOAD);
			Serializer serializer = broker.getSerializer();
			serializer.setEncoding(encoding);
			if (stylesheet != null) {
				if (!stylesheet.startsWith("/")) {
					// make path relative to current collection
					String collection;
					if (doc.getCollection() != null)
						collection = doc.getCollection().getName();
					else {
						int cp = doc.getFileName().lastIndexOf("/");
						collection = (cp > 0) ? doc.getFileName().substring(0, cp) : "/";
					}
					stylesheet =
						(collection.equals("/") ? '/' + stylesheet : collection + '/' + stylesheet);
				}
				serializer.setStylesheet(stylesheet);
			}
			String xml;
			serializer.setIndent(prettyPrint);
			xml = serializer.serialize(doc);

			broker.setRetrvMode(RelationalBroker.SINGLE);
			return xml;
		} catch (NoSuchMethodError nsme) {
			nsme.printStackTrace();
			return null;
		} finally {
			brokerPool.release(broker);
		}
	}

	public int xupdate(User user, String collectionName, String xupdate)
		throws EXistException, PermissionDeniedException, SAXException {
		DBBroker broker = brokerPool.get();
		try {
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName + " not found");
			DocumentSet docs = collection.allDocs(user, true);
			XUpdateProcessor processor = new XUpdateProcessor(brokerPool, user, docs);
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
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
		throws EXistException, PermissionDeniedException, SAXException {
		DBBroker broker = brokerPool.get();
		try {
			Document doc = broker.getDocument(resource);
			if (doc == null)
				throw new EXistException("document " + resource + " not found");
			DocumentSet docs = new DocumentSet();
			docs.add(doc);
			XUpdateProcessor processor = new XUpdateProcessor(brokerPool, user, docs);
			Modification modifications[] =
				processor.parse(new InputSource(new StringReader(xupdate)));
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
	 *  Gets the documentListing attribute of the RpcConnection object
	 *
	 *@param  user                Description of the Parameter
	 *@return                     The documentListing value
	 *@exception  EXistException  Description of the Exception
	 */
	public Vector getDocumentListing(User user) throws EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			DocumentSet docs = broker.getAllDocuments();
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
	 *  Gets the documentListing attribute of the RpcConnection object
	 *
	 *@param  collection                     Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The documentListing value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Vector getDocumentListing(User user, String name)
		throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
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
			for (Iterator i = collection.iterator(); i.hasNext();) {
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
			broker = brokerPool.get();
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			if (!collection.getPermissions().validate(user, Permission.READ))
				throw new PermissionDeniedException("not allowed to read collection " + name);
			Hashtable result = new Hashtable(collection.getDocumentCount());
			if (collection == null)
				return result;
			DocumentImpl doc;
			Permission perm;
			Vector tmp;
			String docName;
			for (Iterator i = collection.iterator(); i.hasNext();) {
				doc = (DocumentImpl) i.next();
				perm = doc.getPermissions();
				tmp = new Vector(3);
				tmp.addElement(perm.getOwner());
				tmp.addElement(perm.getOwnerGroup());
				tmp.addElement(new Integer(perm.getPermissions()));
				docName = doc.getFileName().substring(doc.getFileName().lastIndexOf('/') + 1);
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
			broker = brokerPool.get();
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			if (!collection.getPermissions().validate(user, Permission.READ))
				throw new PermissionDeniedException("not allowed to read collection " + name);
			Hashtable result = new Hashtable(collection.getChildCollectionCount());
			if (collection == null)
				return result;
			String child, path;
			Collection childColl;
			Permission perm;
			Vector tmp;
			for (Iterator i = collection.collectionIterator(); i.hasNext();) {
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

	/**
	 *  Gets the hits attribute of the RpcConnection object
	 *
	 *@param  resultId            Description of the Parameter
	 *@param  user                Description of the Parameter
	 *@return                     The hits value
	 *@exception  EXistException  Description of the Exception
	 */
	public int getHits(User user, int resultId) throws EXistException {
		QueryResult qr = (QueryResult) connectionPool.resultSets.get(resultId);
		if (qr == null)
			throw new EXistException("result set unknown or timed out");
		if (qr.result == null)
			return 0;
		switch (qr.result.getType()) {
			case Value.isNodeList :
				return qr.result.getNodeList().getLength();
			default :
				return qr.result.getValueSet().getLength();
		}
	}

	/**
	 *  Get permissions for the given collection or resource
	 *
	 *@param  name                           Description of the Parameter
	 *@param  user                           Description of the Parameter
	 *@return                                The permissions value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Hashtable getPermissions(User user, String name)
		throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			User admin = brokerPool.getSecurityManager().getUser("admin");
			if (!name.startsWith("/"))
				name = '/' + name;
			if (!name.startsWith("/db"))
				name = "/db" + name;
			Collection collection = broker.getCollection(name);
			Permission perm = null;
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(admin, name);
				if (doc == null)
					throw new EXistException("document or collection " + name + " not found");
				perm = doc.getPermissions();
			} else {
				perm = collection.getPermissions();
				LOG.debug("collection found finally");
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

	/**
	 *  Gets the permissions attribute of the RpcConnection object
	 *
	 *@param  user                           Description of the Parameter
	 *@param  name                           Description of the Parameter
	 *@return                                The permissions value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public Hashtable getUser(User user, String name)
		throws EXistException, PermissionDeniedException {
		User u = brokerPool.getSecurityManager().getUser(name);
		if (u == null)
			throw new EXistException("user " + name + " does not exist");
		Hashtable tab = new Hashtable();
		tab.put("name", u.getName());
		Vector groups = new Vector();
		for (Iterator i = u.getGroups(); i.hasNext();)
			groups.addElement(i.next());
		tab.put("groups", groups);
		if (u.getHome() != null)
			tab.put("home", u.getHome());
		return tab;
	}

	public Vector getUsers(User user) throws EXistException, PermissionDeniedException {
		User users[] = brokerPool.getSecurityManager().getUsers();
		Vector r = new Vector();
		for (int i = 0; i < users.length; i++) {
			final Hashtable tab = new Hashtable();
			tab.put("name", users[i].getName());
			Vector groups = new Vector();
			for (Iterator j = users[i].getGroups(); j.hasNext();)
				groups.addElement(j.next());
			tab.put("groups", groups);
			if (users[i].getHome() != null)
				tab.put("home", users[i].getHome());
			r.addElement(tab);
		}
		return r;
	}

	public boolean hasDocument(User user, String name) throws Exception {
		DBBroker broker = brokerPool.get();
		boolean r = (broker.getDocument(name) != null);
		brokerPool.release(broker);
		return r;
	}

	public boolean parse(User user, byte[] xml, String docName, boolean replace) throws Exception {
		DBBroker broker = null;
		DocumentImpl doc;
		try {
			broker = brokerPool.get();
			long startTime = System.currentTimeMillis();
			if (parser == null)
				parser = new Parser(broker, user, replace);
			else {
				parser.setBroker(broker);
				parser.setUser(user);
				parser.setOverwrite(replace);
			}
			doc = parser.parse(xml, docName);
			broker.flush();
			//LOG.debug( "sync" );
			//broker.sync();
			LOG.debug(
				"parsing " + docName + " took " + (System.currentTimeMillis() - startTime) + "ms.");
			return doc != null;
		} catch (Exception e) {
			LOG.debug(e);
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
	public boolean parseLocal(User user, String localFile, String docName, boolean replace) 
		throws EXistException, PermissionDeniedException, SAXException {
		File file = new File(localFile);
		if(!file.canRead())
			throw new EXistException("unable to read file " + localFile);
		DBBroker broker = null;
		DocumentImpl doc = null;
		try {
			broker = brokerPool.get();
			if (parser == null)
				parser = new Parser(broker, user, replace);
			else {
				parser.setBroker(broker);
				parser.setUser(user);
				parser.setOverwrite(replace);
			}
			doc = parser.parse(file, docName);
			broker.flush();
		} catch (IOException e) {
			throw new EXistException(e);
		} finally {
			brokerPool.release(broker);
		}
		file.delete();
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

	protected String printAll(
		DBBroker broker,
		NodeList resultSet,
		int howmany,
		int start,
		boolean prettyPrint,
		long queryTime,
		String encoding)
		throws Exception {
		if (resultSet.getLength() == 0)
			return "<?xml version=\"1.0\"?>\n"
				+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
				+ "hitCount=\"0\"/>";
		Node n;
		Node nn;
		Element temp;
		DocumentImpl owner;
		if (howmany > resultSet.getLength() || howmany == 0)
			howmany = resultSet.getLength();

		if (start < 1 || start > resultSet.getLength())
			throw new EXistException("start parameter out of range");
		Serializer serializer = broker.getSerializer();
		serializer.setEncoding(encoding);
		if (prettyPrint) {
			StringWriter sout = new StringWriter();
			OutputFormat format = new OutputFormat("xml", encoding, true);
			format.setOmitXMLDeclaration(false);
			format.setOmitComments(false);
			format.setLineWidth(60);
			XMLSerializer xmlout = new XMLSerializer(sout, format);
			serializer.setContentHandler(xmlout);
			serializer.setLexicalHandler(xmlout);
			serializer.toSAX((NodeSet) resultSet, start, howmany, queryTime);
			return sout.toString();
		} else
			return serializer.serialize((NodeSet) resultSet, start, howmany, queryTime);
	}

	protected String printValues(
		ValueSet resultSet,
		int howmany,
		int start,
		boolean prettyPrint,
		String encoding)
		throws Exception {
		if (resultSet.getLength() == 0)
			return "<?xml version=\"1.0\" encoding=\""
				+ encoding
				+ "\"?>\n"
				+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
				+ "hitCount=\"0\"/>";
		if (howmany > resultSet.getLength() || howmany == 0)
			howmany = resultSet.getLength();

		if (start < 1 || start > resultSet.getLength())
			throw new EXistException("start parameter out of range");
		Value value;
		Document dest = docBuilder.newDocument();
		Element root =
			dest.createElementNS("http://exist.sourceforge.net/NS/exist", "exist:result");
		root.setAttribute("xmlns:exist", "http://exist.sourceforge.net/NS/exist");
		root.setAttribute("hitCount", Integer.toString(resultSet.getLength()));
		dest.appendChild(root);

		Element temp;
		for (int i = start - 1; i < start + howmany - 1; i++) {
			value = resultSet.get(i);
			switch (value.getType()) {
				case Value.isNumber :
					temp =
						dest.createElementNS(
							"http://exist.sourceforge.net/NS/exist",
							"exist:number");
					break;
				case Value.isString :
					temp =
						dest.createElementNS(
							"http://exist.sourceforge.net/NS/exist",
							"exist:string");
					break;
				case Value.isBoolean :
					temp =
						dest.createElementNS(
							"http://exist.sourceforge.net/NS/exist",
							"exist:boolean");
					break;
				default :
					LOG.debug("unknown type: " + value.getType());
					continue;
			}
			temp.appendChild(dest.createTextNode(value.getStringValue()));
			root.appendChild(temp);
		}
		StringWriter sout = new StringWriter();
		OutputFormat format = new OutputFormat("xml", encoding, prettyPrint);
		format.setOmitXMLDeclaration(false);
		format.setOmitComments(false);
		format.setLineWidth(60);
		XMLSerializer xmlout = new XMLSerializer(sout, format);
		try {
			xmlout.serialize(dest);
		} catch (IOException ioe) {
			LOG.warn(ioe);
			throw ioe;
		}
		return sout.toString();
	}

	public String query(
		User user,
		String xpath,
		int howmany,
		int start,
		boolean prettyPrint,
		boolean summary,
		String encoding)
		throws Exception {
		return query(user, xpath, howmany, start, prettyPrint, summary, encoding, null);
	}

	public String query(
		User user,
		String xpath,
		int howmany,
		int start,
		boolean prettyPrint,
		boolean summary,
		String encoding,
		String sortExpr)
		throws Exception {
		long startTime = System.currentTimeMillis();
		Value resultValue = doQuery(user, xpath, null, null);
		if (resultValue == null)
			return "<?xml version=\"1.0\"?>\n"
				+ "<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" "
				+ "hitCount=\"0\"/>";
		String result;
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			broker.setRetrvMode(RelationalBroker.PRELOAD);
			switch (resultValue.getType()) {
				case Value.isNodeList :
					NodeList resultSet = resultValue.getNodeList();
					if (sortExpr != null) {
						SortedNodeSet sorted = new SortedNodeSet(brokerPool, user, sortExpr);
						sorted.addAll(resultSet);
						resultSet = sorted;
					}
					result =
						printAll(
							broker,
							resultSet,
							howmany,
							start,
							prettyPrint,
							(System.currentTimeMillis() - startTime),
							encoding);
					break;
				default :
					ValueSet valueSet = resultValue.getValueSet();
					result = printValues(valueSet, howmany, start, prettyPrint, encoding);
					break;
			}
			broker.setRetrvMode(RelationalBroker.SINGLE);
		} finally {
			brokerPool.release(broker);
		}
		return result;
	}

	public Vector query(User user, String xpath) throws Exception {
		return query(user, xpath, null, null);
	}

	public Vector query(User user, String xpath, String docName, String s_id) throws Exception {
		long startTime = System.currentTimeMillis();
		Vector result = new Vector();
		NodeSet nodes = null;
		DocumentSet docs = null;
		if (docName != null && s_id != null) {
			long id = Long.parseLong(s_id);
			DocumentImpl doc;
			if (!documentCache.containsKey(docName)) {
				DBBroker broker = null;
				try {
					broker = brokerPool.get();
					doc = (DocumentImpl) broker.getDocument(docName);
					documentCache.put(docName, doc);
				} finally {
					brokerPool.release(broker);
				}
			} else
				doc = (DocumentImpl) documentCache.get(docName);
			NodeProxy node = new NodeProxy(doc, id);
			nodes = new ArraySet(1);
			nodes.add(node);
			docs = new DocumentSet();
			docs.add(node.doc);
		}
		Value resultValue = doQuery(user, xpath, docs, nodes);
		if (resultValue == null)
			return result;
		switch (resultValue.getType()) {
			case Value.isNodeList :
				NodeList resultSet = resultValue.getNodeList();
				NodeProxy p;
				Vector entry;
				for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
					p = (NodeProxy) i.next();
					entry = new Vector();
					entry.addElement(p.doc.getFileName());
					entry.addElement(Long.toString(p.getGID()));
					result.addElement(entry);
				}
				break;
			default :
				ValueSet valueSet = resultValue.getValueSet();
				Value val;
				for (int i = 0; i < valueSet.getLength(); i++) {
					val = valueSet.get(i);
					result.addElement(val.getStringValue());
				}
		}
		return result;
	}

	public Hashtable queryP(User user, String xpath, String docName, String s_id, String sortBy)
		throws Exception {
		long startTime = System.currentTimeMillis();
		Hashtable ret = new Hashtable();
		Vector result = new Vector();
		NodeSet nodes = null;
		DocumentSet docs = null;
		if (docName != null && s_id != null) {
			long id = Long.parseLong(s_id);
			DocumentImpl doc;
			if (!documentCache.containsKey(docName)) {
				DBBroker broker = null;
				try {
					broker = brokerPool.get();
					doc = (DocumentImpl) broker.getDocument(docName);
					documentCache.put(docName, doc);
				} finally {
					brokerPool.release(broker);
				}
			} else
				doc = (DocumentImpl) documentCache.get(docName);
			NodeProxy node = new NodeProxy(doc, id);
			nodes = new ArraySet(1);
			nodes.add(node);
			docs = new DocumentSet();
			docs.add(node.doc);
		}
		Value resultValue = doQuery(user, xpath, docs, nodes);
		if (resultValue == null)
			return ret;
		switch (resultValue.getType()) {
			case Value.isNodeList :
				NodeSet resultSet = (NodeSet) resultValue.getNodeList();
				if (sortBy != null) {
					SortedNodeSet sorted = new SortedNodeSet(brokerPool, user, sortBy);
					sorted.addAll(resultSet);
					resultSet = sorted;
					resultValue = new ValueNodeSet(sorted);
				}
				NodeProxy p;
				Vector entry;
				for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
					p = (NodeProxy) i.next();
					entry = new Vector();
					entry.addElement(p.doc.getFileName());
					entry.addElement(Long.toString(p.getGID()));
					result.addElement(entry);
				}
				break;
			default :
				ValueSet valueSet = resultValue.getValueSet();
				Value val;
				for (int i = 0; i < valueSet.getLength(); i++) {
					val = valueSet.get(i);
					result.addElement(val.getStringValue());
				}
		}
		QueryResult qr = new QueryResult(resultValue, (System.currentTimeMillis() - startTime));
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
		DBBroker broker = brokerPool.get();
		try {
			if (broker.getDocument(user, docName) == null)
				throw new EXistException("document [" + docName + "] not found!");
			broker.removeDocument(user, docName);
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean removeCollection(User user, String name) throws Exception {
		DBBroker broker = brokerPool.get();
		try {
			if (broker.getCollection(name) == null)
				return false;
			LOG.debug("removing collection " + name);
			if (parser != null)
				parser.collection = null;
			return broker.removeCollection(user, name);
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean removeUser(User user, String name)
		throws EXistException, PermissionDeniedException {
		org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
		if (!manager.hasAdminPrivileges(user))
			throw new PermissionDeniedException("you are not allowed to remove users");

		manager.deleteUser(name);
		return true;
	}

	public String retrieve(
		User user,
		String docName,
		String s_id,
		boolean prettyPrint,
		String encoding)
		throws Exception {
		DBBroker broker = brokerPool.get();
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
			serializer.setEncoding(encoding);
			serializer.setIndent(prettyPrint);
			return serializer.serialize(node);
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  resultId       Description of the Parameter
	 *@param  num            Description of the Parameter
	 *@param  prettyPrint    Description of the Parameter
	 *@param  encoding       Description of the Parameter
	 *@param  user           Description of the Parameter
	 *@return                Description of the Return Value
	 *@exception  Exception  Description of the Exception
	 */
	public String retrieve(User user, int resultId, int num, boolean prettyPrint, String encoding)
		throws Exception {
		System.out.println("pretty-print = " + prettyPrint);
		DBBroker broker = brokerPool.get();
		try {
			QueryResult qr = (QueryResult) connectionPool.resultSets.get(resultId);
			if (qr == null)
				throw new EXistException("result set unknown or timed out");
			switch (qr.result.getType()) {
				case Value.isNodeList :
					NodeList resultSet = qr.result.getNodeList();
					NodeProxy proxy = ((NodeSet) resultSet).get(num);
					if (proxy == null)
						throw new EXistException("index out of range");
					Serializer serializer = broker.getSerializer();
					serializer.setEncoding(encoding);
					serializer.setIndent(prettyPrint);
					return serializer.serialize(proxy);
				default :
					ValueSet valueSet = qr.result.getValueSet();
					Value val = valueSet.get(num);
					return val.getStringValue();
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

	/**
	 *  Sets the permissions attribute of the RpcConnection object
	 *
	 *@param  user                           The new permissions value
	 *@param  resource                       The new permissions value
	 *@param  permissions                    The new permissions value
	 *@param  owner                          The new permissions value
	 *@param  ownerGroup                     The new permissions value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean setPermissions(
		User user,
		String resource,
		String owner,
		String ownerGroup,
		String permissions)
		throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
			Collection collection = broker.getCollection(resource);
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(user, resource);
				if (doc == null)
					throw new EXistException("document or collection " + resource + " not found");
				LOG.debug("changing permissions on document " + resource);
				Permission perm = doc.getPermissions();
				if (perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)) {
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
					throw new PermissionDeniedException("not allowed to change permissions");
			} else {
				LOG.debug("changing permissions on collection " + resource);
				Permission perm = collection.getPermissions();
				if (perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)) {
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
					throw new PermissionDeniedException("not allowed to change permissions");
			}
		} catch (SyntaxException e) {
			throw new EXistException(e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	public boolean setPermissions(
		User user,
		String resource,
		String owner,
		String ownerGroup,
		int permissions)
		throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
			Collection collection = broker.getCollection(resource);
			if (collection == null) {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(user, resource);
				if (doc == null)
					throw new EXistException("document or collection " + resource + " not found");
				LOG.debug("changing permissions on document " + resource);
				Permission perm = doc.getPermissions();
				if (perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)) {
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					perm.setPermissions(permissions);
					broker.saveCollection(doc.getCollection());
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException("not allowed to change permissions");
			} else {
				LOG.debug("changing permissions on collection " + resource);
				Permission perm = collection.getPermissions();
				if (perm.getOwner().equals(user.getName()) || manager.hasAdminPrivileges(user)) {
					perm.setPermissions(permissions);
					if (owner != null) {
						perm.setOwner(owner);
						perm.setGroup(ownerGroup);
					}
					broker.saveCollection(collection);
					broker.flush();
					return true;
				} else
					throw new PermissionDeniedException("not allowed to change permissions");
			}
		} catch (PermissionDeniedException e) {
			throw new EXistException(e.getMessage());
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 *  Sets the password attribute of the RpcConnection object
	 *
	 *@param  user                           The new password value
	 *@param  name                           The new password value
	 *@param  passwd                         The new password value
	 *@param  groups                         The new user value
	 *@return                                Description of the Return Value
	 *@exception  EXistException             Description of the Exception
	 *@exception  PermissionDeniedException  Description of the Exception
	 */
	public boolean setUser(User user, String name, String passwd, Vector groups, String home)
		throws EXistException, PermissionDeniedException {
		org.exist.security.SecurityManager manager = brokerPool.getSecurityManager();
		User u;
		if (!manager.hasUser(name)) {
			if (!manager.hasAdminPrivileges(user))
				throw new PermissionDeniedException("not allowed to create user");
			u = new User(name);
			u.setPasswordDigest(passwd);
		} else {
			u = manager.getUser(name);
			if (!(u.getName().equals(user.getName()) || manager.hasAdminPrivileges(user)))
				throw new PermissionDeniedException("you are not allowed to change this user");
			u.setPasswordDigest(passwd);
		}
		String g;
		for (Iterator i = groups.iterator(); i.hasNext();) {
			g = (String) i.next();
			if (!u.hasGroup(g))
				u.addGroup(g);
		}
		if (home != null)
			u.setHome(home);
		manager.setUser(u);
		return true;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  xpath          Description of the Parameter
	 *@param  user           Description of the Parameter
	 *@return                Description of the Return Value
	 *@exception  Exception  Description of the Exception
	 */
	public Hashtable summary(User user, String xpath) throws Exception {
		long startTime = System.currentTimeMillis();
		Value resultValue = doQuery(user, xpath, null, null);
		if (resultValue == null)
			return new Hashtable();
		NodeList resultSet = resultValue.getNodeList();
		HashMap map = new HashMap();
		HashMap doctypes = new HashMap();
		NodeProxy p;
		String docName;
		DocumentType doctype;
		NodeCount counter;
		DoctypeCount doctypeCounter;
		for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
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
				doctypeCounter = (DoctypeCount) doctypes.get(doctype.getName());
				doctypeCounter.inc();
			} else {
				doctypeCounter = new DoctypeCount(doctype);
				doctypes.put(doctype.getName(), doctypeCounter);
			}
		}
		Hashtable result = new Hashtable();
		result.put("queryTime", new Integer((int) (System.currentTimeMillis() - startTime)));
		result.put("hits", new Integer(resultSet.getLength()));
		Vector documents = new Vector();
		Vector hitsByDoc;
		for (Iterator i = map.values().iterator(); i.hasNext();) {
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
		for (Iterator i = doctypes.values().iterator(); i.hasNext();) {
			docTemp = (DoctypeCount) i.next();
			hitsByType = new Vector();
			hitsByType.addElement(docTemp.doctype.getName());
			hitsByType.addElement(new Integer(docTemp.count));
			dtypes.addElement(hitsByType);
		}
		result.put("doctypes", dtypes);
		return result;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  resultId            Description of the Parameter
	 *@param  user                Description of the Parameter
	 *@return                     Description of the Return Value
	 *@exception  EXistException  Description of the Exception
	 */
	public Hashtable summary(User user, int resultId) throws EXistException {
		long startTime = System.currentTimeMillis();
		QueryResult qr = (QueryResult) connectionPool.resultSets.get(resultId);
		if (qr == null)
			throw new EXistException("result set unknown or timed out");
		Hashtable result = new Hashtable();
		result.put("queryTime", new Integer((int) qr.queryTime));
		if (qr.result == null) {
			result.put("hits", new Integer(0));
			return result;
		}
		DBBroker broker = brokerPool.get();
		try {
			NodeList resultSet = qr.result.getNodeList();
			HashMap map = new HashMap();
			HashMap doctypes = new HashMap();
			NodeProxy p;
			String docName;
			DocumentType doctype;
			NodeCount counter;
			DoctypeCount doctypeCounter;
			for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
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
					doctypeCounter = (DoctypeCount) doctypes.get(doctype.getName());
					doctypeCounter.inc();
				} else {
					doctypeCounter = new DoctypeCount(doctype);
					doctypes.put(doctype.getName(), doctypeCounter);
				}
			}
			result.put("hits", new Integer(resultSet.getLength()));
			Vector documents = new Vector();
			Vector hitsByDoc;
			for (Iterator i = map.values().iterator(); i.hasNext();) {
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
			for (Iterator i = doctypes.values().iterator(); i.hasNext();) {
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

	public Vector getIndexedElements(User user, String collectionName, boolean inclusive)
		throws EXistException, PermissionDeniedException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName + " not found");
			Occurrences occurrences[] = broker.scanIndexedElements(user, collection, inclusive);
			Vector result = new Vector(occurrences.length);
			Vector temp;
			for (int i = 0; i < occurrences.length; i++) {
				temp = new Vector(2);
				temp.addElement(occurrences[i].getTerm());
				temp.addElement(new Integer(occurrences[i].getOccurrences()));
				result.addElement(temp);
			}
			return result;
		} finally {
			brokerPool.release(broker);
		}
	}

	public Vector scanIndexTerms(
		User user,
		String collectionName,
		String start,
		String end,
		boolean inclusive)
		throws PermissionDeniedException, EXistException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			Collection collection = broker.getCollection(collectionName);
			if (collection == null)
				throw new EXistException("collection " + collectionName + " not found");
			Occurrences occurrences[] =
				broker.getTextEngine().scanIndexTerms(user, collection, start, end, inclusive);
			Vector result = new Vector(occurrences.length);
			Vector temp;
			for (int i = 0; i < occurrences.length; i++) {
				temp = new Vector(2);
				temp.addElement(occurrences[i].getTerm());
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

	/**
	 *  Description of the Class
	 *
	 *@author     wolf
	 *@created    28. Mai 2002
	 */
	class DoctypeCount {
		int count = 1;
		DocumentType doctype;

		/**
		 *  Constructor for the DoctypeCount object
		 *
		 *@param  doctype  Description of the Parameter
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
		 *  Constructor for the NodeCount object
		 *
		 *@param  doc  Description of the Parameter
		 */
		public NodeCount(DocumentImpl doc) {
			this.doc = doc;
		}

		public void inc() {
			count++;
		}
	}

}
