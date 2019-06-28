package org.exist.xquery.functions.fn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.PatternFactory;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @serial 201211101626
 * 
 * Corrections were made by to the previous buggy version
 * by taking inspiration from the BaseX 7.3 version.
 */

public class FunAnalyzeString extends BasicFunction {

    private final static QName fnAnalyzeString = new QName("analyze-string", Function.BUILTIN_FUNCTION_NS);

    private final static QName QN_MATCH = new QName("match", Function.BUILTIN_FUNCTION_NS);
    private final static QName QN_GROUP = new QName("group", Function.BUILTIN_FUNCTION_NS);
    private final static QName QN_NR = new QName("nr", XMLConstants.NULL_NS_URI);
    private final static QName QN_NON_MATCH = new QName("non-match", Function.BUILTIN_FUNCTION_NS);
    
    public final static FunctionSignature signatures[] = {
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
        final MemTreeBuilder builder = new MemTreeBuilder(context);
        builder.startDocument();
        builder.startElement(new QName("analyze-string-result", Function.BUILTIN_FUNCTION_NS), null);
        String input = "";
        if (!args[0].isEmpty()) {
            input = args[0].itemAt(0).getStringValue();
        }
        if (!"".equals(input)) {
            final String pattern = args[1].itemAt(0).getStringValue();
            String flags = null;
            if(args.length == 3) {
                flags = args[2].itemAt(0).getStringValue();
            }
            analyzeString(builder, input, pattern, flags);
        }
        builder.endElement();
        builder.endDocument();
        return (NodeValue)builder.getDocument().getDocumentElement();
    }

    private void analyzeString(final MemTreeBuilder builder, final String input, String pattern, final String flags) throws XPathException {

        final int iFlags = parseFlags(this, flags);

        if(!hasLiteral(iFlags)) {
            pattern = translateRegexp(this, pattern, hasIgnoreWhitespace(iFlags), hasCaseInsensitive(iFlags));
        }

        final Pattern ptn = PatternFactory.getInstance().getPattern(pattern, iFlags);
        
        final Matcher matcher = ptn.matcher(input);
        
        int offset = 0;
        while(matcher.find()) {
            if(matcher.start() != offset) {
                nonMatch(builder, input.substring(offset, matcher.start()));
            }
            match(builder, matcher, input, 0);
            
            offset = matcher.end();
        }
        
        if(offset != input.length()) {
            nonMatch(builder, input.substring(offset));
        }
    }
    
    private static class GroupPosition {
        public int groupNumber;
        public int position;

        public GroupPosition(final int groupNumber, final int position) {
            this.groupNumber = groupNumber;
            this.position = position;
        }
    }
    
    private GroupPosition match(final MemTreeBuilder builder, final Matcher matcher, final String input, final int group) {
        if(group == 0) {
            builder.startElement(QN_MATCH, null);
        } else {
            final AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", QN_NR.getLocalPart(), QN_NR.getLocalPart(), "int", Integer.toString(group));
            builder.startElement(QN_GROUP, attributes);
        }
        
        final int groupStart = matcher.start(group);
        final int groupEnd = matcher.end(group);
        final int groupCount = matcher.groupCount();
        
        GroupPosition groupAndPosition = new GroupPosition(group + 1, groupStart);
        while(groupAndPosition.groupNumber <= groupCount && matcher.end(groupAndPosition.groupNumber) <= groupEnd) {
            final int start = matcher.start(groupAndPosition.groupNumber);
            if(start >= 0) { //group matched
                if(groupAndPosition.position < start) {
                    builder.characters(input.substring(groupAndPosition.position, start));
                }
                groupAndPosition = match(builder, matcher, input, groupAndPosition.groupNumber);
            } else {
                groupAndPosition.groupNumber++; //skip to next group
            }
        }
        
        if(groupAndPosition.position < groupEnd) {
            builder.characters(input.substring(groupAndPosition.position, groupEnd));
            groupAndPosition.position = groupEnd;
        }
        
        builder.endElement();
        
        return groupAndPosition;
    }

    private void nonMatch(final MemTreeBuilder builder, final String nonMatch) {
        builder.startElement(QN_NON_MATCH, null);
        builder.characters(nonMatch);
        builder.endElement();
    }
}
