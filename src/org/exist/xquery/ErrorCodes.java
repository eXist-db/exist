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
    public static ErrorCode XPST0001 = new ErrorCode("XPST0001", "It is a static error if analysis of an expression relies on some component of the static context that has not been assigned a value.");
    public static ErrorCode XPDY0002 = new ErrorCode("XPDY0002", "It is a dynamic error if evaluation of an expression relies on some part of the dynamic context that has not been assigned a value.");
    public static ErrorCode XPST0003 = new ErrorCode("XPST0003", "It is a static error if an expression is not a valid instance of the grammar defined in A.1 EBNF.");
    public static ErrorCode XPTY0004 = new ErrorCode("XPTY0004", "It is a type error if, during the static analysis phase, an expression is found to have a static type that is not appropriate for the context in which the expression occurs, or during the dynamic evaluation phase, the dynamic type of a value does not match a required type as specified by the matching rules in 2.5.4 SequenceType Matching.");
    public static ErrorCode XPST0005 = new ErrorCode("XPST0005", "During the analysis phase, it is a static error if the static type assigned to an expression other than the expression () or data(()) is empty-sequence().");
    public static ErrorCode XPTY0006 = new ErrorCode("XPTY0006", "(Not currently used.)");
    public static ErrorCode XPTY0007 = new ErrorCode("XPTY0007", "(Not currently used.)");
    public static ErrorCode XPST0008 = new ErrorCode("XPST0008", "It is a static error if an expression refers to an element name, attribute name, schema type name, namespace prefix, or variable name that is not defined in the static context, except for an ElementName in an ElementTest or an AttributeName in an AttributeTest.");
    public static ErrorCode XPST0010 = new ErrorCode("XPST0010", "An implementation must raise a static error if it encounters a reference to an axis that it does not support.");
    public static ErrorCode XPST0017 = new ErrorCode("XPST0017", "It is a static error if the expanded QName and number of arguments in a function call do not match the name and arity of a function signature in the static context.");
    public static ErrorCode XPTY0018 = new ErrorCode("XPTY0018", "It is a type error if the result of the last step in a path expression contains both nodes and atomic values.");
    public static ErrorCode XPTY0019 = new ErrorCode("XPTY0019", "It is a type error if the result of a step (other than the last step) in a path expression contains an atomic value.");
    public static ErrorCode XPTY0020 = new ErrorCode("XPTY0020", "It is a type error if, in an axis step, the context item is not a node.");
    public static ErrorCode XPDY0021 = new ErrorCode("XPDY0021", "(Not currently used.)");
    public static ErrorCode XPDY0050 = new ErrorCode("XPDY0050", "It is a dynamic error if the dynamic type of the operand of a treat expression does not match the sequence type specified by the treat expression. This error might also be raised by a path expression beginning with \"/\" or \"//\" if the context node is not in a tree that is rooted at a document node. This is because a leading \"/\" or \"//\" in a path expression is an abbreviation for an initial step that includes the clause treat as document-node().");
    public static ErrorCode XPST0051 = new ErrorCode("XPST0051", "It is a static error if a QName that is used as an AtomicType in a SequenceType is not defined in the in-scope schema types as an atomic type.");
    public static ErrorCode XPST0080 = new ErrorCode("XPST0080", "It is a static error if the target type of a cast or castable expression is xs:NOTATION or xs:anyAtomicType.");
    public static ErrorCode XPST0081 = new ErrorCode("XPST0081", "It is a static error if a QName used in an expression contains a namespace prefix that cannot be expanded into a namespace URI by using the statically known namespaces.");
    public static ErrorCode XPST0083 = new ErrorCode("XPST0083", "(Not currently used.)");

    
    /* XQuery 1.0 http://www.w3.org/TR/xquery/#id-errors */
    public static ErrorCode XQST0009 = new ErrorCode("XQST0009", "An implementation that does not support the Schema Import Feature must raise a static error if a Prolog contains a schema import.");
    public static ErrorCode XQST0012 = new ErrorCode("XQST0012", "It is a static error if the set of definitions contained in all schemas imported by a Prolog do not satisfy the conditions for schema validity specified in Sections 3 and 5 of [XML Schema] Part 1--i.e., each definition must be valid, complete, and unique.");
    public static ErrorCode XQST0013 = new ErrorCode("XQST0013", "It is a static error if an implementation recognizes a pragma but determines that its content is invalid.");
    public static ErrorCode XQST0014 = new ErrorCode("XQST0014", "(Not currently used.)");
    public static ErrorCode XQST0015 = new ErrorCode("XQST0015", "(Not currently used.)");
    public static ErrorCode XQST0016 = new ErrorCode("XQST0016", "An implementation that does not support the Module Feature raises a static error if it encounters a module declaration or a module import.");
    public static ErrorCode XQST0022 = new ErrorCode("XQST0022", "It is a static error if the value of a namespace declaration attribute is not a URILiteral.");
    public static ErrorCode XQTY0023 = new ErrorCode("XQTY0023", "(Not currently used.)");
    public static ErrorCode XQTY0024 = new ErrorCode("XQTY0024", "It is a type error if the content sequence in an element constructor contains an attribute node following a node that is not an attribute node.");
    public static ErrorCode XQDY0025 = new ErrorCode("XQDY0025", "It is a dynamic error if any attribute of a constructed element does not have a name that is distinct from the names of all other attributes of the constructed element.");
    public static ErrorCode XQDY0026 = new ErrorCode("XQDY0026", "It is a dynamic error if the result of the content expression of a computed processing instruction constructor contains the string \"?>\".");
    public static ErrorCode XQDY0027 = new ErrorCode("XQDY0027", "In a validate expression, it is a dynamic error if the root element information item in the PSVI resulting from validation does not have the expected validity property: valid if validation mode is strict, or either valid or notKnown if validation mode is lax.");
    public static ErrorCode XQTY0028 = new ErrorCode("XQTY0028", "(Not currently used.)");
    public static ErrorCode XQDY0029 = new ErrorCode("XQDY0029", "(Not currently used.)");
    public static ErrorCode XQTY0030 = new ErrorCode("XQTY0030", "It is a type error if the argument of a validate expression does not evaluate to exactly one document or element node.");
    public static ErrorCode XQST0031 = new ErrorCode("XQST0031", "It is a static error if the version number specified in a version declaration is not supported by the implementation.");
    public static ErrorCode XQST0032 = new ErrorCode("XQST0032", "A static error is raised if a Prolog contains more than one base URI declaration.");
    public static ErrorCode XQST0033 = new ErrorCode("XQST0033", "It is a static error if a module contains multiple bindings for the same namespace prefix.");
    public static ErrorCode XQST0034 = new ErrorCode("XQST0034", "It is a static error if multiple functions declared or imported by a module have the number of arguments and their expanded QNames are equal (as defined by the eq operator).");
    public static ErrorCode XQST0035 = new ErrorCode("XQST0035", "It is a static error to import two schema components that both define the same name in the same symbol space and in the same scope.");
    public static ErrorCode XQST0036 = new ErrorCode("XQST0036", "It is a static error to import a module if the importing module's in-scope schema types do not include definitions for the schema type names that appear in the declarations of variables and functions (whether in an argument type or return type) that are present in the imported module and are referenced in the importing module.");
    public static ErrorCode XQST0037 = new ErrorCode("XQST0037", "(Not currently used.)");
    public static ErrorCode XQST0038 = new ErrorCode("XQST0038", "It is a static error if a Prolog contains more than one default collation declaration, or the value specified by a default collation declaration is not present in statically known collations.");
    public static ErrorCode XQST0039 = new ErrorCode("XQST0039", "It is a static error for a function declaration to have more than one parameter with the same name.");
    public static ErrorCode XQST0040 = new ErrorCode("XQST0040", "It is a static error if the attributes specified by a direct element constructor do not have distinct expanded QNames.");
    public static ErrorCode XQDY0041 = new ErrorCode("XQDY0041", "It is a dynamic error if the value of the name expression in a computed processing instruction constructor cannot be cast to the type xs:NCName.");
    public static ErrorCode XQST0042 = new ErrorCode("XQST0042", "(Not currently used.)");
    public static ErrorCode XQST0043 = new ErrorCode("XQST0043", "(Not currently used.)");
    public static ErrorCode XQDY0044 = new ErrorCode("XQDY0044", "It is a dynamic error if the node-name property of the node constructed by a computed attribute constructor is in the namespace http://www.w3.org/2000/xmlns/ (corresponding to namespace prefix xmlns), or is in no namespace and has local name xmlns.");
    public static ErrorCode XQST0045 = new ErrorCode("XQST0045", "It is a static error if the function name in a function declaration is in one of the following namespaces: http://www.w3.org/XML/1998/namespace, http://www.w3.org/2001/XMLSchema, http://www.w3.org/2001/XMLSchema-instance, http://www.w3.org/2005/xpath-functions.");
    public static ErrorCode XQST0046 = new ErrorCode("XQST0046", "An implementation MAY raise a static error if the value of a URILiteral is of nonzero length and is not in the lexical space of xs:anyURI.");
    public static ErrorCode XQST0047 = new ErrorCode("XQST0047", "It is a static error if multiple module imports in the same Prolog specify the same target namespace.");
    public static ErrorCode XQST0048 = new ErrorCode("XQST0048", "It is a static error if a function or variable declared in a library module is not in the target namespace of the library module.");
    public static ErrorCode XQST0049 = new ErrorCode("XQST0049", "It is a static error if two or more variables declared or imported by a module have equal expanded QNames (as defined by the eq operator.)");
    public static ErrorCode XQDY0052 = new ErrorCode("XQDY0052", "(Not currently used.)");
    public static ErrorCode XQST0053 = new ErrorCode("XQST0053", "(Not currently used.)");
    public static ErrorCode XQST0054 = new ErrorCode("XQST0054", "It is a static error if a variable depends on itself.");
    public static ErrorCode XQST0055 = new ErrorCode("XQST0055", "It is a static error if a Prolog contains more than one copy-namespaces declaration.");
    public static ErrorCode XQST0056 = new ErrorCode("XQST0056", "(Not currently used.)");
    public static ErrorCode XQST0057 = new ErrorCode("XQST0057", "It is a static error if a schema import binds a namespace prefix but does not specify a target namespace other than a zero-length string.");
    public static ErrorCode XQST0058 = new ErrorCode("XQST0058", "It is a static error if multiple schema imports specify the same target namespace.");
    public static ErrorCode XQST0059 = new ErrorCode("XQST0059", "It is a static error if an implementation is unable to process a schema or module import by finding a schema or module with the specified target namespace.");
    public static ErrorCode XQST0060 = new ErrorCode("XQST0060", "It is a static error if the name of a function in a function declaration is not in a namespace (expanded QName has a null namespace URI).");
    public static ErrorCode XQDY0061 = new ErrorCode("XQDY0061", "It is a dynamic error if the operand of a validate expression is a document node whose children do not consist of exactly one element node and zero or more comment and processing instruction nodes, in any order.");
    public static ErrorCode XQDY0062 = new ErrorCode("XQDY0062", "(Not currently used.)");
    public static ErrorCode XQST0063 = new ErrorCode("XQST0063", "(Not currently used.)");
    public static ErrorCode XQDY0064 = new ErrorCode("XQDY0064", "It is a dynamic error if the value of the name expression in a computed processing instruction constructor is equal to \"XML\" (in any combination of upper and lower case).");
    public static ErrorCode XQST0065 = new ErrorCode("XQST0065", "A static error is raised if a Prolog contains more than one ordering mode declaration.");
    public static ErrorCode XQST0066 = new ErrorCode("XQST0066", "A static error is raised if a Prolog contains more than one default element/type namespace declaration, or more than one default function namespace declaration.");
    public static ErrorCode XQST0067 = new ErrorCode("XQST0067", "A static error is raised if a Prolog contains more than one construction declaration.");
    public static ErrorCode XQST0068 = new ErrorCode("XQST0068", "A static error is raised if a Prolog contains more than one boundary-space declaration.");
    public static ErrorCode XQST0069 = new ErrorCode("XQST0069", "A static error is raised if a Prolog contains more than one empty order declaration.");
    public static ErrorCode XQST0070 = new ErrorCode("XQST0070", "A static error is raised if a namespace URI is bound to the predefined prefix xmlns, or if a namespace URI other than http://www.w3.org/XML/1998/namespace is bound to the prefix xml, or if the prefix xml is bound to a namespace URI other than http://www.w3.org/XML/1998/namespace.");
    public static ErrorCode XQST0071 = new ErrorCode("XQST0071", "A static error is raised if the namespace declaration attributes of a direct element constructor do not have distinct names.");
    public static ErrorCode XQDY0072 = new ErrorCode("XQDY0072", "It is a dynamic error if the result of the content expression of a computed comment constructor contains two adjacent hyphens or ends with a hyphen.");
    public static ErrorCode XQST0073 = new ErrorCode("XQST0073", "It is a static error if the graph of module imports contains a cycle (that is, if there exists a sequence of modules M1 ... Mn such that each Mi imports Mi+1  and Mn imports M1), unless all the modules in the cycle share a common namespace.");
    public static ErrorCode XQDY0074 = new ErrorCode("XQDY0074", "It is a dynamic error if the value of the name expression in a computed element or attribute constructor cannot be converted to an expanded QName (for example, because it contains a namespace prefix not found in statically known namespaces.)");
    public static ErrorCode XQST0075 = new ErrorCode("XQST0075", "An implementation that does not support the Validation Feature must raise a static error if it encounters a validate expression.");
    public static ErrorCode XQST0076 = new ErrorCode("XQST0076", "It is a static error if a collation subclause in an order by clause of a FLWOR expression does not identify a collation that is present in statically known collations.");
    public static ErrorCode XQST0077 = new ErrorCode("XQST0077", "(Not currently used.)");
    public static ErrorCode XQST0078 = new ErrorCode("XQST0078", "(Not currently used.)");
    public static ErrorCode XQST0079 = new ErrorCode("XQST0079", "It is a static error if an extension expression contains neither a pragma that is recognized by the implementation nor an expression enclosed in curly braces.");
    public static ErrorCode XQST0082 = new ErrorCode("XQST0082", "(Not currently used.)");
    public static ErrorCode XQDY0084 = new ErrorCode("XQDY0084", "It is a dynamic error if the element validated by a validate statement does not have a top-level element declaration in the in-scope element declarations, if validation mode is strict.");
    public static ErrorCode XQST0085 = new ErrorCode("XQST0085", "It is a static error if the namespace URI in a namespace declaration attribute is a zero-length string, and the implementation does not support [XML Names 1.1].");
    public static ErrorCode XQTY0086 = new ErrorCode("XQTY0086", "It is a type error if the typed value of a copied element or attribute node is namespace-sensitive when construction mode is preserve and copy-namespaces mode is no-preserve.");
    public static ErrorCode XQST0087 = new ErrorCode("XQST0087", "It is a static error if the encoding specified in a Version Declaration does not conform to the definition of EncName specified in [XML 1.0].");
    public static ErrorCode XQST0088 = new ErrorCode("XQST0088", "It is a static    error if the literal that specifies the target namespace in a module import or a module declaration is of zero length.");
    public static ErrorCode XQST0089 = new ErrorCode("XQST0089", "It is a static error if a variable bound in a for clause of a FLWOR expression, and its associated positional variable, do not have distinct names (expanded QNames).");
    public static ErrorCode XQST0090 = new ErrorCode("XQST0090", "It is a static error if a character reference does not identify a valid character in the version of XML that is in use.");
    public static ErrorCode XQDY0091 = new ErrorCode("XQDY0091", "An implementation MAY raise a dynamic error if an xml:id error, as defined in [XML ID], is encountered during construction of an attribute named xml:id.");
    public static ErrorCode XQDY0092 = new ErrorCode("XQDY0092", "An implementation MAY raise a dynamic error  if a constructed attribute named xml:space has a value other than preserve or default.");
    public static ErrorCode XQST0093 = new ErrorCode("XQST0093", "It is a static error to import a module M1 if there exists a sequence of modules M1 ... Mi ... M1 such that each module directly depends on the next module in the sequence (informally, if M1 depends on itself through some chain of module dependencies.)");


    /* XQuery 1.0 and XPath 2.0 Functions and Operators http://www.w3.org/TR/xpath-functions/#error-summary */
    public static ErrorCode FOER0000 = new ErrorCode("FOER0000", "Unidentified error.");
    public static ErrorCode FOAR0001 = new ErrorCode("FOAR0001", "Division by zero.");
    public static ErrorCode FOAR0002 = new ErrorCode("FOAR0002", "Numeric operation overflow/underflow.");
    public static ErrorCode FOCA0001 = new ErrorCode("FOCA0001", "Input value too large for decimal.");
    public static ErrorCode FOCA0002 = new ErrorCode("FOCA0002", "Invalid lexical value.");
    public static ErrorCode FOCA0003 = new ErrorCode("FOCA0003", "Input value too large for integer.");
    public static ErrorCode FOCA0005 = new ErrorCode("FOCA0005", "NaN supplied as float/double value.");
    public static ErrorCode FOCA0006 = new ErrorCode("FOCA0006", "String to be cast to decimal has too many digits of precision.");
    public static ErrorCode FOCH0001 = new ErrorCode("FOCH0001", "Code point not valid.");
    public static ErrorCode FOCH0002 = new ErrorCode("FOCH0002", "Unsupported collation.");
    public static ErrorCode FOCH0003 = new ErrorCode("FOCH0003", "Unsupported normalization form.");
    public static ErrorCode FOCH0004 = new ErrorCode("FOCH0004", "Collation does not support collation units.");
    public static ErrorCode FODC0001 = new ErrorCode("FODC0001", "No context document.");
    public static ErrorCode FODC0002 = new ErrorCode("FODC0002", "Error retrieving resource.");
    public static ErrorCode FODC0003 = new ErrorCode("FODC0003", "Function stability not defined.");
    public static ErrorCode FODC0004 = new ErrorCode("FODC0004", "Invalid argument to fn:collection.");
    public static ErrorCode FODC0005 = new ErrorCode("FODC0005", "Invalid argument to fn:doc or fn:doc-available.");
    public static ErrorCode FODT0001 = new ErrorCode("FODT0001", "Overflow/underflow in date/time operation.");
    public static ErrorCode FODT0002 = new ErrorCode("FODT0002", "Overflow/underflow in duration operation.");
    public static ErrorCode FODT0003 = new ErrorCode("FODT0003", "Invalid timezone value.");
    public static ErrorCode FONS0004 = new ErrorCode("FONS0004", "No namespace found for prefix.");
    public static ErrorCode FONS0005 = new ErrorCode("FONS0005", "Base-uri not defined in the static context.");
    public static ErrorCode FORG0001 = new ErrorCode("FORG0001", "Invalid value for cast/constructor.");
    public static ErrorCode FORG0002 = new ErrorCode("FORG0002", "Invalid argument to fn:resolve-uri().");
    public static ErrorCode FORG0003 = new ErrorCode("FORG0003", "fn:zero-or-one called with a sequence containing more than one item.");
    public static ErrorCode FORG0004 = new ErrorCode("FORG0004", "fn:one-or-more called with a sequence containing no items.");
    public static ErrorCode FORG0005 = new ErrorCode("FORG0005", "fn:exactly-one called with a sequence containing zero or more than one item.");
    public static ErrorCode FORG0006 = new ErrorCode("FORG0006", "Invalid argument type.");
    public static ErrorCode FORG0008 = new ErrorCode("FORG0008", "Both arguments to fn:dateTime have a specified timezone.");
    public static ErrorCode FORG0009 = new ErrorCode("FORG0009", "Error in resolving a relative URI against a base URI in fn:resolve-uri.");
    public static ErrorCode FORX0001 = new ErrorCode("FORX0001", "Invalid regular expression. flags");
    public static ErrorCode FORX0002 = new ErrorCode("FORX0002", "Invalid regular expression.");
    public static ErrorCode FORX0003 = new ErrorCode("FORX0003", "Regular expression matches zero-length string.");
    public static ErrorCode FORX0004 = new ErrorCode("FORX0004", "Invalid replacement string.");
    public static ErrorCode FOTY0012 = new ErrorCode("FOTY0012", "Argument node does not have a typed value.");


    public static class ErrorCode {

        private final QName errorQName;
        private final String description;

        public ErrorCode(String code, String description) {
            this.errorQName = new QName(code, Namespaces.XQUERY_XPATH_ERROR_NS, Namespaces.XQUERY_XPATH_ERROR_PREFIX);
            this.description = description;
        }

        public ErrorCode(QName errorQName, String description) {
            this.errorQName = errorQName;
            this.description = description;
        }

        @Override
        public String toString() {
            return "(" + errorQName.getNamespaceURI() + "#" + errorQName.getLocalName() + "):" + description;
        }
    }
}
