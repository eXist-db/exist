/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
package org.exist.xquery;

import org.exist.Namespaces;
import org.exist.dom.QName;

/**
 *
 * @author aretter
 */
public class ErrorCodes {
    
	/* XPath 2.0 http://www.w3.org/TR/xpath20/#id-errors */
    public static ErrorCode XPST0001 = new W3CErrorCode("XPST0001", "It is a static error if analysis of an expression relies on some component of the static context that has not been assigned a value.");
    public static ErrorCode XPDY0002 = new W3CErrorCode("XPDY0002", "It is a dynamic error if evaluation of an expression relies on some part of the dynamic context that has not been assigned a value.");
    public static ErrorCode XPST0003 = new W3CErrorCode("XPST0003", "It is a static error if an expression is not a valid instance of the grammar defined in A.1 EBNF.");
    public static ErrorCode XPTY0004 = new W3CErrorCode("XPTY0004", "It is a type error if, during the static analysis phase, an expression is found to have a static type that is not appropriate for the context in which the expression occurs, or during the dynamic evaluation phase, the dynamic type of a value does not match a required type as specified by the matching rules in 2.5.4 SequenceType Matching.");
    public static ErrorCode XPST0005 = new W3CErrorCode("XPST0005", "During the analysis phase, it is a static error if the static type assigned to an expression other than the expression () or data(()) is empty-sequence().");
    public static ErrorCode XPTY0006 = new W3CErrorCode("XPTY0006", "(Not currently used.)");
    public static ErrorCode XPTY0007 = new W3CErrorCode("XPTY0007", "(Not currently used.)");
    public static ErrorCode XPST0008 = new W3CErrorCode("XPST0008", "It is a static error if an expression refers to an element name, attribute name, schema type name, namespace prefix, or variable name that is not defined in the static context, except for an ElementName in an ElementTest or an AttributeName in an AttributeTest.");
    public static ErrorCode XPST0010 = new W3CErrorCode("XPST0010", "An implementation must raise a static error if it encounters a reference to an axis that it does not support.");
    public static ErrorCode XPST0017 = new W3CErrorCode("XPST0017", "It is a static error if the expanded QName and number of arguments in a function call do not match the name and arity of a function signature in the static context.");
    public static ErrorCode XPTY0018 = new W3CErrorCode("XPTY0018", "It is a type error if the result of the last step in a path expression contains both nodes and atomic values.");
    public static ErrorCode XPTY0019 = new W3CErrorCode("XPTY0019", "It is a type error if the result of a step (other than the last step) in a path expression contains an atomic value.");
    public static ErrorCode XPTY0020 = new W3CErrorCode("XPTY0020", "It is a type error if, in an axis step, the context item is not a node.");
    public static ErrorCode XPDY0021 = new W3CErrorCode("XPDY0021", "(Not currently used.)");
    public static ErrorCode XPDY0050 = new W3CErrorCode("XPDY0050", "It is a dynamic error if the dynamic type of the operand of a treat expression does not match the sequence type specified by the treat expression. This error might also be raised by a path expression beginning with \"/\" or \"//\" if the context node is not in a tree that is rooted at a document node. This is because a leading \"/\" or \"//\" in a path expression is an abbreviation for an initial step that includes the clause treat as document-node().");
    public static ErrorCode XPST0051 = new W3CErrorCode("XPST0051", "It is a static error if a QName that is used as an AtomicType in a SequenceType is not defined in the in-scope schema types as an atomic type.");
    public static ErrorCode XPST0080 = new W3CErrorCode("XPST0080", "It is a static error if the target type of a cast or castable expression is xs:NOTATION or xs:anyAtomicType.");
    public static ErrorCode XPST0081 = new W3CErrorCode("XPST0081", "It is a static error if a QName used in an expression contains a namespace prefix that cannot be expanded into a namespace URI by using the statically known namespaces.");
    public static ErrorCode XPST0083 = new W3CErrorCode("XPST0083", "(Not currently used.)");

