
/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xpath.functions;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

public class FunLast extends Function {

  public FunLast() {
    super("last");
  }

  public int returnsType() {
    return Type.INTEGER;
  }

  public DocumentSet preselect(DocumentSet in_docs, StaticContext context) {
    return in_docs;
  }

   public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
   	Item contextItem) throws XPathException {
   		if(!Type.subTypeOf(contextItem.getType(), Type.NODE))
   			throw new XPathException("last() can only be applied to nodes");
   		NodeProxy contextNode = (NodeProxy)contextItem;
      DocumentImpl doc = contextNode.getDoc();
      int level = doc.getTreeLevel(contextNode.getGID());
      long pid = (contextNode.getGID() - doc.getLevelStartPoint(level)) /
        doc.getTreeLevelOrder(level)
        + doc.getLevelStartPoint(level - 1);
      long f_gid = (pid - doc.getLevelStartPoint(level -1)) *
        doc.getTreeLevelOrder(level) +
        doc.getLevelStartPoint(level);
      long e_gid = f_gid + doc.getTreeLevelOrder(level);
      NodeSet set = ((NodeSet)contextSequence).getRange(doc, f_gid, e_gid);
      int len = set.getLength();
      return new IntegerValue(len);
   }

  public static boolean nodesEqual(NodeImpl n1, NodeImpl n2) {
    if(n1.getNodeType() != n2.getNodeType())
      return false;
    switch(n1.getNodeType()) {
      case Node.ELEMENT_NODE:
        return n1.getNodeName().equals(n2.getNodeName());
      case Node.ATTRIBUTE_NODE:
        return n1.getNodeName().equals(n2.getNodeName()) &&
          ((Attr)n1).getValue().equals(((Attr)n2).getValue());
      case Node.TEXT_NODE:
        return n1.getNodeValue().equals(n2.getNodeValue());
      default:
        return false;
    }
  }

  public String pprint() {
     return "last()";
  }
}
