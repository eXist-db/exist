package org.exist.xupdate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.FastStringBuffer;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * XUpdateProcessor.java
 * 
 * @author Wolfgang Meier
 */
public class XUpdateProcessor implements ContentHandler {

	public final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

    private final static Logger LOG = Logger.getLogger(XUpdateProcessor.class);
    
	private boolean inModifications = false;
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
    private ArrayList modifications = new ArrayList();
    protected FastStringBuffer charBuf =
            new FastStringBuffer( 6, 15, 5 );
             
	/**
	 * Constructor for XUpdateProcessor.
	 */
	public XUpdateProcessor(BrokerPool pool, User user) 
    throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		builder = factory.newDocumentBuilder();
        this.pool = pool;
        this.user = user;
	}

	public Modification[] parse(InputSource is)
		throws ParserConfigurationException, IOException, SAXException {
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		saxFactory.setNamespaceAware(true);
		saxFactory.setValidating(false);
		SAXParser sax = saxFactory.newSAXParser();
		XMLReader reader = sax.getXMLReader();
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
	}

	/**
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
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
		if (namespaceURI.equals(XUPDATE_NS)) {
			if (localName.equals("modifications")) {
				inModifications = true;
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
				return;
			}
			String select = null;
			if (localName.equals("append")
				|| localName.equals("insert-before")
				|| localName.equals("insert-after")
				|| localName.equals("update")) {
				if (inModification)
					throw new SAXException("nested modifications are not allowed");
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException(
						localName + " requires a select attribute");
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
					"creation elements are only allowed inside "
						+ "a modification");
			if (localName.equals("append"))
				modification = new Append(pool, user, select);
            else if (localName.equals("insert-before"))
                modification = new Insert(pool, user, select, Insert.INSERT_BEFORE);
            else if (localName.equals("element")) {
                String name = atts.getValue("name");
                if(name == null)
                    throw new SAXException("element requires a name attribute");
                Element elem = doc.createElement(name);
                if(stack.isEmpty()) {
                    fragment.appendChild(elem);
                } else {
                    Element last = (Element)stack.peek();
                    last.appendChild(elem);
                }
                stack.push(elem);
            } else if (localName.equals("attribute")) {
                String name = atts.getValue("name");
                if(name == null)
                    throw new SAXException("attribute requires a name attribute");
                Attr attrib = doc.createAttribute(name);
                if(stack.isEmpty())
                    fragment.appendChild(attrib);
                else {
                    Element last = (Element)stack.peek();
                    last.setAttributeNode(attrib);
                }
                inAttribute = true;
                currentNode = attrib;
            }   
		} else if(inModification) {
            Element elem = doc.createElementNS(namespaceURI, qName);
            Attr a;
            for(int i = 0; i < atts.getLength(); i++) {
                 a = doc.createAttributeNS(atts.getURI(i), atts.getQName(i));
                 a.setValue(atts.getValue(i));
                 elem.setAttributeNodeNS(a);
            }
            if(!stack.isEmpty()) {
                Element last = (Element)stack.peek();
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
        if(namespaceURI.equals(XUPDATE_NS)) {
            if(localName.equals("element")) {
                stack.pop();
            } else if(localName.equals("attribute"))
                inAttribute = false;
            if(localName.equals("append") ||
                localName.equals("insert-before") ||
                localName.equals("insert-after")) {
                inModification = false;
                modification.setContent(fragment);
                modifications.add(modification);
                modification = null;
            }
        } else if(inModification)
            stack.pop();
	}

	/**
	 * @see org.xml.sax.ContentHandler#characters(char, int, int)
	 */
	public void characters(char[] ch, int start, int length)
		throws SAXException {
        if(inModification) {
            if(inAttribute)
                ((Attr)currentNode).setValue(new String(ch, start, length));
            else {
                Text text = doc.createTextNode(new String(ch, start, length));
                if(stack.isEmpty()) {
                    LOG.debug("appending text to fragment: " + text.getData());
                    fragment.appendChild(text);
                } else {
                    Element last = (Element)stack.peek();
                    last.appendChild(text);
                }
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
	}

	/**
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
	}

}