    /* XQuery 1.0 http://www.w3.org/TR/xquery/#id-errors */
    public static ErrorCode XQST0009 = new W3CErrorCode("XQST0009", "An implementation that does not support the Schema Import Feature must raise a static error if a Prolog contains a schema import.");
    public static ErrorCode XQST0012 = new W3CErrorCode("XQST0012", "It is a static error if the set of definitions contained in all schemas imported by a Prolog do not satisfy the conditions for schema validity specified in Sections 3 and 5 of [XML Schema] Part 1--i.e., each definition must be valid, complete, and unique.");
    public static ErrorCode XQST0013 = new W3CErrorCode("XQST0013", "It is a static error if an implementation recognizes a pragma but determines that its content is invalid.");
    public static ErrorCode XQST0014 = new W3CErrorCode("XQST0014", "(Not currently used.)");
    public static ErrorCode XQST0015 = new W3CErrorCode("XQST0015", "(Not currently used.)");
    public static ErrorCode XQST0016 = new W3CErrorCode("XQST0016", "An implementation that does not support the Module Feature raises a static error if it encounters a module declaration or a module import.");
    public static ErrorCode XQST0022 = new W3CErrorCode("XQST0022", "It is a static error if the value of a namespace declaration attribute is not a URILiteral.");
    public static ErrorCode XQTY0023 = new W3CErrorCode("XQTY0023", "(Not currently used.)");
    public static ErrorCode XQTY0024 = new W3CErrorCode("XQTY0024", "It is a type error if the content sequence in an element constructor contains an attribute node following a node that is not an attribute node.");
    public static ErrorCode XQDY0025 = new W3CErrorCode("XQDY0025", "It is a dynamic error if any attribute of a constructed element does not have a name that is distinct from the names of all other attributes of the constructed element.");
    public static ErrorCode XQDY0026 = new W3CErrorCode("XQDY0026", "It is a dynamic error if the result of the content expression of a computed processing instruction constructor contains the string \"?>\".");
    public static ErrorCode XQDY0027 = new W3CErrorCode("XQDY0027", "In a validate expression, it is a dynamic error if the root element information item in the PSVI resulting from validation does not have the expected validity property: valid if validation mode is strict, or either valid or notKnown if validation mode is lax.");
    public static ErrorCode XQTY0028 = new W3CErrorCode("XQTY0028", "(Not currently used.)");
    public static ErrorCode XQDY0029 = new W3CErrorCode("XQDY0029", "(Not currently used.)");
    public static ErrorCode XQTY0030 = new W3CErrorCode("XQTY0030", "It is a type error if the argument of a validate expression does not evaluate to exactly one document or element node.");
    public static ErrorCode XQST0031 = new W3CErrorCode("XQST0031", "It is a static error if the version number specified in a version declaration is not supported by the implementation.");
    public static ErrorCode XQST0032 = new W3CErrorCode("XQST0032", "A static error is raised if a Prolog contains more than one base URI declaration.");
    public static ErrorCode XQST0033 = new W3CErrorCode("XQST0033", "It is a static error if a module contains multiple bindings for the same namespace prefix.");
    public static ErrorCode XQST0034 = new W3CErrorCode("XQST0034", "It is a static error if multiple functions declared or imported by a module have the number of arguments and their expanded QNames are equal (as defined by the eq operator).");
    public static ErrorCode XQST0035 = new W3CErrorCode("XQST0035", "It is a static error to import two schema components that both define the same name in the same symbol space and in the same scope.");
    public static ErrorCode XQST0036 = new W3CErrorCode("XQST0036", "It is a static error to import a module if the importing module's in-scope schema types do not include definitions for the schema type names that appear in the declarations of variables and functions (whether in an argument type or return type) that are present in the imported module and are referenced in the importing module.");
    public static ErrorCode XQST0037 = new W3CErrorCode("XQST0037", "(Not currently used.)");
    public static ErrorCode XQST0038 = new W3CErrorCode("XQST0038", "It is a static error if a Prolog contains more than one default collation declaration, or the value specified by a default collation declaration is not present in statically known collations.");
    public static ErrorCode XQST0039 = new W3CErrorCode("XQST0039", "It is a static error for a function declaration to have more than one parameter with the same name.");
    public static ErrorCode XQST0040 = new W3CErrorCode("XQST0040", "It is a static error if the attributes specified by a direct element constructor do not have distinct expanded QNames.");
    public static ErrorCode XQDY0041 = new W3CErrorCode("XQDY0041", "It is a dynamic error if the value of the name expression in a computed processing instruction constructor cannot be cast to the type xs:NCName.");
    public static ErrorCode XQST0042 = new W3CErrorCode("XQST0042", "(Not currently used.)");
    public static ErrorCode XQST0043 = new W3CErrorCode("XQST0043", "(Not currently used.)");
    public static ErrorCode XQDY0044 = new W3CErrorCode("XQDY0044", "It is a dynamic error if the node-name property of the node constructed by a computed attribute constructor is in the namespace http://www.w3.org/2000/xmlns/ (corresponding to namespace prefix xmlns), or is in no namespace and has local name xmlns.");
    public static ErrorCode XQST0045 = new W3CErrorCode("XQST0045", "It is a static error if the function name in a function declaration is in one of the following namespaces: http://www.w3.org/XML/1998/namespace, http://www.w3.org/2001/XMLSchema, http://www.w3.org/2001/XMLSchema-instance, http://www.w3.org/2005/xpath-functions.");
    public static ErrorCode XQST0046 = new W3CErrorCode("XQST0046", "An implementation MAY raise a static error if the value of a URILiteral is of nonzero length and is not in the lexical space of xs:anyURI.");
    public static ErrorCode XQST0047 = new W3CErrorCode("XQST0047", "It is a static error if multiple module imports in the same Prolog specify the same target namespace.");
    public static ErrorCode XQST0048 = new W3CErrorCode("XQST0048", "It is a static error if a function or variable declared in a library module is not in the target namespace of the library module.");
    public static ErrorCode XQST0049 = new W3CErrorCode("XQST0049", "It is a static error if two or more variables declared or imported by a module have equal expanded QNames (as defined by the eq operator.)");
    public static ErrorCode XQDY0052 = new W3CErrorCode("XQDY0052", "(Not currently used.)");
    public static ErrorCode XQST0053 = new W3CErrorCode("XQST0053", "(Not currently used.)");
    public static ErrorCode XQST0054 = new W3CErrorCode("XQST0054", "It is a static error if a variable depends on itself.");
    public static ErrorCode XQST0055 = new W3CErrorCode("XQST0055", "It is a static error if a Prolog contains more than one copy-namespaces declaration.");
    public static ErrorCode XQST0056 = new W3CErrorCode("XQST0056", "(Not currently used.)");
    public static ErrorCode XQST0057 = new W3CErrorCode("XQST0057", "It is a static error if a schema import binds a namespace prefix but does not specify a target namespace other than a zero-length string.");
    public static ErrorCode XQST0058 = new W3CErrorCode("XQST0058", "It is a static error if multiple schema imports specify the same target namespace.");
    public static ErrorCode XQST0059 = new W3CErrorCode("XQST0059", "It is a static error if an implementation is unable to process a schema or module import by finding a schema or module with the specified target namespace.");
    public static ErrorCode XQST0060 = new W3CErrorCode("XQST0060", "It is a static error if the name of a function in a function declaration is not in a namespace (expanded QName has a null namespace URI).");
    public static ErrorCode XQDY0061 = new W3CErrorCode("XQDY0061", "It is a dynamic error if the operand of a validate expression is a document node whose children do not consist of exactly one element node and zero or more comment and processing instruction nodes, in any order.");
    public static ErrorCode XQDY0062 = new W3CErrorCode("XQDY0062", "(Not currently used.)");
    public static ErrorCode XQST0063 = new W3CErrorCode("XQST0063", "(Not currently used.)");
    public static ErrorCode XQDY0064 = new W3CErrorCode("XQDY0064", "It is a dynamic error if the value of the name expression in a computed processing instruction constructor is equal to \"XML\" (in any combination of upper and lower case).");
    public static ErrorCode XQST0065 = new W3CErrorCode("XQST0065", "A static error is raised if a Prolog contains more than one ordering mode declaration.");
    public static ErrorCode XQST0066 = new W3CErrorCode("XQST0066", "A static error is raised if a Prolog contains more than one default element/type namespace declaration, or more than one default function namespace declaration.");
    public static ErrorCode XQST0067 = new W3CErrorCode("XQST0067", "A static error is raised if a Prolog contains more than one construction declaration.");
    public static ErrorCode XQST0068 = new W3CErrorCode("XQST0068", "A static error is raised if a Prolog contains more than one boundary-space declaration.");
    public static ErrorCode XQST0069 = new W3CErrorCode("XQST0069", "A static error is raised if a Prolog contains more than one empty order declaration.");
    public static ErrorCode XQST0070 = new W3CErrorCode("XQST0070", "A static error is raised if a namespace URI is bound to the predefined prefix xmlns, or if a namespace URI other than http://www.w3.org/XML/1998/namespace is bound to the prefix xml, or if the prefix xml is bound to a namespace URI other than http://www.w3.org/XML/1998/namespace.");
    public static ErrorCode XQST0071 = new W3CErrorCode("XQST0071", "A static error is raised if the namespace declaration attributes of a direct element constructor do not have distinct names.");
    public static ErrorCode XQDY0072 = new W3CErrorCode("XQDY0072", "It is a dynamic error if the result of the content expression of a computed comment constructor contains two adjacent hyphens or ends with a hyphen.");
    public static ErrorCode XQST0073 = new W3CErrorCode("XQST0073", "It is a static error if the graph of module imports contains a cycle (that is, if there exists a sequence of modules M1 ... Mn such that each Mi imports Mi+1  and Mn imports M1), unless all the modules in the cycle share a common namespace.");
    public static ErrorCode XQDY0074 = new W3CErrorCode("XQDY0074", "It is a dynamic error if the value of the name expression in a computed element or attribute constructor cannot be converted to an expanded QName (for example, because it contains a namespace prefix not found in statically known namespaces.)");
    public static ErrorCode XQST0075 = new W3CErrorCode("XQST0075", "An implementation that does not support the Validation Feature must raise a static error if it encounters a validate expression.");
    public static ErrorCode XQST0076 = new W3CErrorCode("XQST0076", "It is a static error if a collation subclause in an order by clause of a FLWOR expression does not identify a collation that is present in statically known collations.");
    public static ErrorCode XQST0077 = new W3CErrorCode("XQST0077", "(Not currently used.)");
    public static ErrorCode XQST0078 = new W3CErrorCode("XQST0078", "(Not currently used.)");
    public static ErrorCode XQST0079 = new W3CErrorCode("XQST0079", "It is a static error if an extension expression contains neither a pragma that is recognized by the implementation nor an expression enclosed in curly braces.");
    public static ErrorCode XQST0082 = new W3CErrorCode("XQST0082", "(Not currently used.)");
    public static ErrorCode XQDY0084 = new W3CErrorCode("XQDY0084", "It is a dynamic error if the element validated by a validate statement does not have a top-level element declaration in the in-scope element declarations, if validation mode is strict.");
    public static ErrorCode XQST0085 = new W3CErrorCode("XQST0085", "It is a static error if the namespace URI in a namespace declaration attribute is a zero-length string, and the implementation does not support [XML Names 1.1].");
    public static ErrorCode XQTY0086 = new W3CErrorCode("XQTY0086", "It is a type error if the typed value of a copied element or attribute node is namespace-sensitive when construction mode is preserve and copy-namespaces mode is no-preserve.");
    public static ErrorCode XQST0087 = new W3CErrorCode("XQST0087", "It is a static error if the encoding specified in a Version Declaration does not conform to the definition of EncName specified in [XML 1.0].");
    public static ErrorCode XQST0088 = new W3CErrorCode("XQST0088", "It is a static    error if the literal that specifies the target namespace in a module import or a module declaration is of zero length.");
    public static ErrorCode XQST0089 = new W3CErrorCode("XQST0089", "It is a static error if a variable bound in a for clause of a FLWOR expression, and its associated positional variable, do not have distinct names (expanded QNames).");
    public static ErrorCode XQST0090 = new W3CErrorCode("XQST0090", "It is a static error if a character reference does not identify a valid character in the version of XML that is in use.");
    public static ErrorCode XQDY0091 = new W3CErrorCode("XQDY0091", "An implementation MAY raise a dynamic error if an xml:id error, as defined in [XML ID], is encountered during construction of an attribute named xml:id.");
    public static ErrorCode XQDY0092 = new W3CErrorCode("XQDY0092", "An implementation MAY raise a dynamic error  if a constructed attribute named xml:space has a value other than preserve or default.");
    public static ErrorCode XQST0093 = new W3CErrorCode("XQST0093", "It is a static error to import a module M1 if there exists a sequence of modules M1 ... Mi ... M1 such that each module directly depends on the next module in the sequence (informally, if M1 depends on itself through some chain of module dependencies.)");
    public static ErrorCode XQDY0101 = new W3CErrorCode("XQDY0101", "An error is raised if a computed namespace constructor attempts to do any of the following:\n" +
            "Bind the prefix xml to some namespace URI other than http://www.w3.org/XML/1998/namespace.\n" +
            "Bind a prefix other than xml to the namespace URI http://www.w3.org/XML/1998/namespace.\n" +
            "Bind the prefix xmlns to any namespace URI.\n" +
            "Bind a prefix to the namespace URI http://www.w3.org/2000/xmlns/.\n" +
            "Bind any prefix (including the empty prefix) to a zero-length namespace URI.");

