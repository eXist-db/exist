
/* eXist Native XML Database
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;

public class IntNumber extends AbstractExpression {

	protected double dValue;

	public IntNumber(double doubleValue) {
		this.dValue = doubleValue;
	}

	public int returnsType() {
		return Constants.TYPE_NUM;
	}

	public DocumentSet preselect(DocumentSet in_docs) {
		return in_docs;
	}

	public Value eval(
		StaticContext context,
		DocumentSet docs,
		NodeSet contextSet,
		NodeProxy contextNode) {
		return new ValueNumber(dValue);
	}

	public String pprint() {
		return Double.toString(dValue);
	}

}
