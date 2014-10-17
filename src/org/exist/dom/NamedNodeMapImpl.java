/* eXist Open Source Native XML Database
 * Copyright (C) 2000-2014,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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

import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;

public class NamedNodeMapImpl extends ArrayList<Node> implements NamedNodeMap {

    private static final long serialVersionUID = -7913316703165379285L;

    public NamedNodeMapImpl() {
        super();
    }

    @Override
    public int getLength() {
        return size();
    }

    @Override
    public Node setNamedItem(final Node arg) throws DOMException {
        add(arg);
        return arg;
    }

    @Override
    public Node setNamedItemNS(final Node arg) throws DOMException {
        return setNamedItem(arg);
    }

    @Override
    public Node item(int index) {
        if (index < size()) {
            return get(index);
        }
        return null;
    }

    @Override
    public Node getNamedItem(final String name) {
        final int i = indexOf(new QName(name));
        return (i < 0) ? null : get(i);
    }

    @Override
    public Node getNamedItemNS(final String namespaceURI, final String name) {
        final int i = indexOf(new QName(name, namespaceURI, null));
        return (i < 0) ? null : get(i);
    }

    @Override
    public Node removeNamedItem(final String name) throws DOMException {
        final int i = indexOf(new QName(name));
        final Node node = get(i);
        remove(i);
        return node;
    }

    @Override
    public Node removeNamedItemNS(final String namespaceURI, final String name)
            throws DOMException {
        final int i = indexOf(new QName(name, namespaceURI, null));
        final Node node = get(i);
        remove(i);
        return node;
    }

    private int indexOf(final QName name) {
        for (int i = 0; i < size(); i++) {
            final Node temp = get(i);
            if (temp.getLocalName().equals(name.getLocalPart())
                    && temp.getNamespaceURI().equals(name.getNamespaceURI()))
                {return i;}
        }
        return -1;
    }
}