    /* XQuery 1.0 and XPath 2.0 Functions and Operators http://www.w3.org/TR/xpath-functions/#error-summary */
    public static ErrorCode FOER0000 = new W3CErrorCode("FOER0000", "Unidentified error.");
    public static ErrorCode FOAR0001 = new W3CErrorCode("FOAR0001", "Division by zero.");
    public static ErrorCode FOAR0002 = new W3CErrorCode("FOAR0002", "Numeric operation overflow/underflow.");
    public static ErrorCode FOCA0001 = new W3CErrorCode("FOCA0001", "Input value too large for decimal.");
    public static ErrorCode FOCA0002 = new W3CErrorCode("FOCA0002", "Invalid lexical value.");
    public static ErrorCode FOCA0003 = new W3CErrorCode("FOCA0003", "Input value too large for integer.");
    public static ErrorCode FOCA0005 = new W3CErrorCode("FOCA0005", "NaN supplied as float/double value.");
    public static ErrorCode FOCA0006 = new W3CErrorCode("FOCA0006", "String to be cast to decimal has too many digits of precision.");
    public static ErrorCode FOCH0001 = new W3CErrorCode("FOCH0001", "Code point not valid.");
    public static ErrorCode FOCH0002 = new W3CErrorCode("FOCH0002", "Unsupported collation.");
    public static ErrorCode FOCH0003 = new W3CErrorCode("FOCH0003", "Unsupported normalization form.");
    public static ErrorCode FOCH0004 = new W3CErrorCode("FOCH0004", "Collation does not support collation units.");
    public static ErrorCode FODC0001 = new W3CErrorCode("FODC0001", "No context document.");
    public static ErrorCode FODC0002 = new W3CErrorCode("FODC0002", "Error retrieving resource.");
    public static ErrorCode FODC0003 = new W3CErrorCode("FODC0003", "Function stability not defined.");
    public static ErrorCode FODC0004 = new W3CErrorCode("FODC0004", "Invalid argument to fn:collection.");
    public static ErrorCode FODC0005 = new W3CErrorCode("FODC0005", "Invalid argument to fn:doc or fn:doc-available.");
    public static ErrorCode FODT0001 = new W3CErrorCode("FODT0001", "Overflow/underflow in date/time operation.");
    public static ErrorCode FODT0002 = new W3CErrorCode("FODT0002", "Overflow/underflow in duration operation.");
    public static ErrorCode FODT0003 = new W3CErrorCode("FODT0003", "Invalid timezone value.");
    public static ErrorCode FONS0004 = new W3CErrorCode("FONS0004", "No namespace found for prefix.");
    public static ErrorCode FONS0005 = new W3CErrorCode("FONS0005", "Base-uri not defined in the static context.");
    public static ErrorCode FORG0001 = new W3CErrorCode("FORG0001", "Invalid value for cast/constructor.");
    public static ErrorCode FORG0002 = new W3CErrorCode("FORG0002", "Invalid argument to fn:resolve-uri().");
    public static ErrorCode FORG0003 = new W3CErrorCode("FORG0003", "fn:zero-or-one called with a sequence containing more than one item.");
    public static ErrorCode FORG0004 = new W3CErrorCode("FORG0004", "fn:one-or-more called with a sequence containing no items.");
    public static ErrorCode FORG0005 = new W3CErrorCode("FORG0005", "fn:exactly-one called with a sequence containing zero or more than one item.");
    public static ErrorCode FORG0006 = new W3CErrorCode("FORG0006", "Invalid argument type.");
    public static ErrorCode FORG0008 = new W3CErrorCode("FORG0008", "Both arguments to fn:dateTime have a specified timezone.");
    public static ErrorCode FORG0009 = new W3CErrorCode("FORG0009", "Error in resolving a relative URI against a base URI in fn:resolve-uri.");
    public static ErrorCode FORX0001 = new W3CErrorCode("FORX0001", "Invalid regular expression. flags");
    public static ErrorCode FORX0002 = new W3CErrorCode("FORX0002", "Invalid regular expression.");
    public static ErrorCode FORX0003 = new W3CErrorCode("FORX0003", "Regular expression matches zero-length string.");
    public static ErrorCode FORX0004 = new W3CErrorCode("FORX0004", "Invalid replacement string.");
    public static ErrorCode FOTY0012 = new W3CErrorCode("FOTY0012", "Argument node does not have a typed value.");
    public static ErrorCode FOTY0013 = new W3CErrorCode("FOTY0013", "The argument to fn:data() contains a function item.");

