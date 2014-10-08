/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.xquery.functions.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.regex.JDK15RegexTranslator;
import org.exist.xquery.regex.RegexSyntaxException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *   xQuery function for filtering strings from text that match the specified 
 * pattern. E.g.  AABBBBCBBC and BB.*BB results in BBBBCBB
 *
 * @author dizzzz
 */
public class RegexpFilter extends BasicFunction {
    
    protected static final FunctionParameterSequenceType FLAGS_PARAM = new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.EXACTLY_ONE, "The flags");

	protected static final FunctionParameterSequenceType REGEX_PARAM = new FunctionParameterSequenceType("regularexpression", Type.STRING, Cardinality.EXACTLY_ONE, "The regular expression to perform against the text");

	protected static final FunctionParameterSequenceType TEXT_PARAM = new FunctionParameterSequenceType("text", Type.STRING, Cardinality.EXACTLY_ONE, "The text to filter");

	// Setup function signature
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
            new QName("filter", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "Filter substrings that match the regular expression in the text.",
            new SequenceType[]{
                TEXT_PARAM,
                REGEX_PARAM
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the substrings")
	    ),
		new FunctionSignature(
			new QName("groups", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match the string in $text to the regular expression. " +
			"Returns an empty sequence if the string does not match, or a sequence whose " +
			"first item is the entire string, and whose following items are the matched groups.",
			new SequenceType[] {
                TEXT_PARAM,
                REGEX_PARAM
				},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "an empty sequence if the string does not match, or a sequence whose " +
			"first item is the entire string, and whose following items are the matched groups.")
		),
		new FunctionSignature(
			new QName("groups", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match the string in $text to the regular expression, using " +
			"the flags specified. Returns an empty sequence if the string does "+
			"not match, or a sequence whose first item is the entire string, and whose " +
			"following items are the matched groups.",
			new SequenceType[] {
                TEXT_PARAM,
                REGEX_PARAM,
				FLAGS_PARAM,
				},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "an empty sequence if the string does "+
			"not match, or a sequence whose first item is the entire string, and whose " +
			"following items are the matched groups.")
		),
		new FunctionSignature(
			new QName("groups-regex", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match the string in $text to the regular expression. " +
			"Returns an empty sequence if the string does not match, or a sequence whose " +
			"first item is the entire string, and whose following items are the matched groups. " +
			"Note:\n\n" +
			"The groups-regex() variants of the groups() functions are identical except that they avoid the translation of the specified regular expression from XPath2 to Java syntax. " +
			"That is, the regular expression is evaluated as is, and must be valid according to Java regular expression syntax, rather than the more restrictive XPath2 syntax.",
			new SequenceType[] {
                TEXT_PARAM,
                REGEX_PARAM
				},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "an empty sequence if the string does not match, or a sequence whose " +
			"first item is the entire string, and whose following items are the matched groups.")
		),
		new FunctionSignature(
			new QName("groups-regex", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Tries to match the string in $text to the regular expression, using " +
			"the flags specified. Returns an empty sequence if the string does "+
			"not match, or a sequence whose first item is the entire string, and whose " +
			"following items are the matched groups. " +
			"Note:\n\n" +
			"The groups-regex() variants of the groups() functions are identical except that they avoid the translation of the specified regular expression from XPath2 to Java syntax. " +
			"That is, the regular expression is evaluated as is, and must be valid according to Java regular expression syntax, rather than the more restrictive XPath2 syntax.",
			new SequenceType[] {
                TEXT_PARAM,
                REGEX_PARAM,
				FLAGS_PARAM,
				},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "an empty sequence if the string does "+
			"not match, or a sequence whose first item is the entire string, and whose " +
			"following items are the matched groups.")
		)
    };
            
    // Very Small cache
    private String  cachedRegexp = "";
    private Pattern cachedPattern = null;
    
    
    /** Creates a new instance of RegexpMatcher */
    public RegexpFilter(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws org.exist.xquery.XPathException {
        
    	if(this.isCalledAs("filter")) {
    		return filter(args);
    	} else {
    		return groups(args);
    	}
    }
    
    public Sequence filter(Sequence[] args) throws org.exist.xquery.XPathException {
        // Check input parameters
        if(args.length != 2){
            return Sequence.EMPTY_SEQUENCE;
        }
        
        // Get regular expression
        Pattern pattern = null;
        String regexp = args[1].getStringValue();
        if(cachedRegexp.equals(regexp) && cachedPattern!=null){ 
            // Cached compiled pattern is available!
            pattern = cachedPattern;
            
        } else {
            // Compile new pattern
            pattern = Pattern.compile( regexp );
            
            // Cache new pattern
            cachedPattern = pattern;
            cachedRegexp = regexp;
        }

        // Match pattern on string
        final Matcher matcher = pattern.matcher( args[0].getStringValue() );
        
        // Create response
        final Sequence result = new ValueSequence();
        
        // Add each match to response sequence
        while( matcher.find() ){
            result.add( new StringValue(matcher.group()) );
        }
        
        return result;
    }
    
	public Sequence groups(Sequence[] args) throws XPathException {
        Sequence result;
		final Sequence input = args[0];
		if (input.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;        
         } else {
            result = evalGeneric(args, input);
        }
        
        return result;          
	}

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 * 
	 * @param pattern XPath regexp expression
	 * @return Java regexp expression
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
        try {
        	final int xmlVersion = 11;
        	final boolean ignoreWhitespace = false;
        	final boolean caseBlind = false;
			pattern = JDK15RegexTranslator.translate(pattern, xmlVersion, true, ignoreWhitespace, caseBlind);
		} catch (final RegexSyntaxException e) {
			throw new XPathException(this, "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}

    private Sequence evalGeneric(Sequence[] args, Sequence stringArg) throws XPathException {
        final String string = stringArg.getStringValue();
		
		String pattern;
		
		if( isCalledAs( "groups-regex" ) ) {
			pattern = args[1].getStringValue();
		} else {
			pattern = translateRegexp(args[1].getStringValue());
		}
        
		int flags = 0;
        if(args.length==3)
            {flags = parseFlags(args[2].getStringValue());}
        
		return match(string, pattern, flags);
    }

    /**
     * @param string
     * @param pattern
     * @param flags
     * @throws XPathException
     */
    private Sequence match(String string, String pattern, int flags) throws XPathException {
        try {
        	Matcher matcher;
			if(cachedRegexp == null || (!cachedRegexp.equals(pattern)) || flags != cachedPattern.flags()) {
				matcher = Pattern.compile(pattern, flags).matcher(string);
				cachedPattern = matcher.pattern();
				cachedRegexp = string;
            } else {
            	matcher = cachedPattern.matcher(string);
            }
            
			if(!matcher.find()) {
				return Sequence.EMPTY_SEQUENCE;
			} else {
				final int items = matcher.groupCount() + 1;
				final Sequence seq = new ValueSequence();
				seq.add(new StringValue(string));
				for(int i=1;i<items;i++) {
					String val = matcher.group(i);
					if(val==null) {
						val="";
					}
					seq.add(new StringValue(val));
				}
				return seq;
			}
		} catch (final PatternSyntaxException e) {
			throw new XPathException(this, "err:FORX0001: Invalid regular expression: " + e.getMessage(), e);
		}
    }

    protected final static int parseFlags(String s) throws XPathException {
		int flags = 0;
		for(int i = 0; i < s.length(); i++) {
			final char ch = s.charAt(i);
			switch(ch) {
				case 'm':
					flags |= Pattern.MULTILINE;
					break;
				case 'i':
					flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
					break;
                case 'x':
                    flags |= Pattern.COMMENTS;
                    break;
                case 's':
                    flags |= Pattern.DOTALL;
                    break;
				default:
					throw new XPathException("err:FORX0001: Invalid regular expression flag: " + ch);
			}
		}
		return flags;
	}
    
}

