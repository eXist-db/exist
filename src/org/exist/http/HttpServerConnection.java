/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import org.apache.xmlrpc.Base64;
import org.exist.EXistException;
import org.exist.Parser;
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
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import antlr.collections.AST;

/**
 * Description of the Class
 *
 * @author Wolfgang Meier
 *
 */
public class HttpServerConnection extends Thread {
	private final static String NS = "http://exist.sourceforge.net/NS/exist";
	private final static int GET = 1;
	private final static int HEAD = 2;
	private final static int POST = 3;
	private final static int PUT = 4;
	private final static int BAD_REQUEST = 400;
	private final static int FORBIDDEN = 403;
	private final static int NOT_FOUND = 404;
	private final static int OK = 0;
	private final static int WRONG_REQUEST = 1;
	private final static int PARSE_ERROR = 2;
	private final static int DOCUMENT_NOT_FOUND = 3;
	private final static int SYNTAX_ERROR = 4;
	private final static int OUTPUT_ERROR = 5;
	private final static int UNKNOWN_ERROR = 6;
	private final static String stdHeaders =
		"Allow: POST GET PUT\n"
			+ "Server: eXist 0.6\n"
			+ "Cache-control: no-cache\n";
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

	/**
	 * Constructor for the HttpServerConnection object
	 *
	 * @param config Description of the Parameter
	 * @param pool Description of the Parameter
	 */
	public HttpServerConnection(
		Configuration config,
		HttpServer.ConnectionPool pool) {
		this.config = config;

		//this.broker = broker;
		this.pool = pool;

		if ((tmpDir = (String) config.getProperty("tmpDir")) == null)
			tmpDir = "/tmp";

		DocumentBuilderFactory docFactory =
			DocumentBuilderFactory.newInstance();
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

	/**
	 * Description of the Method
	 *
	 * @param parameters Description of the Parameter
	 * @param name Description of the Parameter
	 * @param len Description of the Parameter
	 */
	protected void doGet(HashMap parameters, String name, int len) {
		int howmany = 10;
		int start = 1;
		boolean summary = false;
		boolean indent = false;
		String encoding = (String) parameters.get("_encoding");
		String query = (String) parameters.get("_xpath");
		String stylesheet = (String) parameters.get("_xsl");
		String p_howmany = (String) parameters.get("_howmany");

		if (encoding == null)
			encoding = "UTF-8";

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

		String p_summary;
		String p_indent;

		if ((p_summary = (String) parameters.get("_summarize")) != null)
			summary = p_summary.equals("true");

		if ((p_indent = (String) parameters.get("_indent")) != null)
			indent = p_indent.equals("true");

		String data;

		try {
			if (query != null) {
				if (!(query.startsWith("document(")
					|| query.startsWith("collection")
					|| query.startsWith("doctype("))) {
					Collection parent = broker.getCollection(name);

					if (parent == null)
						query = "document('" + name + "')" + query;
					else
						query =
							"collection('" + parent.getName() + "')" + query;
				}
				data =
					search(query, howmany, start, summary, indent, stylesheet);
			} else {
				if (name.charAt(0) == '/')
					name = name.substring(1);

				if (name.endsWith("/")) {
					// print collection contents
					DocumentSet docs = broker.getDocumentsByCollection(name);
					String[] names = docs.getNames();
					data = printCollection(name, names);
				} else {
					DocumentImpl d = (DocumentImpl) broker.getDocument(name);

					if (d == null)
						data =
							formatErrorMsg(
								"document " + name + " not found!",
								DOCUMENT_NOT_FOUND);
					else {
						Serializer serializer = broker.getSerializer();
						serializer.reset();

						if (stylesheet != null) {
							serializer.setStylesheet(d, stylesheet);
						}

						try {
							Map properties = new TreeMap();
							serializer.setProperty(OutputKeys.ENCODING, encoding);
							serializer.setProperty(
								OutputKeys.INDENT,
								indent ? "yes" : "no");
							data = serializer.serialize(d);
						} catch (SAXException saxe) {
							HttpServer.LOG.warn(saxe);
							data =
								formatErrorMsg(
									"error while serializing xml: "
										+ saxe.toString(),
									OUTPUT_ERROR);
						}
					}
				}
			}
		} catch (PermissionDeniedException e) {
			data =
				formatErrorMsg(
					"permission denied: " + e.getMessage(),
					OUTPUT_ERROR);
		}

		try {
			byte[] resultData = data.getBytes(encoding);
			DataOutputStream out =
				new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes("HTTP/1.0 200 OK\n");

			if (stylesheet == null)
				out.writeBytes("Content-Type: text/xml\n");
			else
				out.writeBytes("Content-Type: text/html\n");

			out.writeBytes(
				stdHeaders + "Content-Length: " + resultData.length + "\n\n");
			out.write(resultData, 0, resultData.length);
			out.flush();
			out.close();
		} catch (IOException io) {
			HttpServer.LOG.warn(io);
		}
	}

	/**
	 * Description of the Method
	 *
	 * @param request Description of the Parameter
	 * @param name Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	protected String doPost(String request, String name) {
		int i = name.lastIndexOf('/');

		if (i < (name.length() - 1))
			name = name.substring(i + 1);
		else
			name = null;

		boolean indent = true;
		boolean summary = false;
		int howmany = 10;
		int start = 1;
		String query = null;
		String result = null;
		NodeList tmpList;
		Element temp;

		try {
			InputSource src = new InputSource(new StringReader(request));
			Document doc = docBuilder.parse(src);
			Element root = doc.getDocumentElement();

			// process <exist:request>
			if (root.getTagName().equals("exist:request")) {
				// process <display indent="true|false" start="start" howmany="howmany">
				tmpList = root.getElementsByTagNameNS(NS, "display");

				if (tmpList.getLength() == 0) {
					tmpList = root.getElementsByTagNameNS(NS, "summarize");
					summary = (tmpList.getLength() > 0);
				}

				if (tmpList.getLength() > 0) {
					temp = (Element) tmpList.item(0);

					String p_indent = temp.getAttribute("indent");
					indent = p_indent.equals("true");

					String p_howmany = temp.getAttribute("howmany");

					if (p_howmany != null) {
						try {
							howmany = Integer.parseInt(p_howmany);
						} catch (NumberFormatException nfe) {
							howmany = 15;
						}
					}

					String p_start = temp.getAttribute("start");

					if (p_start != null) {
						try {
							start = Integer.parseInt(p_start);
						} catch (NumberFormatException nfe) {
							start = 1;
						}
					}
				}

				// process <get document="docName" indent="true|false"/>
				tmpList = root.getElementsByTagNameNS(NS, "get");

				if (tmpList.getLength() > 0) {
					temp = (Element) tmpList.item(0);

					String docName = temp.getAttribute("document");
					DocumentImpl d = (DocumentImpl) broker.getDocument(docName);

					if (d == null)
						return formatErrorMsg(
							"document " + docName + " not found!",
							DOCUMENT_NOT_FOUND);

					Serializer serializer = broker.getSerializer();
					serializer.setProperty(OutputKeys.ENCODING, "UTF-8");
					serializer.setProperty(
						OutputKeys.INDENT,
						indent ? "yes" : "no");
					return serializer.serialize(d);
				}

				// process <query>xpathQuery</query>
				tmpList = root.getElementsByTagNameNS(NS, "query");

				if (tmpList.getLength() > 0) {
					temp = (Element) tmpList.item(0);

					Text text = (Text) temp.getFirstChild();
					query = text.getData();

					if ((!query.startsWith("document("))
						&& (!query.startsWith("doctype("))) {
						if ((name != null) && (name.length() > 0))
							query = "document(\"" + name + "\")" + query;
						else
							query = "document()" + query;
					}
				}

				// process <remove document="docName"/>
				tmpList = root.getElementsByTagNameNS(NS, "remove");

				if (tmpList.getLength() > 0) {
					temp = (Element) tmpList.item(0);

					String docName = temp.getAttribute("document");
					DocumentImpl d = (DocumentImpl) broker.getDocument(docName);

					if (d == null)
						return formatErrorMsg(
							"document " + docName + " not found!",
							DOCUMENT_NOT_FOUND);

					broker.removeDocument(docName);

					return formatErrorMsg("removed document " + docName, OK);
				}

				// execute query
				if (query != null)
					result =
						search(query, howmany, start, summary, indent, null);
			} else
				result = formatErrorMsg("not a valid request", WRONG_REQUEST);
		} catch (SAXException e) {
			HttpServer.LOG.debug(request);
			HttpServer.LOG.debug(e);
			result = formatErrorMsg(e.getException().toString(), WRONG_REQUEST);
		} catch (Exception e) {
			HttpServer.LOG.debug(e);
			e.printStackTrace();
			result = formatErrorMsg(e.toString(), WRONG_REQUEST);
		}

		return result;
	}

	/**
	 * Description of the Method
	 *
	 * @param code Description of the Parameter
	 */
	protected void errorReply(int code) {
		errorReply(code, null);
	}

	/**
	 * Description of the Method
	 *
	 * @param code Description of the Parameter
	 * @param message Description of the Parameter
	 */
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

			DataOutputStream out =
				new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes(msg.toString());
			out.flush();
			out.close();
		} catch (IOException e) {
			HttpServer.LOG.warn(e);
		}
	}

	/**
	 * Description of the Method
	 *
	 * @param message Description of the Parameter
	 * @param status Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	protected String formatErrorMsg(String message, int status) {
		StringBuffer buf = new StringBuffer();
		buf.append(
			"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
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

	/**
	 * Description of the Method
	 *
	 * @param name Description of the Parameter
	 * @param len Description of the Parameter
	 */
	protected void get(String name, int len) {
		if (name.indexOf("..") != -1) {
			errorReply(FORBIDDEN);

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

	/**
	 * Description of the Method
	 *
	 * @param input Description of the Parameter
	 * @param name Description of the Parameter
	 * @param len Description of the Parameter
	 * @param contentType Description of the Parameter
	 */
	protected void post(
		String input,
		String name,
		int len,
		String contentType) {
		String result = doPost(input, name);

		if (result == null)
			return;

		try {
			DataOutputStream out =
				new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes(
				"HTTP/1.0 200 OK\n"
					+ stdHeaders
					+ "Content-Type: text/xml\n"
					+ "Content-Length: "
					+ result.length()
					+ "\n\n");

			byte[] resultData = result.getBytes("UTF8");
			out.write(resultData, 0, resultData.length);
			out.flush();
			out.close();
		} catch (IOException io) {
			HttpServer.LOG.warn(io);
		}
	}

	/**
	 * Description of the Method
	 *
	 * @param resultSet Description of the Parameter
	 * @param howmany Description of the Parameter
	 * @param start Description of the Parameter
	 * @param queryTime Description of the Parameter
	 * @param indent Description of the Parameter
	 * @param stylesheet Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	protected String printAll(
		NodeSet resultSet,
		int howmany,
		int start,
		long queryTime,
		boolean indent,
		String stylesheet) {
		if (resultSet.getLength() == 0)
			return formatErrorMsg("nothing found!", OK);

		Node n;
		Node nn;
		Element temp;
		DocumentImpl owner;

		if ((howmany > resultSet.getLength()) || (howmany == 0))
			howmany = resultSet.getLength();

		if ((start < 1) || (start > resultSet.getLength()))
			return formatErrorMsg(
				"start parameter out of range",
				WRONG_REQUEST);

		Serializer serializer = broker.getSerializer();
		serializer.reset();

		if (stylesheet != null)
			serializer.setStylesheet(stylesheet);

		try {
			serializer.setProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setProperty(
				OutputKeys.INDENT,
				indent ? "yes" : "no");
			return serializer.serialize(
				(NodeSet) resultSet,
				start,
				howmany,
				queryTime);
		} catch (SAXException saxe) {
			HttpServer.LOG.warn(saxe);

			return formatErrorMsg(
				"error while serializing xml: " + saxe.toString(),
				OUTPUT_ERROR);
		}
	}

	/**
	 * Description of the Method
	 *
	 * @param collection Description of the Parameter
	 * @param names Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
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

	/**
	 * Description of the Method
	 *
	 * @param resultSet Description of the Parameter
	 * @param queryTime Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
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

		NodeSet temp;
		StringBuffer buf = new StringBuffer();
		buf.append(
			"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
		buf.append("hitCount=\"");
		buf.append(resultSet.getLength());
		buf.append("\" queryTime=\"");
		buf.append(queryTime);
		buf.append("\">");

		for (Iterator i = map.values().iterator(); i.hasNext();) {
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

		for (Iterator i = doctypes.values().iterator(); i.hasNext();) {
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

	/**
	 * Description of the Method
	 *
	 * @param resultSet Description of the Parameter
	 * @param howmany Description of the Parameter
	 * @param start Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	protected String printValues(Sequence resultSet, int howmany, int start) {
		if (resultSet.getLength() == 0)
			return formatErrorMsg("nothing found", OK);

		if ((howmany > resultSet.getLength()) || (howmany == 0))
			howmany = resultSet.getLength();

		if ((start < 1) || (start > resultSet.getLength()))
			return formatErrorMsg(
				"start parameter out of range",
				WRONG_REQUEST);

		Item item;
		StringBuffer buf = new StringBuffer();
		buf.append(
			"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" ");
		buf.append("hitCount=\"");
		buf.append(resultSet.getLength());
		buf.append("\">");

		String elem;

		for (int i = start - 1; i < ((start + howmany) - 1); i++) {
			item = resultSet.itemAt(i);

			switch (item.getType()) {
				case Type.NUMBER :
					elem = "exist:number";

					break;

				case Type.STRING :
					elem = "exist:string";

					break;

				case Type.BOOLEAN :
					elem = "exist:boolean";

					break;

				default :
					HttpServer.LOG.debug("unknown type: " + item.getType());

					continue;
			}

			buf.append("<");
			buf.append(elem);
			buf.append(" value=\"");
			buf.append(item.getStringValue());
			buf.append("\"/>");
		}

		buf.append("</exist:result>");

		return buf.toString();
	}

	/**
	 * Description of the Method
	 *
	 * @param sock Description of the Parameter
	 */
	public synchronized void process(Socket sock) {
		this.sock = sock;
		notifyAll();
	}

	/**
	 * Description of the Method
	 *
	 * @param args Description of the Parameter
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
			while ((end < l) && (args.charAt(end++) != '='));

			if (end == l)
				break;

			param = args.substring(start, end - 1);
			start = end;

			while ((end < l) && (args.charAt(end++) != '&'));

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

	/**
	 * Description of the Method
	 *
	 * @param tempFile Description of the Parameter
	 * @param name Description of the Parameter
	 * @param len Description of the Parameter
	 */
	protected void put(File tempFile, String name, int len) {
		String result;

		try {
			if (broker.getDocument(name) != null) {
				HttpServer.LOG.debug("removing old document " + name);
				broker.removeDocument(name);
			}

			Parser parser = new Parser(broker, user, true);
			HttpServer.LOG.debug("parsing document ...");
			parser.parse(tempFile, name);
			broker.flush();
			result = formatErrorMsg("document " + name + " stored.", OK);
		} catch (SAXParseException e) {
			result =
				formatErrorMsg(
					"Parsing exception at "
						+ e.getLineNumber()
						+ ":"
						+ e.getColumnNumber()
						+ "\n"
						+ e.toString(),
					PARSE_ERROR);
		} catch (SAXException e) {
			Exception o = e.getException();
			o.printStackTrace();
			result = formatErrorMsg(o.toString(), PARSE_ERROR);
		} catch (Exception e) {
			e.printStackTrace();
			result = formatErrorMsg(e.toString(), PARSE_ERROR);
		}

		try {
			DataOutputStream out =
				new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes(
				"HTTP/1.0 200 OK\n"
					+ stdHeaders
					+ "Content-Type: text/xml\n"
					+ "Content-Length: "
					+ result.length()
					+ "\n\n");

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
	 * @throws RuntimeException DOCUMENT ME!
	 */
	public void run() {
		String req;
		String first;
		String line;
		String contentType = "text/xml";
		String name = null;
		String username = null;
		String password = null;
		int method = BAD_REQUEST;
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
				BufferedReader in =
					new BufferedReader(
						new InputStreamReader(sock.getInputStream()));
				len = -1;
				contentType = "text/xml";

				// parse http-header
				while (true) {
					req = in.readLine();

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
					} else if (
						req.toUpperCase().startsWith("CONTENT-LENGTH:")) {
						try {
							len = Integer.parseInt(tok.nextToken());
						} catch (NumberFormatException nf) {
							HttpServer.LOG.warn(nf);
							method = BAD_REQUEST;

							break;
						}
					} else if (req.toUpperCase().startsWith("CONTENT-TYPE:"))
						contentType = tok.nextToken();
					else if (req.toUpperCase().startsWith("AUTHORIZATION:")) {
						String auth =
							req.substring("AUTHORIZATION:".length()).trim();
						byte[] c = Base64.decode(auth.substring(6).getBytes());
						String s = new String(c);
						int p = s.indexOf(':');
						username = s.substring(0, p);
						password = s.substring(p + 1);
						user = checkUser(brokerPool, username, password);

						if (user == null)
							method = FORBIDDEN;
					}
				}

				// get request body
				if (len > 0) {
					char[] buffer = new char[2048];
					int count;
					int l = 0;

					if (method == PUT) {
						// put may send a lot of data, so save it
						// to a temporary file first.
						tempFile = File.createTempFile("exist", ".xml");

						BufferedWriter fout =
							new BufferedWriter(new FileWriter(tempFile));

						do {
							count = in.read(buffer);

							if (count > 0)
								fout.write(buffer, 0, count);

							l += count;
						} while (l < len);

						fout.close();
					} else {
						do {
							count = in.read(buffer);

							if (count > 0)
								input.append(buffer, 0, count);

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
						case FORBIDDEN :
							errorReply(FORBIDDEN);

							break;

						case BAD_REQUEST :
							errorReply(BAD_REQUEST);

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
							errorReply(BAD_REQUEST);

							break;
					}
				} finally {
					brokerPool.release(broker);
					broker = null;
					user = null;
				}

				in.close();
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

	/**
	 * Description of the Method
	 *
	 * @param query Description of the Parameter
	 * @param howmany Description of the Parameter
	 * @param start Description of the Parameter
	 * @param printSummary Description of the Parameter
	 * @param indent Description of the Parameter
	 * @param stylesheet Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	protected String search(
		String query,
		int howmany,
		int start,
		boolean printSummary,
		boolean indent,
		String stylesheet) {
		String result = null;

		try {
			StaticContext context = new StaticContext(broker);
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
				return formatErrorMsg(
					treeParser.getErrorMessage(),
					SYNTAX_ERROR);
			}

			HttpServer.LOG.info("query: " + expr.pprint());

			if (parser.foundErrors())
				return formatErrorMsg(parser.getErrorMessage(), SYNTAX_ERROR);

			long startTime = System.currentTimeMillis();
			DocumentSet ndocs = expr.preselect(context);

			if (ndocs.getLength() == 0)
				result = formatErrorMsg("nothing found", OK);
			else {
				Sequence resultSequence = expr.eval(ndocs, null, null);
				long queryTime = System.currentTimeMillis() - startTime;
				HttpServer.LOG.debug("evaluation took " + queryTime + "ms.");
				startTime = System.currentTimeMillis();

				switch (resultSequence.getItemType()) {
					case Type.NODE :

						if (printSummary)
							result =
								printSummary(
									(NodeSet) resultSequence,
									queryTime);
						else
							result =
								printAll(
									(NodeSet) resultSequence,
									howmany,
									start,
									queryTime,
									indent,
									stylesheet);

						break;

					default :
						result = printValues(resultSequence, howmany, start);

						break;
				}

			}
		} catch (Exception e) {
			HttpServer.LOG.debug(e.toString(), e);
			result = formatErrorMsg(e.toString(), UNKNOWN_ERROR);
		}

		return result;
	}

	/**
	 * Description of the Method
	 */
	public void terminate() {
		terminate = true;
	}

	class DoctypeCount {
		int count = 1;
		DocumentType doctype;

		/**
		 * Constructor for the DoctypeCount object
		 *
		 * @param doctype Description of the Parameter
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
		 * @param doc Description of the Parameter
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
