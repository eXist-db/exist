package org.exist.xupdate;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeListImpl;
import org.exist.dom.XMLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.FastStringBuffer;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * XUpdateProcessor.java
 * 
 * @author Wolfgang Meier
 * 
 */
public class XUpdateProcessor implements ContentHandler, LexicalHandler {

	public final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

	private final static Logger LOG = Logger.getLogger(XUpdateProcessor.class);

	private NodeListImpl contents = null;
	
	private boolean inModification = false;
	private boolean inAttribute = false;
	
	private Modification modification = null;
	private DocumentBuilder builder;
	private Document doc;
	
	private Stack stack = new Stack();
	private Node currentNode = null;
	private DBBroker broker;
	private DocumentSet documentSet;
	
	private List modifications = new ArrayList();
	private Stack conditionals = new Stack();
	
	private FastStringBuffer charBuf = new FastStringBuffer(6, 15, 5);
	
	private Map variables = new TreeMap();
	private Map namespaces = new HashMap(10);

	/**
	 * Constructor for XUpdateProcessor.
	 */
	public XUpdateProcessor(DBBroker broker, DocumentSet docs)
		throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		builder = factory.newDocumentBuilder();
		this.broker = broker;
		this.documentSet = docs;
		namespaces.put("xml", "http://www.w3.org/XML/1998/namespace");
	}

	public XUpdateProcessor() throws ParserConfigurationException {
	    this(null, null);
	}
	
	public void setBroker(DBBroker broker) {
	    this.broker = broker;
	}
	
	public void setDocumentSet(DocumentSet docs) {
	    this.documentSet = docs;
	}
	
	/**
	 * Parse the input source into a set of modifications.
	 * 
	 * @param is
	 * @return an array of type Modification
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public Modification[] parse(InputSource is)
		throws ParserConfigurationException, IOException, SAXException {
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		saxFactory.setNamespaceAware(true);
		saxFactory.setValidating(false);
		SAXParser sax = saxFactory.newSAXParser();
		XMLReader reader = sax.getXMLReader();
		reader.setProperty(
				"http://xml.org/sax/properties/lexical-handler",
				this);
		reader.setContentHandler(this);
		
		reader.parse(is);
		Modification mods[] = new Modification[modifications.size()];
		return (Modification[]) modifications.toArray(mods);
	}

	/**
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
	}

	/**
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri)
		throws SAXException {
		namespaces.put(prefix, uri);
	}

	/**
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
	}

	/**
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
		String namespaceURI,
		String localName,
		String qName,
		Attributes atts)
		throws SAXException {
		// save accumulated character content
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (namespaceURI.equals(XUPDATE_NS)) {
			if (localName.equals("modifications")) {
				startModifications(atts);
				return;
			}

			// variable declaration
			if (localName.equals("variable")) {
				startVariableDecl(atts);
				return;
			}
			
			String select = null;
			if ("if".equals(localName)) {
				if (inModification)
					throw new SAXException("xupdate:if is not allowed inside a modification");
				select = atts.getValue("test");
				Conditional cond = new Conditional(broker, documentSet, select, namespaces);
				conditionals.push(cond);
				return;
			} else if ("append".equals(localName)
				|| "insert-before".equals(localName)
				|| "insert-after".equals(localName)
				|| "remove".equals(localName)
				|| "rename".equals(localName)
				|| "update".equals(localName)) {
				if (inModification)
					throw new SAXException("nested modifications are not allowed");
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException(
						localName + " requires a select attribute");
				doc = builder.newDocument();
				contents = new NodeListImpl();
				inModification = true;
			} else if (
				("element".equals(localName)
					|| "attribute".equals(localName)
					|| "text".equals(localName)
					|| "processing-instruction".equals(localName)
					|| "comment".equals(localName))
					&& (!inModification))
				throw new SAXException(
					"creation elements are only allowed inside "
						+ "a modification");

			// start a new modification section
			if ("append".equals(localName)) {
			    String child = atts.getValue("child");
				modification = new Append(broker, documentSet, select, child, namespaces);
			} else if ("update".equals(localName))
				modification = new Update(broker, documentSet, select, namespaces);
			else if ("insert-before".equals(localName))
				modification =
					new Insert(broker, documentSet, select, Insert.INSERT_BEFORE, namespaces);
			else if ("insert-after".equals(localName))
				modification =
					new Insert(broker, documentSet, select, Insert.INSERT_AFTER, namespaces);
			else if ("remove".equals(localName))
				modification = new Remove(broker, documentSet, select, namespaces);
			else if ("rename".equals(localName))
				modification = new Rename(broker, documentSet, select, namespaces);

			// process commands for node creation
			else if ("element".equals(localName)) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("element requires a name attribute");
				int p = name.indexOf(':');
				String namespace = "";
				String prefix = "";
				if (p > -1) {
					prefix = name.substring(0, p);
					if (name.length() == p + 1)
						throw new SAXException(
							"illegal prefix in qname: " + name);
					name = name.substring(p + 1);
					namespace = (String) namespaces.get(prefix);
					if (namespace == null) {
						throw new SAXException(
							"no namespace defined for prefix " + prefix);
					}
				}
				Element elem = doc.createElementNS(namespace, name);
				elem.setPrefix(prefix);
				if (stack.isEmpty()) {
					contents.add(elem);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(elem);
				}
				stack.push(elem);
			} else if ("attribute".equals(localName)) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("attribute requires a name attribute");
				int p = name.indexOf(':');
				String namespace = "";
				if (p > -1) {
					String prefix = name.substring(0, p);
					if (name.length() == p + 1)
						throw new SAXException(
							"illegal prefix in qname: " + name);
					name = name.substring(p + 1);
					namespace = (String) namespaces.get(prefix);
					if (namespace == null)
						throw new SAXException(
							"no namespace defined for prefix " + prefix);
				}
				Attr attrib = doc.createAttributeNS(namespace, name);
				if (stack.isEmpty()) {
					for(int i = 0; i < contents.getLength(); i++) {
						Node n = contents.item(i);
						String ns = n.getNamespaceURI();
						if(ns == null) ns = "";
						if(n.getNodeType() == Node.ATTRIBUTE_NODE &&
								n.getLocalName().equals(name) &&
								ns.equals(namespace))
							throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified twice");
					}
					contents.add(attrib);
				} else {
					Element last = (Element) stack.peek();
					if(last.hasAttributeNS(namespace, name))
						throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified " +
								"twice on the same element");
					last.setAttributeNode(attrib);
				}
				inAttribute = true;
				currentNode = attrib;

				// process value-of
			} else if ("value-of".equals(localName)) {
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException("value-of requires a select attribute");
				Sequence seq = processQuery(select);
				LOG.debug("Found " + seq.getLength() + " items for value-of");
				Item item;
				for (SequenceIterator i = seq.iterate(); i.hasNext();) {
					item = i.nextItem();
					if(Type.subTypeOf(item.getType(), Type.NODE)) { 
						Node node = XMLUtil.copyNode(doc, ((NodeValue)item).getNode());
						if (stack.isEmpty())
							contents.add(node);
						else {
							Element last = (Element) stack.peek();
							last.appendChild(node);
						}
					} else {
						try {
							String value = item.getStringValue();
							characters(value.toCharArray(), 0, value.length());
						} catch(XPathException e) {
							throw new SAXException(e.getMessage(), e);
						}
					}
				}
			}
		} else if (inModification) {
			Element elem = doc.createElementNS(namespaceURI, qName);
			Attr a;
			for (int i = 0; i < atts.getLength(); i++) {
				a = doc.createAttributeNS(atts.getURI(i), atts.getQName(i));
				a.setValue(atts.getValue(i));
				elem.setAttributeNodeNS(a);
			}
			if (stack.isEmpty()) {
				contents.add(elem);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(elem);
			}
			stack.push(elem);
		}
	}

	private void startVariableDecl(Attributes atts) throws SAXException {
		String select = atts.getValue("select");
		if (select == null)
			throw new SAXException("variable declaration requires a select attribute");
		String name = atts.getValue("name");
		if (name == null)
			throw new SAXException("variable declarations requires a name attribute");
		createVariable(name, select);
	}

	private void startModifications(Attributes atts) throws SAXException {
		String version = atts.getValue("version");
		if (version == null)
			throw new SAXException(
				"version attribute is required for "
					+ "element modifications");
		if (!version.equals("1.0"))
			throw new SAXException(
				"Version "
					+ version
					+ " of XUpdate "
					+ "not supported.");
	}

	/**
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName)
		throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (XUPDATE_NS.equals(namespaceURI)) {
			if ("if".equals(localName)) {
				Conditional cond = (Conditional) conditionals.pop();
				modifications.add(cond);
			} else if (localName.equals("element")) {
				stack.pop();
			} else if (localName.equals("attribute")) {
				inAttribute = false;
			} else if (localName.equals("append")
				|| localName.equals("update")
				|| localName.equals("remove")
				|| localName.equals("rename")
				|| localName.equals("insert-before")
				|| localName.equals("insert-after")) {
				inModification = false;
				modification.setContent(contents);
				if(!conditionals.isEmpty()) {
					Conditional cond = (Conditional) conditionals.peek();
					cond.addModification(modification);
				} else {
					modifications.add(modification);
				}
				modification = null;
			}
		} else if (inModification)
			stack.pop();
	}

	/**
	 * @see org.xml.sax.ContentHandler#characters(char, int, int)
	 */
	public void characters(char[] ch, int start, int length)
		throws SAXException {
		if (inModification) {
			if (inAttribute) {
			    Attr attr = (Attr)currentNode;
			    String val = attr.getValue();
			    if(val == null)
			        val = new String(ch, start, length);
			    else
			        val += new String(ch, start, length);
				attr.setValue(val);
			} else {
				charBuf.append(ch, start, length);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char, int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length)
		throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
		throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			ProcessingInstruction pi =
				doc.createProcessingInstruction(target, data);
			if (stack.isEmpty()) {
				contents.add(pi);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(pi);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
	}

	private void createVariable(String name, String select)
		throws SAXException {
		LOG.debug("creating variable " + name + " as " + select);
		Sequence result = processQuery(select);
		LOG.debug("found " + result.getLength() + " for variable " + name);
		variables.put(name, result);
	}

	private Sequence processQuery(String select) throws SAXException {
		try {
			XQueryContext context = new XQueryContext(broker);
			context.setStaticallyKnownDocuments(documentSet);
			Map.Entry entry;
			for (Iterator i = namespaces.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				context.declareNamespace(
					(String) entry.getKey(),
					(String) entry.getValue());
			}
			for (Iterator i = variables.entrySet().iterator(); i.hasNext(); ) {
				entry = (Map.Entry) i.next();
				context.declareVariable(entry.getKey().toString(), entry.getValue());
			}
			XQueryLexer lexer = new XQueryLexer(context, new StringReader(select));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new SAXException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new SAXException(treeParser.getErrorMessage());
			}

			Sequence seq = expr.eval(null, null);
			return seq;
		} catch (RecognitionException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (TokenStreamException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (XPathException e) {
			throw new SAXException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			Comment comment = doc.createComment(new String(ch, start, length));
			if (stack.isEmpty()) {
				contents.add(comment);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(comment);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endCDATA()
	 */
	public void endCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endDTD()
	 */
	public void endDTD() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
	 */
	public void endEntity(String name) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startCDATA()
	 */
	public void startCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void startDTD(String name, String publicId, String systemId)
		throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	public void startEntity(String name) throws SAXException {
	}

	public void reset() {
	    inModification = false;
		inAttribute = false;
		modification = null;
		doc = null;
		contents = null;
		stack.clear();
		currentNode = null;
		broker = null;
		documentSet = null;
		modifications.clear();
		charBuf.setLength(0);
		variables.clear();
		namespaces.clear();
		namespaces.put("xml", "http://www.w3.org/XML/1998/namespace");
	}
}
