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
 */

package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

public class FunEndsWith extends Function {

	public FunEndsWith(BrokerPool pool) {
		super(pool, "ends-with");
	}

	public int returnsType() {
		return Constants.TYPE_BOOL;
	}

	public Value eval(
		StaticContext context,
		DocumentSet docs,
		NodeSet contextSet,
		NodeProxy contextNode) throws XPathException {
		if (getArgumentCount() != 2)
			throw new IllegalArgumentException("ends-with expects two arguments");
		if (contextNode != null)
			contextSet = new SingleNodeSet(contextNode);

		String s1 = getArgument(0).eval(context, docs, contextSet).getStringValue();
		String s2 = getArgument(1).eval(context, docs, contextSet).getStringValue();
		if (s1.startsWith(s2))
			return new ValueBoolean(true);
		else
			return new ValueBoolean(false);
	}

}
