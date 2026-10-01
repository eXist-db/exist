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


import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegexMatchHandler;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.StringValue;
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

import static org.exist.xquery.regex.RegexUtil.*;
import static org.exist.xquery.regex.SaxonRegex.*;

/**
 * XPath and XQuery 3.0 F+O fn:analyze-string()
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunAnalyzeString extends BasicFunction {

    /** Reused for empty-match detection — avoids per-call allocation of an empty StringView. */
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

            final String input = args[0].isEmpty() ? "" : args[0].itemAt(0).getStringValue();
            final String pattern = args[1].itemAt(0).getStringValue();

            String flags = "";
            if (args.length == 3) {
                flags = args[2].itemAt(0).getStringValue();
            }
            analyzeString(builder, input, pattern, flags);
            builder.endElement();
            builder.endDocument();
            return (NodeValue) builder.getDocument().getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }

    private void analyzeString(final MemTreeBuilder builder, final String input, final String pattern, final String rawFlags) throws XPathException {
        final String flags = validateFlags(this, rawFlags);

        // XPath 4.0 lookaround syntax is not yet implemented in eXist's XQuery 3.1 runtime.
        // When XQuery 4.0 lands (v2/xq4-core-functions), replace this guard with the
        // translateXPath4Lookaround / ;j dispatch path.
        if (hasXPath4Lookaround(pattern)) {
            throw new XPathException(this, ErrorCodes.XPST0017,
                    "XPath 4.0 lookaround syntax in regex patterns (e.g. (*positive_lookahead:...)) "
                            + "is not yet implemented in this XQuery 3.1 build. Rewrite the regex without lookaround.");
        }

        // Pre-validate: reject constructs not valid in XPath 3.1 regex
        // Java syntax is the point of ';j', so the XPath-syntax check does not apply to it.
        if (!hasLiteral(flags) && !usesJavaEngine(flags)) {
            validateXPathRegex(this, pattern, false);
        }

        final RegularExpression regularExpression = compile(this,
                context.getBroker().getBrokerPool().getSaxonConfiguration(), pattern, flags);
        if (matchesEmptyString(regularExpression)) {
            throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
        }

        final RegexIterator regexIterator = regularExpression.analyze(StringView.of(input));
        try {
            StringValue item;
            while ((item = regexIterator.next()) != null) {
                if (regexIterator.isMatching()) {
                    match(builder, regexIterator);
                } else {
                    nonMatch(builder, item);
                }
            }
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw translate(this, e, pattern);
        }
    }


    private void match(final MemTreeBuilder builder, final RegexIterator regexIterator) throws net.sf.saxon.trans.XPathException {
        builder.startElement(QN_MATCH, null);
        regexIterator.processMatchingSubstring(new RegexMatchHandler() {
            @Override
            public void characters(final UnicodeString s) {
                builder.characters(s.toString());
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

    private void nonMatch(final MemTreeBuilder builder, final StringValue item) {
        builder.startElement(QN_NON_MATCH, null);
        builder.characters(item.getStringValue());
        builder.endElement();
    }
}
