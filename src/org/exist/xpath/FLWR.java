
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
package org.exist.xpath;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import org.w3c.dom.NodeList;
import org.exist.*;
import org.exist.dom.*;

public class FLWR extends AbstractExpression {

  protected HashMap bindings = new HashMap();
  protected ArrayList returnList = new ArrayList();

  public FLWR() {
  }

  public int returnsType() {
    return Constants.TYPE_NODELIST;
  }

  public void addVariable(VarBinding v) {
    bindings.put(v.getName(), v);
  }

  public boolean hasVariable(String name) {
    return bindings.containsKey(name);
  }

  public VarBinding getVariable(String name) {
    System.out.println("get Variable " + name);
    return (VarBinding)bindings.get(name);
  }

  public void addReturnClause(Expression ret) {
    returnList.add(ret);
  }

  public DocumentSet getDocumentSet() { return null; }
  public void addDocument(DocumentImpl doc) {}

  public DocumentSet preselect(DocumentSet docs_in) {
    return docs_in;
  }

  public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, NodeProxy node) {
	  NodeSet result = new ArraySet(10);
      for(Iterator i = returnList.iterator(); i.hasNext(); ) {
		  Expression r = (Expression)i.next();
		  NodeList t = r.eval(context, docs, null, null).getNodeList();
		  result.addAll(t);
      }
      return new ValueNodeSet(result);
  }

  public String pprint() {
    StringBuffer buf = new StringBuffer();
    buf.append("FOR ");
    for(Iterator i = bindings.values().iterator(); i.hasNext(); ) {
      VarBinding v = (VarBinding)i.next();
      buf.append('$');
      buf.append(v.getName());
      buf.append(" IN ");
      buf.append(v.binding.pprint());
    }
    buf.append(" RETURN ");
    for(Iterator i = returnList.iterator(); i.hasNext(); ) {
      Expression e = (Expression)i.next();
      buf.append(e.pprint());
      if(i.hasNext())
        buf.append(", ");
    }
    return buf.toString();
  }
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
	}

}
