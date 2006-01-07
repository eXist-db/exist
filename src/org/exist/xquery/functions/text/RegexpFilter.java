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
 *  $Id$
 */

package org.exist.xquery.functions.text;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *   xQuery function for filtering strings from text that match the specified 
 * pattern. E.g.  AABBBBCBBC and BB.*BB results in BBBBCBB
 *
 * @author dizzzz
 */
public class RegexpFilter extends BasicFunction {
    
    // Setup function signature
    public final static FunctionSignature signature = new FunctionSignature(
            new QName("filter", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "Filter substrings that match the regular expression $b in text $a.",
            new SequenceType[]{
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
    );
            
    // Very Small cache
    private String  cachedRegexp = "";
    private Pattern cachedPattern = null;
    
    
    /** Creates a new instance of RegexpMatcher */
    public RegexpFilter(XQueryContext context) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws org.exist.xquery.XPathException {
        
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
        Matcher matcher = pattern.matcher( args[0].getStringValue() );
        
        // Create response
        Sequence result = new ValueSequence();
        
        // Add each match to response sequence
        while( matcher.find() ){
            result.add( new StringValue(matcher.group()) );
        }
        
        return result;
    }
    
}

