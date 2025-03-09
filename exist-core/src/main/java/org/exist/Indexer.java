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
package org.exist;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.CDATASectionImpl;
import org.exist.dom.persistent.CommentImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.ProcessingInstructionImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.TextImpl;
import org.exist.dom.persistent.XMLDeclarationImpl;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath2;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.ProgressIndicator;
import org.exist.util.XMLString;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;

import javax.annotation.Nullable;

/**
 * Parses a given input document via SAX, stores it to the database and handles
 * index-creation.
 * 
 * @author wolf
 */
public class Indexer implements ContentHandler, LexicalHandler, ErrorHandler {

    private static final int CACHE_CHILD_COUNT_MAX = 0x10000;

    public static final String ATTR_CDATA_TYPE = "CDATA";
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

    private final DBBroker broker;
    private final Txn transaction;

    private StreamListener indexListener;

    private XMLString charBuf = new XMLString();
    private boolean inCDATASection = false;
    private int currentLine = 0;
    private final NodePath2 currentPath = new NodePath2();

    private DocumentImpl document = null;
    private IndexSpec indexSpec = null;

    private boolean insideDTD = false;
    private boolean validate = false;
    private int level = 0;
    private Locator locator = null;
    private int normalize = XMLString.SUPPRESS_BOTH;
    private final Map<String, String> nsMappings = new HashMap<>();
    private Element rootNode;

    private final Deque<ElementImpl> stack = new ArrayDeque<>();
    private final Deque<XMLString> nodeContentStack = new ArrayDeque<>();

    private StoredNode prevNode = null;

    private String ignorePrefix = null;
    private ProgressIndicator progress;

    protected boolean preserveWSmixed = false;

    protected int docSize = 0;

    private enum ProcessTextParent { COMMENT, PI, CDATA_START, ELEMENT_START, ELEMENT_END}

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
    private final TextImpl text = new TextImpl((Expression) null);
    private final Deque<ElementImpl> usedElements = new ArrayDeque<>();

    // when storing the document data, validation will be switched off, so
    // entities will not be reported. We thus have to cache all needed entities
    // during the validation run.
    private Map<String, String> entityMap = null;
    private String currentEntityName = null;
    private final XMLString currentEntityValue = new XMLString();
    
    /**
     * Create a new parser using the given database broker and user to store the
     * document.
     * 
     * @param broker The database broker to use.
     * @param transaction The transaction to use for indexing privileged access to the db.
     *
     * @throws EXistException if an error orccurs when constructing the indexer.
     */
    public Indexer(final DBBroker broker, final Txn transaction)
            throws EXistException {
        this.broker = broker;
        this.transaction = transaction;
        // TODO : move the configuration in the constructor or in a dedicated
        // method
        final Configuration config = broker.getConfiguration();
        final String suppressWS = (String) config
            .getProperty(PROPERTY_SUPPRESS_WHITESPACE);
        if (suppressWS != null) {
            switch (suppressWS) {
                case "leading":
                    normalize = XMLString.SUPPRESS_LEADING_WS;
                    break;
                case "trailing":
                    normalize = XMLString.SUPPRESS_TRAILING_WS;
                    break;
                case "none":
                    normalize = 0;
                    break;
            }
        }
        Boolean temp;
        if ((temp = (Boolean) config
	     .getProperty(PROPERTY_PRESERVE_WS_MIXED_CONTENT)) != null) {
            preserveWSmixed = temp;
	}
    }

    public void setValidating(final boolean validate) {
        this.validate = validate;
        if (!validate) {
            this.indexListener = broker.getIndexController()
                .getStreamListener(document, ReindexMode.STORE);
        }
    }

