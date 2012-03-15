package org.exist.xquery.functions.fn;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.Cardinality;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Type;
/**
 * XPath and XQuery 3.0 F+O fn:analyze-string()
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 201107071502
 */

public class FunAnalyzeString extends BasicFunction {

    private final static QName fnAnalyzeString = new QName("analyze-string", Function.BUILTIN_FUNCTION_NS);

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

    public FunAnalyzeString(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        MemTreeBuilder builder = new MemTreeBuilder(context);
        builder.startDocument();
        builder.startElement(new QName("analyze-string-result", Function.BUILTIN_FUNCTION_NS), null);
        String input = "";
        if (!args[0].isEmpty()) {
            input = args[0].itemAt(0).getStringValue();
        }
        if (!input.equals("")) {
            String pattern = args[1].itemAt(0).getStringValue();
            String flags = null;
            if(args.length == 3) {
                flags = args[2].itemAt(0).getStringValue();
            }
            analyzeString(builder, input, pattern, flags);
        }
        builder.endElement();
        builder.endDocument();
        return builder.getDocument();
    }

    private void analyzeString(MemTreeBuilder builder, String input,
            String pattern, String flags) throws XPathException {
        final Pattern ptn;
        if (flags != null) {
            int iFlags = parseStringFlags(flags);
            ptn = Pattern.compile(pattern, iFlags);
        } else {
            ptn = Pattern.compile(pattern);
        }
        Matcher matcher = ptn.matcher(input);
        int offset = 0;
        while (matcher.find()) {
            MatchResult matchResult = matcher.toMatchResult();
            if (matchResult.start() != offset) {
                nonMatch(builder, input.substring(offset, matchResult.start()));
                offset = matchResult.start();
            }
            builder.startElement(new QName("match", Function.BUILTIN_FUNCTION_NS), null);
            for (int i = 1; i <= matchResult.groupCount(); i++) {
                if (matchResult.start(i) != offset) {
                    String chars = input.substring(offset, matchResult.start(i));
                    if (matchResult.start(i) >= matchResult.start() &&
                            matchResult.start(i) <= matchResult.end()) {
                        builder.characters(chars);
                    } else {
                        builder.endElement();
                        nonMatch(builder, chars);
                        builder.startElement(new QName("match",
                            Function.BUILTIN_FUNCTION_NS), null);
                    }
                    offset = matchResult.start(i);
                }
                builder.startElement(new QName("group", Function.BUILTIN_FUNCTION_NS), null);
                builder.addAttribute(new QName("nr"), Integer.toString(i));
                builder.characters(input.substring(matchResult.start(i), matchResult.end(i)));
                builder.endElement();
                offset = matchResult.end(i);
            }
            if (offset!= matchResult.end()) {
                String matchedInput = input.substring(offset, matchResult.end());
                builder.characters(matchedInput);
            }
            builder.endElement();
            offset = matchResult.end();
        }
        if (offset != input.length()) {
            nonMatch(builder, input.substring(offset));
        }
    }

    private void nonMatch(MemTreeBuilder builder, String nonMatch) {
        builder.startElement(new QName("non-match", Function.BUILTIN_FUNCTION_NS), null);
        builder.characters(nonMatch);
        builder.endElement();
    }

    private int parseStringFlags(String flags) {
        int iFlags = 0;
        for (char c : flags.toCharArray()) {
            switch(c) {
            case 's':
                iFlags |= Pattern.DOTALL;
                break;
            
            case 'm':
                iFlags |= Pattern.MULTILINE;
                break;
                
            case 'i':
                iFlags |= Pattern.CASE_INSENSITIVE;
                break;
                
            case 'x' :
                iFlags |= Pattern.CANON_EQ;
                break;
                
            case 'q' :
                iFlags |= Pattern.LITERAL;
                break;
            }
        }
        return iFlags;
    }
}