/*
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
 *  $Id: RenderFunction.java 10610 2009-11-26 09:12:00Z shabanovd $
 */

package org.exist.xquery.functions.system;

import java.io.File;
import java.util.Date;

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 */
public class GetLibInfoFunction extends LibFunction {
	
	public final static FunctionSignature signatures[] = {

			new FunctionSignature(
					new QName("get-lib-info", SystemModule.NAMESPACE_URI,
							SystemModule.PREFIX),
					"Return name and size requested lib file from exist's libs",
					new SequenceType[] {
						new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "name of lib")
					},
					new FunctionParameterSequenceType("result", Type.NODE, Cardinality.ZERO_OR_ONE, "result"))
			};


	public GetLibInfoFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		Sequence result = Sequence.EMPTY_SEQUENCE;
		
		String name = args[0].itemAt(0).getStringValue();
		
		File lib = getLib(name);
		
		if (lib!=null){
			MemTreeBuilder builder = context.getDocumentBuilder();
			builder.startDocument();
			builder.startElement(new QName("lib", null, null), null);
			builder.addAttribute(new QName("name", null, null), lib.getName());
	        Long sizeLong = lib.length();
	        String sizeString = Long.toString(sizeLong);
			builder.addAttribute(new QName("size", null, null), sizeString);
	        builder.addAttribute(new QName("modified", null, null), new DateTimeValue(new Date(lib.lastModified())).getStringValue());
			builder.endElement();
			result = (NodeValue) builder.getDocument().getDocumentElement();
		}
		
		return result;
		
	}

}