    /**
     * Prepare the indexer for parsing a new document. This will reset the
     * internal state of the Indexer object.
     * 
     * @param doc The document
     * @param collectionConfig The configuration of the collection holding the document
     */
    public void setDocument(final DocumentImpl doc,
            final CollectionConfiguration collectionConfig) {
        document = doc;
        if (collectionConfig != null) {
            indexSpec = collectionConfig.getIndexConfiguration();
        }
        // reset internal fields
        level = 0;
        currentPath.reset();
        stack.clear();
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
     * @param doc The document
     */
    public void setDocumentObject(final DocumentImpl doc) {
        document = doc;
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public int getDocSize() {
        return docSize;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
        if (length <= 0) {
            return;
        }

        if (charBuf != null) {
            charBuf.append(ch, start, length);
        } else {
            charBuf = new XMLString(ch, start, length);
        }
        if (currentEntityName != null) {
            currentEntityValue.append(ch, start, length);
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) {
        if (insideDTD) {
            return;
        }
        final CommentImpl comment = new CommentImpl(null, ch, start, length);
        comment.setOwnerDocument(document);
        if (stack.isEmpty()) {
            comment.setNodeId(broker.getBrokerPool().getNodeFactory()
                    .createInstance(nodeFactoryInstanceCnt++));
            if (!validate) {
                broker.storeNode(transaction, comment, currentPath, indexSpec);
            }
            document.appendChild((NodeHandle)comment);
        } else {
            final ElementImpl last = stack.peek();
            processText(last, ProcessTextParent.COMMENT);
            last.appendChildInternal(prevNode, comment);
            setPrevious(comment);
            if (!validate) {
                broker.storeNode(transaction, comment, currentPath, indexSpec);
            }
        }
    }

    @Override
    public void endCDATA() {
        if (!stack.isEmpty()) {
            final ElementImpl last = stack.peek();
            if (charBuf != null && !charBuf.isEmpty()) {
                final CDATASectionImpl cdata = new CDATASectionImpl(last.getExpression(), charBuf);
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

    @Override
    public void endDTD() {
        insideDTD = false;
    }

    @Override
    public void endDocument() {
        if (!validate) {
            if(indexListener != null) {
                indexListener.endIndexDocument(transaction);
            }
            progress.finish();
        }
        //LOG.debug("elementCnt = " + childCnt.length);
    }

    private void processText(ElementImpl last, ProcessTextParent ptp) {
	// if (charBuf != null && charBuf.length() > 0) {
        //    // remove whitespace if the node has just a single text child,
        //    // keep whitespace for mixed content.
	//     final XMLString normalized;
	//     if ((charBuf.isWhitespaceOnly() && preserveWSmixed) || last.preserveSpace()) {
	// 	normalized = charBuf;
	//     } else {
	// 	if (last.getChildCount() == 0) {
        //            normalized = charBuf.normalize(normalize);
	// 	} else {
	// 	    normalized = charBuf.isWhitespaceOnly() ? null : charBuf;
	// 	}
	//     }
	//     if (normalized != null && normalized.length() > 0) {
	// 	text.setData(normalized);
	// 	text.setOwnerDocument(document);
	// 	last.appendChildInternal(prevNode, text);
	// 	if (!validate) storeText();
	// 	setPrevious(text);
	//     }
	//     charBuf.reset();
	// }

        //from startElement method
	if (charBuf != null && !charBuf.isEmpty()) {
	    XMLString normalized = null;
            switch (ptp) {
                case COMMENT:
                case PI:
                case CDATA_START:
                    normalized = charBuf;
                    break;
                default:
	    if (charBuf.isWhitespaceOnly()) {
		if (last.preserveSpace() || last.getChildCount() == 0) {
		    normalized = charBuf;
		} else if (preserveWSmixed) {
		    if (!(last.getChildCount() == 0 && (normalize & XMLString.SUPPRESS_LEADING_WS) != 0)) {
			normalized = charBuf;
		    }
                } else {
                    normalized = charBuf.normalize(normalize);
                }
	    } else {
		//normalized = charBuf;
                if (last.preserveSpace()) {
                    normalized = charBuf;
                } else if (last.getChildCount() == 0) {
                    normalized = charBuf.normalize(normalize);
                } else {
                    // mixed element content: don't normalize the text node,
                    // just check if there is any text at all
                    if (preserveWSmixed) {
                        normalized = charBuf;
                    } else {
                        if ((normalize & XMLString.SUPPRESS_LEADING_WS) != 0) {
                            normalized = charBuf.normalize(XMLString.SUPPRESS_LEADING_WS | XMLString.COLLAPSE_WS);
                        } else if ((normalize & XMLString.SUPPRESS_TRAILING_WS) != 0) {
                            normalized = charBuf.normalize(XMLString.SUPPRESS_TRAILING_WS | XMLString.COLLAPSE_WS);
                        } else {
                            //normalized = charBuf.normalize(XMLString.COLLAPSE_WS);
                            normalized = charBuf.normalize(normalize);
                        }
                    }
                }

            }
        }
	    if (normalized != null) {
		text.setData(normalized);
		text.setOwnerDocument(document);
		last.appendChildInternal(prevNode, text);
		if (!validate) storeText();
		setPrevious(text);
	    }
	    charBuf.reset();
	}
    }

    @Override
    public void endElement(final String namespace, final String name, final String qname) {
        final ElementImpl last = stack.peek();
        processText(last, ProcessTextParent.ELEMENT_END);
        stack.pop();
        XMLString elemContent = null;
        try {
            if (!validate && RangeIndexSpec.hasQNameOrValueIndex(last.getIndexType())) {
                elemContent = nodeContentStack.pop();
            }
            if (validate) {
                if (childCnt != null) {
                    setChildCount(last);
                }
            } else {
                final String content = elemContent == null ? null : elemContent.toString();
                broker.endElement(last, currentPath, content);
                if (childCnt == null && last.getChildCount() > 0
                        || (childCnt != null && childCnt[last.getPosition()] != last.getChildCount())) {
                    broker.updateNode(transaction, last, false);
                }

                if (indexListener != null) {
                    indexListener.endElement(transaction, last, currentPath);
                }
            }
            currentPath.removeLastNode();
            setPrevious(last);
            level--;
        } finally {
            if (elemContent != null) {
                elemContent.reset();
            }
        }
    }

    /**
     * @param last The last element
     */
    private void setChildCount(final ElementImpl last) {
        if (last.getPosition() >= childCnt.length) {
            if (childCnt.length > CACHE_CHILD_COUNT_MAX) {
                childCnt = null;
                return;
            }
            final int[] n = new int[childCnt.length * 2];
            System.arraycopy(childCnt, 0, n, 0, childCnt.length);
            childCnt = n;
        }
        childCnt[last.getPosition()] = last.getChildCount();
    }

    @Override
    public void endPrefixMapping(final String prefix) {
        if (ignorePrefix != null && prefix.equals(ignorePrefix)) {
            ignorePrefix = null;
        } else {
            nsMappings.remove(prefix);
        }
    }

    @Override
    public void error(final SAXParseException e) throws SAXException {
        final String msg = "error at (" + e.getLineNumber() + "," + e.getColumnNumber() + ") : " + e.getMessage();
        LOG.debug(msg);
        throw new SAXException(msg, e);
    }

    @Override
    public void fatalError(final SAXParseException e) throws SAXException {
        final String msg = "fatal error at (" + e.getLineNumber() + "," + e.getColumnNumber() + ") : " + e.getMessage();
        LOG.debug(msg);
        throw new SAXException(msg, e);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) {
        //Nothing to do
    }

    @Override
    public void processingInstruction(final String target, final String data) {
        final ProcessingInstructionImpl pi = new ProcessingInstructionImpl((Expression) null, target, data);
        pi.setOwnerDocument(document);
        if (stack.isEmpty()) {
            pi.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance(nodeFactoryInstanceCnt++));

            if (!validate) broker.storeNode(transaction, pi, currentPath, indexSpec);

            document.appendChild((NodeHandle)pi);
        } else {
            final ElementImpl last = stack.peek();
            processText(last, ProcessTextParent.PI);
            last.appendChildInternal(prevNode, pi);
            setPrevious(pi);

            if (!validate) broker.storeNode(transaction, pi, currentPath, indexSpec);
        }
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startCDATA() {
        if (!stack.isEmpty()) {
            processText(stack.peek(), ProcessTextParent.CDATA_START);
        }
        inCDATASection = true;
    }

    // Methods of interface LexicalHandler
    // used to determine Doctype

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) {
        final DocumentTypeImpl docType = new DocumentTypeImpl(null, name, publicId, systemId);
        document.setDocumentType(docType);
        insideDTD = true;
    }

    @Override
    public void startDocument() {
        if (!validate) {
            progress = new ProgressIndicator(currentLine, 100);
            document.setChildCount(0);
            elementCnt = 0;
            if(indexListener != null) {
                indexListener.startIndexDocument(transaction);
            }
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

    @Override
    public void declaration(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) throws SAXException {
        final XMLDeclarationImpl xmlDecl = new XMLDeclarationImpl(version, encoding, standalone);
        document.setXmlDeclaration(xmlDecl);
    }

    final boolean hasNormAttribute(final Attributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++) {
            if("norm".equals(attributes.getLocalName(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void startElement(final String namespace, final String name, final String qname, final Attributes attributes) throws SAXException {
        // calculate number of real attributes:
        // don't store namespace declarations
        int attrLength = attributes.getLength();
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attrNS = attributes.getURI(i);
            final String attrQName = attributes.getQName(i);
            if (attrQName.startsWith("xmlns")
                    || attrNS.equals(Namespaces.EXIST_NS)) {
                --attrLength;
            }
        }

        ElementImpl node;
        int p = qname.indexOf(':');
        final String prefix = (p != Constants.STRING_NOT_FOUND) ? qname.substring(0, p) : "";
        final QName qn = broker.getBrokerPool().getSymbols().getQName(Node.ELEMENT_NODE, namespace, name, prefix);

        if (!stack.isEmpty()) {
            final ElementImpl last = stack.peek();
            processText(last, ProcessTextParent.ELEMENT_START);
            try {
                if (!usedElements.isEmpty()) {
                    node = usedElements.pop();
                    node.setNodeName(qn, broker.getBrokerPool().getSymbols());
                } else {
                    node = new ElementImpl((last != null) ? last.getExpression() : null, qn, broker.getBrokerPool().getSymbols());
                }
            } catch (final DOMException e) {
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
            if (nsMappings != null && !nsMappings.isEmpty()) {
                node.setNamespaceMappings(nsMappings);
                nsMappings.clear();
            }
            stack.push(node);
            currentPath.addNode(node, attributes);
            node.setPosition(elementCnt++);
            if (!validate) {
                if (childCnt != null) {
                    node.setChildCount(childCnt[node.getPosition()]);
                }
                storeElement(node);
            }
        } else {
            try {
                node = new ElementImpl(null, qn, broker.getBrokerPool().getSymbols());
            } catch (final DOMException e) {
                throw new SAXException(e.getMessage(), e);
            }
            rootNode = node;
            setPrevious(null);
            node.setOwnerDocument(document);
            node.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance(nodeFactoryInstanceCnt++));
            node.setAttributes((short) attrLength);
            if (nsMappings != null && !nsMappings.isEmpty()) {
                node.setNamespaceMappings(nsMappings);
                nsMappings.clear();
            }
            stack.push(node);
            currentPath.addNode(node, attributes);
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

        for (int i = 0; i < attributes.getLength(); i++) {
            final String attrNS = attributes.getURI(i);
            final String attrLocalName = attributes.getLocalName(i);
            final String attrQName = attributes.getQName(i);
            // skip xmlns-attributes and attributes in eXist's namespace
            if (attrQName.startsWith("xmlns") || attrNS.equals(Namespaces.EXIST_NS)) {
                --attrLength;
            } else {
                p = attrQName.indexOf(':');
                final String attrPrefix = (p != Constants.STRING_NOT_FOUND) ? attrQName.substring(0, p) : null;
                final AttrImpl attr = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
                final QName attrQN = broker.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, attrNS, attrLocalName, attrPrefix);
                try {
                    attr.setNodeName(attrQN, broker.getBrokerPool().getSymbols());
                } catch (final DOMException e) {
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
                    attr.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attr.getValue())));

                    attr.setType(AttrImpl.ID);
                } else if (attr.getQName().equals(Namespaces.XML_SPACE_QNAME)) {
                    node.setPreserveSpace("preserve".equals(attr.getValue()));
                }
                node.appendChildInternal(prevNode, attr);
                setPrevious(attr);
                if (!validate) {
                    broker.storeNode(transaction, attr, currentPath, indexSpec);

                    if (indexListener != null) {
                        indexListener.attribute(transaction, attr, currentPath);
                    }
                }
            }
        }
        if (attrLength > 0) {
            node.setAttributes((short) attrLength);
        }

        // notify observers about progress every 100 lines
        if (locator != null) {
            currentLine = locator.getLineNumber();
            if (!validate) {
                progress.setValue(currentLine);
            }
        }
        docSize++;
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

    private void storeElement(final ElementImpl node) {
        broker.storeNode(transaction, node, currentPath, indexSpec);

        if (indexListener != null) {
            indexListener.startElement(transaction, node, currentPath);
        }

        node.setChildCount(0);
        if (RangeIndexSpec.hasQNameOrValueIndex(node.getIndexType())) {
            final XMLString contentBuf = new XMLString();
            nodeContentStack.push(contentBuf);
        }
    }

    @Override
    public void startEntity(final String name) {
        // while validating, all entities are put into a map
        // to cache them for later use
        if (validate) {
            if (entityMap == null) {
                entityMap = new HashMap<>();
            }
            currentEntityName = name;
        }
    }

    @Override
    public void endEntity(final String name) {
        // store the entity into a map for later
        if (validate && currentEntityValue != null) {
            entityMap.put(currentEntityName, currentEntityValue.toString());
            currentEntityName = null;
            currentEntityValue.reset();
        }
    }

    @Override
    public void skippedEntity(final String name) {
        if (!validate && entityMap != null) {
            final String value = entityMap.get(name);

            if (value != null) {
                characters(value.toCharArray(), 0, value.length());
            }
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) {
        // skip the eXist namespace
        // if (uri.equals(Namespaces.EXIST_NS)) {
        // ignorePrefix = prefix;
        // return;
        // }
        nsMappings.put(prefix, uri);
    }

    @Override
    public void warning(final SAXParseException e) throws SAXException {
        final String msg = "warning at (" + e.getLineNumber() + "," + e.getColumnNumber() + ") : " + e.getMessage();
        throw new SAXException(msg, e);
    }

    private void setPrevious(final StoredNode previous) {
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
