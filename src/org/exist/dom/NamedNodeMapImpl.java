
/* eXist Open Source Native XML Database
 * Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id:
 */
package org.exist.dom;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.ListIterator;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class NamedNodeMapImpl extends LinkedList implements NamedNodeMap {

  public NamedNodeMapImpl() {
    super();
  }

  public int getLength() {
    return size();
  }

  public Node setNamedItem(Node arg) throws DOMException {
    add(arg);
    return arg;
  }

  public Node setNamedItemNS(Node arg) throws DOMException {
    return setNamedItem(arg);
  }

  public Node item(int index) {
    if(index < size())
      return (Node)get(index);
    return null;
  }

  public Node getNamedItem(String name) {
    NodeImpl comparable = new NodeImpl((short)0, new QName(name, "", null));
    int i = indexOf(comparable);
    return (i < 0) ? null : (Node)get(i);
  }

  public Node getNamedItemNS(String namespaceURI, String name) {
    return getNamedItem(name);
  }

  public Node removeNamedItem(String name) throws DOMException {
    NodeImpl comparable = new NodeImpl((short)0, new QName(name, "", null));
    int i = indexOf(comparable);
    remove(i);
    return comparable;
  }

  public Node removeNamedItemNS(String namespaceURI, String name)
  throws DOMException {
    NodeImpl comparable = new NodeImpl((short)0, new QName(name, "", null));
    int i = indexOf(comparable);
    remove(i);
    return comparable;
  }

  private int indexOf(NodeImpl node) {
	  ListIterator i=this.listIterator();
	  while (i.hasNext()) {
	  	NodeImpl temp=(NodeImpl)i.next();
		if (temp.getNodeName().compareTo(node.getNodeName())==0) return i.previousIndex();
	  }
	return -1;
  }
}
