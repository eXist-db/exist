/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Stack;

//import javax.xml.parsers.ParserConfigurationException;
//import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.CDATASectionImpl;
import org.exist.dom.persistent.CommentImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.ProcessingInstructionImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.TextImpl;
import org.exist.indexing.StreamListener;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.ProgressIndicator;
import org.exist.util.XMLChar;
import org.exist.util.XMLString;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Constants;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
//import org.xml.sax.SAXNotRecognizedException;
//import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;

/**
 * Parses a given input document via SAX, stores it to the database and handles
 * index-creation.
 * 
 * @author wolf
 * 
 */
public class Indexer extends Observable implements ContentHandler,
        LexicalHandler, ErrorHandler {

    private static final int CACHE_CHILD_COUNT_MAX = 0x10000;

    public static final String ATTR_ID_TYPE = "ID";
    public static final String ATTR_IDREF_TYPE = "IDREF";
    public static final String ATTR_IDREFS_TYPE = "IDREFS";

    private final static Logger LOG = LogManager.getLogger(Indexer.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "indexer";
    public static final String CONFIGURATION_INDEX_ELEMENT_NAME = "index";
    public static final String SUPPRESS_WHITESPACE_ATTRIBUTE = "suppress-whitespace";
    public static final String PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE = "preserve-whitespace-mixed-content";

    public static final String PROPERTY_INDEXER_CONFIG = "indexer.config";
    public final static String PROPERTY_SUPPRESS_WHITESPACE = "indexer.suppress-whitespace";
    public static final String PROPERTY_PRESERVE_WS_MIXED_CONTENT = "indexer.preserve-whitespace-mixed-content";

    protected DBBroker broker = null;
    protected Txn transaction;

    protected StreamListener indexListener;

    protected XMLString charBuf = new XMLString();
    protected boolean inCDATASection = false;
    protected int currentLine = 0;
    protected NodePath currentPath = new NodePath();

    protected DocumentImpl document = null;
    protected IndexSpec indexSpec = null;

    protected boolean insideDTD = false;
    protected boolean validate = false;
    protected int level = 0;
    protected Locator locator = null;
    protected int normalize = XMLString.SUPPRESS_BOTH;
    protected Map<String, String> nsMappings = new HashMap<String, String>();
    protected Element rootNode;

    protected Stack<ElementImpl> stack = new Stack<ElementImpl>();
    protected Stack<XMLString> nodeContentStack = new Stack<XMLString>();

    protected StoredNode prevNode = null;

    protected String ignorePrefix = null;
    protected ProgressIndicator progress;

    protected boolean suppressWSmixed = false;

    protected int docSize = 0;

    /*
     * used to record the number of children of an element during validation
     * phase. later, when storing the nodes, we already know the child count and
     * don't need to update the element a second time.
     */
    private int childCnt[] = new int[0x1000];

    // the current position in childCnt
    private int elementCnt = 0;

    // the current nodeFactoryInstanceCnt
    private int nodeFactoryInstanceCnt = 0;

    // reusable fields
    private TextImpl text = new TextImpl();
    private Stack<ElementImpl> usedElements = new Stack<ElementImpl>();

    // when storing the document data, validation will be switched off, so
    // entities will not be reported. We thus have to cache all needed entities
    // during the validation run.
    private Map<String, String> entityMap = null;
    private String currentEntityName = null;
    private XMLString currentEntityValue = new XMLString();

    /**
     * Create a new parser using the given database broker and user to store the
     * document.
     * 
     *@param broker
     *@exception EXistException
     */
    public Indexer(DBBroker broker, Txn transaction) throws EXistException {
    	this(broker, transaction, false);
    }
    
    /**
     * Create a new parser using the given database broker and user to store the
     * document.
     * 
     *@param broker
     *            The database broker to use.
     *@param transaction
     *            The transaction to use for indexing
     *@param priv
     *            used by the security manager to indicate that it needs
     *            privileged access to the db.
     *@exception EXistException
     */
    public Indexer(DBBroker broker, Txn transaction, boolean priv)
            throws EXistException {
        this.broker = broker;
        this.transaction = transaction;
        // TODO : move the configuration in the constructor or in a dedicated
        // method
        final Configuration config = broker.getConfiguration();
        final String suppressWS = (String) config
            .getProperty(PROPERTY_SUPPRESS_WHITESPACE);
        if (suppressWS != null) {
            if ("leading".equals(suppressWS))
                {normalize = XMLString.SUPPRESS_LEADING_WS;}
            else if ("trailing".equals(suppressWS))
                {normalize = XMLString.SUPPRESS_TRAILING_WS;}
            else if ("none".equals(suppressWS))
                {normalize = 0;}
        }
        Boolean temp;
        if ((temp = (Boolean) config
                .getProperty(PROPERTY_PRESERVE_WS_MIXED_CONTENT)) != null)
            {suppressWSmixed = temp.booleanValue();}
    }

    public void setValidating(boolean validate) {
        this.validate = validate;
        if (!validate) {
            broker.getIndexController().setDocument(document,
                    StreamListener.STORE);
            this.indexListener = broker.getIndexController()
                .getStreamListener();
        }
    }

    /**
     * Prepare the indexer for parsing a new document. This will reset the
     * internal state of the Indexer object.
     * 
     * @param doc
     */
    public void setDocument(DocumentImpl doc,
            CollectionConfiguration collectionConfig) {
        document = doc;
        if (collectionConfig != null)
            {indexSpec = collectionConfig.getIndexConfiguration();}
        // reset internal fields
        level = 0;
        currentPath.reset();
        stack = new Stack<ElementImpl>();
        docSize = 0;
        nsMappings.clear();
        indexListener = null;
        rootNode = null;
        setPrevious(null);
    }

    /**
     * Set the document object to be used by this Indexer. This method doesn't
     * reset the internal state.
     * 
     * @param doc
     */
    public void setDocumentObject(DocumentImpl doc) {
        document = doc;
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public int getDocSize() {
        return docSize;
    }

    public void characters(char[] ch, int start, int length) {
        if (length <= 0)
            {return;}
        if (charBuf != null) {
            charBuf.append(ch, start, length);
        } else {
            charBuf = new XMLString(ch, start, length);
        }
        if (currentEntityName != null)
            {currentEntityValue.append(ch, start, length);}
    }

    public void comment(char[] ch, int start, int length) {
        if (insideDTD)
            {return;}
        final CommentImpl comment = new CommentImpl(ch, start, length);
        comment.setOwnerDocument(document);
        if (stack.empty()) {
            comment.setNodeId(broker.getBrokerPool().getNodeFactory()
                    .createInstance(nodeFactoryInstanceCnt++));
            if (!validate) {
                broker.storeNode(transaction, comment, currentPath, indexSpec);
            }
            document.appendChild((NodeHandle)comment);
        } else {
            final ElementImpl last = stack.peek();
            if (charBuf != null && charBuf.length() > 0) {
                text.setData(charBuf);
                text.setOwnerDocument(document);
                last.appendChildInternal(prevNode, text);
                if (!validate) {
                    storeText();
                }
                setPrevious(text);
                charBuf.reset();
            }
            last.appendChildInternal(prevNode, comment);
            setPrevious(comment);
            if (!validate) {
                broker.storeNode(transaction, comment, currentPath, indexSpec);
            }
        }
    }

    public void endCDATA() {
        if (!stack.isEmpty()) {
            final ElementImpl last = stack.peek();
            if (charBuf != null && charBuf.length() > 0) {
                final CDATASectionImpl cdata = new CDATASectionImpl(charBuf);
                cdata.setOwnerDocument(document);
                last.appendChildInternal(prevNode, cdata);
                if (!validate) {
                    broker.storeNode(transaction, cdata, currentPath, indexSpec);
                    if (indexListener != null) {
                        indexListener.characters(transaction, cdata, currentPath);
                    }
                }
                setPrevious(cdata);
                if (!nodeContentStack.isEmpty()) {
                    for (final XMLString next : nodeContentStack) {
                        next.append(charBuf);
                    }
                }
                charBuf.reset();
            }
        }
        inCDATASection = false;
    }

    public void endDTD() {
        insideDTD = false;
    }

    public void endDocument() {
        if (!validate) {
            progress.finish();
            setChanged();
            notifyObservers(progress);
        }
        //LOG.debug("elementCnt = " + childCnt.length);
    }

    public void endElement(String namespace, String name, String qname) {
        final ElementImpl last = stack.peek();
        if (last.getNodeName().equals(qname)) {
            if (charBuf != null && charBuf.length() > 0) {
                // remove whitespace if the node has just a single text child,
                // keep whitespace for mixed content.
                final XMLString normalized;
                if ((charBuf.isWhitespaceOnly() && suppressWSmixed) || last.preserveSpace()) {
                    normalized = charBuf;
                } else {
                    normalized = last.getChildCount() == 0 ? 
                        charBuf.normalize(normalize) : 
                            (charBuf.isWhitespaceOnly() ? null : charBuf);
                }
                if (normalized != null && normalized.length() > 0) {
                    text.setData(normalized);
                    text.setOwnerDocument(document);
                    last.appendChildInternal(prevNode, text);
                    if (!validate)
                        {storeText();}
                    setPrevious(text);
                }
                charBuf.reset();
            }
            stack.pop();
            XMLString elemContent = null;
            if (!validate && RangeIndexSpec.hasQNameOrValueIndex(last.getIndexType())) {
                elemContent = nodeContentStack.pop();
            }
            if (!validate) {
                final String content = elemContent == null ?
                        null : elemContent.toString();
                broker.endElement(last, currentPath, content);
                if (indexListener != null)
                    {indexListener.endElement(transaction, last, currentPath);}
            }
            currentPath.removeLastComponent();
            if (validate) {
                if (childCnt != null)
                    {setChildCount(last);}
            } else {
                document.setOwnerDocument(document);
                if ((childCnt == null && last.getChildCount() > 0)
                    || (childCnt != null && childCnt[last.getPosition()] != last.getChildCount())) {
                    broker.updateNode(transaction, last, false);
                }
            }
            setPrevious(last);
            level--;
        }
    }

    /**
     * @param last
     */
    private void setChildCount(final ElementImpl last) {
        if (last.getPosition() >= childCnt.length) {
            if (childCnt.length > CACHE_CHILD_COUNT_MAX) {
                childCnt = null;
                return;
            }
            int n[] = new int[childCnt.length * 2];
            System.arraycopy(childCnt, 0, n, 0, childCnt.length);
            childCnt = n;
        }
        childCnt[last.getPosition()] = last.getChildCount();
    }

    public void endPrefixMapping(String prefix) {
        if (ignorePrefix != null && prefix.equals(ignorePrefix)) {
            ignorePrefix = null;
        } else {
            nsMappings.remove(prefix);
        }
    }

    public void error(SAXParseException e) throws SAXException {
        final String msg = "error at (" + e.getLineNumber() + ","
            + e.getColumnNumber() + ") : " + e.getMessage();
        LOG.debug(msg);
        throw new SAXException(msg, e);
    }

    public void fatalError(SAXParseException e) throws SAXException {
        final String msg = "fatal error at (" + e.getLineNumber() + ","
            + e.getColumnNumber() + ") : " + e.getMessage();
        LOG.debug(msg);
        throw new SAXException(msg, e);
    }

    public void ignorableWhitespace(char[] ch, int start, int length) {
        //Nothing to do
    }

    public void processingInstruction(String target, String data) {
        final ProcessingInstructionImpl pi = new ProcessingInstructionImpl(target, data);
        pi.setOwnerDocument(document);
        if (stack.isEmpty()) {
            pi.setNodeId(broker.getBrokerPool().getNodeFactory()
                .createInstance(nodeFactoryInstanceCnt++));
            if (!validate) {
                broker.storeNode(transaction, pi, currentPath, indexSpec);
            }
            document.appendChild((NodeHandle)pi);
        } else {
            final ElementImpl last = stack.peek();
            if (charBuf != null && charBuf.length() > 0) {
                final XMLString normalized = charBuf.normalize(normalize);
                if (normalized.length() > 0) {
                    // TextImpl text =
                    // new TextImpl( normalized );
                    text.setData(normalized);
                    text.setOwnerDocument(document);
                    last.appendChildInternal(prevNode, text);
                    if (!validate) {
                        storeText();
                    }
                    setPrevious(text);
                }
                charBuf.reset();
            }
            last.appendChildInternal(prevNode, pi);
            setPrevious(pi);
            if (!validate) {
                broker.storeNode(transaction, pi, currentPath, indexSpec);
            }
        }
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

//    /**
//     * set SAX parser feature. This method will catch (and ignore) exceptions if
//     * the used parser does not support a feature.
//     *
//     *@param factory
//     *@param feature
//     *@param value
//     */
//    //private void setFeature(SAXParserFactory factory, String feature, boolean value) {
//        //try {
//            //factory.setFeature(feature, value);
//        //} catch (SAXNotRecognizedException e) {
//            //LOG.warn(e);
//        //} catch (SAXNotSupportedException snse) {
//            //LOG.warn(snse);
//        //} catch (ParserConfigurationException pce) {
//            //LOG.warn(pce);
//        //}
//    //}

    public void startCDATA() {
        if (!stack.isEmpty()) {
            final ElementImpl last = stack.peek();
            if (charBuf != null && charBuf.length() > 0) {
                text.setData(charBuf);
                text.setOwnerDocument(document);
                last.appendChildInternal(prevNode, text);
                if (!validate)
                    {storeText();}
                setPrevious(text);
                charBuf.reset();
            }
        }
        inCDATASection = true;
    }

    // Methods of interface LexicalHandler
    // used to determine Doctype

    public void startDTD(String name, String publicId, String systemId) {
        final DocumentTypeImpl docType = new DocumentTypeImpl(name, publicId,
                systemId);
        document.setDocumentType(docType);
        insideDTD = true;
    }

    public void startDocument() {
        if (!validate) {
            progress = new ProgressIndicator(currentLine, 100);
            document.setChildCount(0);
            elementCnt = 0;
        }
        docSize = 0;

        /* 
         * Reset node id count
         * 
         * We set this to 1 instead of 0 to match the InMemmory serializer which
         * considers the Document to be the first node with an id.
         */
        nodeFactoryInstanceCnt = 1;
    }

    public void startElement(String namespace, String name, String qname,
            Attributes attributes) throws SAXException {
        // calculate number of real attributes:
        // don't store namespace declarations
        int attrLength = attributes.getLength();
        String attrQName;
        String attrNS;
        for (int i = 0; i < attributes.getLength(); i++) {
            attrNS = attributes.getURI(i);
            attrQName = attributes.getQName(i);
            if (attrQName.startsWith("xmlns")
                    || attrNS.equals(Namespaces.EXIST_NS))
                {--attrLength;}
        }
        ElementImpl last;
        ElementImpl node;
        int p = qname.indexOf(':');
        final String prefix = (p != Constants.STRING_NOT_FOUND) ? 
                qname.substring(0, p) : "";
        final QName qn = broker.getBrokerPool().getSymbols().getQName(
                Node.ELEMENT_NODE, namespace, name, prefix);
        if (!stack.empty()) {
            last = stack.peek();
            if (charBuf != null) {
                if (charBuf.isWhitespaceOnly()) {
                    if (suppressWSmixed) {
                        if (charBuf.length() > 0 && !(last.getChildCount() == 0 && (normalize & XMLString.SUPPRESS_LEADING_WS) != 0)) {
                            text.setData(charBuf);
                            text.setOwnerDocument(document);
                            last.appendChildInternal(prevNode, text);
                            if (!validate)
                                {storeText();}
                            setPrevious(text);
                        }
                    }
                } else if (charBuf.length() > 0) {
                    // mixed element content: don't normalize the text node,
                    // just check
                    // if there is any text at all
                    text.setData(charBuf);
                    text.setOwnerDocument(document);
                    last.appendChildInternal(prevNode, text);
                    if (!validate)
                        {storeText();}
                    setPrevious(text);
                }
                charBuf.reset();
            }
            try {
                if (!usedElements.isEmpty()) {
                    node = usedElements.pop();
                    node.setNodeName(qn, broker.getBrokerPool().getSymbols());
                } else {
                    node = new ElementImpl(qn, broker.getBrokerPool().getSymbols());
                }
            } catch (DOMException e) {
                throw new SAXException(e.getMessage(), e);
            }
            // copy xml:space setting
            node.setPreserveSpace(last.preserveSpace());
            // append the node to its parent
            // (computes the node id and updates the parent's child count)
            last.appendChildInternal(prevNode, node);
            setPrevious(null);
            node.setOwnerDocument(document);
            node.setAttributes((short) attrLength);
            if (nsMappings != null && nsMappings.size() > 0) {
                node.setNamespaceMappings(nsMappings);
                nsMappings.clear();
            }
            stack.push(node);
            currentPath.addComponent(qn);
            node.setPosition(elementCnt++);
            if (!validate) {
                if (childCnt != null) {
                    node.setChildCount(childCnt[node.getPosition()]);
                }
                storeElement(node);
            }
        } else {
            try {
                node = new ElementImpl(qn, broker.getBrokerPool().getSymbols());
            } catch (DOMException e) {
                throw new SAXException(e.getMessage(), e);
            }
            rootNode = node;
            setPrevious(null);
            node.setOwnerDocument(document);
            node.setNodeId(broker.getBrokerPool().getNodeFactory()
                .createInstance(nodeFactoryInstanceCnt++));
            node.setAttributes((short) attrLength);
            if (nsMappings != null && nsMappings.size() > 0) {
                node.setNamespaceMappings(nsMappings);
                nsMappings.clear();
            }
            stack.push(node);
            currentPath.addComponent(qn);
            node.setPosition(elementCnt++);
            if (!validate) {
                if (childCnt != null) {
                    node.setChildCount(childCnt[node.getPosition()]);
                }
                storeElement(node);
            }
            document.appendChild((NodeHandle)node);
        }
        level++;

        String attrPrefix;
        String attrLocalName;
        for (int i = 0; i < attributes.getLength(); i++) {
            attrNS = attributes.getURI(i);
            attrLocalName = attributes.getLocalName(i);
            attrQName = attributes.getQName(i);
            // skip xmlns-attributes and attributes in eXist's namespace
            if (attrQName.startsWith("xmlns")
                    || attrNS.equals(Namespaces.EXIST_NS))
                {--attrLength;}
            else {
                p = attrQName.indexOf(':');
                attrPrefix = (p != Constants.STRING_NOT_FOUND) ?
                    attrQName.substring(0, p) : null;
                final AttrImpl attr = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
                final QName attrQN = broker.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, attrNS, attrLocalName, attrPrefix);
                try {
                    attr.setNodeName(attrQN, broker.getBrokerPool().getSymbols());
                } catch (DOMException e) {
                    throw new SAXException(e.getMessage(), e);
                }
                attr.setValue(attributes.getValue(i));
                attr.setOwnerDocument(document);
                if (attributes.getType(i).equals(ATTR_ID_TYPE)) {
                    attr.setType(AttrImpl.ID);
                } else if (attributes.getType(i).equals(ATTR_IDREF_TYPE)) {
                    attr.setType(AttrImpl.IDREF);
                } else if (attributes.getType(i).equals(ATTR_IDREFS_TYPE)) {
                    attr.setType(AttrImpl.IDREFS);
                } else if (attr.getQName().equals(Namespaces.XML_ID_QNAME)) {
                    // an xml:id attribute. Normalize the attribute and set its
                    // type to ID
                    attr.setValue(StringValue.trimWhitespace(StringValue
                            .collapseWhitespace(attr.getValue())));
                    if (!XMLChar.isValidNCName(attr.getValue()))
                        {throw new SAXException(
                            "Value of xml:id attribute is not a valid NCName: "
                            + attr.getValue());}
                    attr.setType(AttrImpl.ID);
                } else if (attr.getQName().equals(Namespaces.XML_SPACE_QNAME)) {
                    node.setPreserveSpace("preserve".equals(attr.getValue()));
                }
                node.appendChildInternal(prevNode, attr);
                setPrevious(attr);
                if (!validate) {
                    broker.storeNode(transaction, attr, currentPath, indexSpec);
                    if (indexListener != null)
                        {indexListener.attribute(transaction, attr, currentPath);}
                }
            }
        }
        if (attrLength > 0)
            {node.setAttributes((short) attrLength);}
        // notify observers about progress every 100 lines
        if (locator != null) {
            currentLine = locator.getLineNumber();
            if (!validate) {
                progress.setValue(currentLine);
                if (progress.changed()) {
                    setChanged();
                    notifyObservers(progress);
                }
            }
        }
        ++docSize;
    }

    private void storeText() {
        if (!nodeContentStack.isEmpty()) {
            for (final XMLString next : nodeContentStack) {
                next.append(charBuf);
            }
        }
        broker.storeNode(transaction, text, currentPath, indexSpec);
        if (indexListener != null) {
            indexListener.characters(transaction, text, currentPath);
        }
    }

    private void storeElement(ElementImpl node) {
        broker.storeNode(transaction, node, currentPath, indexSpec);
        if (indexListener != null)
            {indexListener.startElement(transaction, node, currentPath);}
        node.setChildCount(0);
        if (RangeIndexSpec.hasQNameOrValueIndex(node.getIndexType())) {
            final XMLString contentBuf = new XMLString();
            nodeContentStack.push(contentBuf);
        }
    }

    public void startEntity(String name) {
        // while validating, all entities are put into a map
        // to cache them for later use
        if (validate) {
            if (entityMap == null)
                {entityMap = new HashMap<String, String>();}
            currentEntityName = name;
        }
    }

    public void endEntity(String name) {
        // store the entity into a map for later
        if (validate && currentEntityValue != null) {
            entityMap.put(currentEntityName, currentEntityValue.toString());
            currentEntityName = null;
            currentEntityValue.reset();
        }
    }

    public void skippedEntity(String name) {
        if (!validate && entityMap != null) {
            final String value = entityMap.get(name);
            if (value != null)
                {characters(value.toCharArray(), 0, value.length());}
        }
	}

    public void startPrefixMapping(String prefix, String uri) {
        // skip the eXist namespace
        // if (uri.equals(Namespaces.EXIST_NS)) {
        // ignorePrefix = prefix;
        // return;
        // }
        nsMappings.put(prefix, uri);
    }

    public void warning(SAXParseException e) throws SAXException {
        final String msg = "warning at (" + e.getLineNumber() + ","
            + e.getColumnNumber() + ") : " + e.getMessage();
        throw new SAXException(msg, e);
    }

    private void setPrevious(StoredNode previous) {
        if (prevNode != null) {
            switch (prevNode.getNodeType()) {
            case Node.ATTRIBUTE_NODE:
                prevNode.release();
                break;
            case Node.ELEMENT_NODE:
                if (prevNode != rootNode) {
                    prevNode.clear();
                    usedElements.push((ElementImpl) prevNode);
                }
                break;
            case Node.TEXT_NODE:
                prevNode.clear();
                break;
            }
        }
        prevNode = previous;
    }
}
