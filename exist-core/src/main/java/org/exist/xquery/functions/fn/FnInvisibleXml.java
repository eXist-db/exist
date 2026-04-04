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
package org.exist.xquery.functions.fn;

import de.bottlecaps.markup.Blitz;
import de.bottlecaps.markup.BlitzException;
import de.bottlecaps.markup.BlitzParseException;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.XMLReaderPool;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import java.io.StringReader;

/**
 * Implements fn:invisible-xml() (XQuery 4.0).
 *
 * Compiles an Invisible XML grammar and returns a function that parses input
 * strings into XML documents.
 *
 * Uses the Markup Blitz library for ixml grammar compilation and parsing.
 * Integration pattern informed by BaseX's implementation.
 */
public class FnInvisibleXml extends BasicFunction {

    // Blitz.generateFromXml() is not thread-safe — synchronize XML grammar compilation
    private static final Object BLITZ_XML_LOCK = new Object();

    private static final FunctionParameterSequenceType PARAM_GRAMMAR =
            new FunctionParameterSequenceType("grammar", Type.ITEM,
                    Cardinality.ZERO_OR_ONE, "The ixml grammar (string or element node)");
    private static final FunctionParameterSequenceType PARAM_OPTIONS =
            new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                    Cardinality.ZERO_OR_ONE, "Options map (fail-on-error: xs:boolean)");
    private static final FunctionReturnSequenceType RETURN_TYPE =
            new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.EXACTLY_ONE,
                    "a function that parses strings according to the grammar");

    public static final FunctionSignature[] SIGNATURES = {
            new FunctionSignature(
                    new QName("invisible-xml", Function.BUILTIN_FUNCTION_NS),
                    "Compiles an Invisible XML grammar and returns a parsing function.",
                    new SequenceType[] { PARAM_GRAMMAR },
                    RETURN_TYPE),
            new FunctionSignature(
                    new QName("invisible-xml", Function.BUILTIN_FUNCTION_NS),
                    "Compiles an Invisible XML grammar and returns a parsing function.",
                    new SequenceType[] { PARAM_GRAMMAR, PARAM_OPTIONS },
                    RETURN_TYPE)
    };

    private AnalyzeContextInfo cachedContextInfo;

    public FnInvisibleXml(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence grammarArg = args[0];

        // Parse options — default fail-on-error is false per spec
        boolean failOnError = false;
        if (args.length > 1 && !args[1].isEmpty()) {
            final AbstractMapType options = (AbstractMapType) args[1].itemAt(0);
            final Sequence failOpt = options.get(new StringValue(this, "fail-on-error"));
            if (!failOpt.isEmpty()) {
                final Item failItem = failOpt.itemAt(0);
                if (failItem.getType() != Type.BOOLEAN) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fail-on-error' must be xs:boolean, got: " +
                                    Type.getTypeName(failItem.getType()));
                }
                failOnError = ((BooleanValue) failItem).getValue();
            } else if (options.contains(new StringValue(this, "fail-on-error"))) {
                // Key exists but value is empty sequence
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Option 'fail-on-error' must be xs:boolean, got empty sequence");
            }
            // else: key not present, use default (false)
        }

        // Compile the grammar
        final de.bottlecaps.markup.blitz.Parser parser;
        try {
            if (grammarArg.isEmpty()) {
                // Empty sequence = use default ixml grammar
                parser = failOnError
                        ? Blitz.generate(Blitz.ixmlGrammar(), Blitz.Option.FAIL_ON_ERROR)
                        : Blitz.generate(Blitz.ixmlGrammar());
            } else {
                final Item grammarItem = grammarArg.itemAt(0);
                final int grammarType = grammarItem.getType();

                if (Type.subTypeOf(grammarType, Type.ELEMENT)) {
                    // Element node — serialize to XML string and use generateFromXml
                    // Synchronized: Blitz.generateFromXml() is not thread-safe
                    final String xmlGrammar = serializeItem(grammarItem);
                    synchronized (BLITZ_XML_LOCK) {
                        parser = failOnError
                                ? Blitz.generateFromXml(xmlGrammar, Blitz.Option.FAIL_ON_ERROR)
                                : Blitz.generateFromXml(xmlGrammar);
                    }
                } else if (Type.subTypeOf(grammarType, Type.STRING) ||
                        grammarType == Type.UNTYPED_ATOMIC) {
                    // String grammar
                    final String grammarStr = grammarItem.getStringValue();
                    parser = failOnError
                            ? Blitz.generate(grammarStr, Blitz.Option.FAIL_ON_ERROR)
                            : Blitz.generate(grammarStr);
                } else if (Type.subTypeOf(grammarType, Type.NODE)) {
                    // Other node types (document, etc.) — not valid
                    throw new XPathException(this, ErrorCodes.FOIX0001,
                            "Grammar must be an element node or string, got: " +
                                    Type.getTypeName(grammarType));
                } else {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Grammar must be a string or element node, got: " +
                                    Type.getTypeName(grammarType));
                }
            }
        } catch (final BlitzParseException ex) {
            throw new XPathException(this, ErrorCodes.FOIX0001,
                    "Invalid ixml grammar at line " + ex.getLine() + ", column " + ex.getColumn()
                            + ": " + ex.getOffendingToken());
        } catch (final BlitzException ex) {
            throw new XPathException(this, ErrorCodes.FOIX0001,
                    "Invalid ixml grammar: " + ex.getMessage());
        }

        // Create a function item that parses input strings using the compiled grammar
        final QName inputParam = new QName("input", XMLConstants.NULL_NS_URI);

        final FunctionSignature parseSig = new FunctionSignature(
                new QName("invisible-xml-parser", Function.BUILTIN_FUNCTION_NS),
                new SequenceType[] {
                        new FunctionParameterSequenceType("input", Type.STRING,
                                Cardinality.EXACTLY_ONE, "The string to parse")
                },
                new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE,
                        "the parsed XML document"));

        final UserDefinedFunction func = new UserDefinedFunction(context, parseSig);
        func.addVariable(inputParam);
        func.setFunctionBody(new ParseExpression(context, parser, inputParam, failOnError));

        final FunctionCall call = new FunctionCall(context, func);
        call.setLocation(getLine(), getColumn());

        return new FunctionReference(this, call);
    }

    private String serializeItem(final Item item) throws XPathException {
        try {
            final org.exist.storage.serializers.Serializer serializer =
                    context.getBroker().borrowSerializer();
            try {
                serializer.setProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
                serializer.setProperty(javax.xml.transform.OutputKeys.INDENT, "no");
                return serializer.serialize((NodeValue) item);
            } finally {
                context.getBroker().returnSerializer(serializer);
            }
        } catch (final Exception ex) {
            throw new XPathException(this, ErrorCodes.FOIX0001,
                    "Failed to serialize grammar node: " + ex.getMessage());
        }
    }

    /**
     * Expression that parses an input string using a compiled ixml parser.
     */
    private static class ParseExpression extends AbstractExpression {

        private final de.bottlecaps.markup.blitz.Parser parser;
        private final QName inputVar;
        private final boolean failOnError;

        ParseExpression(final XQueryContext context, final de.bottlecaps.markup.blitz.Parser parser,
                        final QName inputVar, final boolean failOnError) {
            super(context);
            this.parser = parser;
            this.inputVar = inputVar;
            this.failOnError = failOnError;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final String input = context.resolveVariable(inputVar).getValue().getStringValue();

            // Parse the input using the compiled ixml parser
            final String xmlResult;
            try {
                xmlResult = parser.parse(input);
            } catch (final BlitzParseException ex) {
                if (failOnError) {
                    throw new XPathException(this, ErrorCodes.FOIX0002,
                            "ixml parse error at line " + ex.getLine() + ", column " + ex.getColumn()
                                    + ": " + ex.getOffendingToken());
                }
                // Should not happen when FAIL_ON_ERROR is not set, but handle gracefully
                throw new XPathException(this, ErrorCodes.FOIX0002,
                        "ixml parse error: " + ex.getMessage());
            } catch (final BlitzException ex) {
                throw new XPathException(this, ErrorCodes.FOIX0002,
                        "ixml parse error: " + ex.getMessage());
            }

            // Check for ixml:state="failed" on the root element when fail-on-error is true
            if (failOnError && xmlResult.contains("ixml:state=\"failed\"")) {
                throw new XPathException(this, ErrorCodes.FOIX0002,
                        "ixml parse failed: input is ambiguous or does not match the grammar");
            }

            // Parse the XML string into an in-memory document
            return parseXmlString(xmlResult);
        }

        private DocumentImpl parseXmlString(final String xml) throws XPathException {
            final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getXmlReaderPool();
            XMLReader xr = null;
            try {
                xr = parserPool.borrowXMLReader();
                final InputSource src = new InputSource(new StringReader(xml));
                final SAXAdapter adapter = new SAXAdapter(this, context);
                xr.setContentHandler(adapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                xr.parse(src);
                return adapter.getDocument();
            } catch (final Exception ex) {
                throw new XPathException(this, ErrorCodes.FOIX0002,
                        "Failed to parse ixml output as XML: " + ex.getMessage());
            } finally {
                if (xr != null) {
                    parserPool.returnXMLReader(xr);
                }
            }
        }

        @Override
        public int returnsType() {
            return Type.DOCUMENT;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            // nothing to analyze
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display("invisible-xml-parser(...)");
        }
    }
}
