
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

import org.exist.dom.*;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 * This is the base interface implemented by all classes which are part
 * of an xpath-expression.
 */
public interface Expression {

	/**
	 * Evaluate the expression represented by this object.
	 *
	 * Depending on the context in which this expression is executed,
	 * either context, node or both of them may be set. An implementing
	 * class should know how to handle this. Most classes only expect 
	 * context to contain a list of nodes which represents the current
	 * context of this expression.
	 *
	 * @param context the static xpath context
	 * @param docs the set of documents all nodes belong to.
	 * @param contextSet the node-set which defines the current context node-set.
	 * @param node a single node, taken from context. This defines the node,
	 * the expression should work on.
	 */
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException;

	/**
	 * Evaluate the expression represented by this object.
	 *
	 * Depending on the context in which this expression is executed,
	 * either context, node or both of them may be set. An implementing
	 * class should know how to handle this. Most classes only expect 
	 * context to contain a list of nodes which represents the current
	 * context of this expression.
	 *
	 * @param context the static xpath context
	 * @param docs the set of documents all nodes belong to.
	 * @param contextSet the node-set which defines the current context node-set.
	 */
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence)
		throws XPathException;
	
	/**
	 * Determine the documents, taken from in_docs, for which this expression
	 * will possibly yield a result. An expression does not have to do
	 * anything here. It may simply return in_docs.
	 *
	 * This method is used to restrict the range of documents in question for
	 * a given xpath-expression. It is called before the xpath-expression is
	 * actually executed.
	 */
	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException;

	/**
	 * The type of value, this expression returns.
	 *
	 * Depending on the type of expression, this method should
	 * return one of the constants defined in class Constants, e.g.
	 * TYPE_NODELIST, TYPE_STRING, TYPE_NUM, TYPE_BOOL.
	 */
	public int returnsType();
	
	/**
	 * This method is called to inform the expression object that
	 * it is executed inside an XPath predicate.
	 * 
	 * @param inPredicate
	 */
	public void setInPredicate(boolean inPredicate);

	/**
	 * Return a readable representation of this expression.
	 *
	 * This method is called whenever the xpath-query should be
	 * displayed to the user.
	 */
	public String pprint();
	
}
