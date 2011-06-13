/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-2010 The eXist Project
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
 */
package org.exist.xquery.modules.exi;

import java.io.IOException;

import org.exist.dom.QName;
import org.exist.memtree.AppendingSAXAdapter;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.api.sax.SAXDecoder;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

/**
 * eXist EXI Module Extension DecodeExiFunction.
 * 
 * Decodes an Efficient XML Interchange (EXI) source to XML.
 * 
 * @author Rob Walpole
 * @version 1.0
 *
 */
public class DecodeExiFunction extends BasicFunction {

    public final static FunctionSignature signature = 
		new FunctionSignature(
				new QName("decode-to-xml", ExiModule.NAMESPACE_URI, ExiModule.PREFIX),
				"A function which returns XML from a decoded EXI source",
				new SequenceType[] {
					new FunctionParameterSequenceType("source-exi", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the EXI source")},
				new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the XML result"));

    public DecodeExiFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if(args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
		try {
			BinaryValue exiBinary = ((BinaryValue)args[0].itemAt(0));
			
			MemTreeBuilder builder = context.getDocumentBuilder();
			EXIFactory exiFactory = DefaultEXIFactory.newInstance();
			SAXDecoder decoder = new SAXDecoder(exiFactory);
			SAXAdapter adapter = new AppendingSAXAdapter(builder);
            decoder.setContentHandler(adapter);
            decoder.parse(new InputSource(exiBinary.getInputStream()));
		    
		    NodeValue node  = (NodeValue)builder.getDocument().getDocumentElement();
		    return node;
		}
		catch(EXIException exie) {
			throw new XPathException(this, new JavaErrorCode(exie.getCause()), exie.getMessage());
		}
		catch(SAXException saxe) {
			throw new XPathException(this, new JavaErrorCode(saxe.getCause()), saxe.getMessage());
		}
		catch(IOException ioex) {
			throw new XPathException(this, new JavaErrorCode(ioex.getCause()), ioex.getMessage());
		}
	}

}