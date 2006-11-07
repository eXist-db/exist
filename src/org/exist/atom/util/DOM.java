/*
 * DOM.java
 *
 * Created on June 20, 2006, 12:31 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author R. Alexander Milowski
 */
public class DOM {
   
   /** Creates a new instance of DOM */
   private DOM() {
   }
   
   public static void forEachChild(Element parent,NodeHandler filter) {
      Node current = parent.getFirstChild();
      while (current!=null) {
         Node toProcess = current;
         current = current.getNextSibling();
         filter.process(parent,toProcess);
      }
   }
   
   public static void findChildren(Element parent,String namespaceName,String localName,NodeHandler filter) {
      Node current = parent.getFirstChild();
      while (current!=null) {
         if (current.getNodeType()!=Node.ELEMENT_NODE) {
            current = current.getNextSibling();
            continue;
         }
         if ((namespaceName==null && current.getNamespaceURI()!=null) ||
             (namespaceName!=null && !namespaceName.equals(current.getNamespaceURI())) ) {
            current = current.getNextSibling();
            continue;
         }
         if (current.getLocalName().equals(localName)) {
            Node toProcess = current;
            current = current.getNextSibling();
            filter.process(parent,toProcess);
         } else {
            current = current.getNextSibling();
         }
      }
   }
   
   public static Element findChild(Element parent,String namespaceName,String localName) {
      Node current = parent.getFirstChild();
      while (current!=null) {
         if (current.getNodeType()!=Node.ELEMENT_NODE) {
            current = current.getNextSibling();
            continue;
         }
         if ((namespaceName==null && current.getNamespaceURI()!=null) ||
             (namespaceName!=null && !namespaceName.equals(current.getNamespaceURI())) ) {
            current = current.getNextSibling();
            continue;
         }
         if (current.getLocalName().equals(localName)) {
            return (Element)current;
         }
         current = current.getNextSibling();
      }
      return null;
   }
   
   public static Element replaceTextElement(Element parent,String namespaceName,String localName,String value,boolean firstChild) {
      return DOM.replaceTextElement(parent,namespaceName,localName,value,firstChild,false);
   }
   public static Element replaceTextElement(Element parent,String namespaceName,String localName,String value,boolean firstChild,boolean wrap) {
      Element textE = DOM.findChild(parent,namespaceName,localName);
      if (textE==null) {
         textE = parent.getOwnerDocument().createElementNS(namespaceName,localName);
         if (firstChild) {
            if (wrap) {
               parent.insertBefore(parent.getOwnerDocument().createTextNode("\n"),parent.getFirstChild());
            }
            parent.insertBefore(textE,parent.getFirstChild());
         } else {
            parent.appendChild(textE);
            if (wrap) {
               parent.appendChild(parent.getOwnerDocument().createTextNode("\n"));
            }
         }
      }
      DOM.removeChildren(textE);
      textE.appendChild(parent.getOwnerDocument().createTextNode(value));
      return textE;
   }
   
   public static void replaceText(Element textE,String value) {
      DOM.removeChildren(textE);
      textE.appendChild(textE.getOwnerDocument().createTextNode(value));
   }
   
   public static void removeChildren(Element parent) {
      Node current = parent.getFirstChild();
      while (current!=null) {
         Node toRemove = current;
         current = current.getNextSibling();
         parent.removeChild(toRemove);
      }
   }
   
   public static String textContent(Node n) {
      if (n.getNodeType()==Node.ELEMENT_NODE) {
    	 StringBuffer builder = new StringBuffer();
         Node current = n.getFirstChild();
         while (current!=null) {
            int type = current.getNodeType();
            if (type==Node.CDATA_SECTION_NODE || type==Node.TEXT_NODE) {
               builder.append(current.getNodeValue());
            }
            current = current.getNextSibling();
         }
         return builder.toString();
      } else {
         return n.getNodeValue();
      }
   }
   
}
