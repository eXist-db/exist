package org.exist.xupdate;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.FastStringBuffer;
import org.exist.util.XMLUtil;
import org.exist.xpath.PathExpr;
import org.exist.xpath.RootNode;
import org.exist.xpath.Value;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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

/**
 * XUpdateProcessor.java
 * 
 * @author Wolfgang Meier
 * 
 * TODO:
 *   xupdate:processing-instruction
 *   xupdate:comment
 */
public class XUpdateProcessor implements ContentHandler, LexicalHandler {

	public final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

	private final static Logger LOG = Logger.getLogger(XUpdateProcessor.class);

	private boolean inModification = false;
	private boolean inAttribute = false;
	private Modification modification = null;
	private DocumentBuilder builder;
	private Document doc;
	private DocumentFragment fragment = null;
	private Stack stack = new Stack();
	private Node currentNode = null;
	private BrokerPool pool;
	private User user;
	private DocumentSet documentSet;
	private ArrayList modifications = new ArrayList();
	private FastStringBuffer charBuf = new FastStringBuffer(6, 15, 5);
	private TreeMap variables = new TreeMap();

	/**
	 * Constructor for XUpdateProcessor.
	 */
	public XUpdateProcessor(BrokerPool pool, User user,
		DocumentSet docs) throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		builder = factory.newDocumentBuilder();
		this.pool = pool;
		this.user = user;
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
		reader.setContentHandler(this);
		reader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
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
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
		throws SAXException {
		// save accumulated character content
		if (inModification && charBuf.length() > 0) {
			final String normalized = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					LOG.debug("appending text to fragment: " + text.getData());
					fragment.appendChild(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (namespaceURI.equals(XUPDATE_NS)) {
			if (localName.equals("modifications")) {
				String version = atts.getValue("version");
				if (version == null)
					throw new SAXException(
						"version attribute is required for " + "element modifications");
				if (!version.equals("1.0"))
					throw new SAXException(
						"Version " + version + " of XUpdate " + "not supported.");
				return;
			}
			String select = null;

			// variable declaration
			if (localName.equals("variable")) {
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException("variable declaration requires a select attribute");
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("variable declarations requires a name attribute");
				createVariable(name, select);
				return;
			}

			if (localName.equals("append")
				|| localName.equals("insert-before")
				|| localName.equals("insert-after")
				|| localName.equals("remove")
				|| localName.equals("rename")
				|| localName.equals("update")) {
				if (inModification)
					throw new SAXException("nested modifications are not allowed");
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException(localName + " requires a select attribute");
				doc = builder.newDocument();
				fragment = doc.createDocumentFragment();
				inModification = true;
			} else if (
				(localName.equals("element")
					|| localName.equals("attribute")
					|| localName.equals("text")
					|| localName.equals("processing-instruction")
					|| localName.equals("comment"))
					&& (!inModification))
				throw new SAXException(
					"creation elements are only allowed inside " + "a modification");

			// start a new modification section
			if (localName.equals("append"))
				modification = new Append(pool, user, documentSet, select);
			else if (localName.equals("update"))
				modification = new Update(pool, user, documentSet, select);
			else if (localName.equals("insert-before"))
				modification = new Insert(pool, user, documentSet, select, Insert.INSERT_BEFORE);
			else if (localName.equals("insert-after"))
				modification = new Insert(pool, user, documentSet, select, Insert.INSERT_AFTER);
			else if (localName.equals("remove"))
				modification = new Remove(pool, user, documentSet, select);
			else if (localName.equals("rename"))
				modification = new Rename(pool, user, documentSet, select);

			// process commands for node creation
			else if (localName.equals("element")) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("element requires a name attribute");
				Element elem = doc.createElement(name);
				if (stack.isEmpty()) {
					fragment.appendChild(elem);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(elem);
				}
				stack.push(elem);
			} else if (localName.equals("attribute")) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("attribute requires a name attribute");
				Attr attrib = doc.createAttribute(name);
				if (stack.isEmpty())
					fragment.appendChild(attrib);
				else {
					Element last = (Element) stack.peek();
					last.setAttributeNode(attrib);
				}
				inAttribute = true;
				currentNode = attrib;
			
			// process value-of
			} else if(localName.equals("value-of")) {
				select = atts.getValue("select");
				if(select == null)
					throw new SAXException("value-of requires a select attribute");
				List nodes;
				if(select.startsWith("$")) {
					nodes = (List)variables.get(select);
					if(nodes == null)
						throw new SAXException("variable " + select + " not found");
				} else
					nodes = processQuery(select);
				LOG.debug("found " + nodes.size() + " nodes for value-of");
				Node node;
				for(Iterator i = nodes.iterator(); i.hasNext(); ) {
					node = XMLUtil.copyNode(doc, (Node)i.next());
					if(stack.isEmpty())
						fragment.appendChild(node);
					else {
						Element last = (Element) stack.peek();
						last.appendChild(node);
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
				fragment.appendChild(elem);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(elem);
			}
			stack.push(elem);
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName)
		throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					fragment.appendChild(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (namespaceURI.equals(XUPDATE_NS)) {
			if (localName.equals("element")) {
				stack.pop();
			} else if (localName.equals("attribute"))
				inAttribute = false;
			if (localName.equals("append")
				|| localName.equals("update")
				|| localName.equals("remove")
				|| localName.equals("rename")
				|| localName.equals("insert-before")
				|| localName.equals("insert-after")) {
				inModification = false;
				modification.setContent(fragment);
				modifications.add(modification);
				modification = null;
			}
		} else if (inModification)
			stack.pop();
	}

	/**
	 * @see org.xml.sax.ContentHandler#characters(char, int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (inModification) {
			if (inAttribute)
				 ((Attr) currentNode).setValue(new String(ch, start, length));
			else {
				charBuf.append(ch, start, length);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char, int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					LOG.debug("appending text to fragment: " + text.getData());
					fragment.appendChild(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if(inModification) {
			ProcessingInstruction pi = doc.createProcessingInstruction(target, data);
			if (stack.isEmpty()) {
				fragment.appendChild(pi);
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

	private void createVariable(String name, String select) throws SAXException {
		LOG.debug("creating variable " + name + " as " + select);
		List result = processQuery(select);
		LOG.debug("found " + result.size() + " for variable " + name);
		variables.put('$' + name, result);
	}
	
	private List processQuery(String select) throws SAXException {
		try {
			XPathLexer lexer = new XPathLexer(new StringReader(select));
			XPathParser parser = new XPathParser(pool, user, lexer);
			PathExpr expr = new PathExpr(pool);
			RootNode root = new RootNode(pool);
			expr.add(root);
			parser.expr(expr);
			if (parser.foundErrors())
				throw new SAXException(parser.getErrorMsg());
			DocumentSet ndocs = expr.preselect(documentSet);
			if (ndocs.getLength() == 0)
				return new ArrayList(1);

			Value resultValue = expr.eval(documentSet, null, null);
			if (!(resultValue.getType() == Value.isNodeList))
				throw new SAXException("select expression should evaluate to a" + "node-set");
			NodeList set = resultValue.getNodeList();
			ArrayList out = new ArrayList(set.getLength());
			for (int i = 0; i < set.getLength(); i++) {
				out.add(set.item(i));
			}
			return out;
		} catch (RecognitionException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (TokenStreamException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (PermissionDeniedException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (EXistException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		}
	}
	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
		if (inModification && charBuf.length() > 0) {
					final String normalized = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
					if (normalized.length() > 0) {
						Text text = doc.createTextNode(normalized);
						if (stack.isEmpty()) {
							LOG.debug("appending text to fragment: " + text.getData());
							fragment.appendChild(text);
						} else {
							Element last = (Element) stack.peek();
							last.appendChild(text);
						}
					}
					charBuf.setLength(0);
				}
				if(inModification) {
					Comment comment = doc.createComment(new String(ch, start, length));
					if (stack.isEmpty()) {
						fragment.appendChild(comment);
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
	public void startDTD(String name, String publicId, String systemId) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	public void startEntity(String name) throws SAXException {
	}

}