    /* XSLT 2.0 and XQuery 1.0 Serialization http://www.w3.org/TR/xslt-xquery-serialization/#serial-err */
    public static ErrorCode SENR0001 = new W3CErrorCode("SENR0001", "It is an error if an item in S6 in sequence normalization is an attribute node or a namespace node.");
    public static ErrorCode SERE0003 = new W3CErrorCode("SERE0003", "It is an error if the serializer is unable to satisfy the rules for either a well-formed XML document entity or a well-formed XML external general parsed entity, or both, except for content modified by the character expansion phase of serialization.");
    public static ErrorCode SEPM0004 = new W3CErrorCode("SEPM0004", "It is an error to specify the doctype-system parameter, or to specify the standalone parameter with a value other than omit, if the instance of the data model contains text nodes or multiple element nodes as children of the root node.");
    public static ErrorCode SERE0005 = new W3CErrorCode("SERE0005", "It is an error if the serialized result would contain an NCName Names that contains a character that is not permitted by the version of Namespaces in XML specified by the version parameter.");
    public static ErrorCode SERE0006 = new W3CErrorCode("SERE0006", "It is an error if the serialized result would contain a character that is not permitted by the version of XML specified by the version parameter.");
    public static ErrorCode SESU0007 = new W3CErrorCode("SESU0007", "It is an error if an output encoding other than UTF-8 or UTF-16 is requested and the serializer does not support that encoding.");
    public static ErrorCode SERE0008 = new W3CErrorCode("SERE0008", "It is an error if a character that cannot be represented in the encoding that the serializer is using for output appears in a context where character references are not allowed (for example if the character occurs in the name of an element).");
    public static ErrorCode SEPM0009 = new W3CErrorCode("SEPM0009", "It is an error if the omit-xml-declaration parameter has the value yes, and the standalone attribute has a value other than omit; or the version parameter has a value other than 1.0 and the doctype-system parameter is specified.");
    public static ErrorCode SEPM0010 = new W3CErrorCode("SEPM0010", "It is an error if the output method is xml, the value of the undeclare-prefixes parameter is yes, and the value of the version parameter is 1.0.");
    public static ErrorCode SESU0011 = new W3CErrorCode("SESU0011", "It is an error if the value of the normalization-form parameter specifies a normalization form that is not supported by the serializer.");
    public static ErrorCode SERE0012 = new W3CErrorCode("SERE0012", "It is an error if the value of the normalization-form parameter is fully-normalized and any relevant construct of the result begins with a combining character.");
    public static ErrorCode SESU0013 = new W3CErrorCode("SESU0013", "It is an error if the serializer does not support the version of XML or HTML specified by the version parameter.");
    public static ErrorCode SERE0014 = new W3CErrorCode("SERE0014", "It is an error to use the HTML output method when characters which are legal in XML but not in HTML, specifically the control characters #x7F-#x9F, appear in the instance of the data model.");
    public static ErrorCode SERE0015 = new W3CErrorCode("SERE0015", "It is an error to use the HTML output method when > appears within a processing instruction in the data model instance being serialized.");
    public static ErrorCode SEPM0016 = new W3CErrorCode("SEPM0016", "It is a an error if a parameter value is invalid for the defined domain.");

