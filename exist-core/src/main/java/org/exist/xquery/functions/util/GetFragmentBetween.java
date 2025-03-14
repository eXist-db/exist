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
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Delivers the fragment between two nodes (normally milestones) of a document.
 * It leads to more performance for most XML documents because it
 * determines the fragment directly by the EmbeddedXmlReader and not by
 * XQL operators.
 *
 * @author Josef Willenborg, Max Planck Institute for the history of science,
 * http://www.mpiwg-berlin.mpg.de, jwillenborg@mpiwg-berlin.mpg.de
 */
public class GetFragmentBetween extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(GetFragmentBetween.class);

    public static final FunctionSignature signature =
            new FunctionSignature(
                    new QName("get-fragment-between", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Serializes an XML fragment or a sequence of nodes between two elements (normally milestone elements). " +
                            "This function works only on documents which are stored in the database itself." +
                            "The $beginning-node represents the first node/milestone element, $ending-node, the second one. " +
                            "The results will be inclusive of $beginning-node and exclusive of the $ending-node." +
                            "The third argument, $make-fragment, is " +
                            "a boolean value for the path completion. If it is set to true() the " +
                            "result sequence is wrapped into a parent element node. " +
                            "The fourth argument  display-root-namespace (only used when $make-fragment is true()), is " +
                            "a boolean value for displaying the root node namespace. If it is set to true() the " +
                            "attribute \"xmlns\" in the root node of the result sequence is determined from the $beginning-node. " +
                            "Example call of the function for getting the fragment between two TEI page break element nodes: " +
                            "  let $fragment := util:get-fragment-between(//pb[1], //pb[2], true(), true())",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("beginning-node", Type.NODE, Cardinality.EXACTLY_ONE, "The first node/milestone element"),
                            new FunctionParameterSequenceType("ending-node", Type.NODE, Cardinality.ZERO_OR_ONE, "The second node/milestone element"),
                            new FunctionParameterSequenceType("make-fragment", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "The flag make a fragment."),
                            new FunctionParameterSequenceType("display-root-namespace", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "Display the namespace of the root node of the fragment.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the string containing the fragment between the two node/milestone elements.")
            );

    public GetFragmentBetween(final XQueryContext context) {
        super(context, signature);
    }

    /**
     * Get the fragment between two elements (normally milestone elements) of a document
     *
     * @param contextSequence 1. first node (e.g. pb[10])  2. second node (e.g.: pb[11]) 3. pathCompletion:
     *                        open and closing tags before and after the fragment are appended (Default: true)
     *                        4. Display the namespace of the root node of the fragment (Default: false)
     * @return the fragment between the two nodes
     *
     * @throws XPathException in case of dynamic error
     */
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Node ms1Node = ((NodeValue)args[0].itemAt(0)).getNode();

        final Optional<Node> ms2Node;
        if (args[1].isEmpty()) {
            ms2Node = Optional.empty();
        } else {
            ms2Node = Optional.of(((NodeValue)args[1].itemAt(0)).getNode());
        }

        final boolean pathCompletion;
        if (args[2].isEmpty()) {
            pathCompletion = true;  // default
        } else {
            pathCompletion = args[2].itemAt(0).atomize().effectiveBooleanValue();
        }

        final boolean displayRootNamespace;
        if (args[3].isEmpty()) {
             displayRootNamespace = false;  // default
        } else {
            displayRootNamespace = args[3].itemAt(0).atomize().effectiveBooleanValue();
        }

        // fetch the fragment between the two milestones
        final StringBuilder fragment = getFragmentBetween(ms1Node, ms2Node);
        if (pathCompletion) {
            final String msFromPathName = getNodeXPath(ms1Node.getParentNode(), displayRootNamespace);
            final String openElementsOfMsFrom = pathName2XmlTags(msFromPathName, "open");
            final String closingElementsOfMsTo;
            if (ms2Node.isPresent()) {
                final String msToPathName = getNodeXPath(ms2Node.get().getParentNode(), displayRootNamespace);
                closingElementsOfMsTo = pathName2XmlTags(msToPathName, "close");
            } else {
                closingElementsOfMsTo = "";
            }
            fragment.insert(0, openElementsOfMsFrom);
            fragment.append(closingElementsOfMsTo);
        }

        return new StringValue(this, fragment.toString());
    }

    /**
     * Fetch the fragment between two nodes (normally milestones) in an XML document
     *
     * @param node1 first node from which down to the node node2 the XML fragment is delivered as a string
     * @param node2 the node to which down the XML fragment is delivered as a string
     *
     * @return fragment between the two nodes
     *
     * @throws XPathException
     */
    private StringBuilder getFragmentBetween(final Node node1, final Optional<Node> node2) throws XPathException {
        final StoredNode storedNode1 = (StoredNode) node1;
        final Optional<StoredNode> storedNode2 = node2.map(n -> (StoredNode)n);

        final NodeId node1NodeId = storedNode1.getNodeId();
        final Optional<NodeId> node2NodeId = storedNode2.map(StoredNode::getNodeId);

        final DocumentImpl docImpl = (DocumentImpl) node1.getOwnerDocument();

        final StringBuilder resultFragment = new StringBuilder();
        Optional<NodeId> actualNodeId = Optional.empty();
        boolean getFragmentMode = false;

        try {
            final BrokerPool brokerPool = docImpl.getBrokerPool();
            try (final DBBroker dbBroker = brokerPool.getBroker()) {
                final NodeList children = docImpl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    final StoredNode docChildStoredNode = (StoredNode) children.item(i);
                    final int docChildStoredNodeType = docChildStoredNode.getNodeType();
                    final IEmbeddedXMLStreamReader reader = dbBroker.getXMLStreamReader(docChildStoredNode, false);
                    while (reader.hasNext() && !node2NodeId.equals(actualNodeId) && docChildStoredNodeType != Node.PROCESSING_INSTRUCTION_NODE && docChildStoredNodeType != Node.COMMENT_NODE) {
                        final int status = reader.next();
                        switch (status) {
                            case XMLStreamReader.START_DOCUMENT:
                            case XMLStreamReader.END_DOCUMENT:
                                break;

                            case XMLStreamReader.START_ELEMENT:
                                actualNodeId = Optional.of(reader.getNode().getNodeId());
                                if (actualNodeId.map(node1NodeId::equals).orElse(false)) {
                                    getFragmentMode = true;
                                }
                                if (actualNodeId.equals(node2NodeId)) {
                                    getFragmentMode = false;
                                }
                                if (getFragmentMode) {
                                    final String startElementTag = getStartElementTag(reader);
                                    resultFragment.append(startElementTag);
                                }
                                break;

                            case XMLStreamReader.END_ELEMENT:
                                if (getFragmentMode) {
                                    final String endElementTag = getEndElementTag(reader);
                                    resultFragment.append(endElementTag);
                                }
                                break;

                            case XMLStreamReader.CHARACTERS:
                                if (getFragmentMode) {
                                    final String characters = getCharacters(reader);
                                    resultFragment.append(characters);
                                }
                                break;

                            case XMLStreamReader.CDATA:
                                if (getFragmentMode) {
                                    final String cdata = getCDataTag(reader);
                                    resultFragment.append(cdata);
                                }
                                break;

                            case XMLStreamReader.COMMENT:
                                if (getFragmentMode) {
                                    final String comment = getCommentTag(reader);
                                    resultFragment.append(comment);
                                }
                                break;

                            case XMLStreamReader.PROCESSING_INSTRUCTION:
                                if (getFragmentMode) {
                                    final String piTag = getPITag(reader);
                                    resultFragment.append(piTag);
                                }
                                break;
                        }
                    }
                }
            }
        } catch (final EXistException | XMLStreamException | IOException e) {
            throw new XPathException(this, "An error occurred while getFragmentBetween: " + e.getMessage(), e);
        }
        return resultFragment;
    }

    private String getStartElementTag(final XMLStreamReader reader) {
        final String elemName = reader.getLocalName();

        final int nsCount = reader.getNamespaceCount();
        final StringBuilder elemNsString = new StringBuilder();
        for (int ni = 0; ni < nsCount; ni++) {
            final String nsPrefix = reader.getNamespacePrefix(ni);
            final String nsUri = reader.getNamespaceURI(ni);
            String nsString = "xmlns:" + nsPrefix + "=\"" + nsUri + "\"";
            if (nsPrefix != null && nsPrefix.isEmpty()) {
                nsString = "xmlns" + "=\"" + nsUri + "\"";
            }
            elemNsString.append(" ").append(nsString);
        }

        final int attrCount = reader.getAttributeCount();
        final StringBuilder elemAttrString = new StringBuilder();
        for (int j = 0; j < attrCount; j++) {
            final String attrNamePrefix = reader.getAttributePrefix(j);
            final String attrName = reader.getAttributeLocalName(j);
            final String attrValue = escape(reader.getAttributeValue(j));
            String attrString = "";
            if (!(attrNamePrefix == null || attrNamePrefix.isEmpty())) {
                attrString = attrNamePrefix + ":";
            }
            attrString = attrString + attrName + "=\"" + attrValue + "\"";
            elemAttrString.append(" ").append(attrString);
        }
        final String elemPrefix = reader.getPrefix();
        String elemPart = "";
        if (!(elemPrefix == null || elemPrefix.isEmpty())) {
            elemPart = elemPrefix + ":";
        }
        elemPart = elemPart + elemName;
        return "<" + elemPart + elemNsString + elemAttrString + ">";
    }

    private String getEndElementTag(final XMLStreamReader reader) {
        final String elemName = reader.getLocalName();
        final String elemPrefix = reader.getPrefix();
        String elemPart = "";
        if (!(elemPrefix == null || elemPrefix.isEmpty())) {
            elemPart = elemPrefix + ":";
        }
        elemPart = elemPart + elemName;
        return "</" + elemPart + ">";
    }

    private String getCharacters(final XMLStreamReader reader) {
        String xmlChars = reader.getText();
        xmlChars = escape(xmlChars);
        return xmlChars;
    }

    private String getCDataTag(final XMLStreamReader reader) {
        final char[] chars = reader.getTextCharacters();
        return "<![CDATA[\n" + new String(chars) + "\n]]>";
    }

    private String getCommentTag(final XMLStreamReader reader) {
        final char[] chars = reader.getTextCharacters();
        return "<!--" + new String(chars) + "-->";
    }

    private String getPITag(final XMLStreamReader reader) {
        final String piTarget = reader.getPITarget();
        String piData = reader.getPIData();
        if (!(piData == null || piData.isEmpty())) {
            piData = " " + piData;
        } else {
            piData = "";
        }
        return "<?" + piTarget + piData + "?>";
    }

    private String escape(final String inputStr) {
        final StringBuilder resultStrBuf = new StringBuilder();
        for (int i = 0; i < inputStr.length(); i++) {
            final char ch = inputStr.charAt(i);
            switch (ch) {
                case '<':
                    resultStrBuf.append("&lt;");
                    break;
                case '>':
                    resultStrBuf.append("&gt;");
                    break;
                case '&':
                    resultStrBuf.append("&amp;");
                    break;
                case '\"':
                    resultStrBuf.append("&quot;");
                    break;
                case '\'':
                    resultStrBuf.append("&#039;");
                    break;
                default:
                    resultStrBuf.append(ch);
                    break;
            }
        }
        return resultStrBuf.toString();
    }

    /**
     * A path name delivered by function xnode-path (with special strings such as
     * "@", "[", "]", " eq ") is converted to an XML String with xml tags,
     * opened or closed such as the mode says
     *
     * @param pathName delivered by function xnode-path: Example: /archimedes[@xmlns:xlink eq "http://www.w3.org/1999/xlink"]/text/body/chap/p[@type eq "main"]/s/foreign[@lang eq "en"]
     * @param mode     open or close
     * @return xml tags opened or closed
     */
    private String pathName2XmlTags(final String pathName, final String mode) {
        final StringBuilder result = new StringBuilder();
        final List<String> elements = pathName2ElementsWithAttributes(pathName);
        if ("open".equals(mode)) {

            for (String element : elements) {
                element = element.replaceAll("\\[", " ");  // opening element: replace open bracket with space
                element = element.replaceAll(" eq ", "=");  // opening element: remove @ character
                element = element.replaceAll("@", "");  // opening element: remove @ character
                element = element.replaceAll("\\]", "");  // opening element: remove closing bracket
                if (!(element.isEmpty())) {
                    result.append('<').append(element).append('>').append('\n');
                }
            }

        } else if ("close".equals(mode)) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                String element = elements.get(i);
                element = element.replaceAll("\\[[^\\]]*\\]", "");  // closing element: remove brackets with attributes
                if (!(element.isEmpty())) {
                    result.append('<').append('/').append(element).append('>').append('\n');
                }
            }
        }
        return result.toString();
    }

    private static final String regExpr = "[a-zA-Z0-9:]+?\\[.+?\\]/" + "|" + "[a-zA-Z0-9:]+?/" + "|" + "[a-zA-Z0-9:]+?\\[.+\\]$" + "|" + "[a-zA-Z0-9:]+?$"; // pathName example: "/archimedes[@xmlns:xlink eq "http://www.w3.org/1999/xlink"]/text/body/chap/p[@type eq "main"]/s/foreign[@lang eq "en"]"
    private static final Pattern p = Pattern.compile(regExpr, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE); // both flags enabled

    private List<String> pathName2ElementsWithAttributes(String pathName) {
        final List<String> result = new ArrayList<>();
        if (pathName.charAt(0) == '/') {
            pathName = pathName.substring(1, pathName.length());
        }  // without first "/" character
        final Matcher m = p.matcher(pathName);
        while (m.find()) {
            final int msBeginPos = m.start();
            final int msEndPos = m.end();
            String elementName = pathName.substring(msBeginPos, msEndPos);
            final int elemNameSize = elementName.length();
            if (elemNameSize > 0 && elementName.charAt(elemNameSize - 1) == '/') {
                elementName = elementName.substring(0, elemNameSize - 1);
            }  // without last "/" character
            result.add(elementName);
        }
        return result;
    }

    private String getNodeXPath(Node n, final boolean setRootNamespace) {
        //if at the document level just return /
        if (n.getNodeType() == Node.DOCUMENT_NODE) {
            return "/";
        }
        /* walk up the node hierarchy
         * - node names become path names
         * - attributes become predicates
         */
        final StringBuilder buf = new StringBuilder(nodeToXPath(n, setRootNamespace));
        while ((n = getParent(n)) != null) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                buf.insert(0, nodeToXPath(n, setRootNamespace));
            }
        }
        return buf.toString();
    }

    private Node getParent(final Node n) {
        if (n == null) {
            return null;
        } else if (n instanceof Attr) {
            return ((Attr) n).getOwnerElement();
        } else {
            return n.getParentNode();
        }
    }

    /**
     * Creates an XPath for a Node
     * The nodes attribute's become predicates
     *
     * @param n The Node to generate an XPath for
     * @return StringBuilder containing the XPath
     */
    private StringBuilder nodeToXPath(final Node n, final boolean setRootNamespace) {
        final StringBuilder xpath = new StringBuilder("/" + getFullNodeName(n));
        if (setRootNamespace) {
            // set namespace only if node is root node
            final Node parentNode = getParent(n);
            final short parentNodeType = parentNode.getNodeType();
            if (parentNodeType == Node.DOCUMENT_NODE) {
                final String nsUri = n.getNamespaceURI();
                if (nsUri != null) {
                    xpath.append("[@" + "xmlns" + " eq \"").append(nsUri).append("\"]");
                }
            }
        }
        final NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node attr = attrs.item(i);
            final String fullNodeName = getFullNodeName(attr);
            final String attrNodeValue = attr.getNodeValue();
            if (fullNodeName != null && !fullNodeName.isEmpty()) {
                xpath.append("[@").append(fullNodeName).append(" eq \"").append(attrNodeValue).append("\"]");
            }
        }
        return xpath;
    }

    /**
     * Returns the full node name including the prefix if present
     *
     * @param n The node to get the name for
     * @return The full name of the node
     */
    private String getFullNodeName(final Node n) {
        final String prefix = n.getPrefix();
        final String localName = n.getLocalName();
        if (prefix == null || XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
            if (localName == null || localName.isEmpty()) {
                return "";
            } else {
                return localName;
            }
        } else {
            if (localName == null || localName.isEmpty()) {
                return "";
            } else {
                return prefix + ":" + localName;
            }
        }
    }
}
