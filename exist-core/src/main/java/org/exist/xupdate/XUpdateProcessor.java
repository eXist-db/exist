/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
/*
 *  Some modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as above under the LGPL.
 *  
 */
package org.exist.xupdate;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.NodeListImpl;
import org.exist.dom.persistent.NodeSetHelper;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Constants;
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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Main class to pre-process an XUpdate request. XUpdateProcessor
 * will parse the request via SAX and compile it into a set of
 * {@link Modification} objects as returned by the {@link #parse(org.xml.sax.InputSource)}
 * method. The modifications can then be executed via {@link Modification#process(org.exist.storage.txn.Txn)}.
 * 
 * @author Wolfgang Meier
 * 
 */
public class XUpdateProcessor implements ContentHandler, LexicalHandler {

	public static final String MODIFICATIONS = "modifications";
	
	// Modifications
	public static final String INSERT_AFTER = "insert-after";
	public static final String INSERT_BEFORE = "insert-before";
	public static final String REPLACE = "replace";
	public static final String RENAME = "rename";
	public static final String REMOVE = "remove";
	public static final String APPEND = "append";
	public static final String UPDATE = "update";
	
	// node constructors
	public static final String COMMENT = "comment";
	public static final String PROCESSING_INSTRUCTION = "processing-instruction";
	public static final String TEXT = "text";
	public static final String ATTRIBUTE = "attribute";
	public static final String ELEMENT = "element";
	
	public static final String VALUE_OF = "value-of";
	public static final String VARIABLE = "variable";
	public static final String IF = "if";
	
	public final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

	private final static Logger LOG = LogManager.getLogger(XUpdateProcessor.class);

    /**
     * NodeList to keep track of created document fragments within
     * the currently processed XUpdate modification.
     */
    private NodeListImpl contents = null;

    // Flags needed during SAX processing
    private boolean inModification = false;
	private boolean inAttribute = false;

    /**
     * Whitespace preservation: the XUpdate processor
     * will honour xml:space attribute settings.
     */
    private boolean preserveWhitespace = false;
    private boolean preserveWhitespaceTemp = false;

    /**
     * Stack to maintain xml:space settings. The items on the
     * stack are strings, containing either "default" or "preserve".
     */
    private Deque<String> spaceStack = null;

    /**
     * The modification we are currently processing.
     */
    private Modification modification = null;

    /** The DocumentBuilder used to create new nodes */
    private DocumentBuilder builder;

    /** The Document object used to create new nodes */
    private Document doc;

    /** The current element stack. Contains the last elements processed. */
    private Deque<Element> stack = new ArrayDeque<>();

    /** The last node that has been created */
    private Node currentNode = null;

    /** DBBroker for this instance */
    private DBBroker broker;

    /** The set of documents to which this XUpdate might apply. */
    private DocumentSet documentSet;

    /**
     * The final list of modifications. All modifications encountered
     * within the XUpdate will be added to this list. The final list
     * will be returned to the caller.
     */
    private List<Modification> modifications = new ArrayList<>();

    /** Temporary string buffer used for collecting text chunks */
    private final StringBuilder charBuf = new StringBuilder(64);

    // Environment

    /** Contains all variables declared via xupdate:variable.
     * Maps variable QName to the Sequence returned by
     * evaluating the variable expression.
     */
    private Map<String, Object> variables = new TreeMap<>();

    /**
     * Keeps track of namespaces declared within the XUpdate.
     */
    private Map<String, String> namespaces = new HashMap<>(10);

    /**
     * Stack used to track conditionals.
     */
    private Deque<Conditional> conditionals = new ArrayDeque<>();

	/**
	 * Constructor for XUpdateProcessor.
	 *
	 * @param broker the database broker
	 * @param docs the document set
	 *
	 * @throws ParserConfigurationException if the parser can't be configured
	 */
	public XUpdateProcessor(DBBroker broker, DocumentSet docs)
		throws ParserConfigurationException {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		this.builder = factory.newDocumentBuilder();
		this.broker = broker;
		this.documentSet = docs;
		//namespaces.put("xml", Namespaces.XML_NS);
		//TODO : move this to a dedicated configure() method.
		if (broker != null) {
			final Configuration config = broker.getConfiguration();
			Boolean temp;
			if ((temp = (Boolean) config.getProperty("indexer.preserve-whitespace-mixed-content"))
				!= null)
				{preserveWhitespaceTemp = temp;}
		}
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
	 * @param is the input source
	 *
	 * @return an array of type Modification
	 *
	 * @throws ParserConfigurationException of the parser cannot be configured
	 * @throws IOException if an I/O error occurs
	 * @throws SAXException if an error occurs whilst parsing
	 */
	public Modification[] parse(InputSource is)
		throws ParserConfigurationException, IOException, SAXException {
		final XMLReader reader = broker.getBrokerPool().getParserPool().borrowXMLReader();
		try {
			reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, this);
            reader.setFeature(Namespaces.SAX_NAMESPACES, true);
            reader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, false);
			reader.setContentHandler(this);
			
			reader.parse(is);
			final Modification mods[] = new Modification[modifications.size()];
			return modifications.toArray(mods);
		} finally {
			broker.getBrokerPool().getParserPool().returnXMLReader(reader);
		}
	}

	@Override
	public void setDocumentLocator(Locator locator) {
	}

	@Override
	public void startDocument() throws SAXException {
        // The default...
        this.preserveWhitespace = preserveWhitespaceTemp;
        this.spaceStack = new ArrayDeque<>();
        this.spaceStack.push("default");
	}

	@Override
	public void endDocument() throws SAXException {
	}

	@Override
	public void startPrefixMapping(String prefix, String uri)
		throws SAXException {
		namespaces.put(prefix, uri);
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
	}

	@Override
	public void startElement(
		String namespaceURI,
		String localName,
		String qName,
		Attributes atts)
		throws SAXException {
		// save accumulated character content
		if (inModification && !charBuf.isEmpty()) {
//            String normalized = charBuf.toString();
			final String normalized = preserveWhitespace ? charBuf.toString() :
					charBuf.toString().trim();

			if (!normalized.isEmpty()) {
				final Text text = doc.createTextNode(charBuf.toString());
				final Element last = stack.peek();
				if (last == null) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (namespaceURI.equals(XUPDATE_NS)) {
			String select = null;
			switch (localName) {
				case MODIFICATIONS:
					startModifications(atts);
					return;
				case VARIABLE:
					// variable declaration
					startVariableDecl(atts);
					return;
				case IF:
					if (inModification) {
						throw new SAXException("xupdate:if is not allowed inside a modification");
					}
					select = atts.getValue("test");
					final Conditional cond = new Conditional(broker, documentSet, select, namespaces, variables);
					conditionals.push(cond);
					return;
				case VALUE_OF:
					if (!inModification) {
						throw new SAXException("xupdate:value-of is not allowed outside a modification");
					}

					break;
				case APPEND:
				case INSERT_BEFORE:
				case INSERT_AFTER:
				case REMOVE:
				case RENAME:
				case UPDATE:
				case REPLACE:
					if (inModification) {
						throw new SAXException("nested modifications are not allowed");
					}
					select = atts.getValue("select");
					if (select == null) {
						throw new SAXException(
								localName + " requires a select attribute");
					}
					doc = builder.newDocument();
					contents = new NodeListImpl();
					inModification = true;
					break;
				case ELEMENT:
				case ATTRIBUTE:
				case TEXT:
				case PROCESSING_INSTRUCTION:
				case COMMENT:
					if (!inModification) {
						throw new SAXException(
								"creation elements are only allowed inside "
										+ "a modification");
					}
					charBuf.setLength(0);
					break;
				default:
					throw new SAXException("Unknown XUpdate element: " + qName);
			}

			// start a new modification section
			switch (localName) {
				case APPEND:
					final String child = atts.getValue("child");
					modification = new Append(broker, documentSet, select, child, namespaces, variables);
					break;
				case UPDATE:
					modification = new Update(broker, documentSet, select, namespaces, variables);
					break;
				case INSERT_BEFORE:
					modification =
							new Insert(broker, documentSet, select, Insert.INSERT_BEFORE, namespaces, variables);
					break;
				case INSERT_AFTER:
					modification =
							new Insert(broker, documentSet, select, Insert.INSERT_AFTER, namespaces, variables);
					break;
				case REMOVE:
					modification = new Remove(broker, documentSet, select, namespaces, variables);
					break;
				case RENAME:
					modification = new Rename(broker, documentSet, select, namespaces, variables);
					break;
				case REPLACE:
					modification = new Replace(broker, documentSet, select, namespaces, variables);
					break;

				// process commands for node creation
				case ELEMENT: {
					String name = atts.getValue("name");
					if (name == null) {
						throw new SAXException("element requires a name attribute");
					}
					final int p = name.indexOf(':');
					String namespace = null;
					String prefix = "";
					if (p != Constants.STRING_NOT_FOUND) {
						prefix = name.substring(0, p);
						if (name.length() == p + 1) {
							throw new SAXException(
									"illegal prefix in qname: " + name);
						}
						name = name.substring(p + 1);
						namespace = atts.getValue("namespace");
						if (namespace == null) {
							namespace = namespaces.get(prefix);
						}
						if (namespace == null) {
							throw new SAXException(
									"no namespace defined for prefix " + prefix);
						}
					}
					Element elem;
					if (namespace != null && !namespace.isEmpty()) {
						elem = doc.createElementNS(namespace, name);
						elem.setPrefix(prefix);
					} else {
						elem = doc.createElement(name);
					}

					final Element last = stack.peek();
					if (last == null) {
						contents.add(elem);
					} else {
						last.appendChild(elem);
					}

					stack.push(elem);
					this.setWhitespaceHandling(elem);
					break;
				}
				case ATTRIBUTE: {
					final String name = atts.getValue("name");
					if (name == null) {
						throw new SAXException("attribute requires a name attribute");
					}
					final int p = name.indexOf(':');
					String namespace = null;
					if (p != Constants.STRING_NOT_FOUND) {
						final String prefix = name.substring(0, p);
						if (name.length() == p + 1) {
							throw new SAXException(
									"illegal prefix in qname: " + name);
						}
						namespace = atts.getValue("namespace");
						if (namespace == null) {
							namespace = namespaces.get(prefix);
						}
						if (namespace == null) {
							throw new SAXException(
									"no namespace defined for prefix " + prefix);
						}
					}
					Attr attrib = namespace != null && !namespace.isEmpty() ?
							doc.createAttributeNS(namespace, name) :
							doc.createAttribute(name);
					if (stack.isEmpty()) {
						for (int i = 0; i < contents.getLength(); i++) {
							final Node n = contents.item(i);
							String ns = n.getNamespaceURI();
							final String nname = ns == null ? n.getNodeName() : n.getLocalName();
							if (ns == null) {
								ns = XMLConstants.NULL_NS_URI;
							}
							// check for duplicate attributes
							if (n.getNodeType() == Node.ATTRIBUTE_NODE &&
									nname.equals(name) &&
									ns.equals(namespace)) {
								throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified twice");
							}
						}
						contents.add(attrib);
					} else {
						final Element last = stack.peek();
						if (namespace != null && last.hasAttributeNS(namespace, name) ||
								namespace == null && last.hasAttribute(name)) {
							throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified " +
									"twice on the same element");
						}
						if (namespace != null) {
							last.setAttributeNodeNS(attrib);
						} else {
							last.setAttributeNode(attrib);
						}
					}
					inAttribute = true;
					currentNode = attrib;

					// process value-of
					break;
				}
				case VALUE_OF:
					select = atts.getValue("select");
					if (select == null) {
						throw new SAXException("value-of requires a select attribute");
					}
					final Sequence seq = processQuery(select);
					if (LOG.isDebugEnabled()) {
						LOG.debug("Found {} items for value-of", seq.getItemCount());
					}
					Item item;
					try {
						for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
							item = i.nextItem();
							if (Type.subTypeOf(item.getType(), Type.NODE)) {
								final Node node = NodeSetHelper.copyNode(doc, ((NodeValue) item).getNode());
								final Element last = stack.peek();
								if (last == null) {
									contents.add(node);
								} else {
									last.appendChild(node);
								}
							} else {
								final String value = item.getStringValue();
								characters(value.toCharArray(), 0, value.length());
							}
						}
					} catch (final XPathException e) {
						throw new SAXException(e.getMessage(), e);
					}
					break;
			}
		} else if (inModification) {
			final Element elem = namespaceURI != null && !namespaceURI.isEmpty() ?
									doc.createElementNS(namespaceURI, qName) :
									doc.createElement(qName);
			Attr a;
			for (int i = 0; i < atts.getLength(); i++) {
                final String name = atts.getQName(i);
                final String nsURI = atts.getURI(i);
                if (name.startsWith("xmlns")) {
                    // Why are these showing up? They are supposed to be stripped out?
                } else {
                    a = nsURI != null ?
                          doc.createAttributeNS(nsURI, name) :
                            doc.createAttribute(name);
                    a.setValue(atts.getValue(i));
                    if (nsURI != null)
                    {elem.setAttributeNodeNS(a);}
                    else
                      {elem.setAttributeNode(a);}
                }
			}
			final Element last = stack.peek();
			if (last == null) {
				contents.add(elem);
			} else {
				last.appendChild(elem);
			}

			stack.push(elem);
            this.setWhitespaceHandling(elem);
		}
	}

	private void startVariableDecl(Attributes atts) throws SAXException {
		final String select = atts.getValue("select");
		if (select == null)
			{throw new SAXException("variable declaration requires a select attribute");}
		final String name = atts.getValue("name");
		if (name == null)
			{throw new SAXException("variable declarations requires a name attribute");}
		createVariable(name, select);
	}

	private void startModifications(Attributes atts) throws SAXException {
		final String version = atts.getValue("version");
		if (version == null)
			{throw new SAXException(
				"version attribute is required for "
					+ "element modifications");}
		if (!"1.0".equals(version))
			{throw new SAXException(
				"Version "
					+ version
					+ " of XUpdate "
					+ "not supported.");}
	}

	@Override
	public void endElement(String namespaceURI, String localName, String qName)
		throws SAXException {
		if (inModification && !charBuf.isEmpty()) {
			final String normalized = preserveWhitespace ? charBuf.toString() :
					charBuf.toString().trim();
			if (!normalized.isEmpty()) {
				final Text text = doc.createTextNode(charBuf.toString());
				final Element last = stack.peek();
				if (last == null) {
					contents.add(text);
				} else {
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (XUPDATE_NS.equals(namespaceURI)) {
			if (IF.equals(localName)) {
				final Conditional cond = conditionals.pop();
				modifications.add(cond);
			} else if (localName.equals(ELEMENT)) {
				this.resetWhitespaceHandling(stack.pop());
			} else if (localName.equals(ATTRIBUTE)) {
				inAttribute = false;
			} else if (localName.equals(APPEND)
				|| localName.equals(UPDATE)
				|| localName.equals(REMOVE)
				|| localName.equals(RENAME)
				|| localName.equals(REPLACE)
				|| localName.equals(INSERT_BEFORE)
				|| localName.equals(INSERT_AFTER)) {
				inModification = false;
				modification.setContent(contents);
				final Conditional cond = conditionals.peek();
				if(cond != null) {
					cond.addModification(modification);
				} else {
					modifications.add(modification);
				}
				modification = null;
			}
		} else if (inModification) {
            this.resetWhitespaceHandling(stack.pop());
        }
	}

	@Override
	public void characters(char[] ch, int start, int length)
		throws SAXException {
		if (inModification) {
			if (inAttribute) {
			    final Attr attr = (Attr)currentNode;
			    String val = attr.getValue();
			    if(val == null)
			        {val = new String(ch, start, length);}
			    else
			        {val += new String(ch, start, length);}
				attr.setValue(val);
			} else {
				charBuf.append(ch, start, length);
			}
		}
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
		throws SAXException {
        if (this.preserveWhitespace) {
            if (this.inModification) {
                if (this.inAttribute) {
                    final Attr attr = (Attr) this.currentNode;
                    String val = attr.getValue();
                    if(val == null)
                        {val = new String(ch, start, length);}
                    else
                        {val += new String(ch, start, length);}
                    attr.setValue(val);
                } else {
                    this.charBuf.append(ch, start, length);
                }
            }
        }
	}
    
    private void setWhitespaceHandling(Element e) {
        final String wsSetting = e.getAttributeNS(Namespaces.XML_NS, "space");
        if ("preserve".equals(wsSetting)) {
            this.spaceStack.push(wsSetting);
            this.preserveWhitespace = true;
        } else if ("default".equals(wsSetting)) {
            this.spaceStack.push(wsSetting);
            this.preserveWhitespace = preserveWhitespaceTemp;
        }
        // Otherwise, don't change what's currently in effect!
    }
    
    private void resetWhitespaceHandling(Element e) {
        final String wsSetting = e.getAttributeNS(Namespaces.XML_NS, "space");
        if ("preserve".equals(wsSetting) || "default".equals(wsSetting)) {
            // Since an opinion was expressed, restore what was previously set:
            this.spaceStack.pop();
            if (this.spaceStack.isEmpty()) {
                // This is the default...
                this.preserveWhitespace = preserveWhitespaceTemp;
            } else {
                this.preserveWhitespace = ("preserve".equals(this.spaceStack.peek()));
            }
        }
    }

	@Override
	public void processingInstruction(String target, String data)
		throws SAXException {
		if (inModification && !charBuf.isEmpty()) {
			final String normalized =
					charBuf.toString().trim();
			if (!normalized.isEmpty()) {
				final Text text = doc.createTextNode(normalized);
				final Element last = stack.peek();
				if (last == null) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("appending text to fragment: {}", text.getData());
					}
					contents.add(text);
				} else {
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			final ProcessingInstruction pi =
				doc.createProcessingInstruction(target, data);
			final Element last = stack.peek();
			if (last == null) {
				contents.add(pi);
			} else {
				last.appendChild(pi);
			}
		}
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
	}

	private void createVariable(String name, String select)
		throws SAXException {
		if (LOG.isDebugEnabled())
			{
				LOG.debug("creating variable {} as {}", name, select);}
		
		final Sequence result = processQuery(select);
		
		if (LOG.isDebugEnabled())
			{
				LOG.debug("found {} for variable {}", result.getItemCount(), name);}
		
		variables.put(name, result);
	}
	
	private Sequence processQuery(String select) throws SAXException {
        XQueryContext context = null;
        try {
			context = new XQueryContext(broker.getBrokerPool());
			context.setStaticallyKnownDocuments(documentSet);
			Map.Entry<String, String> namespaceEntry;
			for (Map.Entry<String, String> stringStringEntry : namespaces.entrySet()) {
				namespaceEntry = stringStringEntry;
				context.declareNamespace(
						namespaceEntry.getKey(),
						namespaceEntry.getValue());
			}
			Map.Entry<String, Object> entry;
			for (Map.Entry<String, Object> stringObjectEntry : variables.entrySet()) {
				entry = stringObjectEntry;
				context.declareVariable(entry.getKey(), entry.getValue());
			}
			// TODO(pkaminsk2): why replicate XQuery.compile here?
			final XQueryLexer lexer = new XQueryLexer(context, new StringReader(select));
			final XQueryParser parser = new XQueryParser(lexer);
			final XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new SAXException(parser.getErrorMessage());
			}

			final AST ast = parser.getAST();
			
			if (LOG.isDebugEnabled())
				{
					LOG.debug("generated AST: {}", ast.toStringTree());}

			final PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new SAXException(treeParser.getErrorMessage());
			}
			expr.analyze(new AnalyzeContextInfo());
			final Sequence seq = expr.eval(null, null);
			return seq;
		} catch (final RecognitionException | TokenStreamException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (final XPathException e) {
			throw new SAXException(e);
		} finally {
            if (context != null) {
				context.reset(false);
				context.runCleanupTasks();
			}
        }
	}

	@Override
	public void comment(char[] ch, int start, int length) throws SAXException {
		if (inModification && !charBuf.isEmpty()) {
			final String normalized =
					charBuf.toString().trim();
			if (!normalized.isEmpty()) {
				final Text text = doc.createTextNode(normalized);
				final Element last = stack.peek();
				if (last == null) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			final Comment comment = doc.createComment(new String(ch, start, length));
			final Element last = stack.peek();
			if (last == null) {
				contents.add(comment);
			} else {
				last.appendChild(comment);
			}
		}
	}

	@Override
	public void endCDATA() throws SAXException {
	}

	@Override
	public void endDTD() throws SAXException {
	}

	@Override
	public void endEntity(String name) throws SAXException {
	}

	@Override
	public void startCDATA() throws SAXException {
	}

	@Override
	public void startDTD(String name, String publicId, String systemId)
		throws SAXException {
	}

	@Override
	public void startEntity(String name) throws SAXException {
	}

	public void reset() {
        this.preserveWhitespace = false;
        this.spaceStack = null;
        
	    this.inModification = false;
		this.inAttribute = false;
		this.modification = null;
		this.doc = null;
		this.contents = null;
		this.stack.clear();
		this.currentNode = null;
		this.broker = null;
		this.documentSet = null;
		this.modifications.clear();
		this.charBuf.setLength(0);
		this.variables.clear();
		this.namespaces.clear();
		this.conditionals.clear();
		//this.namespaces.put("xml", Namespaces.XML_NS);
	}
}
