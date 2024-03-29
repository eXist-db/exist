/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.exi;

import java.io.IOException;
import java.io.InputStream;

import org.exist.dom.QName;
import org.exist.dom.memtree.AppendingSAXAdapter;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.siemens.ct.exi.core.EXIFactory;
import com.siemens.ct.exi.grammars.GrammarFactory;
import com.siemens.ct.exi.main.api.sax.SAXDecoder;
import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.Grammars;
import com.siemens.ct.exi.core.helpers.DefaultEXIFactory;

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

    public final static FunctionSignature[] signatures = {
		new FunctionSignature(
				new QName("decode-to-xml", ExiModule.NAMESPACE_URI, ExiModule.PREFIX),
				"A function which returns XML from a decoded EXI source",
				new SequenceType[] {
					new FunctionParameterSequenceType("source-exi", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the EXI source")},
				new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the XML result")
		),
		new FunctionSignature(
				new QName("decode-to-xml", ExiModule.NAMESPACE_URI, ExiModule.PREFIX),
				"A function which return XML from a schema decoded EXI source",
				new SequenceType[] {
					new FunctionParameterSequenceType("source-exi", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the EXI source"),
					new FunctionParameterSequenceType("schema-location", Type.ITEM, Cardinality.EXACTLY_ONE, "the XSD schema location")
				},
				new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the XML result")
		)
    };

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

			context.pushDocumentContext();
			try {
				MemTreeBuilder builder = context.getDocumentBuilder();

				// create default factory and EXI grammar for schema
				EXIFactory exiFactory = DefaultEXIFactory.newInstance();
				if (args.length > 1) {
					if (!args[1].isEmpty()) {
						Item xsdItem = args[1].itemAt(0);
						try (InputStream xsdInputStream = EXIUtils.getInputStream(xsdItem, context, this)) {
							GrammarFactory grammarFactory = GrammarFactory.newInstance();
							Grammars grammar = grammarFactory.createGrammars(xsdInputStream);
							exiFactory.setGrammars(grammar);
						}

					}
				}
				SAXDecoder decoder = new SAXDecoder(exiFactory);
				SAXAdapter adapter = new AppendingSAXAdapter(this, builder);
				decoder.setContentHandler(adapter);

				try (InputStream inputStream = exiBinary.getInputStream()) {
					decoder.parse(new InputSource(inputStream));
				}

				return (NodeValue) builder.getDocument().getDocumentElement();
			} finally {
				context.popDocumentContext();
			}
		}
		catch(EXIException | SAXException | IOException exie) {
			throw new XPathException(this, new JavaErrorCode(exie.getCause()), exie.getMessage());
		}
	}

}
