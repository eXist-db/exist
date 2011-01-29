/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.functions.fn.FunMatches;


/**
 * Module function definitions for text module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 */
public class TextModule extends AbstractInternalModule {
    
    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/text";
    
    public static final String PREFIX = "text";
    public final static String INCLUSION_DATE = "2004-03-01";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0";

    public static final FunctionDef[] functions = {
        new FunctionDef(FuzzyMatchAll.signature, FuzzyMatchAll.class),
        new FunctionDef(FuzzyMatchAny.signature, FuzzyMatchAny.class),
        new FunctionDef(FuzzyIndexTerms.signature, FuzzyIndexTerms.class),
        new FunctionDef(TextRank.signature, TextRank.class),
        new FunctionDef(MatchCount.signature, MatchCount.class),
        new FunctionDef(IndexTerms.signatures[0], IndexTerms.class),
        new FunctionDef(IndexTerms.signatures[1], IndexTerms.class),
        new FunctionDef(HighlightMatches.signature, HighlightMatches.class),
        new FunctionDef(KWICDisplay.signatures[0], KWICDisplay.class),
        new FunctionDef(KWICDisplay.signatures[1], KWICDisplay.class),
//        new FunctionDef(KWICDisplay2.signatures[0], KWICDisplay2.class),
//        new FunctionDef(KWICDisplay2.signatures[1], KWICDisplay2.class),
        new FunctionDef(RegexpFilter.signatures[0], RegexpFilter.class),
        new FunctionDef(RegexpFilter.signatures[1], RegexpFilter.class),
        new FunctionDef(RegexpFilter.signatures[2], RegexpFilter.class),
        new FunctionDef(RegexpFilter.signatures[3], RegexpFilter.class),
        new FunctionDef(RegexpFilter.signatures[4], RegexpFilter.class),
        new FunctionDef(Tokenize.signature, Tokenize.class),
        new FunctionDef(MatchRegexp.signatures[0], MatchRegexp.class),
        new FunctionDef(MatchRegexp.signatures[1], MatchRegexp.class),
        new FunctionDef(MatchRegexp.signatures[2], MatchRegexp.class),
        new FunctionDef(MatchRegexp.signatures[3], MatchRegexp.class),
        new FunctionDef(FilterNested.signature, FilterNested.class),
	
		new FunctionDef(FunMatches.text_signatures[0], FunMatches.class),
        new FunctionDef(FunMatches.text_signatures[1], FunMatches.class)
    };
    
    /**
     * 
     */
    public TextModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getDescription()
         */
    public String getDescription() {
        return "A module for text searching extension functions.";
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getNamespaceURI()
         */
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Module#getDefaultPrefix()
         */
    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
