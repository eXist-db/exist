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
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:false().
 * 
 * @author wolf
 */
public class FunFalse extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("false", Module.BUILTIN_FUNCTION_NS),
			"Always returns the boolean value false",
            null,
			new SequenceType(Type.BOOLEAN, Cardinality.ONE));

	public FunFalse(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.BOOLEAN;
	}

	/**
	 * Always returns false.
	 */
	public Sequence eval(Sequence[] args, Sequence contextSet) {
		return BooleanValue.FALSE;
	}

	public String pprint() {
		return "false()";
	}
}
