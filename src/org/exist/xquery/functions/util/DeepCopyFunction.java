/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 *
 * Original package com.ocom.leaseman.modules.lease (Scott Warren)
 */
package org.exist.xquery.functions.util;

import java.util.Properties;

import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.xml.sax.SAXException;

public class DeepCopyFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("deep-copy", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Performs a Deep Clone of the Parameter $a",
			new SequenceType[]
			{
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE),
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE));

	public DeepCopyFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);			
	}
 
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        
        Item a = args[0].itemAt(0);
        
        MemTreeBuilder builder = new MemTreeBuilder(context);
        builder.startDocument();
        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        
        try {
            Properties props = new Properties();
            a.toSAX(context.getBroker(), receiver, props);
            
        } catch (SAXException e) {
            throw new XPathException("Cannot Deep-copy Item $a");
        }
        
        builder.endDocument();
        
        return (NodeValue)receiver.getDocument().getDocumentElement();
    }
}