    /* XQuery 3.0 functions and operators */
    public static ErrorCode FOFD1340 = new W3CErrorCode("FOFD1340", "Invalid date/time formatting picture string");
    public static ErrorCode FOFD1350 = new W3CErrorCode("FOFD1350", " Invalid date/time formatting component");

    public static final ErrorCode XTDE1310 = new W3CErrorCode("XTDE1310", " Invalid decimal format picture string.");
	public static final ErrorCode FTDY0020 = new W3CErrorCode("FTDY0020", "");
	
	public static final ErrorCode FODC0006 = new W3CErrorCode("FODC0006", "String passed to fn:parse-xml is not a well-formed XML document.");
	
	
    
    /* eXist specific XQuery and XPath errors
     *
     * Codes have the format [EX][XQ|XP][DY|SE|ST][nnnn]
     *
     * EX = eXist
     * XQ = XQuery
     * XP = XPath
     * DY = Dynamic
     * SE = Serialization
     * ST = Static
     * nnnn = number
     */
    public static ErrorCode EXXQDY0001 = new EXistErrorCode("EXXQDY0001", "Index cannot be applied to the given expression.");
    public static ErrorCode EXXQDY0002 = new EXistErrorCode("EXXQDY0002", "Error parsing XML.");
    public static ErrorCode EXXQDY0003 = new EXistErrorCode("EXXQDY0003", "Only Supported for xquery version \"3.0\" and later.");
    
