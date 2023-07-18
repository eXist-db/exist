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

import java.util.ArrayList;
import java.util.List;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Item;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;

/**
 * XPath and XQuery 3.0 F+O fn:analyze-string()
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunAnalyzeString extends BasicFunction {

    private final static QName fnAnalyzeString = new QName("analyze-string", Function.BUILTIN_FUNCTION_NS);

    private final static QName QN_MATCH = new QName("match", Function.BUILTIN_FUNCTION_NS);
    private final static QName QN_GROUP = new QName("group", Function.BUILTIN_FUNCTION_NS);
    private final static QName QN_NR = new QName("nr", XMLConstants.NULL_NS_URI);
    private final static QName QN_NON_MATCH = new QName("non-match", Function.BUILTIN_FUNCTION_NS);
    
    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            fnAnalyzeString,
            "Analyzes a string using a regular expression, returning an XML " +
            "structure that identifies which parts of the input string matched " +
            "or failed to match the regular expression, and in the case of " +
            "matched substrings, which substrings matched each " +
            "capturing group in the regular expression.",
            new SequenceType[] { 
                new FunctionParameterSequenceType("input", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The input string"),
                new FunctionParameterSequenceType("pattern", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The pattern")
            },
            new FunctionReturnSequenceType(Type.ELEMENT,
                Cardinality.EXACTLY_ONE, "The result of the analysis")
        ),
        new FunctionSignature(
            fnAnalyzeString,
            "Analyzes a string using a regular expression, returning an XML " +
            "structure that identifies which parts of the input string matched " +
            "or failed to match the regular expression, and in the case of " +
            "matched substrings, which substrings matched each " +
            "capturing group in the regular expression.",
            new SequenceType[] { 
                new FunctionParameterSequenceType("input", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The input string"),
                new FunctionParameterSequenceType("pattern", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The pattern"),
                new FunctionParameterSequenceType("flags", Type.STRING,
                    Cardinality.EXACTLY_ONE, "Flags"),
            },
            new FunctionReturnSequenceType(Type.ELEMENT,
                Cardinality.EXACTLY_ONE, "The result of the analysis")
        )
    };

    public FunAnalyzeString(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            builder.startElement(new QName("analyze-string-result", Function.BUILTIN_FUNCTION_NS), null);
            String input = "";
            if (!args[0].isEmpty()) {
                input = args[0].itemAt(0).getStringValue();
            }
            if (input != null && !input.isEmpty()) {
                final String pattern = args[1].itemAt(0).getStringValue();
                String flags = "";
                if (args.length == 3) {
                    flags = args[2].itemAt(0).getStringValue();
                }
                analyzeString(builder, input, pattern, flags);
            }
            builder.endElement();
            builder.endDocument();
            return (NodeValue) builder.getDocument().getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }

    private void analyzeString(final MemTreeBuilder builder, final String input, String pattern, final String flags) throws XPathException {
        final Configuration config = context.getBroker().getBrokerPool().getSaxonConfiguration();

        final List<String> warnings = new ArrayList<>(1);

        try {
            final RegularExpression regularExpression = config.compileRegularExpression(pattern, flags, "XP30", warnings);
            if (regularExpression.matches("")) {
                throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
            }

            //TODO(AR) cache the regular expression... might be possible through Saxon config

            final RegexIterator regexIterator = regularExpression.analyze(input);
            Item item;
            while ((item = regexIterator.next()) != null) {
                if (regexIterator.isMatching()) {
                    match(builder, regexIterator);
                } else {
                    nonMatch(builder, item);
                }
            }

            for (final String warning : warnings) {
                LOG.warn(warning);
            }
        } catch (final net.sf.saxon.trans.XPathException e) {
            switch (e.getErrorCodeLocalPart()) {
                case "FORX0001":
                    throw new XPathException(this, ErrorCodes.FORX0001, e.getMessage());
                case "FORX0002":
                    throw new XPathException(this, ErrorCodes.FORX0002, e.getMessage());
                case "FORX0003":
                    throw new XPathException(this, ErrorCodes.FORX0003, e.getMessage());
                default:
                    throw new XPathException(this, e.getMessage());
            }
        }
    }
    
    private void match(final MemTreeBuilder builder, final RegexIterator regexIterator) throws net.sf.saxon.trans.XPathException {
        builder.startElement(QN_MATCH, null);
        regexIterator.processMatchingSubstring(new RegexIterator.MatchHandler() {
            @Override
            public void characters(final CharSequence s) {
                builder.characters(s);
            }

            @Override
            public void onGroupStart(final int groupNumber) throws net.sf.saxon.trans.XPathException {
                final AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", QN_NR.getLocalPart(), QN_NR.getLocalPart(), "int", Integer.toString(groupNumber));

                builder.startElement(QN_GROUP, attributes);
            }

            @Override
            public void onGroupEnd(final int groupNumber) throws net.sf.saxon.trans.XPathException {
                builder.endElement();
            }
        });
        builder.endElement();
    }

    private void nonMatch(final MemTreeBuilder builder, final Item item) {
        builder.startElement(QN_NON_MATCH, null);
        builder.characters(item.getStringValueCS());
        builder.endElement();
    }
}
