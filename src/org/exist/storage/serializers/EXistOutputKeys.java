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
package org.exist.storage.serializers;

public class EXistOutputKeys {

	public final static String OUTPUT_DOCTYPE = "output-doctype";
	 
	public final static String EXPAND_XINCLUDES = "expand-xincludes";
	
	public final static String PROCESS_XSL_PI = "process-xsl-pi";
	
	public final static String HIGHLIGHT_MATCHES = "highlight-matches";
	
	public final static String INDENT_SPACES = "indent-spaces";
	
	public final static String STYLESHEET = "stylesheet";
	
	public final static String STYLESHEET_PARAM = "stylesheet-param";
	
	public final static String COMPRESS_OUTPUT = "compress-output";

    public final static String ADD_EXIST_ID = "add-exist-id";

    public final static String XINCLUDE_PATH = "xinclude-path";
    
    /**
     * Enforce XHTML namespace on elements with no namespace
     */
    public final static String ENFORCE_XHTML = "enforce-xhtml";
    
    /**
     * Applies to JSON serialization only: preserve namespace prefixes in JSON properties
     * by replacing ":" with "_", so element foo:bar becomes "foo_bar".
     */
    public final static String JSON_OUTPUT_NS_PREFIX = "preserve-prefix";
    
    /**
     * Applies to JSON serialization only: sets the jsonp callback function
     */
    public final static String JSONP = "jsonp";

    /**
     * JSON serialization: prefix XML attributes with a '@' when serializing
     * them as JSON properties
     */
    public final static String JSON_PREFIX_ATTRIBUTES = "prefix-attributes";
}
