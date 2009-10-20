/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
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
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Delivers the fragment between two nodes (normally milestones) of a document.
 * It leads to more performance for most XML documents because it
 * determines the fragment directly by the EmbeddedXmlReader and not by 
 * XQL operators.
 * @author Josef Willenborg, Max Planck Institute for the history of science,
 * http://www.mpiwg-berlin.mpg.de, jwillenborg@mpiwg-berlin.mpg.de 
 */
public class GetFragmentBetween extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(GetFragmentBetween.class);

  public final static FunctionSignature signature =
    new FunctionSignature(
      new QName("get-fragment-between", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns an xml fragment or a sequence of nodes between two elements (normally milestone elements). " +
        "The $beginning-node represents the first node/milestone element, $ending-node, the second one. " +
        "The third argument, $make-fragment, is " +
        "a boolean value for the path completion. If it is set to true() the " +
        "result sequence is wrapped into a parent element node. " +
        "Example call of the function for getting the fragment between two TEI page break element nodes: " +
        "  let $fragment := util:get-fragment-between(//pb[1], //pb[2], true())" ,
        new SequenceType[] { 
                             new FunctionParameterSequenceType("beginning-node", Type.NODE, Cardinality.ZERO_OR_ONE, "The first node/milestone element"),
                             new FunctionParameterSequenceType("ending-node", Type.NODE, Cardinality.ZERO_OR_ONE, "The second node/milestone element"),
                             new FunctionParameterSequenceType("make-fragment", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "The flag make a fragment.")
                           },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "the string containing the fragments between the two node/milestone elements."));

  public GetFragmentBetween(XQueryContext context) {
    super(context, signature);
	}

  /**
   * Get the fragment between two elements (normally milestone elements) of a document 
   * @param args 1. first node (e.g. pb[10])  2. second node (e.g.: pb[11]) 3. pathCompletion:
   * open and closing tags before and after the fragment are appended (Default: true)  
   * @return the fragment between the two nodes
   * @throws XPathException
   */
  public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

    Sequence ms1 = args[0];
    Sequence ms2 = args[1];
    if (ms1.isEmpty()) {
      throw new XPathException(this, "your first argument delivers an empty node (no valid node position in document)");
    }
    Node ms1Node = null;
    if (! (ms1.itemAt(0) == null)) 
      ms1Node = ((NodeValue) ms1.itemAt(0)).getNode(); 
    Node ms2Node = null;
    if (! (ms2.itemAt(0) == null)) 
      ms2Node = ((NodeValue) ms2.itemAt(0)).getNode();
    Sequence seqPathCompletion = args[2];
    boolean pathCompletion = true;  // default
    if (! (seqPathCompletion.itemAt(0) == null)) {
      pathCompletion = seqPathCompletion.effectiveBooleanValue();
    }
    // fetch the fragment between the two milestones
    StringBuilder fragment = getFragmentBetween(ms1Node, ms2Node);
    if (pathCompletion) {
      String msFromPathName = getNodeXPath(ms1Node.getParentNode());
      String openElementsOfMsFrom = pathName2XmlTags(msFromPathName, "open");    
      String closingElementsOfMsTo = "";
      if (!(ms2Node == null)) {
        String msToPathName = getNodeXPath(ms2Node.getParentNode());
        closingElementsOfMsTo = pathName2XmlTags(msToPathName, "close");  
      }
      fragment.insert(0, openElementsOfMsFrom);
      fragment.append(closingElementsOfMsTo);
    }
    StringValue strValFragment = new StringValue(fragment.toString());
    ValueSequence resultFragment = new ValueSequence();
    resultFragment.add(strValFragment);
    return resultFragment;
  }

  /**
   * Fetch the fragment between two nodes (normally milestones) in an XML document
   * @param node1 first node from which down to the node node2 the XML fragment is delivered as a string
   * @param node2 the node to which down the XML fragment is delivered as a string
   * @return fragment between the two nodes
   * @throws XPathException
   */
  private StringBuilder getFragmentBetween(Node node1, Node node2) throws XPathException {
    StoredNode storedNode1 = (StoredNode) node1;
    StoredNode storedNode2 = (StoredNode) node2;
    String node1NodeId = storedNode1.getNodeId().toString();
    String node2NodeId = "-1";
    if (! (node2 == null))
      node2NodeId = storedNode2.getNodeId().toString();
    DocumentImpl docImpl = (DocumentImpl) node1.getOwnerDocument();
    BrokerPool brokerPool = null;
    DBBroker dbBroker = null;
    StringBuilder resultFragment = new StringBuilder("");
    String actualNodeId = "-2";
    boolean getFragmentMode = false;
    try {
      brokerPool = docImpl.getBrokerPool();
      dbBroker = brokerPool.get(null);
      EmbeddedXMLStreamReader reader = null;
      NodeList children = docImpl.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        StoredNode docChildStoredNode = (StoredNode) children.item(i);
        reader = dbBroker.getXMLStreamReader(docChildStoredNode, false);
        while (reader.hasNext() && ! node2NodeId.equals(actualNodeId)) {
          int status = reader.next();
          switch (status) {
            case XMLStreamReader.START_DOCUMENT:
            case XMLStreamReader.END_DOCUMENT:
              break;
            case XMLStreamReader.START_ELEMENT :
              actualNodeId = reader.getNode().getNodeId().toString();
              if (actualNodeId.equals(node1NodeId)) 
                getFragmentMode = true;
              if (actualNodeId.equals(node2NodeId)) 
                getFragmentMode = false;
              if (getFragmentMode) {
                String startElementTag = getStartElementTag(reader);
                resultFragment.append(startElementTag);
              }
              break;
            case XMLStreamReader.END_ELEMENT :
              if (getFragmentMode) {
                String endElementTag = getEndElementTag(reader);
                resultFragment.append(endElementTag);
              }
              break;
            case XMLStreamReader.CHARACTERS :
              if (getFragmentMode) {
                String characters = getCharacters(reader);
                resultFragment.append(characters);
              }
              break;
            case XMLStreamReader.CDATA :
              if (getFragmentMode) {
                String cdata = getCDataTag(reader);
                resultFragment.append(cdata);
              }
              break;
            case XMLStreamReader.COMMENT :
              if (getFragmentMode) {
                String comment = getCommentTag(reader);
                resultFragment.append(comment);
              }
              break;
            case XMLStreamReader.PROCESSING_INSTRUCTION :
              if (getFragmentMode) {
                String piTag = getPITag(reader);
                resultFragment.append(piTag);
              }
              break;
          }
        }
      }
    } catch (EXistException e) {
      throw new XPathException(this, "An error occurred while getFragmentBetween: " + e.getMessage(), e);
    } catch (XMLStreamException e) {
      throw new XPathException(this, "An error occurred while getFragmentBetween: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new XPathException(this, "An error occurred while getFragmentBetween: " + e.getMessage(), e);
    } finally {
      if (brokerPool != null)
        brokerPool.release(dbBroker);  
    }
    return resultFragment;
  }

  private String getStartElementTag(EmbeddedXMLStreamReader reader) {
    String elemName = reader.getLocalName();
    String elemAttrString = "";
    String elemNsString ="";
    int nsCount = reader.getNamespaceCount();
    for (int ni = 0; ni < nsCount; ni++) {
      String nsPrefix = reader.getNamespacePrefix(ni);
      String nsUri = reader.getNamespaceURI(ni);
      String nsString = "xmlns:" + nsPrefix + "=\"" + nsUri + "\"";
      elemNsString = elemNsString + " " +nsString;
    }
    int attrCount = reader.getAttributeCount();
    for (int j = 0; j < attrCount; j++) {
      String attrNamePrefix = reader.getAttributePrefix(j);
      String attrName = reader.getAttributeLocalName(j);
      String attrValue = reader.getAttributeValue(j);
      String attrString = "";
      if (! (attrNamePrefix == null || attrNamePrefix.length() == 0))
        attrString = attrNamePrefix + ":";
      if (attrName.toLowerCase().equals("href")) {
        attrValue = escape(attrValue);
      }
      attrString = attrString + attrName + "=\"" + attrValue + "\"";
      elemAttrString = elemAttrString + " " + attrString;
    }
    String elemPrefix = reader.getPrefix();
    String elemPart = "";
    if (! (elemPrefix == null || elemPrefix.length() == 0))
      elemPart = elemPrefix + ":";
    elemPart = elemPart + elemName;
    String elementString = "<" + elemPart + elemNsString + elemAttrString + ">";
    return elementString;
  }

  private String getEndElementTag(EmbeddedXMLStreamReader reader) {
    String elemName = reader.getLocalName();
    String elemPrefix = reader.getPrefix();
    String elemPart = "";
    if (! (elemPrefix == null || elemPrefix.length() == 0))
      elemPart = elemPrefix + ":";
    elemPart = elemPart + elemName;
    return "</" + elemPart + ">";
  }
  
  private String getCharacters(EmbeddedXMLStreamReader reader) {
    String xmlChars = reader.getText();
    xmlChars = escape(xmlChars);
    return xmlChars;
  }

  private String getCDataTag(EmbeddedXMLStreamReader reader) {
    char[] chars = reader.getTextCharacters();
    return "<![CDATA[\n" + new String(chars) + "\n]]>";
  }

  private String getCommentTag(EmbeddedXMLStreamReader reader) {
    char[] chars = reader.getTextCharacters();
    return "<!--" + new String(chars) + "-->";
  }

  private String getPITag(EmbeddedXMLStreamReader reader) {
    String piTarget = reader.getPITarget();
    String piData = reader.getPIData();
    if (! (piData == null || piData.length() == 0))
      piData = " " + piData;
    else
      piData = "";
    return "<?" + piTarget + piData + "?>";
  }

  private String escape(String inputStr) {
    StringBuilder resultStrBuf = new StringBuilder();
    for (int i = 0; i < inputStr.length(); i++) {
      char ch = inputStr.charAt(i);
      switch (ch) {
        case '<' :
          resultStrBuf.append("&lt;");
          break;
        case '>' :
          resultStrBuf.append("&gt;");
          break;
        case '&' :
          resultStrBuf.append("&amp;");
          break;
        case '\"' :
          resultStrBuf.append("&quot;");
          break;
        case '\'' :
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
   * @param pathName delivered by function xnode-path: Example: /archimedes[@xmlns:xlink eq "http://www.w3.org/1999/xlink"]/text/body/chap/p[@type eq "main"]/s/foreign[@lang eq "en"]
   * @param mode open or close
   * @return xml tags opened or closed
   */
  private String pathName2XmlTags(String pathName, String mode) {
    String result = "";
    ArrayList<String> elements = pathName2ElementsWithAttributes(pathName);
    if (mode.equals("open")) {
      for (int i=0; i < elements.size(); i++) {
        String element = elements.get(i);
        element = element.replaceAll("\\[", " ");  // opening element: replace open bracket with space
        element = element.replaceAll(" eq ", "=");  // opening element: remove @ character 
        element = element.replaceAll("@", "");  // opening element: remove @ character 
        element = element.replaceAll("\\]", "");  // opening element: remove closing bracket
        if (! (element.length() == 0))
          result += "<" + element + ">\n";
      }
    } else if (mode.equals("close")) {
      for (int i=elements.size()-1; i >= 0; i--) {
        String element = elements.get(i);
        element = element.replaceAll("\\[[^\\]]*\\]", "");  // closing element: remove brackets with attributes
        if (! (element.length() == 0))
          result += "</" + element + ">\n";
      }
    }
    return result;
  }
  
  private ArrayList<String> pathName2ElementsWithAttributes(String pathName) {
    ArrayList<String> result = new ArrayList<String>();
    if (pathName.charAt(0) == '/')
      pathName = pathName.substring(1, pathName.length());  // without first "/" character
    String regExpr = "[a-zA-Z0-9]+?\\[.+?\\]/" + "|" + "[a-zA-Z0-9]+?/" + "|" + "[a-zA-Z0-9]+?\\[.+\\]$" + "|" + "[a-zA-Z0-9]+?$"; // pathName example: "/archimedes[@xmlns:xlink eq "http://www.w3.org/1999/xlink"]/text/body/chap/p[@type eq "main"]/s/foreign[@lang eq "en"]"
    Pattern p = Pattern.compile(regExpr, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE); // both flags enabled
    Matcher m = p.matcher(pathName);
    while (m.find()) {
      int msBeginPos = m.start();
      int msEndPos = m.end();
      String elementName = pathName.substring(msBeginPos, msEndPos);
      int elemNameSize = elementName.length();
      if (elemNameSize > 0 && elementName.charAt(elemNameSize - 1) == '/')
        elementName = elementName.substring(0, elemNameSize - 1);  // without last "/" character
      result.add(elementName);
    }
    return result;
  }
  
  private String getNodeXPath(Node n) {
    //if at the document level just return /
    if(n.getNodeType() == Node.DOCUMENT_NODE)
      return "/";
    /* walk up the node hierarchy
     * - node names become path names 
     * - attributes become predicates
     */
    StringBuilder buf = new StringBuilder(nodeToXPath(n));
    while((n = n.getParentNode()) != null) {
      if(n.getNodeType() == Node.ELEMENT_NODE) {
        buf.insert(0, nodeToXPath(n));
      }
    }
    return buf.toString();
  }
  
  /**
   * Creates an XPath for a Node
   * The nodes attribute's become predicates
   * 
   * @param n The Node to generate an XPath for
   * @return StringBuilder containing the XPath
   */
  private StringBuilder nodeToXPath(Node n) {
    StringBuilder xpath = new StringBuilder("/" + getFullNodeName(n));
    NamedNodeMap attrs = n.getAttributes();
    for(int i = 0; i < attrs.getLength(); i++) {
      Node attr = attrs.item(i);
      String fullNodeName = getFullNodeName(attr);
      String attrNodeValue = attr.getNodeValue();
      if (!fullNodeName.equals("") && (! (fullNodeName == null)))
        xpath.append("[@" + fullNodeName + " eq \"" + attrNodeValue + "\"]");
    }
    return xpath;
  }

  /**
   * Returns the full node name including the prefix if present
   * 
   * @param n The node to get the name for
   * @return The full name of the node
   */
  private String getFullNodeName(Node n) {
    String prefix = n.getPrefix();
    String localName = n.getLocalName();
    if (prefix == null || prefix.equals("")) {
      if (localName == null || localName.equals(""))
        return "";
      else
        return localName;
    } else {
      if (localName == null || localName.equals(""))
        return "";
      else
        return prefix + ":" + localName;
    }
  }
}
