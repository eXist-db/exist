/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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

import java.io.ByteArrayInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.exist.dom.QName;
import org.exist.util.serializer.EXISerializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.exceptions.EXIException;

/**
 * eXist EXI Module Extension EncodeExiFunction.
 * 
 * Encodes an XML source in Efficient XML Interchange (EXI) format.
 * 
 * @author Rob Walpole
 * @version 1.0
 *
 */
public class EncodeExiFunction extends BasicFunction {

    public final static FunctionSignature[] signatures = {
		new FunctionSignature(
				new QName("encode-from-xml", ExiModule.NAMESPACE_URI, ExiModule.PREFIX),
				"A function which returns encoded EXI from an XML source.",
				new SequenceType[] {
					new FunctionParameterSequenceType("source-xml", Type.NODE, Cardinality.EXACTLY_ONE, "the XML source")},
				new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary EXI result document")
		),
		new FunctionSignature(
				new QName("encode-from-xml", ExiModule.NAMESPACE_URI, ExiModule.PREFIX),
				"A function which returns schema encoded EXI from an XML source.",
				new SequenceType[] {
					new FunctionParameterSequenceType("source-xml", Type.NODE, Cardinality.EXACTLY_ONE, "the XML source"),
					new FunctionParameterSequenceType("schema-location", Type.ITEM, Cardinality.EXACTLY_ONE, "the XSD schema location")
				},
				new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary EXI result document")
		)
    };

    public EncodeExiFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if(args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			EXISerializer exiSerializer = null;
			if(args.length > 1) {
				if(!args[1].isEmpty()) {
					Item xsdItem = args[1].itemAt(0);
					InputStream xsdInputStream = EXIUtils.getInputStream(xsdItem, context);
					exiSerializer = new EXISerializer(baos, xsdInputStream);
				}
				else {
					exiSerializer = new EXISerializer(baos);
				}
			}
			else {
				exiSerializer = new EXISerializer(baos);
			}
			Item inputNode = args[0].itemAt(0);
			exiSerializer.startDocument();
	        inputNode.toSAX(context.getBroker(), exiSerializer, new Properties());
	        exiSerializer.endDocument();
	        return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()));
		}
		catch(IOException ioex) {
			// TODO - test!
			throw new XPathException(this, ErrorCodes.FODC0002, ioex.getMessage());
		}
		catch(EXIException | SAXException exie) {
			throw new XPathException(this, new JavaErrorCode(exie.getCause()), exie.getMessage());
		}
	}
	
}