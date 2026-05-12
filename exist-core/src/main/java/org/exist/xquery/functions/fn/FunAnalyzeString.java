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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.saxon.Configuration;
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

/**
 * XPath and XQuery 3.0 F+O fn:analyze-string()
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunAnalyzeString extends BasicFunction {

    /** Reused for empty-match detection — avoids per-call allocation of an empty StringView. */
    private static final UnicodeString EMPTY_STRING_VIEW = StringView.of("");

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

    private void analyzeString(final MemTreeBuilder builder, final String input, final String pattern, final String flags) throws XPathException {
        final Configuration config = context.getBroker().getBrokerPool().getSaxonConfiguration();

        // XPath 4.0 lookaround syntax is not yet implemented in eXist's XQuery 3.1 runtime.
        // When XQuery 4.0 lands (v2/xq4-core-functions), replace this guard with the
        // translateXPath4Lookaround() dispatch path.
        if (org.exist.xquery.regex.RegexUtil.hasXPath4Lookaround(pattern)) {
            throw new XPathException(this, ErrorCodes.XPST0017,
                    "XPath 4.0 lookaround syntax in regex patterns (e.g. (*positive_lookahead:...)) "
                            + "is not yet implemented in this XQuery 3.1 build. Rewrite the regex without lookaround.");
        }

        // Pre-validate: reject constructs not valid in XPath 3.1 regex
        if (!org.exist.xquery.regex.RegexUtil.hasLiteral(flags)) {
            org.exist.xquery.regex.RegexUtil.validateXPathRegex(this, pattern, false);
        }

        final List<String> warnings = new ArrayList<>(1);

        try {
            final RegularExpression regularExpression = config.compileRegularExpression(StringView.of(pattern), flags, "XP31", warnings);
            if (regularExpression.matches(EMPTY_STRING_VIEW)) {
                throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
            }

            //TODO(AR) cache the regular expression... might be possible through Saxon config

            final RegexIterator regexIterator = regularExpression.analyze(StringView.of(input));
            StringValue item;
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
            // Saxon's XP31 regex translator rejects some valid patterns.
            // Fall back to Java regex before giving up.
            if ("FORX0002".equals(e.getErrorCodeQName().getLocalPart())) {
                try {
                    analyzeStringJavaRegex(builder, input, pattern, flags);
                    return;
                } catch (final PatternSyntaxException ignored) {
                    // Java regex fallback also failed — throw original Saxon error below
                }
            }
            switch (e.getErrorCodeQName().getLocalPart()) {
                case "FORX0001" -> throw new XPathException(this, ErrorCodes.FORX0001, e.getMessage());
                case "FORX0002" -> throw new XPathException(this, ErrorCodes.FORX0002, e.getMessage());
                case "FORX0003" -> throw new XPathException(this, ErrorCodes.FORX0003, e.getMessage());
                default -> throw new XPathException(this, ErrorCodes.ERROR, e.getMessage());
            }
        }
    }

    /**
     * Java regex fallback for fn:analyze-string when Saxon rejects the pattern.
     */
    private void analyzeStringJavaRegex(final MemTreeBuilder builder, final String input,
            final String pattern, final String flags) throws XPathException {
        final String javaPattern = translateRegexp(this, pattern,
                flags.contains("x"), flags.contains("i"));
        final int javaFlags = parseFlags(this, flags);
        final Pattern compiled = Pattern.compile(javaPattern, javaFlags);

        if (compiled.matcher("").matches()) {
            throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
        }

        final Matcher matcher = compiled.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            // Non-matching text before this match
            if (matcher.start() > lastEnd) {
                builder.startElement(QN_NON_MATCH, null);
                builder.characters(input.substring(lastEnd, matcher.start()));
                builder.endElement();
            }

            // The match itself
            builder.startElement(QN_MATCH, null);
            final int groupCount = matcher.groupCount();
            if (groupCount == 0) {
                builder.characters(matcher.group());
            } else {
                // Emit groups — track position within the match to emit non-group text
                int matchPos = matcher.start();
                for (int g = 1; g <= groupCount; g++) {
                    if (matcher.start(g) >= 0) {
                        // Text before this group (within the match)
                        if (matcher.start(g) > matchPos) {
                            builder.characters(input.substring(matchPos, matcher.start(g)));
                        }
                        final AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", QN_NR.getLocalPart(), QN_NR.getLocalPart(), "int", Integer.toString(g));
                        builder.startElement(QN_GROUP, attributes);
                        builder.characters(matcher.group(g));
                        builder.endElement();
                        matchPos = matcher.end(g);
                    }
                }
                // Text after last group (within the match)
                if (matchPos < matcher.end()) {
                    builder.characters(input.substring(matchPos, matcher.end()));
                }
            }
            builder.endElement();
            lastEnd = matcher.end();
        }

        // Trailing non-matching text
        if (lastEnd < input.length()) {
            builder.startElement(QN_NON_MATCH, null);
            builder.characters(input.substring(lastEnd));
            builder.endElement();
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
