/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SAXSerializerPool;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * 
 * @author wolf
 *
 */
public class RESTServer {

	protected final static String NS = "http://exist.sourceforge.net/NS/exist";
	protected final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";
	
	protected final static Logger LOG = Logger.getLogger(RESTServer.class);

	protected final static Properties defaultProperties = new Properties();

	static {
		defaultProperties.setProperty(OutputKeys.INDENT, "yes");
		defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
		defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES,
				"elements");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
	}

	private final static DateFormat dateFormat = new SimpleDateFormat(
			"MMM d, yyyy hh:mm:ss");

	protected DocumentBuilder docBuilder = null;
	
	public RESTServer() {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
		.newInstance();
		docFactory.setNamespaceAware(true);
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOG.warn(e);
		}
	}
	
	public Response doGet(DBBroker broker, Map parameters, String path) 
	throws BadRequestException, PermissionDeniedException, NotFoundException {
		LOG.debug("user = " + broker.getUser().getName());
		int howmany = 10;
		int start = 1;
		boolean summary = false;
		Properties outputProperties = new Properties();
		String query = (String) parameters.get("_xpath");
		if(query == null)
			query = (String) parameters.get("_query");
		
		String p_howmany = (String) parameters.get("_howmany");
		if (p_howmany != null) {
			try {
				howmany = Integer.parseInt(p_howmany);
			} catch (NumberFormatException nfe) {
				throw new BadRequestException("Parameter _howmany should be an int");
			}
		}
		String p_start = (String) parameters.get("_start");
		if (p_start != null) {
			try {
				start = Integer.parseInt(p_start);
			} catch (NumberFormatException nfe) {
				throw new BadRequestException("Parameter _start should be an int");
			}
		}
		String option;
		if ((option = (String) parameters.get("_summarize")) != null)
			summary = option.equals("yes");
		if ((option = (String) parameters.get("_indent")) != null)
			outputProperties.setProperty(OutputKeys.INDENT, option);
		String stylesheet;
		if((stylesheet = (String) parameters.get("_xsl"))!= null) {
			if(stylesheet.equals("no"))
				outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, stylesheet);
			else
				outputProperties.setProperty(EXistOutputKeys.STYLESHEET, stylesheet);
		} else
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
		String encoding;
		if((encoding = (String) parameters.get("_encoding")) !=null)
			outputProperties.setProperty(OutputKeys.ENCODING, encoding);
		else
			encoding = "UTF-8";
		Response response = new Response();
			if (query != null) {
				response.setContent(search(broker, query, path, howmany, start, outputProperties));
			} else {
				DocumentImpl d = (DocumentImpl) broker.getDocument(path);
				if (d == null) {
					Collection collection = broker.getCollection(path);
					if(collection != null) {
						if(!collection.getPermissions().validate(broker.getUser(), Permission.READ))
							throw new PermissionDeniedException("Not allowed to read collection");
						else
							response = new Response(printCollection(broker, collection));
					} else {
						throw new NotFoundException("Document " + path + " not found");
					}
				} else {
					if(d.getResourceType() == DocumentImpl.BINARY_FILE) {
						response.setContent(broker.getBinaryResourceData((BinaryDocument)d));
					} else {
						Serializer serializer = broker.getSerializer();
						serializer.reset();
						
						if (stylesheet != null) {
							serializer.setStylesheet(d, stylesheet);
							response.setContentType("text/html");
						}
						try {
							serializer.setProperties(outputProperties);
							response.setContent(serializer.serialize(d));
							if(serializer.isStylesheetApplied())
								response.setContentType("text/html");
						} catch (SAXException saxe) {
							LOG.warn(saxe);
							throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
						}
					}
				}
			}
			return response;
	}
	
	public Response doPost(DBBroker broker, String content, String path) 
	throws BadRequestException, PermissionDeniedException {
		boolean indent = true;
		boolean summary = false;
		int howmany = 10;
		int start = 1;
		Properties outputProperties = new Properties(defaultProperties);
		String query = null;
		Response response = null;
		try {
			InputSource src = new InputSource(new StringReader(content));
			Document doc = docBuilder.parse(src);
			Element root = doc.getDocumentElement();
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
											if(next.getNodeType() == Node.TEXT_NODE || 
													next.getNodeType() == Node.CDATA_SECTION_NODE)
												buf.append(next.getNodeValue());
											next = next.getNextSibling();
										}
										query =buf.toString();
									} else if(child.getLocalName().equals("properties")) {
										Node node = child.getFirstChild();
										while(node !=null) {
											if(node.getNodeType() == Node.ELEMENT_NODE && 
													node.getNamespaceURI().equals(NS) &&
													node.getLocalName().equals("property")) {
												Element property = (Element)node;
												String key = property.getAttribute("name");
												String value = property.getAttribute("value");
												LOG.debug(key + " = " + value);
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
					response = new Response(search(broker, query, path, howmany, start, outputProperties));
				else
					throw new BadRequestException("No query specified");
			} else if(root.getNamespaceURI().equals(XUPDATE_NS)) {
				LOG.debug("Got xupdate request: " + content);
				DocumentSet docs =new DocumentSet();
				Collection collection = broker.getCollection(path);
				if(collection != null) {
					collection.allDocs(broker, docs, true);
				} else {
					DocumentImpl xupdateDoc = (DocumentImpl)broker.getDocument(path);
					if(doc != null)
						docs.add(doc);
					else
						broker.getAllDocuments(docs);
				}
				XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
				Modification modifications[] =
					processor.parse(new InputSource(new StringReader(content)));
				long mods = 0;
				for (int i = 0; i < modifications.length; i++) {
					mods += modifications[i].process();
					broker.flush();
				}
				//	FD : Returns an XML doc
				response = new Response("<?xml version='1.0'?>\n" +
				"<exist:modifications mlns:exist='" + NS + "' count='" + mods + 
				"'>" + mods + "modifications processed.</exist:modifications>");
				// END FD
			} else
				throw new BadRequestException(
						"Unknown XML root element: " + root.getNodeName());
		} catch (SAXException e) {
			Exception cause = e;
			if(e.getException() != null)
				cause = e.getException();
			LOG.debug("SAX exception while parsing request: " + cause.getMessage(), cause);
			throw new BadRequestException( 
					"SAX exception while parsing request: " + cause.getMessage());
		} catch (ParserConfigurationException e) {
			throw new BadRequestException("Parser exception while parsing request: " + e.getMessage());
		} catch (XPathException e) {
			throw new BadRequestException("Query exception while parsing request: " + e.getMessage());
		} catch (IOException e) {
			throw new BadRequestException("IO exception while parsing request: " + e.getMessage());
		} catch (EXistException e) {
			throw new BadRequestException(e.getMessage());
		}
		return response;
	}
	
	public Response doPut(DBBroker broker, File tempFile, String contentType, String docPath) 
	throws BadRequestException, PermissionDeniedException {
		Response response;
		try {
			int p = docPath.lastIndexOf('/');
			if (p < 0 || p == docPath.length() - 1)
				throw new BadRequestException("Bad path: " + docPath);
			else {
				String collectionName = docPath.substring(0, p);
				docPath = docPath.substring(p + 1);
				Collection collection = broker.getCollection(collectionName);
				if (collection == null) {
					LOG.debug("creating collection " + collectionName);
					collection = broker.getOrCreateCollection(collectionName);
					broker.saveCollection(collection);
				}
				URL url = tempFile.toURL();
				if(contentType == null || contentType.equalsIgnoreCase("text/xml")) {
					DocumentImpl doc = collection.addDocument(broker, docPath,
							new InputSource(url.toString()));
					response = new Response();
					response.setDescription("Document " + docPath + " stored.");
				} else {
					byte[] chunk = new byte[4096];
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					FileInputStream is = new FileInputStream(tempFile);
					int l;
					while((l = is.read(chunk)) > -1) {
						os.write(chunk, 0, l);
					}
					collection.addBinaryResource(broker, docPath, os.toByteArray());
					response = new Response();
					response.setDescription("Document " + docPath + " stored as binary resource.");
				}
			}
		} catch (SAXParseException e) {
			throw new BadRequestException( 
					"Parsing exception at " + e.getLineNumber()
					+ "/" + e.getColumnNumber() + ": " + e.toString());
		} catch (SAXException e) {
			Exception o = e.getException();
			if(o == null)
				o = e;
			throw new BadRequestException(
					"Parsing exception: " + o.getMessage());
		} catch (EXistException e) {
			throw new BadRequestException("Internal error: " + e.getMessage());
		} catch (IOException e) {
			throw new BadRequestException("Internal error: " + e.getMessage());
		} catch (TriggerException e) {
			throw new PermissionDeniedException(e.getMessage());
		} catch (LockException e) {
			throw new PermissionDeniedException(e.getMessage());
		}
		return response;
	}
	
	public Response doDelete(DBBroker broker, String path) 
	throws PermissionDeniedException, NotFoundException {
		Response response;
		try {
			Collection collection = broker.getCollection(path);
			if(collection != null) {
				// remove the collection
				LOG.debug("removing collection " + path);
				broker.removeCollection(path);
				response = new Response();
				response.setDescription("Collection " + path + " removed.");
			} else {
				DocumentImpl doc = (DocumentImpl)broker.getDocument(path);
				if(doc == null)
					throw new NotFoundException("No document or collection found " +
							"for path: " + path);
				else {
					// remove the document
					LOG.debug("removing document " + path);
					int p = path.lastIndexOf('/');
					String docName = p < 0 || p == path.length() - 1 ? path : path.substring(p + 1);
					if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
						doc.getCollection().removeBinaryResource(broker, docName);
					else
						doc.getCollection().removeDocument(broker, docName);
					response = new Response();
					response.setDescription("Document " + path + " removed.");
				}
			}
		} catch (TriggerException e) {
			throw new PermissionDeniedException("Trigger failed: " + e.getMessage());
		} catch (LockException e) {
			throw new PermissionDeniedException("Could not acquire lock: " + e.getMessage());
		}
		return response;
	}
	
	/**
	 * TODO: pass request and response objects to XQuery.
	 */
	protected String search(DBBroker broker, String query, String path,
			int howmany, int start, Properties outputProperties)
	throws BadRequestException, PermissionDeniedException {
		String result = null;
		try {
			XQueryContext context = new XQueryContext(broker);
			DocumentSet docs = new DocumentSet();
			Collection collection = broker.getCollection(path);
			if (collection != null) {
				collection.allDocs(broker, docs, true);
			} else {
				DocumentImpl doc = (DocumentImpl) broker.getDocument(path);
				if (doc != null)
					docs.add(doc);
				else
					broker.getAllDocuments(docs);
			}
			context.setStaticallyKnownDocuments(docs);
			XQueryLexer lexer = new XQueryLexer(new StringReader(query));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new BadRequestException(parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new BadRequestException(treeParser.getErrorMessage());
			}
			LOG.info("query: " + expr.pprint());
			if (parser.foundErrors())
				throw new BadRequestException(parser.getErrorMessage());
			long startTime = System.currentTimeMillis();
			Sequence resultSequence = expr.eval(null, null);
			long queryTime = System.currentTimeMillis() - startTime;
			LOG.debug("Found " + resultSequence.getLength() + " in " + queryTime + "ms.");
			startTime = System.currentTimeMillis();
			return printResults(broker, resultSequence, howmany, start,
					queryTime, outputProperties);
		} catch (RecognitionException e) {
			throw new BadRequestException(e.getMessage(), e);
		} catch (TokenStreamException e) {
			throw new BadRequestException(e.getMessage(), e);
		} catch (XPathException e) {
			throw new BadRequestException(e.getMessage(), e);
		}
	}
	
	protected String printCollection(DBBroker broker, Collection collection) {
		SAXSerializer serializer = null;
		StringWriter writer = new StringWriter();
		try {
			serializer = SAXSerializerPool.getInstance().borrowSAXSerializer();
			serializer.setWriter(writer);
			serializer.setOutputProperties(defaultProperties);
			AttributesImpl attrs = new AttributesImpl();

			serializer.startDocument();
			serializer.startPrefixMapping("exist", NS);
			serializer.startElement(NS, "result", "exist:result", attrs);

			attrs.addAttribute("", "name", "name", "CDATA", collection
					.getName());
			printPermissions(attrs, collection.getPermissions());

			serializer
					.startElement(NS, "collection", "exist:collection", attrs);

			for (Iterator i = collection.collectionIterator(); i.hasNext(); ) {
				String child = (String) i.next();
				Collection childCollection = broker.getCollection(collection
						.getName()
						+ '/' + child);
				if(childCollection.getPermissions().validate(broker.getUser(), Permission.READ)) {
					attrs.clear();
					attrs.addAttribute("", "name", "name", "CDATA", child);
	
					attrs.addAttribute("", "created", "created", "CDATA",
							dateFormat.format(new Date(childCollection
									.getCreationTime())));
					printPermissions(attrs, childCollection.getPermissions());
					serializer.startElement(NS, "collection", "exist:collection",
							attrs);
					serializer.endElement(NS, "collection", "exist:collection");
				}
			}

			for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
				DocumentImpl doc = (DocumentImpl) i.next();
				if(doc.getPermissions().validate(broker.getUser(), Permission.READ)) {
					String resource = doc.getFileName();
					int p = resource.lastIndexOf('/');
					attrs.clear();
					attrs.addAttribute("", "name", "name", "CDATA", p < 0
							? resource
							: resource.substring(p + 1));
					attrs.addAttribute("", "created", "created", "CDATA",
							dateFormat.format(new Date(doc.getCreated())));
					attrs.addAttribute("", "last-modified", "last-modified",
							"CDATA", dateFormat.format(new Date(doc
									.getLastModified())));
					printPermissions(attrs, doc.getPermissions());
					serializer
							.startElement(NS, "resource", "exist:resource", attrs);
					serializer.endElement(NS, "resource", "exist:resource");
				}
			}

			serializer.endElement(NS, "collection", "exist:collection");
			serializer.endElement(NS, "result", "exist:result");

			serializer.endDocument();
		} catch (SAXException e) {
			// should never happen
			LOG.warn("Error while serializing collection contents: "
					+ e.getMessage(), e);
		} finally {
			SAXSerializerPool.getInstance().returnSAXSerializer(serializer);
		}
		return writer.toString();
	}

	protected void printPermissions(AttributesImpl attrs, Permission perm) {
		attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner());
		attrs.addAttribute("", "group", "group", "CDATA", perm.getOwnerGroup());
		attrs.addAttribute("", "permissions", "permissions", "CDATA", perm
				.toString());
	}

	protected String printResults(DBBroker broker, Sequence results,
			int howmany, int start, long queryTime, Properties outputProperties)
			throws BadRequestException {
		int rlen = results.getLength();
		if (rlen > 0) {
			if ((start < 1) || (start > rlen))
				throw new BadRequestException("Start parameter out of range");
			// FD : correct bound evaluation
			if (((howmany + start) > rlen) || (howmany <= 0))
				howmany = rlen - start + 1;
		} else
			howmany = 0;
		Serializer serializer = broker.getSerializer();
		serializer.reset();
		String stylesheet = outputProperties
				.getProperty(EXistOutputKeys.STYLESHEET);
		if (stylesheet != null)
			serializer.setStylesheet(stylesheet);
		SAXSerializer sax = null;
		try {
			StringWriter writer = new StringWriter();
			sax = SAXSerializerPool.getInstance().borrowSAXSerializer();
			sax.setWriter(writer);
			sax.setOutputProperties(outputProperties);
			serializer.setProperties(outputProperties);
			serializer.setContentHandler(sax);
			serializer.setLexicalHandler(sax);
			
			AttributesImpl attrs = new AttributesImpl();
			attrs.addAttribute("", "hits", "hits", "CDATA", Integer.toString(rlen));
			attrs.addAttribute("", "start", "start", "CDATA", Integer.toString(start));
			attrs.addAttribute("", "count", "count", "CDATA", Integer.toString(howmany));
			
			sax.startDocument();
			sax.startPrefixMapping("exist", NS);
			sax.startElement(NS, "result", "exist:result", attrs);
			
			Item item;
			for (int i = --start; i < start + howmany; i++) {
				item = results.itemAt(i);
				if (item == null) {
					LOG.debug("item " + i + " not found");
					continue;
				}
				if (item.getType() == Type.ELEMENT || item.getType() == Type.COMMENT ||
					item.getType() == Type.PROCESSING_INSTRUCTION) {
					NodeValue node = (NodeValue) item;
					serializer.toSAX(node);
				} else {
					attrs.clear();
					attrs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(item.getType()));
					sax.startElement(NS, "value", "exist:value", attrs);
					item.toSAX(broker, sax);
					sax.endElement(NS, "value", "exist:value");
				}
			}
			sax.endElement(NS, "result", "exist:result");
			sax.endPrefixMapping("exist");
			sax.endDocument();
			
			return writer.toString();
		} catch (SAXException e) {
			LOG.warn(e);
			throw new BadRequestException("Error while serializing xml: "
					+ e.toString(), e);
		}
	}
}
