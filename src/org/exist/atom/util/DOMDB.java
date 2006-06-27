/*
 * DOM.java
 *
 * Created on June 20, 2006, 12:31 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.util;

import org.exist.dom.ElementImpl;
import org.exist.dom.NodeListImpl;
import org.exist.storage.txn.Txn;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author R. Alexander Milowski
 */
public class DOMDB {
   
   /** Creates a new instance of DOM */
   private DOMDB() {
   }
   
   public static Element replaceTextElement(Txn txn,ElementImpl parent,String namespaceName,String localName,String value,boolean firstChild) {
      ElementImpl textE = (ElementImpl)DOM.findChild(parent,namespaceName,localName);
      if (textE==null) {
         textE = (ElementImpl)parent.getOwnerDocument().createElementNS(namespaceName,localName);
         NodeListImpl nl = new NodeListImpl(1);
         nl.add(textE);
         if (firstChild) {
            parent.insertAfter(txn,nl,parent.getFirstChild());
         } else {
            parent.appendChildren(txn,nl,-1);
         }
      }
      DOMDB.removeChildren(txn,textE);
      textE.appendChild(parent.getOwnerDocument().createTextNode(value));
      return textE;
   }
   
   public static void appendChild(Txn txn,ElementImpl parent,Node child) {
      NodeListImpl nl = new NodeListImpl(1);
      nl.add(child);
      parent.appendChildren(txn,nl,-1);
   }
   
   public static Node insertBefore(Txn txn,ElementImpl parent,Node child,Node refChild) {
      NodeListImpl nl = new NodeListImpl(1);
      nl.add(child);
      parent.insertBefore(txn,nl,refChild);
      return child;
   }
   
   public static void replaceText(Txn txn,ElementImpl textE,String value) {
      DOMDB.removeChildren(txn,textE);
      textE.appendChild(textE.getOwnerDocument().createTextNode(value));
   }
   
   public static void removeChildren(Txn txn,ElementImpl parent) {
      Node current = parent.getFirstChild();
      while (current!=null) {
         Node toRemove = current;
         current = current.getNextSibling();
         parent.removeChild(txn,toRemove);
      }
   }
   
}
