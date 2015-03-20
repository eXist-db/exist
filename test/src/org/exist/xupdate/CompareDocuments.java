package org.exist.xupdate;

/*
 *  The XML:DB Initiative Software License, Version 1.0
 *
 *
 * Copyright (c) 2000-2003 The XML:DB Initiative.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        XML:DB Initiative (http://www.xmldb.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The name "XML:DB Initiative" must not be used to endorse or
 *    promote products derived from this software without prior written
 *    permission. For written permission, please contact info@xmldb.org.
 *
 * 5. Products derived from this software may not be called "XML:DB",
 *    nor may "XML:DB" appear in their name, without prior written
 *    permission of the XML:DB Initiative.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the XML:DB Initiative. For more information
 * on the XML:DB Initiative, please see <http://www.xmldb.org/>.
 */

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
*/
public class CompareDocuments {

    /**
     * Constructor
     */
    public CompareDocuments() {
    }

    public void compare(Node node1, Node node2) throws Exception{
        compare(node1, node2, "", true);
    }
    
    public void compare(Node node1, Node node2, String space, boolean show) throws Exception{
        if (node1.getNodeType()==Node.DOCUMENT_NODE && node2.getNodeType()==Node.DOCUMENT_NODE) {
            compare( ((Document)node1).getDocumentElement( ), ((Document)node2).getDocumentElement( ), space, show );
            return;
        }

        if (show) {
            System.out.print(space);
            switch (node1.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    System.out.print("@");
                default:
            }
            System.out.print(node1 + "[" + node1.getChildNodes().getLength() + "] <==> ");
            switch (node2.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    System.out.print("@");
                default:
            }
            System.out.println(node2 + "[" + node2.getChildNodes().getLength() + "]");
        }
        if (node1.getNodeType()!=node2.getNodeType()) {
            throw new Exception("different node types ("+
                    node1.getNodeType()+"!="+node2.getNodeType()+")...");
        }
        if (node1.getNamespaceURI() == null ^ node2.getNamespaceURI() == null) {
            throw new Exception("only one node has a Namespace");
        }
        if (node1.getNamespaceURI() != null && node2.getNamespaceURI() != null &&
            !node1.getNamespaceURI().equals(node2.getNamespaceURI())) {
            throw new Exception("different NamespaceURI's ("+
                    node1.getNamespaceURI()+"!="+node2.getNamespaceURI()+")...");
        }
        if (!node1.getNodeName().equals(node2.getNodeName())) {
            throw new Exception("different node names ("+
                    node1.getNodeName()+"!="+node2.getNodeName()+"...");
        }
        if (node1.getNodeValue()!=null && node2.getNodeValue()!=null && 
                !node1.getNodeValue().equals(node2.getNodeValue())) {
            throw new Exception("different node values ("+
                    node1.getNodeValue()+"!="+node2.getNodeValue()+")...");
        }
        NamedNodeMap attr1 = node1.getAttributes();
        NamedNodeMap attr2 = node2.getAttributes();
        if (attr1!=null && attr2!=null) { 
            if (attr1.getLength()!=attr2.getLength()) {
                throw new Exception("different attribute counts: node1: " + attr1.getLength() +
                	"; node2: " + attr2.getLength());
            } 
            for (int i=0; i<attr1.getLength(); i++) {
                compare(attr1.item(i), attr2.item(i), space, show);
            }
        }
        NodeList list1 = node1.getChildNodes();
        NodeList list2 = node2.getChildNodes();
        if (list1.getLength()!=list2.getLength()) {
            throw new Exception("different child node counts for node " + node1.getNodeName() + " (" +
                    list1.getLength() + "!=" + list2.getLength() + ")...");
        }
/*
        Node child1 = node1.getFirstChild();
        Node last1  = node1.getLastChild();
        Node child2 = node2.getFirstChild();
        if (!(child1==child2 && child1==null)) {
            switch (node1.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    space += "   @";
                    break;
                default:
                    space += "    ";
            }
            while (child1!=last1){
                compare (child1, child2, space, show);
                child1 = child1.getNextSibling();
                child2 = child2.getNextSibling();
            }
            compare(child1, child2, space, show);
        }
*/
        Node child1 = node1.getFirstChild();
        Node child2 = node2.getFirstChild();
        space += "    ";
        while (child1!=null){
            compare (child1, child2, space, show);
            child1 = child1.getNextSibling();
            child2 = child2.getNextSibling();
        }
    }
    
}
