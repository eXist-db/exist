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
package org.exist.storage.serializers;

public class EXistOutputKeys {

    /**
     * Parameter "item-separator" from the XQuery serialization spec 3.1
     */
    public final static String ITEM_SEPARATOR = "item-separator";

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

    /**
     * JSON serialization: if text nodes are encountered which consist solely of whitespace then they
     * will be ignored by the serializer
     */
    public final static String JSON_IGNORE_WHITESPACE_TEXT_NODES = "json-ignore-whitespace-text-nodes";

    /**
     * Defines the output method to be used for serializing nodes within json output.
     */
    public final static String JSON_NODE_OUTPUT_METHOD = "json-node-output-method";

    /**
     * Defines the output for JSON serializing to array even if only one item.
     */
    public final static String JSON_ARRAY_OUTPUT = "json-array-output";

    /**
     * Determines whether the presence of multiple keys in a map item with the same string value
     * will or will not raise serialization error err:SERE0022.
     */
    public final static String ALLOW_DUPLICATE_NAMES = "allow-duplicate-names";

    public final static String HTML_VERSION = "html-version";

    /**
     * When serializing an XDM this should be used
     * to enforce XDM serialization rules.
     *
     * XDM has different serialization rules
     * compared to retrieving and serializing resources from the database.
     *
     * Set to "yes" to enable xdm-serialization rules, false otherwise.
     */
    public final static String XDM_SERIALIZATION = "xdm-serialization";

    /**
     * Enforce newline at the end of an XML document.
     *
     * Since a lot of editors set this enforcing it on
     * serialization out of exist-db will lead to less
     * meaningless changes in git and tools like diff will
     * be able to provide more meaningful as well.
     */
    public final static String INSERT_FINAL_NEWLINE = "insert-final-newline";
}
