/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;


/**
 * @author wolf
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FunNodeName extends BasicFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-name", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			" Returns an expanded-QName for node kinds that can have names. For other kinds " +
			"of nodes it returns the empty sequence. If $a is the empty sequence, the " +
			"empty sequence is returned.",
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE),
			true);
    
    /**
     * @param context
     * @param signature
     */
    public FunNodeName(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if(args[0].getLength() == 0)
            return Sequence.EMPTY_SEQUENCE;
        NodeValue val = (NodeValue)args[0];
        Node node = val.getNode();
        if(node.getNodeType() == Node.ELEMENT_NODE ||
                node.getNodeType() == Node.ATTRIBUTE_NODE) {
            if(val.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                return new QNameValue(context, ((NodeImpl)val).getQName());
            else
                return new QNameValue(context, ((org.exist.dom.NodeImpl)node).getQName());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

}
