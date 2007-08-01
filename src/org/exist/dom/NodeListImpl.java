
/* eXist xml document repository and xpath implementation
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
 */
package org.exist.dom;

import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NodeListImpl extends ArrayList implements NodeList {

  public NodeListImpl() {
    super();
  }

  public NodeListImpl(int initialCapacity) {
    super(initialCapacity);
  }

  public void add(Node node) {
    if(node == null)
      return;
    super.add(node);
  }

  public void addAll(NodeList other) {
    if(other.getLength() == 0)
      return;
    for(int i = 0; i < other.getLength(); i++)
      add(other.item(i));
  }

  public int getLength() {
    return size();
  }

  public Node item(int pos) {
      if(pos >= size())
          return null;
      return (Node)get(pos);
  }
}
