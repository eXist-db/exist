/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
 *  $Id: MD5.java 2933 2006-03-20 04:27:24Z alexmilowski $
 */
package org.exist.xquery.functions.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Generate an MD5 key from a string.
 * 
 * @author wolf
 */
public class Matches extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("matches", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Tries to match the string in $a to the regular expression in $b. " +
			"Returns an empty sequence if the string does not match, or a sequence whose " +
			"first item is the entire string, and whose following items are the matched groups.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
		),
		new FunctionSignature(
			new QName("matches", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Tries to match the string in $a to the regular expression in $b, using " +
			"the flags specified in $c. Returns an empty sequence if the string does "+
			"not match, or a sequence whose first item is the entire string, and whose " +
			"following items are the matched groups.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
		)
	};

	protected Matcher matcher = null;
	protected Pattern pat = null;

	public Matches(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
	    if (contextItem != null)
			contextSequence = contextItem.toSequence();
	    
        Sequence result;
		Sequence input = getArgument(0).eval(contextSequence, contextItem);
		if (input.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;        
         } else {
            result = evalGeneric(contextSequence, contextItem, input);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 * 
	 * @param pattern
	 * @return
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
        try {
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexSyntaxException e) {
			throw new XPathException(getASTNode(), "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}

    /**
     * @param contextSequence
     * @param contextItem
     * @param stringArg
     * @return
     * @throws XPathException
     */
    private Sequence evalGeneric(Sequence contextSequence, Item contextItem, Sequence stringArg) throws XPathException {
        String string = stringArg.getStringValue();
		String pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
        
		int flags = 0;
        if(getSignature().getArgumentCount() == 3)
            flags = parseFlags(getArgument(2).eval(contextSequence, contextItem).getStringValue());
        
		return match(string, pattern, flags);
    }

    /**
     * @param string
     * @param pattern
     * @param flags
     * @return
     * @throws XPathException
     */
    private Sequence match(String string, String pattern, int flags) throws XPathException {
        try {
			if(pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
				pat = Pattern.compile(pattern, flags);
				//TODO : make matches('&#x212A;', '[A-Z]', 'i') work !
                matcher = pat.matcher(string);
            } else {
                matcher.reset(string);
            }
            
			if(!matcher.find()) {
				return Sequence.EMPTY_SEQUENCE;
			} else {
				int items = matcher.groupCount() + 1;
				Sequence seq = new ValueSequence(items);
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
		} catch (PatternSyntaxException e) {
			throw new XPathException("Invalid regular expression: " + e.getMessage(), e);
		}
    }

    protected final static int parseFlags(String s) throws XPathException {
		int flags = 0;
		for(int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
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
					throw new XPathException("Invalid regular expression flag: " + ch);
			}
		}
		return flags;
	}

}