    public static ErrorCode ERROR = new EXistErrorCode("ERROR", "Error.");
    
    public static class ErrorCode {

        private final QName errorQName;
        private final String description;

        public ErrorCode(String code, String description) {
            this.errorQName = new QName(code, Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX);
            this.description = description;
        }

        public ErrorCode(QName errorQName, String description) {
            this.errorQName = errorQName;
            this.description = description;
        }

        public QName getErrorQName() {
            return errorQName;
        }

        @Override
        public String toString() {
            return "(" + errorQName.getNamespaceURI() + "#" + errorQName.getLocalName() + "):" + description;
        }

        public String getDescription(){
            return description;
        }
    }

    public static class W3CErrorCode extends ErrorCode {

        private W3CErrorCode(String code, String description) {
            super(new QName(code, Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX), description);
        }
    }

    public static class EXistErrorCode extends ErrorCode {

        private EXistErrorCode(String code, String description) {
            super(new QName(code, Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX), description);
        }
    }

    public static class JavaErrorCode extends ErrorCode {

        public JavaErrorCode(Throwable throwable) {
            super(new QName(
                        throwable.getClass().getName(),
                        Namespaces.EXIST_XQUERY_XPATH_ERROR_NS,
                        "java" ), // Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX
                  throwable.getMessage());
        }
    }
}
