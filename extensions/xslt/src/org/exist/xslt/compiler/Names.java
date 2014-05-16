/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
 *  http://exist-db.org
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
package org.exist.xslt.compiler;

/**
 * The resolved names container.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Names {
	
	public static final String XMLNS = "xmlns";

	public static final String ID = "id";
	public static final String EXTENSION_ELEMENT_PREFIXES = "extension-element-prefixes";
	public static final String EXCLUDE_RESULT_PREFIXES = "exclude-result-prefixes";
	public static final String VERSION = "version";
	public static final String XPATH_DEFAULT_NAMESPACE = "xpath-default-namespace";
	public static final String DEFAULT_VALIDATION = "default-validation";
	public static final String DEFAULT_COLLATION = "default-collation";
	public static final String INPUT_TYPE_ANNOTATIONS = "input-type-annotations";
	
	public static final String PRESERVE = "preserve";
	public static final String STRIP = "strip";
	public static final String UNSPECIFIED = "unspecified";

	public static final String SELECT = "select";
	public static final String SEPARATOR = "separator";
	public static final String DISABLE_OUTPUT_ESCAPING = "disable-output-escaping";

	public static final String YES = "yes";
	public static final String NO = "no";

	public static final String MATCH = "match";
	public static final String NAME = "name";
	public static final String PRIORITY = "priority";
	public static final String MODE = "mode";
	public static final String AS = "as";
	
	public static final String HREF = "href";
	public static final String USE_ATTRIBUTE_SETS = "use-attribute-sets";
	public static final String USE_CHARACTER_MAPS = "use-character-maps";
	
	public static final String DECIMAL_SEPARATOR = "decimal_separator";
	public static final String GROUPING_SEPARATOR = "grouping-separator";
	public static final String INFINITY = "infinity";
	public static final String MINUS_SIGN = "minus-sign";
	public static final String NAN = "NaN";
	public static final String PERCENT = "percent";
	public static final String PER_MILLE = "per-mille";
	public static final String ZERO_DIGIT = "zero-digit";
	public static final String DIGIT = "digit";
	public static final String PATTERN_SEPARATOR = "pattern-separator";

	public static final String OVERRIDE = "override";

	public static final String NAMESPACE = "namespace";
	public static final String SCHEMA_LOCATION = "schema-location";

	public static final String USE = "use";
	public static final String COLLATION = "collation";

	public static final String STYLESHEET_PREFIX = "stylesheet-prefix";
	public static final String RESULT_PREFIX = "result-prefix";

	public static final String METHOD = "method";
	public static final String BYTE_ORDER_MARK = "byte-order-mark";
	public static final String CDATA_SECTION_ELEMENTS = "cdata-section-elements";
	public static final String DOCTYPE_PUBLIC = "doctype-public";
	public static final String DOCTYPE_SYSTEM = "doctype-system";
	public static final String ENCODING = "encoding";
	public static final String ESCAPE_URI_ATTRIBUTES = "escape-uri-attributes";
	public static final String INCLUDE_CONTENT_TYPE = "include-content-type";
	public static final String INDENT = "indent";
	public static final String MEDIA_TYPE = "media-type";
	public static final String NORMALIZATION_FORM = "normalization-form";
	public static final String OMIT_XML_DECLARATION = "omit-xml-declaration";
	public static final String STANDALONE = "standalone";
	public static final String UNDECLARE_PREFIXES = "undeclare-prefixes";

	public static final String REQUIRED = "required";
	public static final String TUNNEL = "tunnel";
	
	public static final String ELEMENTS = "elements";

	public static final String TYPE = "type";
	public static final String VALIDATION = "validation";

	public static final String COPY_NAMESPACES = "copy-namespaces";
	public static final String INHERIT_NAMESPACES = "inherit-namespaces";

	public static final String FORMAT = "format";
	public static final String OUTPUT_VERSION = "output-version";

	public static final String TERMINATE = "terminate";
	
	public static final String REGEX = "regex";
	public static final String FLAGS = "flags";

	public static final String TEST = "test";

	public static final String GROUP_BY = "group-by";
	public static final String GROUP_ADJACENT = "group-adjacent";
	public static final String GROUP_STARTING_WITH = "group-starting-with";
	public static final String GROUP_ENDING_WITH = "group-ending-with";

	public static final String VALUE = "value";
	public static final String LEVEL = "level";
	public static final String COUNT = "count";
	public static final String FROM = "from";
	public static final String LANG = "lang";
	public static final String LETTER_VALUE = "letter-value";
	public static final String ORDINAL = "ordinal";
	public static final String GROUPING_SIZE = "grouping-size";

	public static final String CHARACTER = "character";
	public static final String STRING = "string";

	public static final String ORDER = "order";
	public static final String STABLE = "stable";
	public static final String CASE_ORDER = "case-order";
	public static final String DATA_TYPE = "data-type";
	
	public static final String XML_SPACE = "xml:space";

//	public static final String AA = "aa";
}
