/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public abstract class BinaryOp extends PathExpr {

  public BinaryOp() {
    super();
  }

  public int returnsType() {
    return Type.NODE;
  }

  public Expression getLeft() { return getExpression(0); }
  public Expression getRight() { return getExpression(1); }
  
  public void setLeft(Expression expr) {
  	steps.add(0, expr);
  }
  public void setRight(Expression expr) {
  	steps.add(1, expr);
  }
  
  public abstract DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException;
  public abstract Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
  	Item contextItem) throws XPathException;

  public abstract String pprint();
}
