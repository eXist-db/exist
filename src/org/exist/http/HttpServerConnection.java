/*
 * eXist Open Source Native XML Database 
 * Copyright (C) 2001, Wolfgang M. Meier (wolfgang@exist-db.org)
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
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xmlrpc.Base64;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NodeList;

public class HttpServerConnection extends RESTServer implements Runnable {
	
	private final static int GET = 1;
	private final static int HEAD = 2;
	private final static int POST = 3;
	private final static int PUT = 4;
	private final static int DELETE = 5;

	private final static int OK = 0;
	private final static int WRONG_REQUEST = 1;
	private final static int PARSE_ERROR = 2;
	private final static int DOCUMENT_NOT_FOUND = 3;
	private final static int SYNTAX_ERROR = 4;
	private final static int OUTPUT_ERROR = 5;
	private final static int UNKNOWN_ERROR = 6;

	// HTTP response codes
	public final static int HTTP_OK = 200;
	public final static int HTTP_BAD_REQUEST = 400;
	public final static int HTTP_FORBIDDEN = 403;
	public final static int HTTP_NOT_FOUND = 404;
	public final static int HTTP_INTERNAL_ERROR = 500;
	
	private final static String stdHeaders = "Allow: POST GET PUT DELETE\n"
			+ "Server: eXist\n" + "Cache-control: no-cache\n";
	
	protected DBBroker broker = null;
	protected Configuration config;
	protected DocumentBuilder docBuilder = null;
	protected HttpServer.ConnectionPool pool;
	protected Socket sock = null;
	protected boolean terminate = false;
	protected String tmpDir = null;
	protected User user = null;
	protected String xslStyle = null;

	public HttpServerConnection(Configuration config,
			HttpServer.ConnectionPool pool) {
		super();
		this.config = config;
		this.pool = pool;
		if ((tmpDir = (String) config.getProperty("tmpDir")) == null)
			tmpDir = "/tmp";
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
		docFactory.setNamespaceAware(true);
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOG.warn(e);
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
		Response response;
		try {
			response = doGet(broker, parameters, name);
		} catch (BadRequestException e) {
			response = new Response(HTTP_BAD_REQUEST, e.getMessage());
		} catch (PermissionDeniedException e) {
			response = new Response(HTTP_FORBIDDEN, e.getMessage());
		} catch (NotFoundException e) {
			response = new Response(HTTP_NOT_FOUND, e.getMessage());
		}
		writeResponse(response);
	}
	
	protected void put(File tempFile, String contentType, String docPath) {
		Response response;
		try {
			response = doPut(broker, tempFile, contentType, docPath);
		} catch (BadRequestException e) {
			response = new Response(HTTP_BAD_REQUEST, e.getMessage());
		} catch (PermissionDeniedException e) {
			response = new Response(HTTP_FORBIDDEN, e.getMessage());
		}
		writeResponse(response);
	}
	
	protected void delete(String path) {
		Response response;
		try {
			response = doDelete(broker, path);
		} catch(PermissionDeniedException e) {
			LOG.debug("permission denied to remove " + path);
			response = new Response(HTTP_FORBIDDEN, e.getMessage());
		} catch (NotFoundException e) {
			response = new Response(HTTP_NOT_FOUND, e.getMessage());
		}
		writeResponse(response);
	}	

	protected void post(String content, String path, int len, String contentType) {
		Response response;
		try {
			response = doPost(broker, content, path);
		} catch (BadRequestException e) {
			response = new Response(HTTP_BAD_REQUEST, e.getMessage());
		} catch (PermissionDeniedException e) {
			response = new Response(HTTP_FORBIDDEN, e.getMessage());
		}
		writeResponse(response);		
	}

	protected void writeResponse(Response resp) {
		try {
			DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(sock.getOutputStream()));
			resp.write(out);
		} catch (IOException e) {
			LOG.warn("IO exception while writing response output" ,e);
		}
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
		msg.append("HTTP/1.0 400 ");
		msg.append(message);
		msg.append("\n");
		msg.append(stdHeaders);
		msg.append("Content-Type: text/html\n");
		msg.append("Content-Length: ");
		msg.append(content.length());
		msg.append("\n\n");
		msg.append(content.toString());
		try {
			LOG.warn("BAD_REQUEST");
			DataOutputStream out = new DataOutputStream(
					new BufferedOutputStream(sock.getOutputStream()));
			out.writeBytes(msg.toString());
			out.flush();
			out.close();
		} catch (IOException e) {
			LOG.warn(e);
		}
	}

	protected Response statusReport(String message) {
		Response response = new Response(HTTP_OK, message);
		StringBuffer buf = new StringBuffer();
		buf.append("<exist:result xmlns:exist=\"");
		buf.append(NS);
		buf.append("\">");
		if (message != null) {
			buf.append("<exist:message>");
			buf.append(message);
			buf.append("</exist:message>");
			buf.append("</exist:result>");
		}
		response.setContent( buf.toString() );
		return response;
	}

	protected String printSummary(NodeList resultSet, long queryTime) {
		if (resultSet.getLength() == 0)
			return "nothing found";
		HashMap map = new HashMap();
		HashMap doctypes = new HashMap();
		NodeProxy p;
		String docName;
		DocumentType doctype;
		NodeCount counter;
		DoctypeCount doctypeCounter;
		for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext(); ) {
			p = (NodeProxy) i.next();
			docName = p.getDocument().getFileName();
			doctype = p.getDocument().getDoctype();
			if (map.containsKey(docName)) {
				counter = (NodeCount) map.get(docName);
				counter.inc();
			} else {
				counter = new NodeCount(p.getDocument());
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

	public synchronized void process(Socket sock) {
		this.sock = sock;
		notifyAll();
	}

	protected HashMap processParameters(String args) {
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
			LOG.debug("parameter: " + param + " = " + value);
			parameters.put(param, value);
		}
		return parameters;
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
					} else if (first.equals("DELETE")) {
						method = DELETE;
						name = tok.nextToken();
					} else if (req.toUpperCase().startsWith("CONTENT-LENGTH:")) {
						try {
							len = Integer.parseInt(tok.nextToken());
						} catch (NumberFormatException nf) {
							LOG.warn(nf);
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
						tempFile = File.createTempFile("exist", ".tmp");
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

					// select method
					switch (method) {
						case HTTP_FORBIDDEN :
							errorReply(HTTP_FORBIDDEN, "Permission denied.");
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
							put(tempFile, contentType, name);
							break;
						case DELETE:
							delete(name);
							break;
						default :
							errorReply(HTTP_BAD_REQUEST);
							break;
					}
				} catch (EXistException e) {
					LOG.warn("Internal error: " + e.getMessage(), e);
				} finally {
					brokerPool.release(broker);
					broker = null;
					user = null;
				}
				is.close();
				input = new StringBuffer();
			} catch (IOException io) {
				LOG.warn(io);
			}
			sock = null;
			xslStyle = null;
			pool.release(this);
		}
		pool.release(this);
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
