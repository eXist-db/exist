/*
 * eXist Open Source Native XML Database Copyright (C) 2001, Wolfgang M. Meier
 * (meier@ifs.tu-darmstadt.de)
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Library General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import org.apache.xmlrpc.Base64;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xpath.PathExpr;
import org.exist.xpath.XPathException;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import antlr.collections.AST;

public class HttpServerConnection extends Thread {

	private final static String NS = "http://exist.sourceforge.net/NS/exist";
	private final static int GET = 1;
	private final static int HEAD = 2;
	private final static int POST = 3;
	private final static int PUT = 4;

	private final static int OK = 0;
	private final static int WRONG_REQUEST = 1;
	private final static int PARSE_ERROR = 2;
	private final static int DOCUMENT_NOT_FOUND = 3;
	private final static int SYNTAX_ERROR = 4;
	private final static int OUTPUT_ERROR = 5;
	private final static int UNKNOWN_ERROR = 6;

	private final static int HTTP_OK = 200;
	private final static int HTTP_BAD_REQUEST = 400;
	private final static int HTTP_FORBIDDEN = 403;
	private final static int HTTP_NOT_FOUND = 404;
	
	private final static String stdHeaders = "Allow: POST GET PUT\n"
			+ "Server: eXist\n" + "Cache-control: no-cache\n";
	
	private final static Properties defaultProperties = new Properties();
	
	static {
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
		defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
	}
	
	protected DBBroker broker = null;
	protected Configuration config;
	protected DocumentBuilder docBuilder = null;
	protected HttpServer.ConnectionPool pool;
	protected SAXParser sax = null;
	protected Socket sock = null;
	protected boolean terminate = false;
	protected String tmpDir = null;
	protected User user = null;
	protected String xslStyle = null;

	public HttpServerConnection(Configuration config,
			HttpServer.ConnectionPool pool) {
		this.config = config;
		this.pool = pool;
		if ((tmpDir = (String) config.getProperty("tmpDir")) == null)
			tmpDir = "/tmp";
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
		docFactory.setNamespaceAware(true);
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		try {
			docBuilder = docFactory.newDocumentBuilder();
			sax = saxFactory.newSAXParser();
		} catch (ParserConfigurationException e) {
			HttpServer.LOG.warn(e);
		} catch (SAXException saxe) {
			HttpServer.LOG.warn(saxe);
		}
	}

	private User checkUser(BrokerPool pool, String username, String password) {
		// check user
		user = pool.getSecurityManager().getUser(username);
		if (user == null)
			return null;
		if (!user.validate(password))
			return null;
		return user;
	}
	
	protected void doGet(HashMap parameters, String name, int len) {
		int howmany = 10;
		int start = 1;
		boolean summary = false;
		Properties outputProperties = new Properties();
		String query = (String) parameters.get("_xpath");
		String p_howmany = (String) parameters.get("_howmany");
		if (p_howmany != null) {
			try {
				howmany = Integer.parseInt(p_howmany);
			} catch (NumberFormatException nfe) {
			}
		}
		String p_start = (String) parameters.get("_start");
		if (p_start != null) {
			try {
				start = Integer.parseInt(p_start);
			} catch (NumberFormatException nfe) {
			}
		}
		String option;
		if ((option = (String) parameters.get("_summarize")) != null)
			summary = option.equals("yes");
		if ((option = (String) parameters.get("_indent")) != null)
			outputProperties.setProperty(OutputKeys.INDENT, option);
		String stylesheet;
		if((stylesheet = (String) parameters.get("_xsl"))!= null)
			outputProperties.setProperty(EXistOutputKeys.STYLESHEET, stylesheet);
		String encoding;
		if((encoding = (String) parameters.get("_encoding")) !=null)
			outputProperties.setProperty(OutputKeys.ENCODING, encoding);
		else
			encoding = "UTF-8";
		String data;
		try {
			if (query != null) {
				data = search(query, name, howmany, start, summary, outputProperties);
			} else {
				if (name.charAt(0) == '/')
					name = name.substring(1);
				if (name.endsWith("/")) {
					// print collection contents
					DocumentSet docs = broker.getDocumentsByCollection(name,
							new DocumentSet());
					String[] names = docs.getNames();
					data = printCollection(name, names);
				} else {
					DocumentImpl d = (DocumentImpl) broker.getDocument(name);
					if (d == null)
						data = formatErrorMsg("document " + name
								+ " not found!", DOCUMENT_NOT_FOUND);
					else {
						Serializer serializer = broker.getSerializer();
						serializer.reset();
						if (stylesheet != null) {
							serializer.setStylesheet(d, stylesheet);
						}
						try {
							serializer.setProperties(outputProperties);
							data = serializer.serialize(d);
						} catch (SAXException saxe) {
							HttpServer.LOG.warn(saxe);
							data = formatErrorMsg(
									"error while serializing xml: "
											+ saxe.toString(), OUTPUT_ERROR);
						}
					}
				}
			}
		} catch (PermissionDeniedException e) {
			data = formatErrorMsg("permission denied: " + e.getMessage(),
					OUTPUT_ERROR);
		}
		try {
			byte[] resultData = data.getBytes(encoding);
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes("HTTP/1.0 200 OK\n");
			if (stylesheet == null)
				out.writeBytes("Content-Type: text/xml\n");
			else
				out.writeBytes("Content-Type: text/html\n");
			out.writeBytes(stdHeaders + "Content-Length: " + resultData.length
					+ "\n\n");
			out.write(resultData, 0, resultData.length);
			out.flush();
			out.close();
		} catch (IOException io) {
			HttpServer.LOG.warn(io);
		}
	}

	protected String doPost(String request, String path) {
		HttpServer.LOG.debug("Processing request " + request);
		boolean indent = true;
		boolean summary = false;
		int howmany = 10;
		int start = 1;
		Properties outputProperties = null;
		String query = null;
		String result = null;
		try {
			InputSource src = new InputSource(new StringReader(request));
			Document doc = docBuilder.parse(src);
			Element root = doc.getDocumentElement();
			HttpServer.LOG.debug(root.getNodeName());
			if(root.getNamespaceURI().equals(NS)) {
				if(root.getLocalName().equals("query")) {
					// process <query>xpathQuery</query>
					String option = root.getAttribute("start");
					if(option != null)
						try {
							start = Integer.parseInt(option);
						} catch(NumberFormatException e) {
						}
					option = root.getAttribute("max");
					if(option != null)
						try {
							howmany = Integer.parseInt(option);
						} catch(NumberFormatException e) {
						}
						
					NodeList children =root.getChildNodes();
					for(int i = 0;i < children.getLength(); i++) {
						Node child = children.item(i);
						if(child.getNodeType() == Node.ELEMENT_NODE && child.getNamespaceURI().equals(NS)) {
							if(child.getLocalName().equals("text")) {
								StringBuffer buf =new StringBuffer();
								Node next = child.getFirstChild();
								while(next != null) {
									if(next.getNodeType() == Node.TEXT_NODE)
										buf.append(next.getNodeValue());
									next = next.getNextSibling();
								}
								query =buf.toString();
							} else if(child.getLocalName().equals("properties")) {
								outputProperties = new Properties(defaultProperties);
								Node node = child.getFirstChild();
								while(node !=null) {
									if(node.getNodeType() == Node.ELEMENT_NODE && 
											node.getNamespaceURI().equals(NS) &&
											node.getLocalName().equals("property")) {
										Element property = (Element)node;
										String key = property.getAttribute("name");
										String value = property.getAttribute("value");
										if(key != null && value != null)
											outputProperties.setProperty(key, value);
									}
									node = node.getNextSibling();
								}
							}
						}
					}
				}
				// execute query
				if (query != null)
					result = search(query, path, howmany, start, false, outputProperties);
			} else
				result = formatErrorMsg("not a valid request", WRONG_REQUEST);
		} catch (SAXException e) {
			HttpServer.LOG.debug("SAX exception while parsing request: " + request, e);
			result = formatErrorMsg("SAX exception while parsing request: ", PARSE_ERROR);
		} catch (IOException e) {
			HttpServer.LOG.debug("IO exception while parsing request: " + request, e);
			result = formatErrorMsg("IO exception while parsing request: ", PARSE_ERROR);
		}
		return result;
	}

	protected void errorReply(int code) {
		errorReply(code, null);
	}

	protected void errorReply(int code, String message) {
		StringBuffer content = new StringBuffer();
		content.append("<h1>HTTP/1.0 400 Bad Request</h1>\n");
		if (message != null) {
			content.append(message);
			content.append("<br />");
		}
		StringBuffer msg = new StringBuffer();
		msg.append("HTTP/1.0 400 Bad Request\n");
		msg.append(stdHeaders);
		msg.append("Content-Type: text/html\n");
		msg.append("Content-Length: ");
		msg.append(content.length());
		msg.append("\n\n");
		msg.append(content);
		try {
			HttpServer.LOG.warn("BAD_REQUEST");
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes(msg.toString());
			out.flush();
			out.close();
		} catch (IOException e) {
			HttpServer.LOG.warn(e);
		}
	}

	protected String formatErrorMsg(String message, int status) {
		StringBuffer buf = new StringBuffer();
		buf
				.append("<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
		buf.append("hitCount=\"0\" ");
		buf.append("errcode=\"");
		buf.append(status);
		if (message != null) {
			buf.append("\">");
			buf.append("<exist:message>");
			buf.append(message);
			buf.append("</exist:message>");
			buf.append("</exist:result>");
		} else
			buf.append("/>");
		return buf.toString();
	}

	protected void get(String name, int len) {
		if (name.indexOf("..") != -1) {
			errorReply(HTTP_FORBIDDEN);
			return;
		}
		int p = name.indexOf('?');
		HashMap parameters;
		if (p >= 0) {
			parameters = processParameters(name.substring(++p));
			name = name.substring(0, p - 1);
		} else
			parameters = new HashMap();
		doGet(parameters, name, len);
	}
	
	protected void post(String input, String name, int len, String contentType) {
		String result = doPost(input, name);
		if (result == null)
			return;
		try {
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes("HTTP/1.0 200 OK\n" + stdHeaders
					+ "Content-Type: text/xml\n" + "Content-Length: "
					+ result.length() + "\n\n");
			byte[] resultData = result.getBytes("UTF8");
			out.write(resultData, 0, resultData.length);
			out.flush();
			out.close();
		} catch (IOException io) {
			HttpServer.LOG.warn(io);
		}
	}

	protected String printAll(NodeSet resultSet, int howmany, int start,
			long queryTime, Properties outputProperties) {
		if (resultSet.getLength() == 0)
			return formatErrorMsg("nothing found!", OK);
		if ((howmany > resultSet.getLength()) || (howmany <= 0))
			howmany = resultSet.getLength();
		if ((start < 1) || (start > resultSet.getLength()))
			return formatErrorMsg("start parameter out of range", WRONG_REQUEST);
		Serializer serializer = broker.getSerializer();
		serializer.reset();
		String stylesheet = outputProperties.getProperty(EXistOutputKeys.STYLESHEET);
		if (stylesheet != null)
			serializer.setStylesheet(stylesheet);
		try {
			serializer.setProperties(outputProperties);
			return serializer.serialize((NodeSet) resultSet, start, howmany,
					queryTime);
		} catch (SAXException saxe) {
			HttpServer.LOG.warn(saxe);
			return formatErrorMsg("error while serializing xml: "
					+ saxe.toString(), OUTPUT_ERROR);
		}
	}

	protected String printCollection(String collection, String[] names) {
		StringBuffer buf = new StringBuffer();
		buf.append("<exist:result ");
		buf.append("xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">");
		buf.append("<exist:collection name=\"");
		buf.append(collection);
		buf.append("\">");
		for (int i = 0; i < names.length; i++) {
			buf.append("<exist:document name=\"");
			buf.append(names[i]);
			buf.append("\"/>");
		}
		buf.append("</exist:collection></exist:result>");
		return buf.toString();
	}
	
	protected String printSummary(NodeList resultSet, long queryTime) {
		if (resultSet.getLength() == 0)
			return formatErrorMsg("nothing found", OK);
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
				doctypeCounter = (DoctypeCount) doctypes.get(doctype.getName());
				doctypeCounter.inc();
			} else {
				doctypeCounter = new DoctypeCount(doctype);
				doctypes.put(doctype.getName(), doctypeCounter);
			}
		}
		NodeSet temp;
		StringBuffer buf = new StringBuffer();
		buf.append("<exist:result xmlns:exist=\"" + NS + "\" ");
		buf.append("hitCount=\"");
		buf.append(resultSet.getLength());
		buf.append("\" queryTime=\"");
		buf.append(queryTime);
		buf.append("\">");
		for (Iterator i = map.values().iterator(); i.hasNext(); ) {
			counter = (NodeCount) i.next();
			buf.append("<exist:document name=\"");
			buf.append(counter.doc.getFileName());
			buf.append("\" id=\"");
			buf.append(counter.doc.getDocId());
			buf.append("\" hitCount=\"");
			buf.append(counter.count);
			buf.append("\"/>");
		}
		DoctypeCount docTemp;
		for (Iterator i = doctypes.values().iterator(); i.hasNext(); ) {
			docTemp = (DoctypeCount) i.next();
			buf.append("<exist:doctype name=\"");
			buf.append(docTemp.doctype.getName());
			buf.append("\" hitCount=\"");
			buf.append(docTemp.count);
			buf.append("\"/>");
		}
		buf.append("</exist:result>");
		return buf.toString();
	}

	protected String printValues(Sequence resultSet, int howmany, int start) {
		if (resultSet.getLength() == 0)
			return formatErrorMsg("nothing found", OK);
		if ((howmany > resultSet.getLength()) || (howmany <= 0))
			howmany = resultSet.getLength();
		if ((start < 1) || (start > resultSet.getLength()))
			return formatErrorMsg("start parameter out of range", WRONG_REQUEST);
		Item item;
		StringBuffer buf = new StringBuffer();
		buf.append("<exist:result xmlns:exist=\"" + NS + "\" ");
		buf.append("hitCount=\"");
		buf.append(resultSet.getLength());
		buf.append("\">");
		String elem;
		for (int i = start - 1; i < ((start + howmany) - 1); i++) {
			item = resultSet.itemAt(i);
			buf.append("<exist:value>");
			try {
				buf.append(item.getStringValue());
			} catch (XPathException e) {
				buf.append("ERROR: " + e.getMessage());
			}
			buf.append("</exist:value>");
		}
		buf.append("</exist:result>");
		return buf.toString();
	}

	/**
	 * Description of the Method
	 * 
	 * @param sock
	 *                   Description of the Parameter
	 */
	public synchronized void process(Socket sock) {
		this.sock = sock;
		notifyAll();
	}

	/**
	 * Description of the Method
	 * 
	 * @param args
	 *                   Description of the Parameter
	 * 
	 * @return Description of the Return Value
	 */
	protected HashMap processParameters(String args) {
		HttpServer.LOG.debug(args);
		HashMap parameters = new HashMap();
		String param;
		String value;
		int start = 0;
		int end = 0;
		int l = args.length();
		while ((start < l) && (end < l)) {
			while ((end < l) && (args.charAt(end++) != '='))
				;
			if (end == l)
				break;
			param = args.substring(start, end - 1);
			start = end;
			while ((end < l) && (args.charAt(end++) != '&'))
				;
			if (end == l)
				value = args.substring(start);
			else
				value = args.substring(start, end - 1);
			start = end;
			param = URLDecoder.decode(param);
			value = URLDecoder.decode(value);
			HttpServer.LOG.debug("parameter: " + param + " = " + value);
			parameters.put(param, value);
		}
		return parameters;
	}

	protected void put(File tempFile, String docName, int len) {
		String result;
		int errcode = HTTP_OK;
		try {
			int p = docName.lastIndexOf('/');
			if (p < 0 || p == docName.length() - 1)
				throw new EXistException("Illegal document path");
			String collectionName = docName.substring(0, p);
			docName = docName.substring(p + 1);
			Collection collection = broker.getCollection(collectionName);
			if (collection == null) {
				HttpServer.LOG.debug("creating collection " + collectionName);
				collection = broker.getOrCreateCollection(collectionName);
				broker.saveCollection(collection);
			}
			String uri = tempFile.toURI().toASCIIString();
			DocumentImpl doc = collection.addDocument(broker, docName,
					new InputSource(uri));
			result = formatErrorMsg("document " + docName + " stored.", OK);
		} catch (SAXParseException e) {
			result = formatErrorMsg("Parsing exception at " + e.getLineNumber()
					+ ":" + e.getColumnNumber() + "\n" + e.toString(),
					PARSE_ERROR);
			errcode = HTTP_BAD_REQUEST;
		} catch (SAXException e) {
			Exception o = e.getException();
			o.printStackTrace();
			result = formatErrorMsg(o.toString(), PARSE_ERROR);
			errcode = HTTP_BAD_REQUEST;
		} catch (Exception e) {
			e.printStackTrace();
			result = formatErrorMsg(e.toString(), PARSE_ERROR);
			errcode = HTTP_BAD_REQUEST;
		}
		try {
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes("HTTP/1.0 " + errcode + " OK\n" + stdHeaders
					+ "Content-Type: text/xml\n" + "Content-Length: "
					+ result.length() + "\n\n");
			byte[] resultData = result.getBytes("UTF8");
			out.write(resultData, 0, resultData.length);
			out.flush();
			out.close();
		} catch (IOException io) {
			HttpServer.LOG.warn(io);
		}
	}

	/**
	 * Main processing method for the HttpServerConnection object
	 * 
	 * @throws RuntimeException
	 *                    DOCUMENT ME!
	 */
	public void run() {
		String req;
		String first;
		String line;
		String contentType = "text/xml";
		String name = null;
		String username = null;
		String password = null;
		int method = HTTP_BAD_REQUEST;
		int len = 0;
		StringBuffer input = new StringBuffer();
		File tempFile = null;
		BrokerPool brokerPool = null;
		try {
			brokerPool = BrokerPool.getInstance();
		} catch (EXistException e) {
			throw new RuntimeException(e.getMessage());
		}
		// the main loop of this thread
		while (true) {
			while ((sock == null) && !terminate)
				synchronized (this) {
					try {
						wait(500);
					} catch (InterruptedException ie) {
					}
				}
			if (terminate)
				break;
			try {
				DataInputStream is = 
					new DataInputStream(new BufferedInputStream(sock.getInputStream()));
				
				len = -1;
				contentType = "text/xml";
				// parse http-header
				while (true) {
					req = is.readLine();
					if (req == null)
						break;
					StringTokenizer tok = new StringTokenizer(req, " ");
					if (!tok.hasMoreTokens())
						break;
					first = tok.nextToken();
					if (first.equals("GET")) {
						method = GET;
						name = tok.nextToken();
					} else if (first.equals("POST")) {
						method = POST;
						name = tok.nextToken();
					} else if (first.equals("HEAD")) {
						method = HEAD;
						name = tok.nextToken();
					} else if (first.equals("PUT")) {
						method = PUT;
						name = tok.nextToken();
					} else if (req.toUpperCase().startsWith("CONTENT-LENGTH:")) {
						try {
							len = Integer.parseInt(tok.nextToken());
						} catch (NumberFormatException nf) {
							HttpServer.LOG.warn(nf);
							method = HTTP_BAD_REQUEST;
							break;
						}
					} else if (req.toUpperCase().startsWith("CONTENT-TYPE:"))
						contentType = tok.nextToken();
					else if (req.toUpperCase().startsWith("AUTHORIZATION:")) {
						String auth = req.substring("AUTHORIZATION:".length())
								.trim();
						byte[] c = Base64.decode(auth.substring(6).getBytes());
						String s = new String(c);
						int p = s.indexOf(':');
						username = s.substring(0, p);
						password = s.substring(p + 1);
						user = checkUser(brokerPool, username, password);
						if (user == null)
							method = HTTP_FORBIDDEN;
					}
				}
				// get request body
				if (len > 0) {
					byte[] buffer = new byte[2048];
					int count;
					int l = 0;
					if (method == PUT) {
						// put may send a lot of data, so save it
						// to a temporary file first.
						tempFile = File.createTempFile("exist", ".xml");
						OutputStream os = new FileOutputStream(tempFile);
						do {
							count = is.read(buffer);
							if (count > 0)
								os.write(buffer, 0, count);
							l += count;
						} while (l < len);
						os.close();
					} else {
						do {
							count = is.read(buffer);
							if (count > 0)
								input.append(new String(buffer, 0, count, "UTF-8"));
							l += count;
						} while (l < len);
					}
				}
				if (user == null)
					// no user specified: assume guest identity
					user = new User("guest", "guest", "guest");
				try {
					broker = brokerPool.get();
					broker.setUser(user);
				} catch (EXistException e) {
					throw new RuntimeException(e.getMessage());
				}
				try {
					// select method
					switch (method) {
						case HTTP_FORBIDDEN :
							errorReply(HTTP_FORBIDDEN);
							break;
						case HTTP_BAD_REQUEST :
							errorReply(HTTP_BAD_REQUEST);
							break;
						case GET :
							get(name, len);
							break;
						case POST :
							post(input.toString(), name, len, contentType);
							break;
						case PUT :
							put(tempFile, name, len);
							break;
						default :
							errorReply(HTTP_BAD_REQUEST);
							break;
					}
				} finally {
					brokerPool.release(broker);
					broker = null;
					user = null;
				}
				is.close();
				input = new StringBuffer();
			} catch (IOException io) {
				HttpServer.LOG.warn(io);
			}
			sock = null;
			xslStyle = null;
			pool.release(this);
		}
		pool.release(this);
	}

	protected String search(String query, String path, int howmany, int start,
			boolean printSummary, Properties outputProperties) {
		String result = null;
		try {
			XQueryContext context = new XQueryContext(broker);
			DocumentSet docs =new DocumentSet();
			Collection collection = broker.getCollection(path);
			if(collection != null) {
				collection.getDocuments(docs);
			} else {
				DocumentImpl doc = (DocumentImpl)broker.getDocument(path);
				if(doc != null)
					docs.add(doc);
			}
			context.setStaticallyKnownDocuments(docs);
			XPathLexer2 lexer = new XPathLexer2(new StringReader(query));
			XPathParser2 parser = new XPathParser2(lexer);
			XPathTreeParser2 treeParser = new XPathTreeParser2(context);
			parser.xpath();
			if (parser.foundErrors()) {
				return formatErrorMsg(parser.getErrorMessage(), SYNTAX_ERROR);
			}
			AST ast = parser.getAST();
			HttpServer.LOG.debug("generated AST: " + ast.toStringTree());
			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				return formatErrorMsg(treeParser.getErrorMessage(),
						SYNTAX_ERROR);
			}
			HttpServer.LOG.info("query: " + expr.pprint());
			if (parser.foundErrors())
				return formatErrorMsg(parser.getErrorMessage(), SYNTAX_ERROR);
			long startTime = System.currentTimeMillis();
			Sequence resultSequence = expr.eval(null, null);
			long queryTime = System.currentTimeMillis() - startTime;
			HttpServer.LOG.debug("evaluation took " + queryTime + "ms.");
			startTime = System.currentTimeMillis();
			switch (resultSequence.getItemType()) {
				case Type.NODE :
					if (printSummary)
						result = printSummary((NodeSet) resultSequence,
								queryTime);
					else
						result = printAll((NodeSet) resultSequence, howmany,
								start, queryTime, outputProperties);
					break;
				default :
					result = printValues(resultSequence, howmany, start);
					break;
			}
		} catch (Exception e) {
			HttpServer.LOG.debug(e.toString(), e);
			result = formatErrorMsg(e.toString(), UNKNOWN_ERROR);
		}
		return result;
	}

	public void terminate() {
		terminate = true;
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

		/**
		 * Description of the Method
		 */
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

		/**
		 * Description of the Method
		 */
		public void inc() {
			count++;
		}
	}
}
