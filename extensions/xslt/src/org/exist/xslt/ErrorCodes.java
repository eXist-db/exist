/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.xslt;

import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ErrorCodes extends org.exist.xquery.ErrorCodes{

    /* XSL 2.0 http://www.w3.org/TR/xslt20/#error-summary */
	/**
	 * A static error is signaled if an XSLT-defined element is used in a context where it is not permitted, if a required attribute is omitted, or if the content of the element does not correspond to the content that is allowed for the element. 
	 */
	public static final ErrorCode XTSE0010 = new ErrorCode("XTSE0010","A static error is signaled if an XSLT-defined element is used in a context where it is not permitted, if a required attribute is omitted, or if the content of the element does not correspond to the content that is allowed for the element.");

	/**
	 * It is a static error if an attribute (other than an attribute written using curly brackets in a position where an attribute value template is permitted) contains a value that is not one of the permitted values for that attribute.
	 */
	public static final ErrorCode XTSE0020 = new ErrorCode("XTSE0020","It is a static error if an attribute (other than an attribute written using curly brackets in a position where an attribute value template is permitted) contains a value that is not one of the permitted values for that attribute.");

	/**
	 * It is a static error to use a reserved namespace in the name of a named template, a mode, an attribute set, a key, a decimal-format, a variable or parameter, a stylesheet function, a named output definition, or a character map. 
	 */
	public static final ErrorCode XTSE0080 = new ErrorCode("XTSE0080","It is a static error to use a reserved namespace in the name of a named template, a mode, an attribute set, a key, a decimal-format, a variable or parameter, a stylesheet function, a named output definition, or a character map.");

	/**
	 * It is a static error for an element from the XSLT namespace to have an attribute whose namespace is either null (that is, an attribute with an unprefixed name) or the XSLT namespace, other than attributes defined for the element in this document. 
	 */
	public static final ErrorCode XTSE0090 = new ErrorCode("XTSE0090","It is a static error for an element from the XSLT namespace to have an attribute whose namespace is either null (that is, an attribute with an unprefixed name) or the XSLT namespace, other than attributes defined for the element in this document.");

	/**
	 * The value of the version attribute must be a number: specifically, it must be a a valid instance of the type xs:decimal as defined in [XML Schema Part 2]. 
	 */
	public static final ErrorCode XTSE0110 = new ErrorCode("XTSE0110","The value of the version attribute must be a number: specifically, it must be a a valid instance of the type xs:decimal as defined in [XML Schema Part 2].");

	/**
	 * An xsl:stylesheet element must not have any text node children. 
	 */
	public static final ErrorCode XTSE0120 = new ErrorCode("XTSE0120","An xsl:stylesheet element must not have any text node children.");

	/**
	 * It is a static error if the value of an [xsl:]default-collation attribute, after resolving against the base URI, contains no URI that the implementation recognizes as a collation URI. 
	 */
	public static final ErrorCode XTSE0125 = new ErrorCode("XTSE0125","It is a static error if the value of an [xsl:]default-collation attribute, after resolving against the base URI, contains no URI that the implementation recognizes as a collation URI.");
	
	/**
	 * It is a static error if the xsl:stylesheet element has a child element whose name has a null namespace URI. 
	 */
	public static final ErrorCode XTSE0130 = new ErrorCode("XTSE0130","It is a static error if the xsl:stylesheet element has a child element whose name has a null namespace URI.");

	/**
	 * A literal result element that is used as the outermost element of a simplified stylesheet module must have an xsl:version attribute. 
	 */
	public static final ErrorCode XTSE0150 = new ErrorCode("XTSE0150","A literal result element that is used as the outermost element of a simplified stylesheet module must have an xsl:version attribute.");

	/**
	 * It is a static error if the processor is not able to retrieve the resource identified by the URI reference [ in the href attribute of xsl:include or xsl:import] , or if the resource that is retrieved does not contain a stylesheet module conforming to this specification. 
	 */
	public static final ErrorCode XTSE0165 = new ErrorCode("XTSE0165","It is a static error if the processor is not able to retrieve the resource identified by the URI reference [ in the href attribute of xsl:include or xsl:import] , or if the resource that is retrieved does not contain a stylesheet module conforming to this specification.");

	/**
	 * An xsl:include element must be a top-level element. 
	 */
	public static final ErrorCode XTSE0170 = new ErrorCode("XTSE0170","An xsl:include element must be a top-level element.");

	/**
	 * It is a static error if a stylesheet module directly or indirectly includes itself. 
	 */
	public static final ErrorCode XTSE0180 = new ErrorCode("XTSE0180","It is a static error if a stylesheet module directly or indirectly includes itself.");

	/**
	 * An xsl:import element must be a top-level element. 
	 */
	public static final ErrorCode XTSE0190 = new ErrorCode("XTSE0190","An xsl:import element must be a top-level element.");

	/**
	 * The xsl:import element children must precede all other element children of an xsl:stylesheet element, including any xsl:include element children and any user-defined data elements. 
	 */
	public static final ErrorCode XTSE0200 = new ErrorCode("XTSE0200","The xsl:import element children must precede all other element children of an xsl:stylesheet element, including any xsl:include element children and any user-defined data elements.");

	/**
	 * It is a static error if a stylesheet module directly or indirectly imports itself. 
	 */
	public static final ErrorCode XTSE0210 = new ErrorCode("XTSE0210","It is a static error if a stylesheet module directly or indirectly imports itself.");

	/**
	 * It is a static error if an xsl:import-schema element that contains an xs:schema element has a schema-location attribute, or if it has a namespace attribute that conflicts with the target namespace of the contained schema. 
	 */
	public static final ErrorCode XTSE0215 = new ErrorCode("XTSE0215","It is a static error if an xsl:import-schema element that contains an xs:schema element has a schema-location attribute, or if it has a namespace attribute that conflicts with the target namespace of the contained schema.");
	
	/**
	 * It is a static error if the synthetic schema document does not satisfy the constraints described in [XML Schema Part 1] (section 5.1, Errors in Schema Construction and Structure). This includes, without loss of generality, conflicts such as multiple definitions of the same name.
	 */
	public static final ErrorCode XTSE0220 = new ErrorCode("XTSE0220","It is a static error if the synthetic schema document does not satisfy the constraints described in [XML Schema Part 1] (section 5.1, Errors in Schema Construction and Structure). This includes, without loss of generality, conflicts such as multiple definitions of the same name.");

	/**
	 * Within an XSLT element that is required to be empty, any content other than comments or processing instructions, including any whitespace text node preserved using the xml:space="preserve" attribute, is a static error.
	 */
	public static final ErrorCode XTSE0260 = new ErrorCode("XTSE0260","Within an XSLT element that is required to be empty, any content other than comments or processing instructions, including any whitespace text node preserved using the xml:space=\"preserve\" attribute, is a static error.");

	/**
	 * It is a static error if there is a stylesheet module in the stylesheet that specifies input-type-annotations="strip" and another stylesheet module that specifies input-type-annotations="preserve". 
	 */
	public static final ErrorCode XTSE0265 = new ErrorCode("XTSE0265","It is a static error if there is a stylesheet module in the stylesheet that specifies input-type-annotations=\"strip\" and another stylesheet module that specifies input-type-annotations=\"preserve\".");

	/**
	 * In the case of a prefixed QName used as the value of an attribute in the stylesheet, or appearing within an XPath expression in the stylesheet, it is a static error if the defining element has no namespace node whose name matches the prefix of the QName. 
	 */
	public static final ErrorCode XTSE0280 = new ErrorCode("XTSE0280","In the case of a prefixed QName used as the value of an attribute in the stylesheet, or appearing within an XPath expression in the stylesheet, it is a static error if the defining element has no namespace node whose name matches the prefix of the QName.");

	/**
	 * Where an attribute is defined to contain a pattern, it is a static error if the pattern does not match the production Pattern. 
	 */
	public static final ErrorCode XTSE0340 = new ErrorCode("XTSE0340","Where an attribute is defined to contain a pattern, it is a static error if the pattern does not match the production Pattern.");

	/**
	 * It is a static error if an unescaped left curly bracket appears in a fixed part of an attribute value template without a matching right curly bracket. 
	 */
	public static final ErrorCode XTSE0350 = new ErrorCode("XTSE0350","It is a static error if an unescaped left curly bracket appears in a fixed part of an attribute value template without a matching right curly bracket.");

	/**
	 * It is a static error if an unescaped right curly bracket occurs in a fixed part of an attribute value template. 
	 */
	public static final ErrorCode XTSE0370 = new ErrorCode("XTSE0370","It is a static error if an unescaped right curly bracket occurs in a fixed part of an attribute value template.");

	/**
	 * An xsl:template element must have either a match attribute or a name attribute, or both. An xsl:template element that has no match attribute must have no mode attribute and no priority attribute. 
	 */
	public static final ErrorCode XTSE0500 = new ErrorCode("XTSE0500","An xsl:template element must have either a match attribute or a name attribute, or both. An xsl:template element that has no match attribute must have no mode attribute and no priority attribute.");
	
	/**
	 * The value of this attribute [the priority attribute of the xsl:template element] must conform to the rules for the xs:decimal type defined in [XML Schema Part 2]. Negative values are permitted.. 
	 */
	public static final ErrorCode XTSE0530 = new ErrorCode("XTSE0530","The value of this attribute [the priority attribute of the xsl:template element] must conform to the rules for the xs:decimal type defined in [XML Schema Part 2]. Negative values are permitted..");

	/**
	 * It is a static error if the list [of modes in the mode attribute of xsl:template] is empty, if the same token is included more than once in the list, if the list contains an invalid token, or if the token #all appears together with any other value. 
	 */
	public static final ErrorCode XTSE0550 = new ErrorCode("XTSE0550","It is a static error if the list [of modes in the mode attribute of xsl:template] is empty, if the same token is included more than once in the list, if the list contains an invalid token, or if the token #all appears together with any other value.");

	/**
	 * It is a static error if two parameters of a template or of a stylesheet function have the same name. 
	 */
	public static final ErrorCode XTSE0580 = new ErrorCode("XTSE0580","It is a static error if two parameters of a template or of a stylesheet function have the same name.");

	/**
	 * It is a static error if a variable-binding element has a select attribute and has non-empty content. 
	 */
	public static final ErrorCode XTSE0620 = new ErrorCode("XTSE0620","It is a static error if a variable-binding element has a select attribute and has non-empty content.");

	/**
	 * It is a static error if a stylesheet contains more than one binding of a global variable with the same name and same import precedence, unless it also contains another binding with the same name and higher import precedence. 
	 */
	public static final ErrorCode XTSE0630 = new ErrorCode("XTSE0630","It is a static error if a stylesheet contains more than one binding of a global variable with the same name and same import precedence, unless it also contains another binding with the same name and higher import precedence.");

	/**
	 * It is a static error if a stylesheet contains an xsl:call-template instruction whose name attribute does not match the name attribute of any xsl:template in the stylesheet.
	 */
	public static final ErrorCode XTSE0650 = new ErrorCode("XTSE0650","It is a static error if a stylesheet contains an xsl:call-template instruction whose name attribute does not match the name attribute of any xsl:template in the stylesheet.");

	/**
	 * It is a static error if a stylesheet contains more than one template with the same name and the same import precedence, unless it also contains a template with the same name and higher import precedence. 
	 */
	public static final ErrorCode XTSE0660 = new ErrorCode("XTSE0660","It is a static error if a stylesheet contains more than one template with the same name and the same import precedence, unless it also contains a template with the same name and higher import precedence.");

	/**
	 * It is a static error if a single xsl:call-template, xsl:apply-templates, xsl:apply-imports, or xsl:next-match element contains two or more xsl:with-param elements with matching name attributes. 
	 */
	public static final ErrorCode XTSE0670 = new ErrorCode("XTSE0670","It is a static error if a single xsl:call-template, xsl:apply-templates, xsl:apply-imports, or xsl:next-match element contains two or more xsl:with-param elements with matching name attributes.");
	
	/**
	 * In the case of xsl:call-template, it is a static error to pass a non-tunnel parameter named x to a template that does not have a template parameter named x, unless backwards compatible behavior is enabled for the xsl:call-template instruction. 
	 */
	public static final ErrorCode XTSE0680 = new ErrorCode("XTSE0680","In the case of xsl:call-template, it is a static error to pass a non-tunnel parameter named x to a template that does not have a template parameter named x, unless backwards compatible behavior is enabled for the xsl:call-template instruction.");

	/**
	 * It is a static error if a template that is invoked using xsl:call-template declares a template parameter specifying required="yes" and not specifying tunnel="yes", if no value for this parameter is supplied by the calling instruction.
	 */
	public static final ErrorCode XTSE0690 = new ErrorCode("XTSE0690","It is a static error if a template that is invoked using xsl:call-template declares a template parameter specifying required=\"yes\" and not specifying tunnel=\"yes\", if no value for this parameter is supplied by the calling instruction.");

	/**
	 * It is a static error if the value of the use-attribute-sets attribute of an xsl:copy, xsl:element, or xsl:attribute-set element, or the xsl:use-attribute-sets attribute of a literal result element, is not a whitespace-separated sequence of QNames, or if it contains a QName that does not match the name attribute of any xsl:attribute-set declaration in the stylesheet. 
	 */
	public static final ErrorCode XTSE0710 = new ErrorCode("XTSE0710","It is a static error if the value of the use-attribute-sets attribute of an xsl:copy, xsl:element, or xsl:attribute-set element, or the xsl:use-attribute-sets attribute of a literal result element, is not a whitespace-separated sequence of QNames, or if it contains a QName that does not match the name attribute of any xsl:attribute-set declaration in the stylesheet.");

	/**
	 * It is a static error if an xsl:attribute-set element directly or indirectly references itself via the names contained in the use-attribute-sets attribute. 
	 */
	public static final ErrorCode XTSE0720 = new ErrorCode("XTSE0720","It is a static error if an xsl:attribute-set element directly or indirectly references itself via the names contained in the use-attribute-sets attribute.");

	/**
	 * A stylesheet function must have a prefixed name, to remove any risk of a clash with a function in the default function namespace. It is a static error if the name has no prefix. 
	 */
	public static final ErrorCode XTSE0740 = new ErrorCode("XTSE0740","A stylesheet function must have a prefixed name, to remove any risk of a clash with a function in the default function namespace. It is a static error if the name has no prefix.");

	/**
	 * Because arguments to a stylesheet function call must all be specified, the xsl:param elements within an xsl:function element must not specify a default value: this means they must be empty, and must not have a select attribute. 
	 */
	public static final ErrorCode XTSE0760 = new ErrorCode("XTSE0760","Because arguments to a stylesheet function call must all be specified, the xsl:param elements within an xsl:function element must not specify a default value: this means they must be empty, and must not have a select attribute.");

	/**
	 * It is a static error for a stylesheet to contain two or more functions with the same expanded-QName, the same arity, and the same import precedence, unless there is another function with the same expanded-QName and arity, and a higher import precedence. 
	 */
	public static final ErrorCode XTSE0770 = new ErrorCode("XTSE0770","It is a static error for a stylesheet to contain two or more functions with the same expanded-QName, the same arity, and the same import precedence, unless there is another function with the same expanded-QName and arity, and a higher import precedence.");

	/**
	 * It is a static error if an attribute on a literal result element is in the XSLT namespace, unless it is one of the attributes explicitly defined in this specification. 
	 */
	public static final ErrorCode XTSE0805 = new ErrorCode("XTSE0805","It is a static error if an attribute on a literal result element is in the XSLT namespace, unless it is one of the attributes explicitly defined in this specification.");

	/**
	 * It is a static error if a namespace prefix is used within the [xsl:]exclude-result-prefixes attribute and there is no namespace binding in scope for that prefix. 
	 */
	public static final ErrorCode XTSE0808 = new ErrorCode("XTSE0808","It is a static error if a namespace prefix is used within the [xsl:]exclude-result-prefixes attribute and there is no namespace binding in scope for that prefix.");

	/**
	 * It is a static error if the value #default is used within the [xsl:]exclude-result-prefixes attribute and the parent element of the [xsl:]exclude-result-prefixes attribute has no default namespace. 
	 */
	public static final ErrorCode XTSE0809 = new ErrorCode("XTSE0809","It is a static error if the value #default is used within the [xsl:]exclude-result-prefixes attribute and the parent element of the [xsl:]exclude-result-prefixes attribute has no default namespace.");

	/**
	 * It is a static error if there is more than one such declaration [more than one xsl:namespace-alias declaration] with the same literal namespace URI and the same import precedence and different values for the target namespace URI, unless there is also an xsl:namespace-alias declaration with the same literal namespace URI and a higher import precedence. 
	 */
	public static final ErrorCode XTSE0810 = new ErrorCode("XTSE0810","It is a static error if there is more than one such declaration [more than one xsl:namespace-alias declaration] with the same literal namespace URI and the same import precedence and different values for the target namespace URI, unless there is also an xsl:namespace-alias declaration with the same literal namespace URI and a higher import precedence.");
	

	/**
	 * It is a static error if a value other than #default is specified for either the stylesheet-prefix or the result-prefix attributes of the xsl:namespace-alias element when there is no in-scope binding for that namespace prefix. 
	 */
	public static final ErrorCode XTSE0812 = new ErrorCode("XTSE0812","It is a static error if a value other than #default is specified for either the stylesheet-prefix or the result-prefix attributes of the xsl:namespace-alias element when there is no in-scope binding for that namespace prefix.");

	/**
	 * It is a static error if the select attribute of the xsl:attribute element is present unless the element has empty content. 
	 */
	public static final ErrorCode XTSE0840 = new ErrorCode("XTSE0840","It is a static error if the select attribute of the xsl:attribute element is present unless the element has empty content.");

	/**
	 * It is a static error if the select attribute of the xsl:value-of element is present when the content of the element is non-empty, or if the select attribute is absent when the content is empty. 
	 */
	public static final ErrorCode XTSE0870 = new ErrorCode("XTSE0870","It is a static error if the select attribute of the xsl:value-of element is present when the content of the element is non-empty, or if the select attribute is absent when the content is empty.");

	/**
	 * It is a static error if the select attribute of the xsl:processing-instruction element is present unless the element has empty content. 
	 */
	public static final ErrorCode XTSE0880 = new ErrorCode("XTSE0880","It is a static error if the select attribute of the xsl:processing-instruction element is present unless the element has empty content.");

	/**
	 * It is a static error if the select attribute of the xsl:namespace element is present when the element has content other than one or more xsl:fallback instructions, or if the select attribute is absent when the element has empty content. 
	 */
	public static final ErrorCode XTSE0910 = new ErrorCode("XTSE0910","It is a static error if the select attribute of the xsl:namespace element is present when the element has content other than one or more xsl:fallback instructions, or if the select attribute is absent when the element has empty content.");
	
	/**
	 * It is a static error if the select attribute of the xsl:comment element is present unless the element has empty content. 
	 */
	public static final ErrorCode XTSE0940 = new ErrorCode("XTSE0940","It is a static error if the select attribute of the xsl:comment element is present unless the element has empty content.");

	/**
	 * It is a type error to use the xsl:copy or xsl:copy-of instruction to copy a node that has namespace-sensitive content if the copy-namespaces attribute has the value no and its explicit or implicit validation attribute has the value preserve. It is also a type error if either of these instructions (with validation="preserve") is used to copy an attribute having namespace-sensitive content, unless the parent element is also copied. A node has namespace-sensitive content if its typed value contains an item of type xs:QName or xs:NOTATION or a type derived therefrom. The reason this is an error is because the validity of the content depends on the namespace context being preserved. 
	 */
	public static final ErrorCode XTTE0950 = new ErrorCode("XTTE0950","It is a type error to use the xsl:copy or xsl:copy-of instruction to copy a node that has namespace-sensitive content if the copy-namespaces attribute has the value no and its explicit or implicit validation attribute has the value preserve. It is also a type error if either of these instructions (with validation=\"preserve\") is used to copy an attribute having namespace-sensitive content, unless the parent element is also copied. A node has namespace-sensitive content if its typed value contains an item of type xs:QName or xs:NOTATION or a type derived therefrom. The reason this is an error is because the validity of the content depends on the namespace context being preserved.");

	/**
	 * It is a static error if the value attribute of xsl:number is present unless the select, level, count, and from attributes are all absent. 
	 */
	public static final ErrorCode XTSE0975 = new ErrorCode("XTSE0975","It is a static error if the value attribute of xsl:number is present unless the select, level, count, and from attributes are all absent.");
	
	/**
	 * It is a static error if an xsl:sort element with a select attribute has non-empty content. 
	 */
	public static final ErrorCode XTSE1015 = new ErrorCode("XTSE1015","It is a static error if an xsl:sort element with a select attribute has non-empty content.");

	/**
	 * It is a static error if an xsl:sort element other than the first in a sequence of sibling xsl:sort elements has a stable attribute.
	 */
	public static final ErrorCode XTSE1017 = new ErrorCode("XTSE1017","It is a static error if an xsl:sort element other than the first in a sequence of sibling xsl:sort elements has a stable attribute.");

	/**
	 * It is a static error if an xsl:perform-sort instruction with a select attribute has any content other than xsl:sort and xsl:fallback instructions. 
	 */
	public static final ErrorCode XTSE1040 = new ErrorCode("XTSE1040","It is a static error if an xsl:perform-sort instruction with a select attribute has any content other than xsl:sort and xsl:fallback instructions.");

	/**
	 * It is a static error if the current-group function is used within a pattern. 
	 */
	public static final ErrorCode XTSE1060 = new ErrorCode("XTSE1060","It is a static error if the current-group function is used within a pattern.");

	/**
	 * It is a static error if the current-grouping-key function is used within a pattern. 
	 */
	public static final ErrorCode XTSE1070 = new ErrorCode("XTSE1070","It is a static error if the current-grouping-key function is used within a pattern.");

	/**
	 * These four attributes [the group-by, group-adjacent, group-starting-with, and group-ending-with attributes of xsl:for-each-group ] are mutually exclusive: it is a static error if none of these four attributes is present, or if more than one of them is present. 
	 */
	public static final ErrorCode XTSE1080 = new ErrorCode("XTSE1080","These four attributes [the group-by, group-adjacent, group-starting-with, and group-ending-with attributes of xsl:for-each-group ] are mutually exclusive: it is a static error if none of these four attributes is present, or if more than one of them is present.");

	/**
	 * It is an error to specify the collation attribute if neither the group-by attribute nor group-adjacent attribute is specified. 
	 */
	public static final ErrorCode XTSE1090 = new ErrorCode("XTSE1090","It is an error to specify the collation attribute if neither the group-by attribute nor group-adjacent attribute is specified.");

	/**
	 * It is a static error if the xsl:analyze-ErrorCode instruction contains neither an xsl:matching-subErrorCode nor an xsl:non-matching-subErrorCode element. 
	 */
	public static final ErrorCode XTSE1130 = new ErrorCode("XTSE1130","It is a static error if the xsl:analyze-ErrorCode instruction contains neither an xsl:matching-subErrorCode nor an xsl:non-matching-subErrorCode element.");

	/**
	 * It is a static error if an xsl:key declaration has a use attribute and has non-empty content, or if it has empty content and no use attribute. 
	 */
	public static final ErrorCode XTSE1205 = new ErrorCode("XTSE1205","It is a static error if an xsl:key declaration has a use attribute and has non-empty content, or if it has empty content and no use attribute.");

	/**
	 * It is a static error if the xsl:key declaration has a collation attribute whose value (after resolving against the base URI) is not a URI recognized by the implementation as referring to a collation. 
	 */
	public static final ErrorCode XTSE1210 = new ErrorCode("XTSE1210","It is a static error if the xsl:key declaration has a collation attribute whose value (after resolving against the base URI) is not a URI recognized by the implementation as referring to a collation.");

	/**
	 * It is a static error if there are several xsl:key declarations in the stylesheet with the same key name and different effective collations. Two collations are the same if their URIs are equal under the rules for comparing xs:anyURI values, or if the implementation can determine that they are different URIs referring to the same collation. 
	 */
	public static final ErrorCode XTSE1220 = new ErrorCode("XTSE1220","It is a static error if there are several xsl:key declarations in the stylesheet with the same key name and different effective collations. Two collations are the same if their URIs are equal under the rules for comparing xs:anyURI values, or if the implementation can determine that they are different URIs referring to the same collation.");

	/**
	 * It is a static error if a named or unnamed decimal format contains two conflicting values for the same attribute in different xsl:decimal-format declarations having the same import precedence, unless there is another definition of the same attribute with higher import precedence. 
	 */
	public static final ErrorCode XTSE1290 = new ErrorCode("XTSE1290","It is a static error if a named or unnamed decimal format contains two conflicting values for the same attribute in different xsl:decimal-format declarations having the same import precedence, unless there is another definition of the same attribute with higher import precedence.");

	/**
	 * It is a static error if the character specified in the zero-digit attribute is not a digit or is a digit that does not have the numeric value zero. 
	 */
	public static final ErrorCode XTSE1295 = new ErrorCode("XTSE1295","It is a static error if the character specified in the zero-digit attribute is not a digit or is a digit that does not have the numeric value zero.");

	/**
	 * It is a static error if, for any named or unnamed decimal format, the variables representing characters used in a picture ErrorCode do not each have distinct values. These variables are decimal-separator-sign, grouping-sign, percent-sign, per-mille-sign, digit-zero-sign, digit-sign, and pattern-separator-sign. 
	 */
	public static final ErrorCode XTSE1300 = new ErrorCode("XTSE1300","It is a static error if, for any named or unnamed decimal format, the variables representing characters used in a picture ErrorCode do not each have distinct values. These variables are decimal-separator-sign, grouping-sign, percent-sign, per-mille-sign, digit-zero-sign, digit-sign, and pattern-separator-sign.");

	/**
	 * It is a static error if there is no namespace bound to the prefix on the element bearing the [xsl:]extension-element-prefixes attribute or, when #default is specified, if there is no default namespace. 
	 */
	public static final ErrorCode XTSE1430 = new ErrorCode("XTSE1430","It is a static error if there is no namespace bound to the prefix on the element bearing the [xsl:]extension-element-prefixes attribute or, when #default is specified, if there is no default namespace.");

	/**
	 * It is a static error if both the [xsl:]type and [xsl:]validation attributes are present on the xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, xsl:document, or xsl:result-document instructions, or on a literal result element.
	 */
	public static final ErrorCode XTSE1505 = new ErrorCode("XTSE1505","It is a static error if both the [xsl:]type and [xsl:]validation attributes are present on the xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, xsl:document, or xsl:result-document instructions, or on a literal result element.");

	/**
	 * It is a static error if the value of the type attribute of an xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, xsl:document, or xsl:result-document instruction, or the xsl:type attribute of a literal result element, is not a valid QName, or if it uses a prefix that is not defined in an in-scope namespace declaration, or if the QName is not the name of a type definition included in the in-scope schema components for the stylesheet.
	 */
	public static final ErrorCode XTSE1520 = new ErrorCode("XTSE1520","It is a static error if the value of the type attribute of an xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, xsl:document, or xsl:result-document instruction, or the xsl:type attribute of a literal result element, is not a valid QName, or if it uses a prefix that is not defined in an in-scope namespace declaration, or if the QName is not the name of a type definition included in the in-scope schema components for the stylesheet.");

	/**
	 * It is a static error if the value of the type attribute of an xsl:attribute instruction refers to a complex type definition
	 */
	public static final ErrorCode XTSE1530 = new ErrorCode("XTSE1530","It is a static error if the value of the type attribute of an xsl:attribute instruction refers to a complex type definition");

	/**
	 * It is a static error if two xsl:output declarations within an output definition specify explicit values for the same attribute (other than cdata-section-elements and use-character-maps), with the values of the attributes being not equal, unless there is another xsl:output declaration within the same output definition that has higher import precedence and that specifies an explicit value for the same attribute.
	 */
	public static final ErrorCode XTSE1560 = new ErrorCode("XTSE1560","It is a static error if two xsl:output declarations within an output definition specify explicit values for the same attribute (other than cdata-section-elements and use-character-maps), with the values of the attributes being not equal, unless there is another xsl:output declaration within the same output definition that has higher import precedence and that specifies an explicit value for the same attribute.");

	/**
	 * The value [of the method attribute on xsl:output ] must (if present) be a valid QName. If the QName does not have a prefix, then it identifies a method specified in [XSLT and XQuery Serialization] and must be one of xml, html, xhtml, or text.
	 */
	public static final ErrorCode XTSE1570 = new ErrorCode("XTSE1570","The value [of the method attribute on xsl:output ] must (if present) be a valid QName. If the QName does not have a prefix, then it identifies a method specified in [XSLT and XQuery Serialization] and must be one of xml, html, xhtml, or text.");

	/**
	 * It is a static error if the stylesheet contains two or more character maps with the same name and the same import precedence, unless it also contains another character map with the same name and higher import precedence.
	 */
	public static final ErrorCode XTSE1580 = new ErrorCode("XTSE1580","It is a static error if the stylesheet contains two or more character maps with the same name and the same import precedence, unless it also contains another character map with the same name and higher import precedence.");

	/**
	 * It is a static error if a name in the use-character-maps attribute of the xsl:output or xsl:character-map elements does not match the name attribute of any xsl:character-map in the stylesheet.
	 */
	public static final ErrorCode XTSE1590 = new ErrorCode("XTSE1590","It is a static error if a name in the use-character-maps attribute of the xsl:output or xsl:character-map elements does not match the name attribute of any xsl:character-map in the stylesheet.");

	/**
	 * It is a static error if a character map references itself, directly or indirectly, via a name in the use-character-maps attribute.
	 */
	public static final ErrorCode XTSE1600 = new ErrorCode("XTSE1600","It is a static error if a character map references itself, directly or indirectly, via a name in the use-character-maps attribute.");

	/**
	 * A basic XSLT processor must signal a static error if the stylesheet includes an xsl:import-schema declaration.
	 */
	public static final ErrorCode XTSE1650 = new ErrorCode("XTSE1650","A basic XSLT processor must signal a static error if the stylesheet includes an xsl:import-schema declaration.");

	/**
	 * A basic XSLT processor must signal a static error if the stylesheet includes an [xsl:]type attribute, or an [xsl:]validation or default-validation attribute with a value other than strip.
	 */
	public static final ErrorCode XTSE1660 = new ErrorCode("XTSE1660","A basic XSLT processor must signal a static error if the stylesheet includes an [xsl:]type attribute, or an [xsl:]validation or default-validation attribute with a value other than strip.");

	//Type errors
	/**
	 * It is a type error if the result of evaluating the sequence constructor cannot be converted to the required type.
	 */
	public static final ErrorCode XTTE0505 = new ErrorCode("XTTE0505","");

	/**
	 * It is a type error if an xsl:apply-templates instruction with no select attribute is evaluated when the context item is not a node.
	 */
	public static final ErrorCode XTTE0510 = new ErrorCode("XTTE0510","");

	/**
	 * It is a type error if the sequence returned by the select expression [of xsl:apply-templates] contains an item that is not a node.
	 */
	public static final ErrorCode XTTE0520 = new ErrorCode("XTTE0520","");

	/**
	 * It is a type error if the supplied value of a variable cannot be converted to the required type.
	 */
	public static final ErrorCode XTTE0570 = new ErrorCode("XTTE0570","");

	/**
	 * It is a type error if the conversion of the supplied value of a parameter to its required type fails.
	 */
	public static final ErrorCode XTTE0590 = new ErrorCode("XTTE0590","");

	/**
	 * If a default value is given explicitly, that is, if there is either a select attribute or a non-empty sequence constructor, then it is a type error if the default value cannot be converted to the required type, using the function conversion rules.
	 */
	public static final ErrorCode XTTE0600 = new ErrorCode("XTTE0600","");

	/**
	 * If the as attribute [of xsl:function ] is specified, then the result evaluated by the sequence constructor (see 5.7 Sequence Constructors) is converted to the required type, using the function conversion rules. It is a type error if this conversion fails.
	 */
	public static final ErrorCode XTTE0780 = new ErrorCode("XTTE0780","");

	/**
	 * If the value of a parameter to a stylesheet function cannot be converted to the required type, a type error is signaled.
	 */
	public static final ErrorCode XTTE0790 = new ErrorCode("XTTE0790","");

	/**
	 * It is a type error if the xsl:number instruction is evaluated, with no value or select attribute, when the context item is not a node.
	 */
	public static final ErrorCode XTTE0990 = new ErrorCode("XTTE0990","");

	/**
	 * It is a type error if the result of evaluating the select attribute of the xsl:number instruction is anything other than a single node.
	 */
	public static final ErrorCode XTTE1000 = new ErrorCode("XTTE1000","");

	/**
	 * If any sort key value, after atomization and any type conversion required by the data-type attribute, is a sequence containing more than one item, then the effect depends on whether the xsl:sort element is evaluated with backwards compatible behavior. With backwards compatible behavior, the effective sort key value is the first item in the sequence. In other cases, this is a type error.
	 */
	public static final ErrorCode XTTE1020 = new ErrorCode("XTTE1020","");

	/**
	 * It is a type error if the grouping key evaluated using the group-adjacent attribute is an empty sequence, or a sequence containing more than one item.
	 */
	public static final ErrorCode XTTE1100 = new ErrorCode("XTTE1100","");

	/**
	 * When the group-starting-with or group-ending-with attribute [of the xsl:for-each-group instruction] is used, it is a type error if the result of evaluating the select expression contains an item that is not a node.
	 */
	public static final ErrorCode XTTE1120 = new ErrorCode("XTTE1120","");

	/**
	 * If the validation attribute of an xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, or xsl:result-document instruction, or the xsl:validation attribute of a literal result element, has the effective value strict, and schema validity assessment concludes that the validity of the element or attribute is invalid or unknown, a type error occurs. As with other type errors, the error may be signaled statically if it can be detected statically.
	 */
	public static final ErrorCode XTTE1510 = new ErrorCode("XTTE1510","");

	/**
	 * If the validation attribute of an xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, or xsl:result-document instruction, or the xsl:validation attribute of a literal result element, has the effective value strict, and there is no matching top-level declaration in the schema, then a type error occurs. As with other type errors, the error may be signaled statically if it can be detected statically.
	 */
	public static final ErrorCode XTTE1512 = new ErrorCode("XTTE1512","");

	/**
	 * If the validation attribute of an xsl:element, xsl:attribute, xsl:copy, xsl:copy-of, or xsl:result-document instruction, or the xsl:validation attribute of a literal result element, has the effective value lax, and schema validity assessment concludes that the element or attribute is invalid, a type error occurs. As with other type errors, the error may be signaled statically if it can be detected statically.
	 */
	public static final ErrorCode XTTE1515 = new ErrorCode("XTTE1515","");

	/**
	 * It is a type error if an [xsl:]type attribute is defined for a constructed element or attribute, and the outcome of schema validity assessment against that type is that the validity property of that element or attribute information item is other than valid.
	 */
	public static final ErrorCode XTTE1540 = new ErrorCode("XTTE1540","");

	/**
	 * A type error occurs if a type or validation attribute is defined (explicitly or implicitly) for an instruction that constructs a new attribute node, if the effect of this is to cause the attribute value to be validated against a type that is derived from, or constructed by list or union from, the primitive types xs:QName or xs:NOTATION.
	 */
	public static final ErrorCode XTTE1545 = new ErrorCode("XTTE1545","");

	/**
	 * A type error occurs [when a document node is validated] unless the children of the document node comprise exactly one element node, no text nodes, and zero or more comment and processing instruction nodes, in any order.
	 */
	public static final ErrorCode XTTE1550 = new ErrorCode("XTTE1550","");

	/**
	 * It is a type error if, when validating a document node, document-level constraints are not satisfied. These constraints include identity constraints (xs:unique, xs:key, and xs:keyref) and ID/IDREF constraints.
	 */
	public static final ErrorCode XTTE1555 = new ErrorCode("XTTE1555","");

	//Dynamic errors
	/**
	 * It is a non-recoverable dynamic error if the effective value of an attribute written using curly brackets, in a position where an attribute value template is permitted, is a value that is not one of the permitted values for that attribute. If the processor is able to detect the error statically (for example, when any XPath expressions within the curly brackets can be evaluated statically), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE0030 = new ErrorCode("XTDE0030","");

	/**
	 * It is a non-recoverable dynamic error if the invocation of the stylesheet specifies a template name that does not match the expanded-QName of a named template defined in the stylesheet.
	 */
	public static final ErrorCode XTDE0040 = new ErrorCode("XTDE0040","");

	/**
	 * It is a non-recoverable dynamic error if the invocation of the stylesheet specifies an initial mode (other than the default mode) that does not match the expanded-QName in the mode attribute of any template defined in the stylesheet.
	 */
	public static final ErrorCode XTDE0045 = new ErrorCode("XTDE0045","");

	/**
	 * It is a non-recoverable dynamic error if the invocation of the stylesheet specifies both an initial mode and an initial template.
	 */
	public static final ErrorCode XTDE0047 = new ErrorCode("XTDE0047","");

	/**
	 * It is a non-recoverable dynamic error if the stylesheet that is invoked declares a visible stylesheet parameter with required="yes" and no value for this parameter is supplied during the invocation of the stylesheet. A stylesheet parameter is visible if it is not masked by another global variable or parameter with the same name and higher import precedence.
	 */
	public static final ErrorCode XTDE0050 = new ErrorCode("XTDE0050","");

	/**
	 * It is a non-recoverable dynamic error if the initial template defines a template parameter that specifies required="yes".
	 */
	public static final ErrorCode XTDE0060 = new ErrorCode("XTDE0060","");

	/**
	 * If an implementation does not support backwards-compatible behavior, then it is a non-recoverable dynamic error if any element is evaluated that enables backwards-compatible behavior.
	 */
	public static final ErrorCode XTDE0160 = new ErrorCode("XTDE0160","");

	/**
	 * It is a recoverable dynamic error if this [the process of finding an xsl:strip-space or xsl:preserve-space declaration to match an element in the source document] leaves more than one match, unless all the matched declarations are equivalent (that is, they are all xsl:strip-space or they are all xsl:preserve-space).
	 * 	    Action: The optional recovery action is to select, from the matches that are left, the one that occurs last in declaration order.
	 */
	public static final ErrorCode XTRE0270 = new ErrorCode("XTRE0270","");

	/**
	 * Where the result of evaluating an XPath expression (or an attribute value template) is required to be a lexical QName, then unless otherwise specified it is a non-recoverable dynamic error if the defining element has no namespace node whose name matches the prefix of the lexical QName. This error may be signaled as a static error if the value of the expression can be determined statically.
	 */
	public static final ErrorCode XTDE0290 = new ErrorCode("XTDE0290","");

	/**
	 * It is a non-recoverable dynamic error if the result sequence used to construct the content of an element node contains a namespace node or attribute node that is preceded in the sequence by a node that is neither a namespace node nor an attribute node.
	 */
	public static final ErrorCode XTDE0410 = new ErrorCode("XTDE0410","");

	/**
	 * It is a non-recoverable dynamic error if the result sequence used to construct the content of a document node contains a namespace node or attribute node.
	 */
	public static final ErrorCode XTDE0420 = new ErrorCode("XTDE0420","");

	/**
	 * It is a non-recoverable dynamic error if the result sequence contains two or more namespace nodes having the same name but different ErrorCode values (that is, namespace nodes that map the same prefix to different namespace URIs).
	 */
	public static final ErrorCode XTDE0430 = new ErrorCode("XTDE0430","");

	/**
	 * It is a non-recoverable dynamic error if the result sequence contains a namespace node with no name and the element node being constructed has a null namespace URI (that is, it is an error to define a default namespace when the element is in no namespace).
	 */
	public static final ErrorCode XTDE0440 = new ErrorCode("XTDE0440","");

	/**
	 * It is a non-recoverable dynamic error if namespace fixup is performed on an element that contains among the typed values of the element and its attributes two values of type xs:QName or xs:NOTATION containing conflicting namespace prefixes, that is, two values that use the same prefix to refer to different namespace URIs.
	 */
	public static final ErrorCode XTDE0485 = new ErrorCode("XTDE0485","");

	/**
	 * It is a recoverable dynamic error if the conflict resolution algorithm for template rules leaves more than one matching template rule.
	 * 	    Action: The optional recovery action is to select, from the matching template rules that are left, the one that occurs last in declaration order.
	 */
	public static final ErrorCode XTRE0540 = new ErrorCode("XTRE0540","");

    /**
	 * It is a non-recoverable dynamic error if xsl:apply-imports or xsl:next-match is evaluated when the current template rule is null.
	 */
	public static final ErrorCode XTDE0560 = new ErrorCode("XTDE0560","");

	/**
	 * If an optional parameter has no select attribute and has an empty sequence constructor, and if there is an as attribute, then the default value of the parameter is an empty sequence. If the empty sequence is not a valid instance of the required type defined in the as attribute, then the parameter is treated as a required parameter, which means that it is a non-recoverable dynamic error if the caller supplies no value for the parameter.
	 */
	public static final ErrorCode XTDE0610 = new ErrorCode("XTDE0610","");

	/**
	 * In general, a circularity in a stylesheet is a non-recoverable dynamic error.
	 */
	public static final ErrorCode XTDE0640 = new ErrorCode("XTDE0640","");

	/**
	 * In other cases, [with xsl:apply-templates, xsl:apply-imports, and xsl:next-match, or xsl:call-template with tunnel parameters] it is a non-recoverable dynamic error if the template that is invoked declares a template parameter with required="yes" and no value for this parameter is supplied by the calling instruction.
	 */
	public static final ErrorCode XTDE0700 = new ErrorCode("XTDE0700","");

	/**
	 * It is a recoverable dynamic error if the name of a constructed attribute is xml:space and the value is not either default or preserve.
	 * 	    Action: The optional recovery action is to construct the attribute with the value as requested.
	 */
	public static final ErrorCode XTRE0795 = new ErrorCode("XTRE0795","");

    /**
	 * It is a non-recoverable dynamic error if the effective value of the name attribute [of the xsl:element instruction] is not a lexical QName.
	 */
	public static final ErrorCode XTDE0820 = new ErrorCode("XTDE0820","");

	/**
	 * In the case of an xsl:element instruction with no namespace attribute, it is a non-recoverable dynamic error if the effective value of the name attribute is a QName whose prefix is not declared in an in-scope namespace declaration for the xsl:element instruction.
	 */
	public static final ErrorCode XTDE0830 = new ErrorCode("XTDE0830","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the namespace attribute [of the xsl:element instruction] is not in the lexical space of the xs:anyURI data type.
	 */
	public static final ErrorCode XTDE0835 = new ErrorCode("XTDE0835","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the name attribute [of an xsl:attribute instruction] is not a lexical QName.
	 */
	public static final ErrorCode XTDE0850 = new ErrorCode("XTDE0850","");

	/**
	 * In the case of an xsl:attribute instruction with no namespace attribute, it is a non-recoverable dynamic error if the effective value of the name attribute is the ErrorCode xmlns.
	 */
	public static final ErrorCode XTDE0855 = new ErrorCode("XTDE0855","");

	/**
	 * In the case of an xsl:attribute instruction with no namespace attribute, it is a non-recoverable dynamic error if the effective value of the name attribute is a lexical QName whose prefix is not declared in an in-scope namespace declaration for the xsl:attribute instruction.
	 */
	public static final ErrorCode XTDE0860 = new ErrorCode("XTDE0860","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the namespace attribute [of the xsl:attribute instruction] is not in the lexical space of the xs:anyURI data type.
	 */
	public static final ErrorCode XTDE0865 = new ErrorCode("XTDE0865","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the name attribute [of the xsl:processing-instruction instruction] is not both an NCName Names and a PITarget XML.
	 */
	public static final ErrorCode XTDE0890 = new ErrorCode("XTDE0890","");

	/**
	 * It is a non-recoverable dynamic error if the ErrorCode value of the new namespace node [created using xsl:namespace] is not valid in the lexical space of the data type xs:anyURI. [see XTDE0835] 
	 */
	public static final ErrorCode XTDE0905 = new ErrorCode("XTDE0905","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the name attribute [of the xsl:namespace instruction] is neither a zero-length ErrorCode nor an NCName Names, or if it is xmlns.
	 */
	public static final ErrorCode XTDE0920 = new ErrorCode("XTDE0920","");

	/**
	 * It is a non-recoverable dynamic error if the xsl:namespace instruction generates a namespace node whose name is xml and whose ErrorCode value is not http://www.w3.org/XML/1998/namespace, or a namespace node whose ErrorCode value is http://www.w3.org/XML/1998/namespace and whose name is not xml.
	 */
	public static final ErrorCode XTDE0925 = new ErrorCode("XTDE0925","");

	/**
	 * It is a non-recoverable dynamic error if evaluating the select attribute or the contained sequence constructor of an xsl:namespace instruction results in a zero-length ErrorCode.
	 */
	public static final ErrorCode XTDE0930 = new ErrorCode("XTDE0930","");

	/**
	 * It is a non-recoverable dynamic error if any undiscarded item in the atomized sequence supplied as the value of the value attribute of xsl:number cannot be converted to an integer, or if the resulting integer is less than 0 (zero).
	 */
	public static final ErrorCode XTDE0980 = new ErrorCode("XTDE0980","");

	/**
	 * It is a non-recoverable dynamic error if, for any sort key component, the set of sort key values evaluated for all the items in the initial sequence, after any type conversion requested, contains a pair of ordinary values for which the result of the XPath lt operator is an error.
	 */
	public static final ErrorCode XTDE1030 = new ErrorCode("XTDE1030","");

	/**
	 * It is a non-recoverable dynamic error if the collation attribute of xsl:sort (after resolving against the base URI) is not a URI that is recognized by the implementation as referring to a collation.
	 */
	public static final ErrorCode XTDE1035 = new ErrorCode("XTDE1035","");

	/**
	 * It is a non-recoverable dynamic error if the collation URI specified to xsl:for-each-group (after resolving against the base URI) is a collation that is not recognized by the implementation. (For notes, [see XTDE1035].)
	 */
	public static final ErrorCode XTDE1110 = new ErrorCode("XTDE1110","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the regex attribute [of the xsl:analyze-ErrorCode instruction] does not conform to the required syntax for regular expressions, as specified in [Functions and Operators]. If the regular expression is known statically (for example, if the attribute does not contain any expressions enclosed in curly brackets) then the processor may signal the error as a static error.
	 */
	public static final ErrorCode XTDE1140 = new ErrorCode("XTDE1140","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the flags attribute [of the xsl:analyze-ErrorCode instruction] has a value other than the values defined in [Functions and Operators]. If the value of the attribute is known statically (for example, if the attribute does not contain any expressions enclosed in curly brackets) then the processor may signal the error as a static error.
	 */
	public static final ErrorCode XTDE1145 = new ErrorCode("XTDE1145","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the regex attribute [of the xsl:analyze-ErrorCode instruction] is a regular expression that matches a zero-length ErrorCode: or more specifically, if the regular expression $r and flags $f are such that matches("", $r, $f) returns true. If the regular expression is known statically (for example, if the attribute does not contain any expressions enclosed in curly brackets) then the processor may signal the error as a static error.
	 */
	public static final ErrorCode XTDE1150 = new ErrorCode("XTDE1150","");

	/**
	 * When a URI reference [supplied to the document function] contains a fragment identifier, it is a recoverable dynamic error if the media type is not one that is recognized by the processor, or if the fragment identifier does not conform to the rules for fragment identifiers for that media type, or if the fragment identifier selects something other than a sequence of nodes (for example, if it selects a range of characters within a text node).
	 * 	    Action: The optional recovery action is to ignore the fragment identifier and return the document node.
	 */
	public static final ErrorCode XTRE1160 = new ErrorCode("XTRE1160","");

    /**
	 * It is a non-recoverable dynamic error if a URI [supplied in the first argument to the unparsed-text function] contains a fragment identifier, or if it cannot be used to retrieve a resource containing text.
	 */
	public static final ErrorCode XTDE1170 = new ErrorCode("XTDE1170","");

	/**
	 * It is a non-recoverable dynamic error if a resource [retrieved using the unparsed-text function] contains octets that cannot be decoded into Unicode characters using the specified encoding, or if the resulting characters are not permitted XML characters. This includes the case where the processor does not support the requested encoding.
	 */
	public static final ErrorCode XTDE1190 = new ErrorCode("XTDE1190","");

	/**
	 * It is a non-recoverable dynamic error if the second argument of the unparsed-text function is omitted and the processor cannot infer the encoding using external information and the encoding is not UTF-8.
	 */
	public static final ErrorCode XTDE1200 = new ErrorCode("XTDE1200","");

	/**
	 * It is a non-recoverable dynamic error if the value [of the first argument to the key function] is not a valid QName, or if there is no namespace declaration in scope for the prefix of the QName, or if the name obtained by expanding the QName is not the same as the expanded name of any xsl:key declaration in the stylesheet. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1260 = new ErrorCode("XTDE1260","");

	/**
	 * It is a non-recoverable dynamic error to call the key function with two arguments if there is no context node, or if the root of the tree containing the context node is not a document node; or to call the function with three arguments if the root of the tree containing the node supplied in the third argument is not a document node.
	 */
	public static final ErrorCode XTDE1270 = new ErrorCode("XTDE1270","");

	/**
	 * It is a non-recoverable dynamic error if the name specified as the $decimal-format-name argument [ to the format-number function] is not a valid QName, or if its prefix has not been declared in an in-scope namespace declaration, or if the stylesheet does not contain a declaration of a decimal-format with a matching expanded-QName. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1280 = new ErrorCode("XTDE1280","");

	/**
	 * The picture ErrorCode [supplied to the format-number function] must conform to the following rules. [ See full specification.] It is a non-recoverable dynamic error if the picture ErrorCode does not satisfy these rules.
	 */
	public static final ErrorCode XTDE1310 = new ErrorCode("XTDE1310","");

	/**
	 * It is a non-recoverable dynamic error if the syntax of the picture [used for date/time formatting] is incorrect.
	 */
	public static final ErrorCode XTDE1340 = new ErrorCode("XTDE1340","");

	/**
	 * It is a non-recoverable dynamic error if a component specifier within the picture [used for date/time formatting] refers to components that are not available in the given type of $value, for example if the picture supplied to the format-time refers to the year, month, or day component.
	 */
	public static final ErrorCode XTDE1350 = new ErrorCode("XTDE1350","");

	/**
	 * If the current function is evaluated within an expression that is evaluated when the context item is undefined, a non-recoverable dynamic error occurs.
	 */
	public static final ErrorCode XTDE1360 = new ErrorCode("XTDE1360","");

	/**
	 * It is a non-recoverable dynamic error if the unparsed-entity-uri function is called when there is no context node, or when the root of the tree containing the context node is not a document node.
	 */
	public static final ErrorCode XTDE1370 = new ErrorCode("XTDE1370","");

	/**
	 * It is a non-recoverable dynamic error if the unparsed-entity-public-id function is called when there is no context node, or when the root of the tree containing the context node is not a document node.
	 */
	public static final ErrorCode XTDE1380 = new ErrorCode("XTDE1380","");

	/**
	 * It is a non-recoverable dynamic error if the value [supplied as the $property-name argument to the system-property function] is not a valid QName, or if there is no namespace declaration in scope for the prefix of the QName. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1390 = new ErrorCode("XTDE1390","");

	/**
	 * When a transformation is terminated by use of xsl:message terminate="yes", the effect is the same as when a non-recoverable dynamic error occurs during the transformation.
	 */
	public static final ErrorCode XTMM9000 = new ErrorCode("XTMM9000","");

	/**
	 * It is a non-recoverable dynamic error if the argument [passed to the function-available function] does not evaluate to a ErrorCode that is a valid QName, or if there is no namespace declaration in scope for the prefix of the QName. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1400 = new ErrorCode("XTDE1400","");

	/**
	 * It is a non-recoverable dynamic error if the arguments supplied to a call on an extension function do not satisfy the rules defined for that particular extension function, or if the extension function reports an error, or if the result of the extension function cannot be converted to an XPath value.
	 */
	public static final ErrorCode XTDE1420 = new ErrorCode("XTDE1420","");

	/**
	 * When backwards compatible behavior is enabled, it is a non-recoverable dynamic error to evaluate an extension function call if no implementation of the extension function is available.
	 */
	public static final ErrorCode XTDE1425 = new ErrorCode("XTDE1425","");

	/**
	 * It is a non-recoverable dynamic error if the argument [passed to the type-available function] does not evaluate to a ErrorCode that is a valid QName, or if there is no namespace declaration in scope for the prefix of the QName. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1428 = new ErrorCode("XTDE1428","");

	/**
	 * It is a non-recoverable dynamic error if the argument [passed to the element-available function] does not evaluate to a ErrorCode that is a valid QName, or if there is no namespace declaration in scope for the prefix of the QName. If the processor is able to detect the error statically (for example, when the argument is supplied as a ErrorCode literal), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1440 = new ErrorCode("XTDE1440","");

	/**
	 * When a processor performs fallback for an extension instruction that is not recognized, if the instruction element has one or more xsl:fallback children, then the content of each of the xsl:fallback children must be evaluated; it is a non-recoverable dynamic error if it has no xsl:fallback children.
	 */
	public static final ErrorCode XTDE1450 = new ErrorCode("XTDE1450","");

	/**
	 * It is a non-recoverable dynamic error if the effective value of the format attribute [of an xsl:result-document element] is not a valid lexical QName, or if it does not match the expanded-QName of an output definition in the stylesheet. If the processor is able to detect the error statically (for example, when the format attribute contains no curly brackets), then the processor may optionally signal this as a static error.
	 */
	public static final ErrorCode XTDE1460 = new ErrorCode("XTDE1460","");

	/**
	 * 	It is a non-recoverable dynamic error to evaluate the xsl:result-document instruction in temporary output state.
	 */
	public static final ErrorCode XTDE1480 = new ErrorCode("XTDE1480","");

	/**
	 * 	It is a non-recoverable dynamic error for a transformation to generate two or more final result trees with the same URI.
	 */
	public static final ErrorCode XTDE1490 = new ErrorCode("XTDE1490","");

	/**
	 * 	It is a recoverable dynamic error for a transformation to generate two or more final result trees with URIs that identify the same physical resource. The optional recovery action is implementation-dependent, since it may be impossible for the processor to detect the error.
	 */
	public static final ErrorCode XTRE1495 = new ErrorCode("XTRE1495","");

	/**
	 * 	It is a recoverable dynamic error for a stylesheet to write to an external resource and read from the same resource during a single transformation, whether or not the same URI is used to access the resource in both cases.
	 * 	    Action: The optional recovery action is implementation-dependent: implementations are not required to detect the error condition. Note that if the error is not detected, it is undefined whether the document that is read from the resource reflects its state before or after the result tree is written.
	 */
	public static final ErrorCode XTRE1500 = new ErrorCode("XTRE1500","");

    /**
	 * 	It is a recoverable dynamic error if an xsl:value-of or xsl:text instruction specifies that output escaping is to be disabled and the implementation does not support this.
	 * 	    Action: The optional recovery action is to ignore the disable-output-escaping attribute.
	 */
	public static final ErrorCode XTRE1620 = new ErrorCode("XTRE1620","");

    /**
	 * It is a recoverable dynamic error if an xsl:value-of or xsl:text instruction specifies that output escaping is to be disabled when writing to a final result tree that is not being serialized.
	 * 	    Action: The optional recovery action is to ignore the disable-output-escaping attribute. 
	 */
	public static final ErrorCode XTRE1630 = new ErrorCode("XTRE1630","");

    /**
	 * A basic XSLT processor must raise a non-recoverable dynamic error if the input to the processor includes a node with a type annotation other than xs:untyped or xs:untypedAtomic, or an atomic value of a type other than those which a basic XSLT processor supports. 
	 */
	public static final ErrorCode XTDE1665 = new ErrorCode("XTDE1665","");
    
}
