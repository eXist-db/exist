/* eXist xml document repository and xpath implementation
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
 */
package org.exist.dom;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

public class NodeListImpl extends ArrayList<Node> implements NodeList {

    private static final long serialVersionUID = 5505309345079983721L;

    public NodeListImpl() {
        super();
    }

    public NodeListImpl(final int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public boolean add(final Node node) {
        if (node == null) {
            return false;
        }
        return super.add(node);
    }

    /**
     * Add all elements of the other NodeList to
     * this NodeList
     * @param other NodeList to add
     * @return true if all elements were added, false
     *   if none or only some were added.
     */
    public boolean addAll(final NodeList other) {
        if (other.getLength() == 0) {
            return false;
        } else {
            boolean result = true;
            for(int i = 0; i < other.getLength(); i++) {
                if(!add(other.item(i))) {
                    result = false;
                    break;
                }
            }
            return result;
        }
    }

    @Override
    public int getLength() {
        return size();
    }

    @Override
    public Node item(final int pos) {
        if (pos >= size()) {
            return null;
        }
        return get(pos);
    }
}
