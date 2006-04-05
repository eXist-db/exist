
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

import java.util.ArrayList;

import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class NamedNodeMapImpl extends ArrayList implements NamedNodeMap {

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
		if (index < size())
			return (Node) get(index);
		return null;
	}

	public Node getNamedItem(String name) {
		int i = indexOf(new QName(name));
		return (i < 0) ? null : (Node) get(i);
	}

	public Node getNamedItemNS(String namespaceURI, String name) {
		int i = indexOf(new QName(name, namespaceURI, null));
		return (i < 0) ? null : (Node) get(i);
	}

	public Node removeNamedItem(String name) throws DOMException {
		int i = indexOf(new QName(name));
		Node node = (Node) get(i);
		remove(i);
		return node;
	}

	public Node removeNamedItemNS(String namespaceURI, String name)
		throws DOMException {
		int i = indexOf(new QName(name, namespaceURI, null));
		Node node = (Node) get(i);
		remove(i);
		return node;
	}

	private int indexOf(QName name) {
		for (int i = 0; i < size(); i++) {
			Node temp = (Node) get(i);
			if (temp.getLocalName().equals(name.getLocalName())
				&& temp.getNamespaceURI().equals(name.getNamespaceURI()))
				return i;
		}
		return -1;
	}
}
