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
header {
    package org.exist.xquery.parser;

    import antlr.debug.misc.*;
    import java.io.StringReader;
    import java.io.BufferedReader;
    import java.io.InputStreamReader;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Iterator;
    import java.util.Map;
    import java.util.Set;
    import java.util.TreeSet;
    import java.util.HashMap;
    import java.util.HashSet;
    import java.util.Stack;
    import javax.xml.XMLConstants;
    import org.apache.xerces.util.XMLChar;
    import org.exist.storage.BrokerPool;
    import org.exist.storage.DBBroker;
    import org.exist.EXistException;
    import org.exist.Namespaces;
    import org.exist.dom.persistent.DocumentSet;
    import org.exist.dom.persistent.DocumentImpl;
    import org.exist.dom.QName;
    import org.exist.dom.QName.IllegalQNameException;
    import org.exist.security.PermissionDeniedException;
    import org.exist.xquery.*;
    import org.exist.xquery.Constants.ArithmeticOperator;
    import org.exist.xquery.Constants.Comparison;
    import org.exist.xquery.Constants.NodeComparisonOperator;
    import org.exist.xquery.value.*;
    import org.exist.xquery.functions.fn.*;
    import org.exist.xquery.update.*;
    import org.exist.xquery.xquf.*;
    import org.exist.storage.ElementValue;
    import org.exist.xquery.functions.map.MapExpr;
    import org.exist.xquery.functions.array.ArrayConstructor;
    import org.exist.xquery.ft.*;

    import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
}

/**
 * The tree parser: walks the AST created by {@link XQueryParser} and generates
 * an internal representation of the query in the form of XQuery expression objects.
 */
class XQueryTreeParser extends TreeParser;

options {
    importVocab=XQuery;
    k= 1;
    defaultErrorHandler = false;
    ASTLabelType = org.exist.xquery.parser.XQueryAST;
}

{
    private XQueryContext staticContext;
    private XQueryContext context;
    private ExternalModule myModule = null;
    protected List<Exception> exceptions = new ArrayList<>(2);
    protected boolean foundError = false;
    protected Map<String, String> declaredNamespaces = new HashMap<>();
    protected Set<QName> declaredGlobalVars = new TreeSet<>();
    protected Set<String> importedModules = new HashSet<>();
    protected Set<String> importedModuleFunctions = null;
    protected Set<QName> importedModuleVariables = null;

    public XQueryTreeParser(XQueryContext context) {
        this(context, null);
    }

    public XQueryTreeParser(XQueryContext context, ExternalModule module) {
        this();
        this.staticContext = new XQueryContext(context);
        this.context= context;
        this.myModule = module;
    }

    public ExternalModule getModule() {
        return myModule;
    }

    public boolean foundErrors() {
        return foundError;
    }

    public String getErrorMessage() {
        StringBuilder buf= new StringBuilder();
        for (Iterator i= exceptions.iterator(); i.hasNext();) {
            buf.append(((Exception) i.next()).toString());
            buf.append('\n');
        }
        return buf.toString();
    }

    public Exception getLastException() {
        return (Exception) exceptions.get(exceptions.size() - 1);
    }

    protected void handleException(Exception e) {
        foundError= true;
        exceptions.add(e);
    }

    private void throwException(XQueryAST ast, String message) throws XPathException {
        throw new XPathException(ast, message);
    }

    private static class ForLetClause {
        XQueryAST ast;
        QName varName;
        SequenceType sequenceType= null;
        QName posVar = null;
        QName scoreVar = null;
        boolean isScoreBinding = false;
        Expression inputSequence;
        Expression action;
        FLWORClause.ClauseType type = FLWORClause.ClauseType.FOR;
        List<GroupSpec> groupSpecs = null;
        List<OrderSpec> orderSpecs = null;
        List<WindowCondition> windowConditions = null;
        WindowExpr.WindowType windowType = null;
        boolean allowEmpty = false;
        QName valueVarName = null;
        SequenceType valueSequenceType = null;
        // XQ4 destructuring
        List<QName> destructureVarNames = null;
        List<SequenceType> destructureVarTypes = null;
    }

    /**
     * Check QName of an annotation to see if it is in a reserved namespace.
     */
    private boolean annotationValid(QName qname) {
        String ns = qname.getNamespaceURI();
        if (ns.equals(Namespaces.XPATH_FUNCTIONS_NS)) {
            String ln = qname.getLocalPart();
            return ("private".equals(ln) || "public".equals(ln) || "updating".equals(ln));
        } else {
            return !(ns.equals(Namespaces.XML_NS)
                     || ns.equals(Namespaces.SCHEMA_NS)
                     || ns.equals(Namespaces.SCHEMA_INSTANCE_NS)
                     || ns.equals(Namespaces.XPATH_FUNCTIONS_MATH_NS)
                     || ns.equals(Namespaces.XPATH_FUNCTIONS_MAP_NS)
                     || ns.equals(Namespaces.XPATH_FUNCTIONS_ARRAY_NS)
                     || ns.equals(Namespaces.XQUERY_OPTIONS_NS)
                     || ns.equals(Namespaces.XQUERY_NS));
        }
    }

    /**
     * Check for duplicate or conflicting %public/%private annotations.
     * @param annots the parsed annotation list
     * @param errorCode XQST0106 for functions, XQST0116 for variables
     * @param declType "function" or "variable" for error messages
     * @param ast the AST node for error location reporting
     */
    /** The XQuery annotation namespace (http://www.w3.org/2012/xquery) */
    private static final String XQUERY_ANNOTATION_NS = "http://www.w3.org/2012/xquery";

    /** Check if a QName refers to a %public or %private visibility annotation */
    private static boolean isVisibilityAnnotation(QName qn) {
        String ns = qn.getNamespaceURI();
        String ln = qn.getLocalPart();
        return ("public".equals(ln) || "private".equals(ln))
            && (Namespaces.XPATH_FUNCTIONS_NS.equals(ns) || XQUERY_ANNOTATION_NS.equals(ns));
    }

    private static void checkVisibilityAnnotations(List annots, ErrorCodes.ErrorCode errorCode, String declType, XQueryAST ast)
    throws XPathException {
        int publicCount = 0;
        int privateCount = 0;
        for (int i = 0; i < annots.size(); i++) {
            List la = (List) annots.get(i);
            QName qn = (QName) la.get(0);
            if (isVisibilityAnnotation(qn)) {
                if ("public".equals(qn.getLocalPart())) {
                    publicCount++;
                } else if ("private".equals(qn.getLocalPart())) {
                    privateCount++;
                }
            }
        }
        if (publicCount + privateCount > 1) {
            throw new XPathException(ast, errorCode,
                "A " + declType + " declaration must not contain more than one " +
                "%public or %private annotation, and must not contain both.");
        }
    }

    /**
     * Check if any annotation in the list is %private.
     */
    private static boolean hasPrivateAnnotation(List annots) {
        for (int i = 0; i < annots.size(); i++) {
            List la = (List) annots.get(i);
            QName qn = (QName) la.get(0);
            if (isVisibilityAnnotation(qn)
                    && "private".equals(qn.getLocalPart())) {
                return true;
            }
        }
        return false;
    }

    private static void processAnnotations(List annots, FunctionSignature signature) {
        Annotation[] anns = new Annotation[annots.size()];

        //iterate the declare Annotations
        for(int i = 0; i < anns.length; i++) {
           List la = (List)annots.get(i);

           //extract the Value for the Annotation
           LiteralValue[] aValue;
           if(la.size() > 1) {

            PathExpr aPath = (PathExpr)la.get(1);

            aValue = new LiteralValue[aPath.getSubExpressionCount()];
            for(int j = 0; j < aValue.length; j++) {
                aValue[j] = (LiteralValue)aPath.getExpression(j);
            }
           } else {
            aValue = new LiteralValue[0];
           }

           Annotation a = new Annotation((QName)la.get(0), aValue, signature);
           anns[i] = a;
        }

        //set the Annotations on the Function Signature
        signature.setAnnotations(anns);

        // W3C XQuery Update Facility 3.0: %updating annotation
        for (Annotation a : anns) {
            if ("updating".equals(a.getName().getLocalPart())
                    && Namespaces.XPATH_FUNCTIONS_NS.equals(a.getName().getNamespaceURI())) {
                signature.setUpdating(true);
                break;
            }
        }
    }

    private static void processParams(List varList, UserDefinedFunction func, FunctionSignature signature)
    throws XPathException {
        SequenceType[] types= new SequenceType[varList.size()];
        int j= 0;
        for (Iterator i= varList.iterator(); i.hasNext(); j++) {
            FunctionParameterSequenceType param= (FunctionParameterSequenceType) i.next();
            types[j]= param;
            func.addVariable(param.getAttributeName());
        }
        signature.setArgumentTypes(types);
    }

    private static class DistinctVariableNames {
        private final Set<QName> distinctVariableNames = new HashSet<>();

        public QName check(final ErrorCodes.ErrorCode errorCode, final AST astNode, final QName variableName) throws XPathException {
            if (distinctVariableNames.contains(variableName)) {
                throw new XPathException(astNode.getLine(), astNode.getColumn(), errorCode, "Variable $" + variableName + " is defined twice");
            }
            distinctVariableNames.add(variableName);
            return variableName;
        }
    }

    private static String dfRequireSingleChar(final AST node, final String propName, final String value) throws XPathException {
        if (value.codePointCount(0, value.length()) != 1) {
            throw new XPathException(node.getLine(), node.getColumn(), ErrorCodes.XQST0098,
                "The value of decimal-format property '" + propName + "' must be a single character, but got: \"" + value + "\"");
        }
        return value;
    }

    private static void dfValidateZeroDigit(final AST node, final String value) throws XPathException {
        final int cp = value.codePointAt(0);
        if (Character.getType(cp) != Character.DECIMAL_DIGIT_NUMBER || Character.getNumericValue(cp) != 0) {
            throw new XPathException(node.getLine(), node.getColumn(), ErrorCodes.XQST0098,
                "The value of decimal-format property 'zero-digit' must be a Unicode digit with numeric value zero, but got: \"" + value + "\"");
        }
    }

    private static void dfValidateDistinctPictureChars(final AST node, final DecimalFormat df) throws XPathException {
        // The 8 single-character picture-string properties must all have distinct values
        final int[] chars = { df.decimalSeparator, df.groupingSeparator, df.percent, df.perMille,
                              df.zeroDigit, df.digit, df.patternSeparator, df.exponentSeparator };
        final String[] names = { "decimal-separator", "grouping-separator", "percent", "per-mille",
                                 "zero-digit", "digit", "pattern-separator", "exponent-separator" };
        for (int i = 0; i < chars.length; i++) {
            for (int j = i + 1; j < chars.length; j++) {
                if (chars[i] == chars[j]) {
                    throw new XPathException(node.getLine(), node.getColumn(), ErrorCodes.XQST0098,
                        "Decimal-format properties '" + names[i] + "' and '" + names[j] +
                        "' must have distinct values, but both are: '" + new String(Character.toChars(chars[i])) + "'");
                }
            }
        }
    }

    private DecimalFormat processDecimalFormatProperties(final AST parentNode) throws XPathException {
        // Start with UNNAMED defaults
        int decimalSeparator = DecimalFormat.UNNAMED.decimalSeparator;
        int exponentSeparator = DecimalFormat.UNNAMED.exponentSeparator;
        int groupingSeparator = DecimalFormat.UNNAMED.groupingSeparator;
        int percent = DecimalFormat.UNNAMED.percent;
        int perMille = DecimalFormat.UNNAMED.perMille;
        int zeroDigit = DecimalFormat.UNNAMED.zeroDigit;
        int digit = DecimalFormat.UNNAMED.digit;
        int patternSeparator = DecimalFormat.UNNAMED.patternSeparator;
        String infinity = DecimalFormat.UNNAMED.infinity;
        String nan = DecimalFormat.UNNAMED.NaN;
        int minusSign = DecimalFormat.UNNAMED.minusSign;

        AST child = parentNode.getFirstChild();
        while (child != null) {
            final String propName = child.getText();
            final AST valueNode = child.getFirstChild();
            if (valueNode == null) {
                child = child.getNextSibling();
                continue;
            }
            final String value = valueNode.getText();

            switch (propName) {
                case "decimal-separator":
                    dfRequireSingleChar(child, propName, value);
                    decimalSeparator = value.codePointAt(0);
                    break;
                case "grouping-separator":
                    dfRequireSingleChar(child, propName, value);
                    groupingSeparator = value.codePointAt(0);
                    break;
                case "infinity":
                    infinity = value;
                    break;
                case "minus-sign":
                    dfRequireSingleChar(child, propName, value);
                    minusSign = value.codePointAt(0);
                    break;
                case "NaN":
                    nan = value;
                    break;
                case "percent":
                    dfRequireSingleChar(child, propName, value);
                    percent = value.codePointAt(0);
                    break;
                case "per-mille":
                    dfRequireSingleChar(child, propName, value);
                    perMille = value.codePointAt(0);
                    break;
                case "zero-digit":
                    dfRequireSingleChar(child, propName, value);
                    dfValidateZeroDigit(child, value);
                    zeroDigit = value.codePointAt(0);
                    break;
                case "digit":
                    dfRequireSingleChar(child, propName, value);
                    digit = value.codePointAt(0);
                    break;
                case "pattern-separator":
                    dfRequireSingleChar(child, propName, value);
                    patternSeparator = value.codePointAt(0);
                    break;
                case "exponent-separator":
                    dfRequireSingleChar(child, propName, value);
                    exponentSeparator = value.codePointAt(0);
                    break;
                default:
                    break;
            }
            child = child.getNextSibling();
        }

        final DecimalFormat df = new DecimalFormat(
            decimalSeparator, exponentSeparator, groupingSeparator,
            percent, perMille, zeroDigit, digit,
            patternSeparator, infinity, nan, minusSign
        );
        dfValidateDistinctPictureChars(parentNode, df);
        return df;
    }
}

xpointer [PathExpr path]
throws XPathException
{ Expression step = null; }:
    #( XPOINTER step=expr [path] )
    |
    #( XPOINTER_ID nc:NCNAME )
    {
        PathExpr p = new PathExpr(context);
        p.setASTNode(xpointer_AST_in);
        RootNode root = new RootNode(context);
        p.add(root);
        Function fun= new FunId(context, FunId.signature[0]);
        List params= new ArrayList(1);
        params.add(new LiteralValue(context, new StringValue(p, nc.getText())));
        fun.setArguments(params);
        p.addPath(fun);
        path.add(p);
    }
    ;
//    exception catch [RecognitionException e]
//    { handleException(e); }
    exception catch [EXistException e]
    { handleException(e); }
    catch [PermissionDeniedException e]
    { handleException(e); }
//    catch [XPathException e]
//    { handleException(e); }

xpath [PathExpr path]
throws XPathException
{ context.setRootExpression(path); }
:
    module [path]
    {
        context.resolveForwardReferences();
    }
    ;
    exception catch [RecognitionException e]
    { handleException(e); }
    catch [EXistException e]
    { handleException(e); }
    catch [PermissionDeniedException e]
    { handleException(e); }
//    catch [XPathException e]
//    { handleException(e); }


module [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
    (
        #(
            v:VERSION_DECL
            {
                final String version = v.getText();
                if (version.equals("4.0")) {
                    if (!"true".equals(System.getProperty("exist.xquery4.enabled", "true"))) {
                        throw new XPathException(v, ErrorCodes.XPST0003,
                            "XQuery 4.0 is not enabled. Set system property exist.xquery4.enabled=true to enable.");
                    }
                    context.setXQueryVersion(40);
                    staticContext.setXQueryVersion(40);
                } else if (version.equals("3.1")) {
                    context.setXQueryVersion(31);
                    staticContext.setXQueryVersion(31);
                } else if (version.equals("3.0")) {
                    context.setXQueryVersion(30);
                    staticContext.setXQueryVersion(30);
                } else if (version.equals("1.0")) {
                    context.setXQueryVersion(10);
                    staticContext.setXQueryVersion(10);
                } else {
                    throw new XPathException(v, ErrorCodes.XQST0031, "Wrong XQuery version: require 1.0, 3.0, 3.1, or 4.0");
                }
            }
            ( enc:STRING_LITERAL )?
            {
                if (enc != null) {
                    if (!XMLChar.isValidIANAEncoding(enc.getText())) {
                        throw new XPathException(enc, ErrorCodes.XQST0087, "Unknown or wrong encoding not adhering to required XML 1.0 EncName.");
                    }
                    if (!enc.getText().equals("UTF-8")) {
                        //util.serializer.encodings.CharacterSet
                        //context.setEncoding(enc.getText());
                    }
                }
            }
        )
    )?
    (   libraryModule [path]
    |
    mainModule [path] )
    ;

libraryModule [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
   #(
            m:MODULE_DECL uri:STRING_LITERAL
            {
                if (uri.getText() == null || uri.getText().isEmpty()) {
                    throw new XPathException(uri.getLine(), uri.getColumn(), ErrorCodes.XQST0088,
                        "The literal that specifies the target namespace in a module declaration must not be of zero length.");
                }
                if (myModule == null)
                    myModule = new ExternalModuleImpl(uri.getText(), m.getText());
                else {
                    myModule.setNamespace(m.getText(), uri.getText());
                    if (myModule.getContext() != null)
                        ((ModuleContext)myModule.getContext()).setModuleNamespace(m.getText(), uri.getText());
                }
                myModule.setDescription(m.getDoc());
                context.declareNamespace(m.getText(), uri.getText());
                staticContext.declareNamespace(m.getText(), uri.getText());
            }
    )
    prolog [path]
    ;

mainModule [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
    prolog [path] step=expr [path]
    ;


/**
 * Process the XQuery prolog.
 */
prolog [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null;
  boolean boundaryspace = false;
  boolean defaultcollation = false;
  boolean orderempty = false;
  boolean copynamespaces = false;
  boolean baseuri = false;
  boolean ordering = false;
  boolean construction = false;
  Set declaredDecimalFormats = new HashSet();
  boolean defaultDecimalFormatDeclared = false;

}:
    (
        #(
            prefix:NAMESPACE_DECL uri:STRING_LITERAL
            {
                if (declaredNamespaces.get(prefix.getText()) != null)
                    throw new XPathException(prefix, ErrorCodes.XQST0033, "Prolog contains " +
                        "multiple declarations for namespace prefix: " + prefix.getText());
                context.declareNamespace(prefix.getText(), uri.getText());
                staticContext.declareNamespace(prefix.getText(), uri.getText());
                declaredNamespaces.put(prefix.getText(), uri.getText());
            }
        )
        |
        #(
            "boundary-space"
            (
                "preserve"
                {
                if (boundaryspace)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0068, "Boundary-space already declared.");
                boundaryspace = true;
                context.setStripWhitespace(false);
                }
                |
                "strip"
                {
                if (boundaryspace)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0068, "Boundary-space already declared.");
                boundaryspace = true;
                context.setStripWhitespace(true);
                }
            )
        )
        |
        #(
            "order"
            ( "greatest"
              {
                context.setOrderEmptyGreatest(true);
              }
            |
              "least"
              {
                context.setOrderEmptyGreatest(false);
              }
            )
            {
                if (orderempty)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0069, "Default empty-order already declared.");
                orderempty = true;
            }
        )
        |
        #(
            "copy-namespaces"
            (
            "preserve"
            {
                staticContext.setPreserveNamespaces(true);
                context.setPreserveNamespaces(true);
            }
            |
            "no-preserve"
            {
                staticContext.setPreserveNamespaces(false);
                context.setPreserveNamespaces(false);
            }
            )
            (
            "inherit"
            {
                staticContext.setInheritNamespaces(true);
                context.setInheritNamespaces(true);
            }
            |
            "no-inherit"
            {
                staticContext.setInheritNamespaces(false);
                context.setInheritNamespaces(false);
            }
            )
            {
                if (copynamespaces)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0055, "Copy-namespaces mode already declared.");
                copynamespaces = true;
            }
        )
            exception catch [RecognitionException se]
        {throw new XPathException(prolog_AST_in, ErrorCodes.XPST0003, "XQuery syntax error.");}
        |
        #(
            "base-uri" base:STRING_LITERAL
            {
                context.setBaseURI(new AnyURIValue(StringValue.expand(base.getText(), null)), true);
                if (baseuri)
                    throw new XPathException(base, ErrorCodes.XQST0032, "Base URI is already declared.");
                baseuri = true;
            }
        )
        |
        #(
            "ordering" ( "ordered" | "unordered" )
            {
                // ignored
                if (ordering)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0065, "Ordering already declared.");
                ordering = true;
            }
        )
        |
        #(
            "construction" ( "preserve" | "strip" )    // ignored
            {
                // ignored
                if (construction)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0067, "Construction already declared.");
                construction = true;
            }
        )
        |
        // === W3C XQuery Update Facility 3.0 - Revalidation Declaration ===
        #(
            "revalidation" ( "strict" | "lax" | "skip" )
            {
                // eXist does not support schema revalidation; declaration is accepted and ignored
            }
        )
        |
        #(
            DEF_NAMESPACE_DECL defu:STRING_LITERAL
            {
                // Check for duplicate default element namespace first (XQST0066)
                context.setDefaultElementNamespace(defu.getText(), null);
                staticContext.setDefaultElementNamespace(defu.getText(), null);
                context.declareNamespace("", defu.getText());
                staticContext.declareNamespace("", defu.getText());
            }
        )
        |
        #(
            DEF_FUNCTION_NS_DECL deff:STRING_LITERAL
            {
                context.setDefaultFunctionNamespace(deff.getText());
                staticContext.setDefaultFunctionNamespace(deff.getText());
            }
        )
        |
        #(
            DEF_COLLATION_DECL defc:STRING_LITERAL
            {
                if (defaultcollation)
                    throw new XPathException(prolog_AST_in, ErrorCodes.XQST0038, "Default collation already declared.");
                defaultcollation = true;
                try {
                    context.setDefaultCollation(defc.getText());
                } catch (XPathException xp) {
                    throw new XPathException(defc, ErrorCodes.XQST0038, "the value specified by a default collation declaration is not present in statically known collations.");
                }
            }
        )
        |
        #(
            qname:GLOBAL_VAR
            {
                PathExpr enclosed= new PathExpr(context);
                enclosed.setASTNode(prolog_AST_in);
                SequenceType type= null;
                final QName qn;
                try {
                    qn = QName.parse(staticContext, qname.getText(), null);
                } catch (final IllegalQNameException iqe) {
                    throw new XPathException(qname.getLine(), qname.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + qname.getText());
                }
                if (declaredGlobalVars.contains(qn)
                        || (importedModuleVariables != null && importedModuleVariables.contains(qn))) {
                    throw new XPathException(qname, ErrorCodes.XQST0049, "It is a " +
                        "static error if more than one variable declared or " +
                        "imported by a module has the same expanded QName. " +
                        "Variable: " + qn.toString());
                }
                declaredGlobalVars.add(qn);
            }
                        { List annots = new ArrayList(); boolean varIsPrivate = false; }
                        (annotations [annots]
                        {
                            checkVisibilityAnnotations(annots, ErrorCodes.XQST0116, "variable", qname);
                            varIsPrivate = hasPrivateAnnotation(annots);
                        }
                        )?
            (
                #(
                    "as"
                    { type= new SequenceType(); }
                    sequenceType [type]
                )
            )?
            (
                step=e:expr [enclosed]
                {
                    final VariableDeclaration decl= new VariableDeclaration(context, qn, enclosed);
                    decl.setSequenceType(type);
                    decl.setPrivate(varIsPrivate);
                    decl.setASTNode(e);
                    path.add(decl);
                    if(myModule != null) {
                        myModule.declareVariable(qn, decl);
                    }
                }
                |
                "external"
                { PathExpr defaultValue = null; }
                (
                    {
                        defaultValue = new PathExpr(context);
                        defaultValue.setASTNode(prolog_AST_in);
                    }
                    step=ext:expr [defaultValue]
                )?
                {
                    // variable may be declared in static context: retrieve and set its sequence type
                    Variable external = null;
                    try {
                        external = context.resolveVariable(qname.getText());
                        if (external != null) {
                            external.setSequenceType(type);
                        }
                    } catch (XPathException ignoredException) {
                    }

                    final VariableDeclaration decl = new VariableDeclaration(context, qn, defaultValue);
                    decl.setSequenceType(type);
                    decl.setPrivate(varIsPrivate);
                    decl.setASTNode(ext);
                    if (external == null) {
                        path.add(decl);
                    }
                    if(myModule != null) {
                        myModule.declareVariable(qn, decl);
                    }
                }
            )
        )
        |
        #(
            qname2:OPTION
            content:STRING_LITERAL
            {
                try {
                    context.addOption(qname2.getText(), content.getText());
                } catch(XPathException e2) {
                    e2.setLocation(qname2.getLine(), qname2.getColumn());
                    throw e2;
                }
            }
        )
        |
        #(
            c:CONTEXT_ITEM_DECL
            {
                PathExpr enclosed= new PathExpr(context);
                enclosed.setASTNode(prolog_AST_in);
                SequenceType type= null;
            }
            (
                #(
                    "as"
                    { type= new SequenceType(); }
                    sequenceType [type]
                )
            )?
            (
                step=cidExpr:expr [enclosed]
                {

                    final ContextItemDeclaration cid = new ContextItemDeclaration(context, type, false, enclosed);
                    cid.setASTNode(cidExpr);
                    //path.add(cid);
                    staticContext.setContextItemDeclaration(cid);
                    context.setContextItemDeclaration(cid);
                    if(myModule != null) {
                        if(myModule.getContext() != null) {
                            myModule.getContext().setContextItemDeclaration(cid);
                        }
                    }
                }
                |
                "external"
                { PathExpr defaultValue = null; }
                (
                    {
                        defaultValue = new PathExpr(context);
                        defaultValue.setASTNode(prolog_AST_in);
                    }
                    step=cidExtDefExpr:expr [defaultValue]
                )?
                {
                    final ContextItemDeclaration cid = new ContextItemDeclaration(context, type, true, defaultValue);
                    cid.setASTNode(cidExtDefExpr);
                    //path.add(cid);
                    staticContext.setContextItemDeclaration(cid);
                    context.setContextItemDeclaration(cid);
                    if(myModule != null) {
                        if(myModule.getContext() != null) {
                            myModule.getContext().setContextItemDeclaration(cid);
                        }
                    }
                }
            )
        )
        |
        // XQFT 3.0 §5.2: declare ft-option using <match-options>
        #(
            FT_OPTION_DECL
            {
                FTMatchOptions ftDefaultOpts = new FTMatchOptions();
            }
            ftDefaultOpts=ftMatchOptionsExpr
            {
                if (ftDefaultOpts.hasConflict()) {
                    throw new XPathException(ErrorCodes.FTST0019,
                            ftDefaultOpts.getConflictDescription());
                }
                context.setDefaultFTMatchOptions(ftDefaultOpts);
            }
        )
        |
        #(
            dfDecl:DECIMAL_FORMAT_DECL (.)*
            {
                final QName dfQName;
                try {
                    dfQName = QName.parse(staticContext, dfDecl.getText(), null);
                } catch (final IllegalQNameException iqe) {
                    throw new XPathException(dfDecl.getLine(), dfDecl.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix in decimal format name: " + dfDecl.getText());
                }
                final String dfKey = dfQName.getNamespaceURI() + ":" + dfQName.getLocalPart();
                if (declaredDecimalFormats.contains(dfKey))
                    throw new XPathException(dfDecl, ErrorCodes.XQST0097, "Duplicate decimal format declaration: " + dfDecl.getText());
                declaredDecimalFormats.add(dfKey);
                final DecimalFormat df = processDecimalFormatProperties(dfDecl);
                context.setStaticDecimalFormat(dfQName, df);
            }
        )
        |
        #(
            defDfDecl:DEF_DECIMAL_FORMAT_DECL (.)*
            {
                if (defaultDecimalFormatDeclared)
                    throw new XPathException(defDfDecl, ErrorCodes.XQST0097, "Duplicate default decimal format declaration.");
                defaultDecimalFormatDeclared = true;
                final DecimalFormat df = processDecimalFormatProperties(defDfDecl);
                context.setDefaultStaticDecimalFormat(df);
            }
        )
        |
        functionDecl [path]
        |
        importDecl [path]
    )*
    ;

importDecl [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
:
    #(
        i:MODULE_IMPORT
        {
            String modulePrefix = null;
            final List<AnyURIValue> uriList = new ArrayList<>(1);
        }
        ( pfx:NCNAME { modulePrefix = pfx.getText(); } )?
        moduleURI:STRING_LITERAL
        ( uriList [uriList] )?
        {
            // Normalize whitespace in module namespace URI per XQuery spec section 4.12:
            // xs:anyURI uses collapse (strip leading/trailing, replace internal whitespace sequences with single space)
            final String moduleNamespaceUri = moduleURI.getText().strip().replaceAll("[\\x20\\x09\\x0A\\x0D]+", " ");

            if (modulePrefix != null) {
                if (declaredNamespaces.get(modulePrefix) != null) {
                    throw new XPathException(i, ErrorCodes.XQST0033, "Prolog contains " +
                        "multiple declarations for namespace prefix: " + modulePrefix);
                }
                declaredNamespaces.put(modulePrefix, moduleNamespaceUri);
            }
            if (importedModules.contains(moduleNamespaceUri)) {
                throw new XPathException(i, ErrorCodes.XQST0047, "Prolog has " +
                    "more than one 'import module' statement for module(s) of namespace: " + moduleNamespaceUri);
            }
            importedModules.add(moduleNamespaceUri);

            final org.exist.xquery.Module[] modules;
            try {
                modules = context.importModule(moduleNamespaceUri, modulePrefix, uriList.toArray(new AnyURIValue[uriList.size()]));
                staticContext.declareNamespace(modulePrefix, moduleNamespaceUri);
            } catch (final XPathException xpe) {
                xpe.prependMessage("error found while loading module " + modulePrefix + ": ");
                throw xpe;
            }

            if (isNotEmpty(modules)) {
                for (final org.exist.xquery.Module module : modules) {

                    // check modules does not import any duplicate function definitions
                    final FunctionSignature[] signatures = module.listFunctions();
                    if (isNotEmpty(signatures)) {
                        for (final FunctionSignature signature : signatures) {
                            final String qualifiedNameArity = signature.getName().toURIQualifiedName() + '#' + signature.getArgumentCount();
                            if (importedModuleFunctions != null) {
                                if (importedModuleFunctions.contains(qualifiedNameArity)) {
                                    throw new XPathException(i, ErrorCodes.XQST0034, "Prolog has " +
                                        "more than one imported module that defines the function: " + qualifiedNameArity);
                                }
                            } else {
                                importedModuleFunctions = new HashSet<>();
                            }

                            importedModuleFunctions.add(qualifiedNameArity);
                        }
                    }

                    // check modules does not import any duplicate variable definitions
                    final Iterator<QName> globalVariables = module.getGlobalVariables();
                    if (globalVariables != null) {
                        while (globalVariables.hasNext()) {
                            final QName globalVarName = globalVariables.next();

                            if (importedModuleVariables != null) {
                                if (importedModuleVariables.contains(globalVarName)) {
                                        throw new XPathException(i, ErrorCodes.XQST0049, "Prolog has " +
                                                "more than one imported module that defines the variable: " + globalVarName.toURIQualifiedName());
                                }
                            } else {
                                importedModuleVariables = new HashSet<>();
                            }

                            importedModuleVariables.add(globalVarName);
                        }
                    }
                }
            }
        }
    )
    |
    #(
        s:SCHEMA_IMPORT
        {
            String nsPrefix = null;
            String location = null;
            boolean defaultElementNS = false;
            List uriList= new ArrayList(2);
        }
        ( pfx1:NCNAME { nsPrefix = pfx1.getText(); }
          |
          "default" "element" "namespace" { defaultElementNS = true; }
        )?
        targetURI:STRING_LITERAL
        ( uriList [uriList] )?
        {
            if ("".equals(targetURI.getText()) && nsPrefix != null) {
                    throw new XPathException(s, ErrorCodes.XQST0057, "A schema without target namespace (zero-length string target namespace) may not bind a namespace prefix: " + nsPrefix);
            }
            if (nsPrefix != null) {
                if (declaredNamespaces.get(nsPrefix) != null)
                    throw new XPathException(s, ErrorCodes.XQST0033, "Prolog contains " +
                                             "multiple declarations for namespace prefix: " + nsPrefix);
                declaredNamespaces.put(nsPrefix, targetURI.getText());
            }
            try {
                context.declareNamespace(nsPrefix, targetURI.getText());
                staticContext.declareNamespace(nsPrefix, targetURI.getText());
                // We currently do nothing with eventual location hints. /ljo
            } catch(XPathException xpe) {
                xpe.prependMessage("err:XQST0059: Error found while loading schema " + nsPrefix + ": ");
                throw xpe;
            }
            // We ought to do this for now until Dannes can say it works. /ljo
            //throw new XPathException(s, ErrorCodes.XQST0009, "The eXist-db XQuery implementation does not support the Schema Import Feature quite yet.");
        }
    )
    ;

/** Parse a declared set of annotation associated to a function declaration or
 * a variable declaration
 * (distinction is made via one of the two parameters set to null)
 */
annotations [List annots]
throws XPathException
{
  List annotList = null;
}
:       ( { annotList = new ArrayList(); }
          annotation[annotList]
          { if (annotList.size() != 0)
              annots.add(annotList);
          }
        )*
;

annotation [List annotList]
throws XPathException
{ Expression le = null; }
:
        #(
        name:ANNOT_DECL {
                    final QName qn;
                    try {
                        qn = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(name.getLine(), name.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + name.getText());
                    }

                    String ns = qn.getNamespaceURI();

                    if(!annotationValid(qn)) {
                        XPathException e = new XPathException(annotation_AST_in, ErrorCodes.XQST0045,
                            "Annotation in a reserved namespace: " + qn.getNamespaceURI());
                        e.setLocation(name.getLine(), name.getColumn());
                        throw e;
                    }

                    annotList.add(qn);
                }
                ( {
                      PathExpr annotPath = new PathExpr(context);
                      annotPath.setASTNode(annotation_AST_in);
                  }
                  le=literalExpr[annotPath]
                  {annotPath.add(le); }
                  (le=literalExpr[annotPath]
                   {annotPath.add(le); }
                  )*
                  { annotList.add(annotPath); }
                )?
        )
;

/**
 * Parse a function declared in the prolog.
 */
functionDecl [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ step = null; }:
    #(
        name:FUNCTION_DECL // for inline functions, name is null
        {
            PathExpr body= new PathExpr(context);
            body.setASTNode(functionDecl_AST_in);
        }
        {
            QName qn= null;
            try {
                // XQ4 (PR2200): unprefixed function declarations go into "no namespace"
                // instead of the default function namespace (fn:)
                if (name.getText() != null && !name.getText().contains(":") && staticContext.getXQueryVersion() >= 40) {
                    qn = new QName(name.getText(), "");
                } else {
                    qn = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
                }
            } catch (final IllegalQNameException iqe) {
                throw new XPathException(name.getLine(), name.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + name.getText());
            }
            FunctionSignature signature= new FunctionSignature(qn);
            signature.setDescription(name.getDoc());
            if (name instanceof XQueryFunctionAST && ((XQueryFunctionAST) name).isUpdating()) {
                signature.setUpdating(true);
            }
            UserDefinedFunction func= new UserDefinedFunction(context, signature);
            func.setASTNode(name);
            List varList= new ArrayList(3);
        }
                { List annots = new ArrayList(); }
                (annotations [annots]
                 {
                    checkVisibilityAnnotations(annots, ErrorCodes.XQST0106, "function", name);
                    processAnnotations(annots, signature);
                  }
                )?
        ( paramList [varList] )?
        {
            processParams(varList, func, signature);

            final String qualifiedNameArity = signature.getName().toURIQualifiedName() + '#' + signature.getArgumentCount();
            if (importedModuleFunctions != null && importedModuleFunctions.contains(qualifiedNameArity)) {
                throw new XPathException(name.getLine(), name.getColumn(), ErrorCodes.XQST0034, "Prolog has " +
                        "imports a module that defines a function which is already defined in the importing module: " + qualifiedNameArity);
            }
        }
        (
            #(
                "as"
                { SequenceType type= new SequenceType(); }
                sequenceType [type]
                {
                    signature.setReturnType(type);
                    // XUST0028: updating functions must not declare a return type
                    if (signature.isUpdating()) {
                        throw new XPathException(name.getLine(), name.getColumn(),
                            ErrorCodes.XUST0028, "An updating function must not declare a return type.");
                    }
                }
            )
        )?
        (
            // the function body:
            #(
                LCURLY expr [body]
                {
                    func.setFunctionBody(body);
                    context.declareFunction(func);
                    if(myModule != null)
                        myModule.declareFunction(func);
                }
            )
            |
            "external"
        )
    )
    ;

/**
 * Parse an inline function declaration.
 */
inlineFunctionDecl [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ step = null; }:
    #(
        name:INLINE_FUNCTION_DECL // for inline functions, name is null
        {
            PathExpr body= new PathExpr(context);
            body.setASTNode(inlineFunctionDecl_AST_in);
        }
        {
            FunctionSignature signature= new FunctionSignature(InlineFunction.INLINE_FUNCTION_QNAME);
            signature.setDescription(name.getDoc());
            UserDefinedFunction func= new UserDefinedFunction(context, signature);
            func.setASTNode(name);
            List varList= new ArrayList(3);
        }
        { List annots = new ArrayList(); }
        (
            annotations [annots]
            {
                checkVisibilityAnnotations(annots, ErrorCodes.XQST0106, "function", name);
                processAnnotations(annots, signature);
            }
        )?
        ( paramList [varList] )?
        {
            processParams(varList, func, signature);
        }
        (
            #(
                "as"
                { SequenceType type= new SequenceType(); }
                sequenceType [type]
                { signature.setReturnType(type); }
            )
        )?
        // the function body:
        #(
            LCURLY expr [body]
            {
                func.setFunctionBody(body);
                // anonymous function
                step = new InlineFunction(context, func);
            }
        )
    )
    ;

focusFunctionDecl [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ step = null; }:
    #(
        ff:FOCUS_FUNCTION
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(ff, ErrorCodes.XPST0003,
                    "Focus functions require xquery version \"4.0\"");
            }
            PathExpr body = new PathExpr(context);
            body.setASTNode(focusFunctionDecl_AST_in);

            // Create a function with a single implicit parameter
            FunctionSignature signature = new FunctionSignature(InlineFunction.INLINE_FUNCTION_QNAME);
            UserDefinedFunction func = new UserDefinedFunction(context, signature);
            func.setASTNode(ff);

            // Add the implicit focus parameter: $(.focus) as item()*
            FunctionParameterSequenceType focusParam = new FunctionParameterSequenceType(
                FocusFunction.FOCUS_PARAM_NAME, Type.ITEM, Cardinality.ZERO_OR_MORE,
                "implicit focus parameter");
            signature.setArgumentTypes(new SequenceType[] { focusParam });
            signature.setReturnType(new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
            func.addVariable(FocusFunction.FOCUS_PARAM_NAME);
        }
        ( expr [body] )?
        {
            func.setFunctionBody(body);
            step = new FocusFunction(context, func);
        }
    )
    ;

/**
 * Parse params in function declaration.
 */
paramList [List vars]
throws PermissionDeniedException, EXistException, XPathException
:
    param [vars] ( param [vars] )*
    ;

/**
 * Single function param.
 */
param [List vars]
throws PermissionDeniedException, EXistException, XPathException
:
    #(
        varname:VARIABLE_BINDING
        {
            FunctionParameterSequenceType var = new FunctionParameterSequenceType(varname.getText());
            var.setCardinality(Cardinality.ZERO_OR_MORE);
            vars.add(var);
        }
        (
            #(
                "as"
                { var.setCardinality(Cardinality.EXACTLY_ONE); }
                sequenceType [var]
            )
        )?
        (
            #(
                pd:PARAM_DEFAULT
                {
                    if (staticContext.getXQueryVersion() < 40) {
                        throw new XPathException(pd, ErrorCodes.XPST0003,
                            "Default parameter values require xquery version \"4.0\"");
                    }
                    PathExpr defaultExpr = new PathExpr(context);
                }
                expr [defaultExpr]
                {
                    var.setDefaultValue(defaultExpr.simplify());
                }
            )
        )?
    )
    ;

/**
 * Parse uris in schema and module declarations.
 */
uriList [List uris]
throws XPathException
:
    uri [uris] ( uri [uris] )*
    ;

/**
 * Single uri.
 */
uri [List uris]
throws XPathException
:
    #(
        uri:STRING_LITERAL
        {
            AnyURIValue any= new AnyURIValue(uri.getText());
            uris.add(any);
        }
    )
    ;

/**
 * catchErrorList in try-catch.
 */
catchErrorList [List<QName> catchErrors]
throws XPathException
:
    catchError [catchErrors] ( catchError [catchErrors] )*
    ;

/**
 * Single catchError.
 */
catchError [List<QName> catchErrors]
throws XPathException
:
    (
        #(ncwc:NCNAME WILDCARD
        {
            try {
                catchErrors.add(QName.WildcardLocalPartQName.parseFromPrefix(staticContext, ncwc.toString()));
            } catch (final IllegalQNameException e) {
                throw new XPathException(ncwc.getLine(), ncwc.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + ncwc.getText());
            }
        }
        )
        |
        #(wc:WILDCARD
        {
            catchErrors.add(QName.WildcardQName.getInstance());
        }
        )
        |
        #(PREFIX_WILDCARD pwcnc:NCNAME
        {
            catchErrors.add(new QName.WildcardNamespaceURIQName(pwcnc.toString()));
        }
        )
        |
        #(eq:EQNAME
        {
            try {
                catchErrors.add(QName.parse(staticContext, eq.toString()));
            } catch (final IllegalQNameException e) {
                throw new XPathException(eq.getLine(), eq.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq.getText());
            }
        }
        )
    )
    ;

/**
 * A sequence type declaration.
 */
sequenceType [SequenceType type]
throws XPathException
:
    (
        #(
            t:ATOMIC_TYPE
            {
                try {
                    QName qn= QName.parse(staticContext, t.getText());
                    int code= Type.getType(qn);
                    if (!Type.subTypeOf(code, Type.ANY_ATOMIC_TYPE) && !Type.subTypeOf(code, Type.RECORD))
                        throw new XPathException(t.getLine(), t.getColumn(), ErrorCodes.XPST0051, qn.toString() + " is not atomic");
                    type.setPrimaryType(code);
                } catch (final XPathException e) {
                    throw new XPathException(t.getLine(), t.getColumn(), ErrorCodes.XPST0051, "Unknown simple type " + t.getText());
                } catch (final IllegalQNameException e) {
                    throw new XPathException(t.getLine(), t.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + t.getText());
                }
            }
        )
        |
        #(
            "empty"
            {
                type.setPrimaryType(Type.EMPTY_SEQUENCE);
                type.setCardinality(Cardinality.EMPTY_SEQUENCE);
            }
        )
        |
        #(
            "empty-sequence"
            {
                type.setPrimaryType(Type.EMPTY_SEQUENCE);
                type.setCardinality(Cardinality.EMPTY_SEQUENCE);
            }
        )
        |
        #(
            FUNCTION_TEST { type.setPrimaryType(Type.FUNCTION); }
            (
                STAR
                |
                (
                    { List<SequenceType> paramTypes = new ArrayList<SequenceType>(5); }
                    (
                        { SequenceType paramType = new SequenceType(); }
                        sequenceType [paramType]
                        { paramTypes.add(paramType); }
                    )*
                    { SequenceType returnType = new SequenceType(); }
                    "as" sequenceType [returnType]
                    {
                        type.setFunctionParamTypes(paramTypes.toArray(new SequenceType[0]));
                        type.setFunctionReturnType(returnType);
                    }
                )
            )
        )
        |
        // XQ3.1+: AnnotatedFunctionTest. Annotations are validated for reserved
        // namespaces (XQST0045), then the inner FunctionTest is processed identically
        // to a non-annotated one. Annotations themselves are not currently used as
        // type-system constraints (consistent with eXist's existing FUNCTION_TEST handling).
        #(
            ANNOTATED_FUNCTION_TEST { type.setPrimaryType(Type.FUNCTION); }
            { List annots = new ArrayList(); }
            ( annotation [annots] )+
            #(
                FUNCTION_TEST
                (
                    STAR
                    |
                    (
                        { List<SequenceType> paramTypes2 = new ArrayList<SequenceType>(5); }
                        (
                            { SequenceType paramType2 = new SequenceType(); }
                            sequenceType [paramType2]
                            { paramTypes2.add(paramType2); }
                        )*
                        { SequenceType returnType2 = new SequenceType(); }
                        "as" sequenceType [returnType2]
                    )
                )
            )
        )
        |
        #(
            MAP_TEST { type.setPrimaryType(Type.MAP_ITEM); }
            (
                STAR
                |
                (
                    { List<SequenceType> paramTypes = new ArrayList<SequenceType>(5); }
                    (
                        { SequenceType paramType = new SequenceType(); }
                        sequenceType [paramType]
                        { paramTypes.add(paramType); }
                    )*
                    {
                        type.setFunctionParamTypes(paramTypes.toArray(new SequenceType[0]));
                    }
                )
            )
        )
        |
        #(
            ARRAY_TEST { type.setPrimaryType(Type.ARRAY_ITEM); }
            (
                STAR
                |
                (
                    { List<SequenceType> paramTypes = new ArrayList<SequenceType>(5); }
                    (
                        { SequenceType paramType = new SequenceType(); }
                        sequenceType [paramType]
                        { paramTypes.add(paramType); }
                    )*
                    {
                        type.setFunctionParamTypes(paramTypes.toArray(new SequenceType[0]));
                    }
                )
            )
        )
        |
        #(
            RECORD_TEST
            {
                type.setPrimaryType(Type.RECORD);
                List<RecordType.FieldDeclaration> recordFields = new ArrayList<RecordType.FieldDeclaration>();
            }
            (
                #(
                    rf:RECORD_FIELD
                    {
                        String rfName = rf.getText();
                        boolean rfOptional = rfName.endsWith("?");
                        if (rfOptional) {
                            rfName = rfName.substring(0, rfName.length() - 1);
                        }
                        SequenceType rfType = null;
                    }
                    (
                        { rfType = new SequenceType(); }
                        sequenceType [rfType]
                    )?
                    {
                        recordFields.add(new RecordType.FieldDeclaration(rfName, rfType, rfOptional));
                    }
                )
            )*
            {
                type.setRecordType(new RecordType(recordFields, false));
            }
        )
        |
        #(
            "item" { type.setPrimaryType(Type.ITEM); }
        )
        |
        #(
            "node" { type.setPrimaryType(Type.NODE); }
        )
        |
        #(
            lelement:"element"
            { type.setPrimaryType(Type.ELEMENT); }
            (
                WILDCARD
                |
                eq1:EQNAME
                {
                    try {
                        QName qname= QName.parse(staticContext, eq1.getText());
                        type.setNodeName(qname);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(eq1.getLine(), eq1.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq1.getText());
                    }
                }
                ( eq12:EQNAME
                    {
                        try {
                            QName qname12= QName.parse(staticContext, eq12.getText());
                            TypeTest test = new TypeTest(Type.getType(qname12));
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(eq12.getLine(), eq12.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq12.getText());
                        }
                    }
                )?
            )?
        )
        |
        #(
            lattr:ATTRIBUTE_TEST
            { type.setPrimaryType(Type.ATTRIBUTE); }
            (
                eq2:EQNAME
                {
                    try {
                        QName qname = QName.parse(staticContext, eq2.getText(), XMLConstants.DEFAULT_NS_PREFIX);
                        qname = new QName(qname, ElementValue.ATTRIBUTE);
                        type.setNodeName(qname);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(lattr.getLine(), lattr.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq2.getText());
                    }
                }
                |
                WILDCARD
                ( eq21:EQNAME
                    {
                        try {
                            QName qname21= QName.parse(staticContext, eq21.getText());
                            TypeTest test = new TypeTest(Type.getType(qname21));
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(lattr.getLine(), lattr.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq21.getText());
                        }
                    }
                )?
            )?
        )
        |
        #(
            "text" { type.setPrimaryType(Type.TEXT); }
        )
        |
        #(
            "processing-instruction"
            { type.setPrimaryType(Type.PROCESSING_INSTRUCTION); }
            ( nc:NCNAME | sl:STRING_LITERAL )?
            {
                String value = "";
                if (nc != null)
                    value = nc.getText();
                if (sl != null)
                    value = sl.getText();
                QName qname= new QName.WildcardNamespaceURIQName(value);
                if (!"".equals(value))
                    type.setNodeName(qname);
            }
        )
        |
        #(
            "comment" { type.setPrimaryType(Type.COMMENT); }
        )
        |
        #(
            "namespace-node" { type.setPrimaryType(Type.NAMESPACE); }
        )
        |
        #(
            "document-node"
            { type.setPrimaryType(Type.DOCUMENT); }
            (
                #( lelement2:"element"
                    (
                    dneq:EQNAME
                    {
                        try {
                            QName qname= QName.parse(staticContext, dneq.getText());
                            type.setNodeName(qname);
                            NameTest test= new NameTest(Type.DOCUMENT, qname);
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(lelement2.getLine(), lelement2.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + dneq.getText());
                        }
                    }
                    |
                    WILDCARD
                    {
                        TypeTest test= new TypeTest(Type.DOCUMENT);
                    }
                        ( dneq2:EQNAME
                            {
                                try {
                                    QName qname = QName.parse(staticContext, dneq2.getText());
                                    test = new TypeTest(Type.getType(qname));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(lelement2.getLine(), lelement2.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + dneq2.getText());
                                }
                            }
                        )?
                    )?
                )
                |
                #( "schema-element" EQNAME )
            )?
        )
        // === XQuery 4.0 JNode Kind Tests (version-gated) ===
        |
        #( jnt1:JSON_NODE_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt1, ErrorCodes.XPST0003, "json-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_NODE);
            }
        )
        |
        #( jnt2:JSON_OBJECT_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt2, ErrorCodes.XPST0003, "object-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_OBJECT);
            }
        )
        |
        #( jnt3:JSON_ARRAY_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt3, ErrorCodes.XPST0003, "array-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_ARRAY);
            }
        )
        |
        #( jnt4:JSON_STRING_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt4, ErrorCodes.XPST0003, "string-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_STRING);
            }
        )
        |
        #( jnt5:JSON_NUMBER_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt5, ErrorCodes.XPST0003, "number-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_NUMBER);
            }
        )
        |
        #( jnt6:JSON_BOOLEAN_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt6, ErrorCodes.XPST0003, "boolean-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_BOOLEAN);
            }
        )
        |
        #( jnt7:JSON_NULL_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt7, ErrorCodes.XPST0003, "null-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_NULL);
            }
        )
        |
        #( jnt8:JSON_MEMBER_TEST
            {
                if (staticContext.getXQueryVersion() < 40) {
                    throw new XPathException(jnt8, ErrorCodes.XPST0003, "member-node() requires xquery version \"4.0\"");
                }
                type.setPrimaryType(Type.JSON_MEMBER);
            }
        )
        // === End XQuery 4.0 JNode Kind Tests ===
        |
        #(
            CHOICE_TYPE
            {
                List<SequenceType> alternatives = new ArrayList<SequenceType>();
            }
            (
                {
                    SequenceType altType = new SequenceType();
                }
                sequenceType [altType]
                {
                    alternatives.add(altType);
                }
            )+
            {
                for (final SequenceType alt : alternatives) {
                    type.addChoiceAlternative(alt);
                }
                type.setPrimaryType(Type.ITEM);
            }
        )
        |
        #(
            en:ENUM_TYPE
            {
                String enumText = en.getText();
                String[] enumVals = enumText.split(",", -1);
                type.setEnumValues(enumVals);
            }
        )
    )
    (
        STAR { type.setCardinality(Cardinality.ZERO_OR_MORE); }
        |
        PLUS { type.setCardinality(Cardinality.ONE_OR_MORE); }
        |
        QUESTION { type.setCardinality(Cardinality.ZERO_OR_ONE); }
    )?
    ;

/**
 * Process a top-level expression like FLWOR, conditionals, comparisons etc.
 */
expr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
}
:
    eof:EOF
        {
            // Added for handling empty mainModule /ljo
            // System.out.println("EMPTY EXPR");
            if (eof.getText() == null || "".equals(eof.getText()))
                throw new XPathException(eof, ErrorCodes.XPST0003, "EOF or zero-length string found where a valid XPath expression was expected.");

        }
    |
    step=arrowOp [path]
    |
    step=mappingArrowOp [path]
    |
    step=pipelineOp [path]
    |
    step=methodCallOp [path]  // XQ4 method call operator =?>
    |
    step=otherwiseExpr [path]
    |
    step=typeCastExpr [path]
    |
    // sequence constructor:
    #(
     seq:SEQUENCE
     {
       SequenceConstructor sc = new SequenceConstructor(context);
       sc.setASTNode(seq);
     }
     (
       {
           PathExpr seqPath = new PathExpr(context);
           seqPath.setASTNode(expr_AST_in);
       }
       step = expr [seqPath]
       {
         sc.addPath(seqPath);
       }
     )*
     {
       path.addPath(sc);
       step = sc;
   }
    )
    |
    // string concatenation
    #(
        CONCAT
        {
            ConcatExpr concat = new ConcatExpr(context);
			concat.setASTNode(tmp3_AST_in);
        }
        (
            {
                PathExpr strPath = new PathExpr(context);
                strPath.setASTNode(expr_AST_in);
            }
            step = expr [strPath ]
            {
                concat.add(strPath);
            }
        )*
        {
            path.addPath(concat);
            step = concat;
        }
    )
    |
    // sequence constructor:
    #(
        c:COMMA
        {
            PathExpr left= new PathExpr(context);
            left.setASTNode(expr_AST_in);

            PathExpr right= new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [left]
        step=expr [right]
        {
            SequenceConstructor sc= new SequenceConstructor(context);
            sc.setASTNode(c);
            sc.addPath(left);
            sc.addPath(right);
            path.addPath(sc);
            step = sc;
        }
    )
    |
    step=exprFlowControl [path]

    |
    // treat as:
    #(
        "treat"
        {
            PathExpr expr = new PathExpr(context);
            expr.setASTNode(expr_AST_in);
            SequenceType type= new SequenceType();
        }
        step=expr [expr]
        sequenceType [type]
        {
            step = new TreatAsExpression(context, expr, type);
            step.setASTNode(expr_AST_in);
            path.add(step);
        }
    )
    |
    // switch
    #(
        switchAST:"switch"
        {
            PathExpr operand = new PathExpr(context);
            operand.setASTNode(expr_AST_in);
            boolean booleanMode = false;
        }
        (
            SWITCH_BOOLEAN
            { booleanMode = true; }
        |
            step=expr [operand]
        )
        {
            SwitchExpression switchExpr = new SwitchExpression(context, operand);
            switchExpr.setBooleanMode(booleanMode);
            switchExpr.setASTNode(switchAST);
            path.add(switchExpr);
        }
        (
            {
                List caseOperands = new ArrayList<Expression>(2);
                PathExpr returnExpr = new PathExpr(context);
                returnExpr.setASTNode(expr_AST_in);
            }
             ((
               {
                   PathExpr caseOperand = new PathExpr(context);
                   caseOperand.setASTNode(expr_AST_in);
               }
                "case"
                expr [caseOperand]
                { caseOperands.add(caseOperand); }
             )+
             #(
                "return"
                step= expr [returnExpr]
                { switchExpr.addCase(caseOperands, returnExpr); }
             ))
        )+
        (
            "default"
            {
                PathExpr returnExpr = new PathExpr(context);
                returnExpr.setASTNode(expr_AST_in);
            }
            step=expr [returnExpr]
            {
                switchExpr.setDefault(returnExpr);
            }
        )
        { step = switchExpr; }
    )
    |
    // typeswitch
    #(
        "typeswitch"
        {
            PathExpr operand = new PathExpr(context);
            operand.setASTNode(expr_AST_in);
        }
        step=expr [operand]
        {
            TypeswitchExpression tswitch = new TypeswitchExpression(context, operand);
            tswitch.setASTNode(expr_AST_in);
            path.add(tswitch);
        }
        (
            {
                PathExpr returnExpr = new PathExpr(context);
                returnExpr.setASTNode(expr_AST_in);
                QName qn = null;
                List<SequenceType> types = new ArrayList<SequenceType>(2);
                SequenceType type = new SequenceType();
            }
            #(
                "case"
                (
                    var:VARIABLE_BINDING
                    {
                        try {
                            qn = QName.parse(staticContext, var.getText());
                        } catch (final IllegalQNameException iqe) {
                          throw new XPathException(var.getLine(), var.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + var.getText());
                        }
                    }
                )?
                (
                    sequenceType[type]
                    {
                        types.add(type);
                        type = new SequenceType();
                    }
                )+
                // Need return as root in following to disambiguate
                // e.g. ( case a xs:integer ( * 3 3 ) )
                // which gives xs:integer* and no operator left for 3 3 ...
                // Now ( case a xs:integer ( return ( + 3 3 ) ) ) /ljo
                #(
                    "return"
                    step= expr [returnExpr]
                    {
                        SequenceType[] atype = new SequenceType[types.size()];
                        atype = types.toArray(atype);
                        tswitch.addCase(atype, qn, returnExpr);
                    }
                )
            )

        )+
        (
            "default"
            {
                PathExpr returnExpr = new PathExpr(context);
                returnExpr.setASTNode(expr_AST_in);
                QName qn = null;
            }
            (
                dvar:VARIABLE_BINDING
                {
                    try {
                        qn = QName.parse(staticContext, dvar.getText());
                    } catch (final IllegalQNameException iqe) {
                      throw new XPathException(dvar.getLine(), dvar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + dvar.getText());
                    }
                }
            )?
            step=expr [returnExpr]
            {
                tswitch.setDefault(qn, returnExpr);
            }
        )
        { step = tswitch; }
    )
    |
    // logical operator: or
    #(
        "or"
        {
            PathExpr left= new PathExpr(context);
            left.setASTNode(expr_AST_in);
        }
        step=expr [left]
        {
            PathExpr right= new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [right]
    )
    {
        OpOr or= new OpOr(context);
        or.addPath(left);
        or.addPath(right);
        path.addPath(or);
        step = or;
    }
    |
    // logical operator: and
    #(
        "and"
        {
            PathExpr left= new PathExpr(context);
            left.setASTNode(expr_AST_in);

            PathExpr right= new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [left]
        step=expr [right]
    )
    {
        OpAnd and= new OpAnd(context);
        and.addPath(left);
        and.addPath(right);
        path.addPath(and);
        step = and;
    }
    |
    // union expressions: | and union
    #(
        UNION
        {
            PathExpr left= new PathExpr(context);
            left.setASTNode(expr_AST_in);

            PathExpr right= new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [left]
        step=expr [right]
    )
    {
        Union union= new Union(context, left, right);
        path.add(union);
        step = union;
    }
    |
    // intersections:
    #( "intersect"
        {
            PathExpr left = new PathExpr(context);
            left.setASTNode(expr_AST_in);

            PathExpr right = new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [left]
        step=expr [right]
    )
    {
        Intersect intersect = new Intersect(context, left, right);
        path.add(intersect);
        step = intersect;
    }
    |
    #( "except"
        {
            PathExpr left = new PathExpr(context);
            left.setASTNode(expr_AST_in);

            PathExpr right = new PathExpr(context);
            right.setASTNode(expr_AST_in);
        }
        step=expr [left]
        step=expr [right]
    )
    {
        Except intersect = new Except(context, left, right);
        path.add(intersect);
        step = intersect;
    }
    |
    // absolute path expression starting with a /
    #(
        ABSOLUTE_SLASH
        {
            path.setHasSlash();
            RootNode root= new RootNode(context);
            path.add(root);
        }
        ( step=expr [path] )?
    )
    |
    // absolute path expression starting with //
    #(
        ABSOLUTE_DSLASH
        {
            path.setHasSlash();
            RootNode root= new RootNode(context);
            path.add(root);
        }
        (
            step=expr [path]
            {
                if (step instanceof LocationStep) {
                    LocationStep s= (LocationStep) step;
                    if (s.getAxis() == Constants.ATTRIBUTE_AXIS ||
                        (s.getTest().getType() == Type.ATTRIBUTE && s.getAxis() == Constants.CHILD_AXIS))
                        // combines descendant-or-self::node()/attribute:*
                        s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
                    else if (s.getAxis() <= Constants.PRECEDING_SIBLING_AXIS) {
                        // Reverse axis: insert explicit descendant-or-self::node() step
                        LocationStep descStep = new LocationStep(context, Constants.DESCENDANT_SELF_AXIS, new TypeTest(Type.NODE));
                        descStep.setAbbreviated(true);
                        path.replaceLastExpression(descStep);
                        path.add(step);
                    } else {
                        s.setAxis(Constants.DESCENDANT_SELF_AXIS);
                        s.setAbbreviated(true);
                    }
                } else
                    step.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
            }
        )?
    )
    |
    // range expression: to
    #(
        "to"
        {
            PathExpr start= new PathExpr(context);
            start.setASTNode(expr_AST_in);

            PathExpr end= new PathExpr(context);
            end.setASTNode(expr_AST_in);

            List args= new ArrayList(2);
            args.add(start);
            args.add(end);
        }
        step=expr [start]
        step=expr [end]
        {
            RangeExpression range= new RangeExpression(context);
            range.setASTNode(expr_AST_in);
            range.setArguments(args);
            path.addPath(range);
            step = range;
        }
    )
    |
    step=generalComp [path]
    |
    step=valueComp [path]
    |
    step=nodeComp [path]
    |
    step=ftContainsExpr [path]
    |
    step=primaryExpr [path]
    |
    step=pathExpr [path]
    |
    step=extensionExpr [path]
    |
    step=numericExpr [path]
    |
    // Legacy update (DEPRECATED)
    step=updateExpr [path]
    |
    // W3C XQuery Update Facility 3.0
    step=xqufInsertExpr [path]
    |
    step=xqufDeleteExpr [path]
    |
    step=xqufReplaceExpr [path]
    |
    step=xqufRenameExpr [path]
    |
    step=xqufTransformExpr [path]
    ;

/**
 * Flow control expressions extracted from expr to avoid
 * Java method size limit (64KB bytecode).
 * Handles: conditional, ternary, quantified (some/every),
 * try/catch/finally, FLWOR, instance of.
 */
exprFlowControl [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ step = null; }
:
    // conditional:
    #(
        astIf:"if"
        {
            PathExpr testExpr= new PathExpr(context);
            PathExpr thenExpr= new PathExpr(context);
            PathExpr elseExpr= new PathExpr(context);
        }
        step=expr [testExpr]
        step=astThen:expr [thenExpr]
        step=astElse:expr [elseExpr]
        {
            thenExpr.setASTNode(astThen);
            elseExpr.setASTNode(astElse);
            ConditionalExpression cond =
                new ConditionalExpression(context, testExpr, thenExpr,
                                          new DebuggableExpression(elseExpr));
            cond.setASTNode(astIf);
            path.add(cond);
            step = cond;
        }
    )
    |
    // ternary conditional: condition ?? then !! else
    #(
        astTernary:TERNARY
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(astTernary, ErrorCodes.XPST0003,
                    "The ternary conditional operator (?? !!) requires xquery version \"4.0\"");
            }
            PathExpr ternTestExpr = new PathExpr(context);
            PathExpr ternThenExpr = new PathExpr(context);
            PathExpr ternElseExpr = new PathExpr(context);
        }
        step=expr [ternTestExpr]
        step=expr [ternThenExpr]
        step=expr [ternElseExpr]
        {
            ConditionalExpression ternCond =
                new ConditionalExpression(context, ternTestExpr, ternThenExpr,
                                          new DebuggableExpression(ternElseExpr));
            ternCond.setASTNode(astTernary);
            path.add(ternCond);
            step = ternCond;
        }
    )
    |
    // quantified expression: some
    #(
        "some"
        {
            List clauses= new ArrayList();
            PathExpr satisfiesExpr = new PathExpr(context);
            satisfiesExpr.setASTNode(exprFlowControl_AST_in);
        }
        (
            #(
                someVarName:VARIABLE_BINDING
                {
                    ForLetClause clause= new ForLetClause();
                    PathExpr inputSequence = new PathExpr(context);
                    inputSequence.setASTNode(exprFlowControl_AST_in);
                }
                (
                    #(
                        "as"
                        { SequenceType type= new SequenceType(); }
                        sequenceType[type]
                    )
                    { clause.sequenceType = type; }
                )?
                step=expr[inputSequence]
                {
                    try {
                        clause.varName = QName.parse(staticContext, someVarName.getText(), null);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(someVarName.getLine(), someVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + someVarName.getText());
                    }
                    clause.inputSequence= inputSequence;
                    clauses.add(clause);
                }
            )
        )*
        step=expr[satisfiesExpr]
        {
            Expression action = satisfiesExpr;
            for (int i= clauses.size() - 1; i >= 0; i--) {
                ForLetClause clause= (ForLetClause) clauses.get(i);
                BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.SOME);
                expr.setASTNode(exprFlowControl_AST_in);
                expr.setVariable(clause.varName);
                expr.setSequenceType(clause.sequenceType);
                expr.setInputSequence(clause.inputSequence);
                expr.setReturnExpression(action);
                satisfiesExpr= null;
                action= expr;
            }
            path.add(action);
            step = action;
        }
    )
    |
    // quantified expression: every
    #(
        "every"
        {
            List clauses= new ArrayList();
            PathExpr satisfiesExpr = new PathExpr(context);
            satisfiesExpr.setASTNode(exprFlowControl_AST_in);
        }
        (
            #(
                everyVarName:VARIABLE_BINDING
                {
                    ForLetClause clause= new ForLetClause();
                    PathExpr inputSequence = new PathExpr(context);
                    inputSequence.setASTNode(exprFlowControl_AST_in);
                }
                (
                    #(
                        "as"
                        { SequenceType type= new SequenceType(); }
                        sequenceType[type]
                    )
                    { clause.sequenceType = type; }
                )?
                step=expr[inputSequence]
                {
                    try {
                        clause.varName = QName.parse(staticContext, everyVarName.getText(), null);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(everyVarName.getLine(), everyVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + everyVarName.getText());
                    }
                    clause.inputSequence= inputSequence;
                    clauses.add(clause);
                }
            )
        )*
        step=expr[satisfiesExpr]
        {
            Expression action = satisfiesExpr;
            for (int i= clauses.size() - 1; i >= 0; i--) {
                ForLetClause clause= (ForLetClause) clauses.get(i);
                BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.EVERY);
                expr.setASTNode(exprFlowControl_AST_in);
                expr.setVariable(clause.varName);
                expr.setSequenceType(clause.sequenceType);
                expr.setInputSequence(clause.inputSequence);
                expr.setReturnExpression(action);
                satisfiesExpr= null;
                action= expr;
            }
            path.add(action);
            step = action;
        }
    )
    |
    //try/catch expression
    #(
        astTry:"try"
        {
            PathExpr tryTargetExpr = new PathExpr(context);
            tryTargetExpr.setASTNode(exprFlowControl_AST_in);
        }
        step=expr [tryTargetExpr]
        {
            TryCatchExpression cond = new TryCatchExpression(context, tryTargetExpr);
            cond.setASTNode(astTry);
            path.add(cond);
        }
        (
            {
                final List<QName> catchErrorList = new ArrayList<>(2);
                final List<QName> catchVars = new ArrayList<>(3);
                final PathExpr catchExpr = new PathExpr(context);
                catchExpr.setASTNode(exprFlowControl_AST_in);
            }
            #(
                astCatch:"catch"
                (catchErrorList [catchErrorList])
                (
                    {
                        QName qncode = null;
                        QName qndesc = null;
                        QName qnval = null;
                    }
                    code:CATCH_ERROR_CODE
                    {
                        try {
                            qncode = QName.parse(staticContext, code.getText());
                            catchVars.add(qncode);
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(code.getLine(), code.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + code.getText());
                        }
                    }
                    (
                        desc:CATCH_ERROR_DESC
                        {
                            try {
                                qndesc = QName.parse(staticContext, desc.getText());
                            catchVars.add(qndesc);
                            } catch (final IllegalQNameException iqe) {
                                throw new XPathException(desc.getLine(), desc.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + desc.getText());
                            }
                        }

                        (
                            val:CATCH_ERROR_VAL
                            {
                                try {
                                    qnval = QName.parse(staticContext, val.getText());
                                    catchVars.add(qnval);
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(val.getLine(), val.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + val.getText());
                                }
                            }

                        )?
                    )?
                )?
                step= expr [catchExpr]
                {
                  catchExpr.setASTNode(astCatch);
                  cond.addCatchClause(catchErrorList, catchVars, catchExpr);
                }
            )
        )*
        (
            #(
                astFinally:"finally"
                {
                    final PathExpr finallyExpr = new PathExpr(context);
                    finallyExpr.setASTNode(astFinally);
                }
                (step=expr [finallyExpr])?
                {
                    finallyExpr.setASTNode(astFinally);
                    cond.setFinallyExpr(finallyExpr);
                }
            )
        )?

        {
            step = cond;
        }
    )
    |
    // FLWOR expressions: let and for
    #(
        r:"return"
        {
            List<ForLetClause> clauses= new ArrayList<ForLetClause>();
            Expression action= new PathExpr(context);
            action.setASTNode(r);
            PathExpr whereExpr= null;
            List<OrderSpec> orderBy= null;
        }
        (
            #(
                f:"for"
                (
                    #(
                        varName:VARIABLE_BINDING
                        {
                            ForLetClause clause= new ForLetClause();
                            clause.ast = varName;
                            PathExpr inputSequence= new PathExpr(context);
                            inputSequence.setASTNode(exprFlowControl_AST_in);inputSequence.setASTNode(exprFlowControl_AST_in);
			    final DistinctVariableNames distinctVariableNames = new DistinctVariableNames();
                        }
                        (
                            #(
                                "as"
                                { clause.sequenceType= new SequenceType(); }
                                sequenceType [clause.sequenceType]
                            )
                        )?
                        (
                                "empty"
                                { clause.allowEmpty = true; }
                        )?
                        (
                            posVar:POSITIONAL_VAR
                            {
                                try {
                                    clause.posVar = distinctVariableNames.check(ErrorCodes.XQST0089, posVar, QName.parse(staticContext, posVar.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(posVar.getLine(), posVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + posVar.getText());
                                }
                            }
                        )?
                        (
                            scoreVar:FT_SCORE_VAR
                            {
                                try {
                                    clause.scoreVar = distinctVariableNames.check(ErrorCodes.XQST0089, scoreVar, QName.parse(staticContext, scoreVar.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(scoreVar.getLine(), scoreVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + scoreVar.getText());
                                }
                            }
                        )?
                        step=expr [inputSequence]
                        {
                            try {
                                clause.varName = distinctVariableNames.check(ErrorCodes.XQST0089, varName, QName.parse(staticContext, varName.getText(), null));
                            } catch (final IllegalQNameException iqe) {
                                throw new XPathException(varName.getLine(), varName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + varName.getText());
                            }
                            clause.inputSequence= inputSequence;
                            clauses.add(clause);
                        }
                    )
                    |
                    #(
                        fmAST:FOR_MEMBER
                        {
                            if (staticContext.getXQueryVersion() < 40) {
                                throw new XPathException(fmAST, ErrorCodes.XPST0003,
                                    "The 'for member' clause requires xquery version \"4.0\"");
                            }
                        }
                        #(
                            memberVarName:VARIABLE_BINDING
                            {
                                ForLetClause clause= new ForLetClause();
                                clause.ast = memberVarName;
                                clause.type = FLWORClause.ClauseType.FOR_MEMBER;
                                PathExpr inputSequence= new PathExpr(context);
                                inputSequence.setASTNode(exprFlowControl_AST_in);
                                final DistinctVariableNames memberDistinctVars = new DistinctVariableNames();
                            }
                            (
                                #(
                                    "as"
                                    { clause.sequenceType= new SequenceType(); }
                                    sequenceType [clause.sequenceType]
                                )
                            )?
                            (
                                memberPosVar:POSITIONAL_VAR
                                {
                                    try {
                                        clause.posVar = memberDistinctVars.check(ErrorCodes.XQST0089, memberPosVar, QName.parse(staticContext, memberPosVar.getText(), null));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(memberPosVar.getLine(), memberPosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + memberPosVar.getText());
                                    }
                                }
                            )?
                            step=expr [inputSequence]
                            {
                                try {
                                    clause.varName = memberDistinctVars.check(ErrorCodes.XQST0089, memberVarName, QName.parse(staticContext, memberVarName.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(memberVarName.getLine(), memberVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + memberVarName.getText());
                                }
                                clause.inputSequence= inputSequence;
                                clauses.add(clause);
                            }
                        )
                    )
                    |
                    #(
                        FOR_KEY
                        #(
                            keyVarName:VARIABLE_BINDING
                            {
                                ForLetClause clause= new ForLetClause();
                                clause.ast = keyVarName;
                                clause.type = FLWORClause.ClauseType.FOR_KEY;
                                PathExpr inputSequence= new PathExpr(context);
                                inputSequence.setASTNode(exprFlowControl_AST_in);
                                final DistinctVariableNames keyDistinctVars = new DistinctVariableNames();
                            }
                            (
                                #(
                                    "as"
                                    { clause.sequenceType= new SequenceType(); }
                                    sequenceType [clause.sequenceType]
                                )
                            )?
                            (
                                keyPosVar:POSITIONAL_VAR
                                {
                                    try {
                                        clause.posVar = keyDistinctVars.check(ErrorCodes.XQST0089, keyPosVar, QName.parse(staticContext, keyPosVar.getText(), null));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(keyPosVar.getLine(), keyPosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + keyPosVar.getText());
                                    }
                                }
                            )?
                            step=expr [inputSequence]
                            {
                                try {
                                    clause.varName = keyDistinctVars.check(ErrorCodes.XQST0089, keyVarName, QName.parse(staticContext, keyVarName.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(keyVarName.getLine(), keyVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + keyVarName.getText());
                                }
                                clause.inputSequence= inputSequence;
                                clauses.add(clause);
                            }
                        )
                    )
                    |
                    #(
                        FOR_VALUE
                        #(
                            valueVarName:VARIABLE_BINDING
                            {
                                ForLetClause clause= new ForLetClause();
                                clause.ast = valueVarName;
                                clause.type = FLWORClause.ClauseType.FOR_VALUE;
                                PathExpr inputSequence= new PathExpr(context);
                                inputSequence.setASTNode(exprFlowControl_AST_in);
                                final DistinctVariableNames valueDistinctVars = new DistinctVariableNames();
                            }
                            (
                                #(
                                    "as"
                                    { clause.sequenceType= new SequenceType(); }
                                    sequenceType [clause.sequenceType]
                                )
                            )?
                            (
                                valuePosVar:POSITIONAL_VAR
                                {
                                    try {
                                        clause.posVar = valueDistinctVars.check(ErrorCodes.XQST0089, valuePosVar, QName.parse(staticContext, valuePosVar.getText(), null));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(valuePosVar.getLine(), valuePosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + valuePosVar.getText());
                                    }
                                }
                            )?
                            step=expr [inputSequence]
                            {
                                try {
                                    clause.varName = valueDistinctVars.check(ErrorCodes.XQST0089, valueVarName, QName.parse(staticContext, valueVarName.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(valueVarName.getLine(), valueVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + valueVarName.getText());
                                }
                                clause.inputSequence= inputSequence;
                                clauses.add(clause);
                            }
                        )
                    )
                    |
                    #(
                        FOR_KEY_VALUE
                        #(
                            kvKeyVarName:VARIABLE_BINDING
                            {
                                ForLetClause clause= new ForLetClause();
                                clause.ast = kvKeyVarName;
                                clause.type = FLWORClause.ClauseType.FOR_KEY_VALUE;
                                PathExpr inputSequence= new PathExpr(context);
                                inputSequence.setASTNode(exprFlowControl_AST_in);
                                final DistinctVariableNames kvDistinctVars = new DistinctVariableNames();
                            }
                            (
                                #(
                                    "as"
                                    { clause.sequenceType= new SequenceType(); }
                                    sequenceType [clause.sequenceType]
                                )
                            )?
                            (
                                #(
                                    kvValueVar:VALUE_VAR
                                    {
                                        try {
                                            clause.valueVarName = kvDistinctVars.check(ErrorCodes.XQST0089, kvValueVar, QName.parse(staticContext, kvValueVar.getText(), null));
                                        } catch (final IllegalQNameException iqe) {
                                            throw new XPathException(kvValueVar.getLine(), kvValueVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + kvValueVar.getText());
                                        }
                                    }
                                    (
                                        #(
                                            "as"
                                            { clause.valueSequenceType = new SequenceType(); }
                                            sequenceType [clause.valueSequenceType]
                                        )
                                    )?
                                )
                            )?
                            (
                                kvPosVar:POSITIONAL_VAR
                                {
                                    try {
                                        clause.posVar = kvDistinctVars.check(ErrorCodes.XQST0089, kvPosVar, QName.parse(staticContext, kvPosVar.getText(), null));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(kvPosVar.getLine(), kvPosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + kvPosVar.getText());
                                    }
                                }
                            )?
                            step=expr [inputSequence]
                            {
                                try {
                                    clause.varName = kvDistinctVars.check(ErrorCodes.XQST0089, kvKeyVarName, QName.parse(staticContext, kvKeyVarName.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(kvKeyVarName.getLine(), kvKeyVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + kvKeyVarName.getText());
                                }
                                clause.inputSequence= inputSequence;
                                clauses.add(clause);
                            }
                        )
                    )
                )+
            )
            |
            #(
                l:"let"
                (
                    #(
                        letVarName:VARIABLE_BINDING
                        {
                            ForLetClause clause= new ForLetClause();
                            clause.ast = letVarName;
                            clause.type = FLWORClause.ClauseType.LET;
                            PathExpr inputSequence= new PathExpr(context);
                            inputSequence.setASTNode(exprFlowControl_AST_in);
                        }
                        (
                            letScoreVar:FT_SCORE_VAR
                            {
                                try {
                                    clause.scoreVar = QName.parse(staticContext, letScoreVar.getText(), null);
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(letScoreVar.getLine(), letScoreVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + letScoreVar.getText());
                                }
                            }
                        )?
                        (
                            #(
                                "as"
                                { clause.sequenceType= new SequenceType(); }
                                sequenceType [clause.sequenceType]
                            )
                        )?
                        step=expr [inputSequence]
                        {
                            try {
                                clause.varName = QName.parse(staticContext, letVarName.getText(), null);
                            } catch (final IllegalQNameException iqe) {
                                throw new XPathException(letVarName.getLine(), letVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + letVarName.getText());
                            }
                            clause.inputSequence= inputSequence;
                            clauses.add(clause);
                        }
                    )
                    |
                    // XQ4: sequence destructuring
                    #(
                        seqDestAST:SEQ_DESTRUCTURE
                        {
                            ForLetClause seqClause = new ForLetClause();
                            seqClause.ast = seqDestAST;
                            seqClause.type = FLWORClause.ClauseType.LET_SEQ_DESTRUCTURE;
                            seqClause.destructureVarNames = new ArrayList();
                            seqClause.destructureVarTypes = new ArrayList();
                            String[] seqVarNames = seqDestAST.getText().split(",", -1);
                            int seqTypedIdx = 0;
                            boolean[] seqHasType = new boolean[seqVarNames.length];
                            for (int dv = 0; dv < seqVarNames.length; dv++) {
                                String svn = seqVarNames[dv];
                                seqHasType[dv] = svn.endsWith("+");
                                if (seqHasType[dv]) svn = svn.substring(0, svn.length() - 1);
                                try {
                                    seqClause.destructureVarNames.add(
                                        QName.parse(staticContext, svn, null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(seqDestAST.getLine(), seqDestAST.getColumn(),
                                        ErrorCodes.XPST0081, "No namespace defined for prefix " + svn);
                                }
                                seqClause.destructureVarTypes.add(null);
                            }
                            PathExpr seqInput = new PathExpr(context);
                            seqInput.setASTNode(exprFlowControl_AST_in);
                        }
                        (
                            #(
                                DESTRUCTURE_VAR_TYPE
                                #(
                                    "as"
                                    {
                                        SequenceType seqVarType = new SequenceType();
                                        while (seqTypedIdx < seqHasType.length && !seqHasType[seqTypedIdx]) seqTypedIdx++;
                                    }
                                    sequenceType [seqVarType]
                                    {
                                        if (seqTypedIdx < seqClause.destructureVarTypes.size()) {
                                            seqClause.destructureVarTypes.set(seqTypedIdx, seqVarType);
                                        }
                                        seqTypedIdx++;
                                    }
                                )
                            )
                        )*
                        (
                            #(
                                "as"
                                { seqClause.sequenceType = new SequenceType(); }
                                sequenceType [seqClause.sequenceType]
                            )
                        )?
                        step=expr [seqInput]
                        {
                            seqClause.inputSequence = seqInput;
                            clauses.add(seqClause);
                        }
                    )
                    |
                    // XQ4: array destructuring
                    #(
                        arrDestAST:ARRAY_DESTRUCTURE
                        {
                            ForLetClause arrClause = new ForLetClause();
                            arrClause.ast = arrDestAST;
                            arrClause.type = FLWORClause.ClauseType.LET_ARRAY_DESTRUCTURE;
                            arrClause.destructureVarNames = new ArrayList();
                            arrClause.destructureVarTypes = new ArrayList();
                            String[] arrVarNames = arrDestAST.getText().split(",", -1);
                            int arrTypedIdx = 0;
                            boolean[] arrHasType = new boolean[arrVarNames.length];
                            for (int dv = 0; dv < arrVarNames.length; dv++) {
                                String avn = arrVarNames[dv];
                                arrHasType[dv] = avn.endsWith("+");
                                if (arrHasType[dv]) avn = avn.substring(0, avn.length() - 1);
                                try {
                                    arrClause.destructureVarNames.add(
                                        QName.parse(staticContext, avn, null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(arrDestAST.getLine(), arrDestAST.getColumn(),
                                        ErrorCodes.XPST0081, "No namespace defined for prefix " + avn);
                                }
                                arrClause.destructureVarTypes.add(null);
                            }
                            PathExpr arrInput = new PathExpr(context);
                            arrInput.setASTNode(exprFlowControl_AST_in);
                        }
                        (
                            #(
                                DESTRUCTURE_VAR_TYPE
                                #(
                                    "as"
                                    {
                                        SequenceType arrVarType = new SequenceType();
                                        while (arrTypedIdx < arrHasType.length && !arrHasType[arrTypedIdx]) arrTypedIdx++;
                                    }
                                    sequenceType [arrVarType]
                                    {
                                        if (arrTypedIdx < arrClause.destructureVarTypes.size()) {
                                            arrClause.destructureVarTypes.set(arrTypedIdx, arrVarType);
                                        }
                                        arrTypedIdx++;
                                    }
                                )
                            )
                        )*
                        (
                            #(
                                "as"
                                { arrClause.sequenceType = new SequenceType(); }
                                sequenceType [arrClause.sequenceType]
                            )
                        )?
                        step=expr [arrInput]
                        {
                            arrClause.inputSequence = arrInput;
                            clauses.add(arrClause);
                        }
                    )
                    |
                    // XQ4: map destructuring
                    #(
                        mapDestAST:MAP_DESTRUCTURE
                        {
                            ForLetClause mapClause = new ForLetClause();
                            mapClause.ast = mapDestAST;
                            mapClause.type = FLWORClause.ClauseType.LET_MAP_DESTRUCTURE;
                            mapClause.destructureVarNames = new ArrayList();
                            mapClause.destructureVarTypes = new ArrayList();
                            String[] mapVarNames = mapDestAST.getText().split(",", -1);
                            int mapTypedIdx = 0;
                            boolean[] mapHasType = new boolean[mapVarNames.length];
                            for (int dv = 0; dv < mapVarNames.length; dv++) {
                                String mvn = mapVarNames[dv];
                                mapHasType[dv] = mvn.endsWith("+");
                                if (mapHasType[dv]) mvn = mvn.substring(0, mvn.length() - 1);
                                try {
                                    mapClause.destructureVarNames.add(
                                        QName.parse(staticContext, mvn, null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(mapDestAST.getLine(), mapDestAST.getColumn(),
                                        ErrorCodes.XPST0081, "No namespace defined for prefix " + mvn);
                                }
                                mapClause.destructureVarTypes.add(null);
                            }
                            PathExpr mapInput = new PathExpr(context);
                            mapInput.setASTNode(exprFlowControl_AST_in);
                        }
                        (
                            #(
                                DESTRUCTURE_VAR_TYPE
                                #(
                                    "as"
                                    {
                                        SequenceType mapVarType = new SequenceType();
                                        while (mapTypedIdx < mapHasType.length && !mapHasType[mapTypedIdx]) mapTypedIdx++;
                                    }
                                    sequenceType [mapVarType]
                                    {
                                        if (mapTypedIdx < mapClause.destructureVarTypes.size()) {
                                            mapClause.destructureVarTypes.set(mapTypedIdx, mapVarType);
                                        }
                                        mapTypedIdx++;
                                    }
                                )
                            )
                        )*
                        (
                            #(
                                "as"
                                { mapClause.sequenceType = new SequenceType(); }
                                sequenceType [mapClause.sequenceType]
                            )
                        )?
                        step=expr [mapInput]
                        {
                            mapClause.inputSequence = mapInput;
                            clauses.add(mapClause);
                        }
                    )
                )+
            )
            |
	    #(
            	wc:"window"
            	{
            	    ForLetClause clause= new ForLetClause();
                    clause.type = FLWORClause.ClauseType.WINDOW;
                    clause.windowConditions = new ArrayList<WindowCondition>(2);
                    final DistinctVariableNames distinctVariableNames = new DistinctVariableNames();
            	}
            	(
                    "tumbling"
                    {
                        clause.windowType = WindowExpr.WindowType.TUMBLING_WINDOW;
                    }
                    |
                    "sliding"
                    {
                        clause.windowType = WindowExpr.WindowType.SLIDING_WINDOW;
                    }
                )?
            	// invarBinding
            	(
            		#(
            			windowWarName:VARIABLE_BINDING
            			{
            				clause.ast = windowWarName;
            				PathExpr inputSequence= new PathExpr(context);
            			}
            			(
            				#(
            					"as"
            					{ clause.sequenceType= new SequenceType(); }
            					sequenceType [clause.sequenceType]
            				)
            			)?
            			step=expr [inputSequence]
            			{
            			  try {
            				  clause.varName = distinctVariableNames.check(ErrorCodes.XQST0103, windowWarName, QName.parse(staticContext, windowWarName.getText(), null));
            				} catch (final IllegalQNameException iqe) {
                      throw new XPathException(windowWarName.getLine(), windowWarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + windowWarName.getText());
                    }
            				clause.inputSequence= inputSequence;
            				clauses.add(clause);
            			}
            		)
            	)
            	// windowStartCondition
            	#(
            	    "start"
            	    {
            	        PathExpr whenExpr = new PathExpr(context);
                        QName currentItemName = null;
                        QName previousItemName = null;
                        QName nextItemName = null;
                        QName windowStartPosVar = null;
            	    }
            	    #(
                    	// WINDOW_VARS
                    	WINDOW_VARS
                    	(
                    	    currentItem:CURRENT_ITEM
                    	    {
                    	        if (currentItem != null && currentItem.getText() != null) {
                    	            try {
                                        currentItemName = distinctVariableNames.check(ErrorCodes.XQST0103, currentItem, QName.parse(staticContext, currentItem.getText()));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(currentItem.getLine(), currentItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + currentItem.getText());
                                    }
                                }
                    	    }
                    	)?
                    	(
                    	    startPosVar:POSITIONAL_VAR
                            {
                                try {
                                    windowStartPosVar = distinctVariableNames.check(ErrorCodes.XQST0103, startPosVar, QName.parse(staticContext, startPosVar.getText(), null));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(startPosVar.getLine(), startPosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + startPosVar.getText());
                                }
                            }
                    	)?
                    	(
                            previousItem:PREVIOUS_ITEM
                            {
                                if (previousItem != null && previousItem.getText() != null) {
                                    try {
                                       previousItemName = distinctVariableNames.check(ErrorCodes.XQST0103, previousItem, QName.parse(staticContext, previousItem.getText()));
                                   } catch (final IllegalQNameException iqe) {
                                       throw new XPathException(previousItem.getLine(), previousItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + previousItem.getText());
                                   }
                                }
                            }
                        )?
                        (
                            nextItem:NEXT_ITEM
                            {
                                if (nextItem != null && nextItem.getText() != null) {
                                    try {
                                           nextItemName = distinctVariableNames.check(ErrorCodes.XQST0103, nextItem, QName.parse(staticContext, nextItem.getText()));
                                    } catch (final IllegalQNameException iqe) {
                                           throw new XPathException(nextItem.getLine(), nextItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + nextItem.getText());
                                    }
                                }
                            }
                        )?
                    )
                    "when"
                    step=expr [whenExpr]
                    {
                        WindowCondition windowCondition = new WindowCondition(
                        	context, false, currentItemName, windowStartPosVar, previousItemName, nextItemName, whenExpr
                    	);
                    	clause.windowConditions.add(windowCondition);
                    }
            	)
            	// windowEndCondition
            	(
                    {
                         PathExpr endWhenExpr = new PathExpr(context);
                         QName endCurrentItemName = null;
                         QName endPreviousItemName = null;
                         QName endNextItemName = null;
                         QName windowEndPosVar = null;
                         Boolean only = false;
                    }
                    #(
                        "end"
                        (
                            "only"
                            {
                                only = true;
                            }
                        )?
                        #(
                            // WINDOW_VARS
                            WINDOW_VARS
                           	(
                           	    endCurrentItem:CURRENT_ITEM
                           	    {
                           	        if (endCurrentItem != null && endCurrentItem.getText() != null) {
                           	            try {
                                               endCurrentItemName = distinctVariableNames.check(ErrorCodes.XQST0103, endCurrentItem, QName.parse(staticContext, endCurrentItem.getText()));
                                           } catch (final IllegalQNameException iqe) {
                                               throw new XPathException(endCurrentItem.getLine(), endCurrentItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + endCurrentItem.getText());
                                           }
                                       }
                           	    }
                           	)?
                           	(
                           	    endPosVar:POSITIONAL_VAR
                                {
                                    try {
                                        windowEndPosVar = distinctVariableNames.check(ErrorCodes.XQST0103, endPosVar, QName.parse(staticContext, endPosVar.getText(), null));
                                    } catch (final IllegalQNameException iqe) {
                                        throw new XPathException(endPosVar.getLine(), endPosVar.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + endPosVar.getText());
                                    }
                                }
                           	)?
                           	(
                                endPreviousItem:PREVIOUS_ITEM
                                {
                                    if (endPreviousItem != null && endPreviousItem.getText() != null) {
                                       try {
                                            endPreviousItemName = distinctVariableNames.check(ErrorCodes.XQST0103, endPreviousItem, QName.parse(staticContext, endPreviousItem.getText()));
                                       } catch (final IllegalQNameException iqe) {
                                           throw new XPathException(endPreviousItem.getLine(), endPreviousItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + endPreviousItem.getText());
                                       }
                                    }
                                }
                            )?
                            (
                                endNextItem:NEXT_ITEM
                                {
                                    if (endNextItem != null && endNextItem.getText() != null) {
                                        try {
                                            endNextItemName = distinctVariableNames.check(ErrorCodes.XQST0103, endNextItem, QName.parse(staticContext, endNextItem.getText()));
                                        } catch (final IllegalQNameException iqe) {
                                               throw new XPathException(endNextItem.getLine(), endNextItem.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + endNextItem.getText());
                                        }
                                    }
                                }
                            )?
                        )
                        "when"
                        step=expr [endWhenExpr]
                        {
                        	WindowCondition endWindowCondition = new WindowCondition(
                            	context, only, endCurrentItemName, windowEndPosVar, endPreviousItemName, endNextItemName, endWhenExpr
                        	);
                            clause.windowConditions.add(endWindowCondition);
                        }
                    )
                )?
            )
			|
      // XQuery 3.0 group by clause
      #(
          gb:GROUP_BY
          {
              ForLetClause clause = new ForLetClause();
              clause.ast = gb;
              clause.type = FLWORClause.ClauseType.GROUPBY;
              clause.groupSpecs = new ArrayList<GroupSpec>(4);
              clauses.add(clause);
          }
          (
              #(
                  groupVarName:VARIABLE_BINDING
                  { PathExpr groupSpecExpr = null; }
                  (
                    (
                      #(
                        "as"
                        { clause.sequenceType = new SequenceType(); }
                        sequenceType [clause.sequenceType]
                      )
                    )?
                    // optional := exprSingle
                    (
                      {
                          groupSpecExpr = new PathExpr(context);
                          groupSpecExpr.setASTNode(exprFlowControl_AST_in);
                      }
                        step=expr [groupSpecExpr]
                    )
                  )?
                  {
                      final QName groupKeyVar;
                      try {
                        groupKeyVar = QName.parse(staticContext, groupVarName.getText(), null);
                      } catch (final IllegalQNameException iqe) {
                          throw new XPathException(groupVarName.getLine(), groupVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + groupVarName.getText());
                      }

                      GroupSpec groupSpec = new GroupSpec(context, groupSpecExpr, groupKeyVar, clause.sequenceType);
                      clause.groupSpecs.add(groupSpec);
                  }
                  (
                      "collation" groupCollURI:STRING_LITERAL
                      {
                          groupSpec.setCollator(groupCollURI.getText());
                      }
                  )?
              )
          )+
      )
      |
      #(
          ob:ORDER_BY { orderBy = new ArrayList(3); }
          (
              {
                  PathExpr orderSpecExpr= new PathExpr(context);
                  orderSpecExpr.setASTNode(exprFlowControl_AST_in);
              }
              step=expr [orderSpecExpr]
              {
                  OrderSpec orderSpec= new OrderSpec(context, orderSpecExpr);
                  int modifiers= 0;
                  boolean orderDescending = false;
                  orderBy.add(orderSpec);

                  if (!context.orderEmptyGreatest()) {
                      modifiers |= OrderSpec.EMPTY_LEAST;
                      orderSpec.setModifiers(modifiers);
                  }
              }
              (
                  (

                      "ascending"
                      |
                      "descending"
                      {
                          modifiers |= OrderSpec.DESCENDING_ORDER;
                          orderSpec.setModifiers(modifiers);
                          orderDescending = true;
                      }
                  )
              )?
              (
                  "empty"
                  (
                      "greatest"
                      {
                          if (!context.orderEmptyGreatest())
                              modifiers &= OrderSpec.EMPTY_GREATEST;
                          if (orderDescending)
                              modifiers |= OrderSpec.DESCENDING_ORDER;
                          orderSpec.setModifiers(modifiers);
                      }
                      |
                      "least"
                      {
                          modifiers |= OrderSpec.EMPTY_LEAST;
                          orderSpec.setModifiers(modifiers);
                      }
                  )
              )?
              (
                  "collation" collURI:STRING_LITERAL
                  {
                      orderSpec.setCollation(collURI.getText());
                  }
              )?
          )+
          {
              ForLetClause clause= new ForLetClause();
              clause.ast = ob;
              clause.type = FLWORClause.ClauseType.ORDERBY;
                            clause.orderSpecs = orderBy;
              clauses.add(clause);
          }
      )
            |
            #(
                w:"where"
                {
                    whereExpr= new PathExpr(context);
                    whereExpr.setASTNode(exprFlowControl_AST_in);
                }
                step=expr [whereExpr]
                {
                    ForLetClause clause = new ForLetClause();
                    clause.ast = w;
                    clause.type = FLWORClause.ClauseType.WHERE;
                    clause.inputSequence = whereExpr;
                    clauses.add(clause);
                }
            )
            |
            #(
                wh:"while"
                {
                    if (staticContext.getXQueryVersion() < 40) {
                        throw new XPathException(wh, ErrorCodes.XPST0003,
                            "The 'while' clause requires xquery version \"4.0\"");
                    }
                    PathExpr whileExpr = new PathExpr(context);
                    whileExpr.setASTNode(exprFlowControl_AST_in);
                }
                step=expr [whileExpr]
                {
                    ForLetClause clause = new ForLetClause();
                    clause.ast = wh;
                    clause.type = FLWORClause.ClauseType.WHILE;
                    clause.inputSequence = whileExpr;
                    clauses.add(clause);
                }
            )
            |
            #(
            	co:"count"
            	countVarName:VARIABLE_BINDING
                {
                    ForLetClause clause = new ForLetClause();
                    clause.ast = co;
                    try {
                        clause.varName = QName.parse(staticContext, countVarName.getText(), null);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(countVarName.getLine(), countVarName.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + countVarName.getText());
                    }
                    clause.type = FLWORClause.ClauseType.COUNT;
                    clause.inputSequence = null;
                    clauses.add(clause);
                }
            )
		)+
		step=expr [(PathExpr) action]
		{
            for (int i= clauses.size() - 1; i >= 0; i--) {
                ForLetClause clause= (ForLetClause) clauses.get(i);
                FLWORClause expr;
                switch (clause.type) {
                    case LET:
                        expr = new LetExpr(context);
                        expr.setASTNode(exprFlowControl_AST_in);
                        break;
                    case GROUPBY:
                        expr = new GroupByClause(context);
                        break;
                    case ORDERBY:
                        expr = new OrderByClause(context, clause.orderSpecs);
                        break;
                    case WHERE:
                        expr = new WhereClause(context, new DebuggableExpression(clause.inputSequence));
                        break;
                    case WHILE:
                        expr = new WhileClause(context, new DebuggableExpression(clause.inputSequence));
                        break;
                    case COUNT:
                        expr = new CountClause(context, clause.varName);
                        break;
                    case WINDOW:
                        expr = new WindowExpr(context, clause.windowType, clause.windowConditions.get(0), clause.windowConditions.size() > 1 ? clause.windowConditions.get(1) : null);
                        break;
                    case FOR_MEMBER:
                        expr = new ForMemberExpr(context);
                        break;
                    case FOR_KEY:
                        expr = new ForKeyValueExpr(context, FLWORClause.ClauseType.FOR_KEY);
                        break;
                    case FOR_VALUE:
                        expr = new ForKeyValueExpr(context, FLWORClause.ClauseType.FOR_VALUE);
                        break;
                    case FOR_KEY_VALUE:
                        expr = new ForKeyValueExpr(context, FLWORClause.ClauseType.FOR_KEY_VALUE);
                        break;
                    case LET_SEQ_DESTRUCTURE:
                    case LET_ARRAY_DESTRUCTURE:
                    case LET_MAP_DESTRUCTURE:
                    {
                        LetDestructureExpr.DestructureMode dmode;
                        if (clause.type == FLWORClause.ClauseType.LET_SEQ_DESTRUCTURE) {
                            dmode = LetDestructureExpr.DestructureMode.SEQUENCE;
                        } else if (clause.type == FLWORClause.ClauseType.LET_ARRAY_DESTRUCTURE) {
                            dmode = LetDestructureExpr.DestructureMode.ARRAY;
                        } else {
                            dmode = LetDestructureExpr.DestructureMode.MAP;
                        }
                        LetDestructureExpr dexpr = new LetDestructureExpr(context, dmode);
                        dexpr.setASTNode(clause.ast);
                        for (int j = 0; j < clause.destructureVarNames.size(); j++) {
                            dexpr.addVariable(
                                (QName) clause.destructureVarNames.get(j),
                                clause.destructureVarTypes.size() > j ?
                                    (SequenceType) clause.destructureVarTypes.get(j) : null);
                        }
                        dexpr.setInputSequence(clause.inputSequence);
                        if (clause.sequenceType != null) {
                            dexpr.setOverallType(clause.sequenceType);
                        }
                        expr = dexpr;
                        break;
                    }
                    default:
                        expr = new ForExpr(context, clause.allowEmpty);
                        break;
                }
                expr.setASTNode(clause.ast);
                if (clause.type == FLWORClause.ClauseType.FOR || clause.type == FLWORClause.ClauseType.LET
                		|| clause.type == FLWORClause.ClauseType.WINDOW
                		|| clause.type == FLWORClause.ClauseType.FOR_MEMBER
                		|| clause.type == FLWORClause.ClauseType.FOR_KEY
                		|| clause.type == FLWORClause.ClauseType.FOR_VALUE
                		|| clause.type == FLWORClause.ClauseType.FOR_KEY_VALUE) {
                    final BindingExpression bind = (BindingExpression)expr;
            bind.setVariable(clause.varName);
            bind.setSequenceType(clause.sequenceType);
            bind.setInputSequence(clause.inputSequence);
            if (clause.type == FLWORClause.ClauseType.FOR) {
                 ((ForExpr) bind).setPositionalVariable(clause.posVar);
                 if (clause.scoreVar != null) {
                     ((ForExpr) bind).setScoreVariable(clause.scoreVar);
                 }
            } else if (clause.type == FLWORClause.ClauseType.FOR_MEMBER) {
                 ((ForMemberExpr) bind).setPositionalVariable(clause.posVar);
            } else if (clause.type == FLWORClause.ClauseType.FOR_KEY
                 || clause.type == FLWORClause.ClauseType.FOR_VALUE
                 || clause.type == FLWORClause.ClauseType.FOR_KEY_VALUE) {
                 ((ForKeyValueExpr) bind).setPositionalVariable(clause.posVar);
                 if (clause.valueVarName != null) {
                     ((ForKeyValueExpr) bind).setValueVariable(clause.valueVarName);
                     if (clause.valueSequenceType != null) {
                         ((ForKeyValueExpr) bind).setValueSequenceType(clause.valueSequenceType);
                     }
                 }
						 }
                 if (clause.type == FLWORClause.ClauseType.LET && clause.scoreVar != null) {
                     ((LetExpr) bind).setScoreBinding(true);
                 }
				} else if (clause.type == FLWORClause.ClauseType.GROUPBY) {
				    if (clause.groupSpecs != null) {
                GroupSpec specs[] = new GroupSpec[clause.groupSpecs.size()];
                int k = 0;
                for (GroupSpec groupSpec : clause.groupSpecs) {
                    specs[k++]= groupSpec;
                }
                ((GroupByClause)expr).setGroupSpecs(specs);
            }
            }
        if (!(action instanceof FLWORClause))
            expr.setReturnExpression(new DebuggableExpression(action));
        else {
            expr.setReturnExpression(action);
            ((FLWORClause)action).setPreviousClause(expr);
        }

                action= expr;
            }

            path.add(action);
            step = action;
        }
    )
    |
    // instance of:
    #(
        "instance"
        {
            PathExpr expr = new PathExpr(context);
            expr.setASTNode(exprFlowControl_AST_in);
            SequenceType type= new SequenceType();
        }
        step=expr [expr]
        sequenceType [type]
        {
            step = new InstanceOfExpression(context, expr, type);
            step.setASTNode(exprFlowControl_AST_in);
            path.add(step);
        }
    )
    ;

/**
 * Process a primary expression like function calls,
 * variable references, value constructors etc.
 */
primaryExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ step = null; }
:
    step=constructor [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=mapConstr [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=arrayConstr [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    s:SELF
    {
        step= new ContextItemExpression(context);
        step.setASTNode(s);
    }
    step=postfixExpr [step]
    { path.add(step); }
    |
    #(
        PARENTHESIZED
        {
            PathExpr pathExpr= new PathExpr(context);
            pathExpr.setASTNode(primaryExpr_AST_in);
        }
        (
            expr [pathExpr]
            {
                // simplify the expression
                switch (pathExpr.getSubExpressionCount()) {
                    case 0:
                        step = new EmptySequenceExpr(context);
                        step.setASTNode(primaryExpr_AST_in);
                        break;
                    case 1:
                        step = pathExpr.getSubExpression(0);
                        break;
                    default:
                        step = pathExpr;
                        break;
                }
            }
        )?
    )
    step=postfixExpr [pathExpr]
    { path.add(step); }
    |
    step=literalExpr [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    v:VARIABLE_REF
    {
        final QName vrqn;
        try {
            vrqn = QName.parse(staticContext, v.getText(), null);
        } catch (final IllegalQNameException iqe) {
          throw new XPathException(v.getLine(), v.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + v.getText());
        }
        step= new VariableReference(context, vrqn);
        step.setASTNode(v);
    }
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=functionCall [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=functionReference [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    ql:QNAME_LITERAL
    {
        final String qlText = ql.getText();
        final QName qlQName;
        try {
            qlQName = QName.parse(staticContext, qlText);
        } catch (final IllegalQNameException iqe) {
            throw new XPathException(ql.getLine(), ql.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + qlText);
        }
        step = new LiteralValue(context, new QNameValue(context, qlQName));
        step.setASTNode(ql);
    }
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=inlineFunctionDecl [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    step=focusFunctionDecl [path]
    step=postfixExpr [step]
    { path.add(step); }
    |
    step = lookup [null]
    step=postfixExpr [step]
    { path.add(step); }
    |
    #(
        stAST:STRING_TEMPLATE
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(stAST, ErrorCodes.XPST0003,
                    "String templates require xquery version \"4.0\"");
            }
            StringConstructor st = new StringConstructor(context);
            st.setASTNode(stAST);
        }
        (
            stContent:STRING_TEMPLATE_CONTENT
            {
                // Unescape {{ -> {, }} -> }, `` -> `
                String raw = stContent.getText();
                raw = raw.replace("{{", "{").replace("}}", "}").replace("``", "`");
                st.addContent(raw);
            }
            |
            {
                PathExpr stInterpolation = new PathExpr(context);
                stInterpolation.setASTNode(primaryExpr_AST_in);
            }
            expr[stInterpolation]
            {
                st.addInterpolation(stInterpolation.simplify());
            }
        )*
        {
            path.add(st);
            step = st;
        }
    )
    |
    #(
        scAST:STRING_CONSTRUCTOR_START
        {
            StringConstructor sc = new StringConstructor(context);
            sc.setASTNode(scAST);
        }
        (
            content:STRING_CONSTRUCTOR_CONTENT
            {
                sc.addContent(content.getText());
            }
            |
            #( i:STRING_CONSTRUCTOR_INTERPOLATION_START
                {
                    PathExpr interpolation = new PathExpr(context);
                    interpolation.setASTNode(primaryExpr_AST_in);
                }
                (
                    expr[interpolation]
                    {
                        sc.addInterpolation(interpolation.simplify());
                    }
                )?
            )
        )*
        {
            path.add(sc);
            step = sc;
        }
    )
    ;

pathExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    Expression rightStep= null;
    step= null;
    int axis= Constants.CHILD_AXIS;
    boolean axisGiven = false;
}
:
    ( axis=forwardAxis { axisGiven = true; })?
    {
        NodeTest test = null;
        XQueryAST ast = null;
    }
    (
        eq:EQNAME
        {
            try {
                QName qname= QName.parse(staticContext, eq.getText());
                if (axis == Constants.ATTRIBUTE_AXIS) {
                    qname = new QName(qname, ElementValue.ATTRIBUTE);
                    test= new NameTest(Type.ATTRIBUTE, qname);

                } else {
                    test= new NameTest(Type.ELEMENT, qname);
                }
                ast = eq;
            } catch (final IllegalQNameException iqe) {
                throw new XPathException(eq.getLine(), eq.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq.getText());
            }
        }
        |
        #( PREFIX_WILDCARD nc1:NCNAME )
        {
            try {
                QName qname= new QName.WildcardNamespaceURIQName(nc1.getText());
                test= new NameTest(Type.ELEMENT, qname);
                if (axis == Constants.ATTRIBUTE_AXIS)
                    test.setType(Type.ATTRIBUTE);
                ast = nc1;
            } catch(XPathException ex2) {
                ex2.setLocation(nc1.getLine(), nc1.getColumn());
            }
        }
        |
        #( nc:NCNAME WILDCARD )
        {
            try {
                String namespaceURI= staticContext.getURIForPrefix(nc.getText());
                QName qname= new QName.WildcardLocalPartQName(namespaceURI, nc.getText());
                test= new NameTest(Type.ELEMENT, qname);
                if (axis == Constants.ATTRIBUTE_AXIS)
                    test.setType(Type.ATTRIBUTE);
                ast = nc;
            } catch(XPathException ex3) {
                ex3.setLocation(nc1.getLine(), nc1.getColumn());
            }
        }
        |
        w:WILDCARD
        {
            if (axis == Constants.ATTRIBUTE_AXIS)
                test= new TypeTest(Type.ATTRIBUTE);
            else
                test= new TypeTest(Type.ELEMENT);
            ast = w;
        }
        |
        n:"node"
        {
            if (axis == Constants.ATTRIBUTE_AXIS) {
            //    throw new XPathException(n, "Cannot test for node() on the attribute axis");
               test= new TypeTest(Type.ATTRIBUTE);
            } else {
               test= new AnyNodeTest();
            }
            ast = n;
        }
        |
        t:"text"
        {
            if (axis == Constants.ATTRIBUTE_AXIS)
                throw new XPathException(t, "Cannot test for text() on the attribute axis");
            test= new TypeTest(Type.TEXT);
            ast = t;
        }
        |
        #( e:"element"
            {
                if (axis == Constants.ATTRIBUTE_AXIS)
                    throw new XPathException(e, "Cannot test for element() on the attribute axis");
                test= new TypeTest(Type.ELEMENT);
                ast = e;
            }
            (
                eq2:EQNAME
                {
                    try {
                        QName qname= QName.parse(staticContext, eq2.getText());
                        test= new NameTest(Type.ELEMENT, qname);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(eq2.getLine(), eq2.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq2.getText());
                    }
                }
                |
                WILDCARD
                ( eq21:EQNAME
                    {
                        try {
                            QName qname= QName.parse(staticContext, eq21.getText());
                            test = new TypeTest(Type.getType(qname));
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(eq21.getLine(), eq21.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq21.getText());
                        }
                    }
                )?
            )?
        )
        |
        #( att:ATTRIBUTE_TEST
            {
                test= new TypeTest(Type.ATTRIBUTE);
                ast = att;

                if (!axisGiven) {
                    axis= Constants.ATTRIBUTE_AXIS;
                }
            }
            (
                eq3:EQNAME
                {
                    try {
                        QName qname = QName.parse(staticContext, eq3.getText());
                        qname = new QName(qname, ElementValue.ATTRIBUTE);
                        test= new NameTest(Type.ATTRIBUTE, qname);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(eq3.getLine(), eq3.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq3.getText());
                    }
                }
                |
                WILDCARD
                ( eq31:EQNAME
                    {
                        try {
                            QName qname= QName.parse(staticContext, eq31.getText());
                            test = new TypeTest(Type.getType(qname));
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(eq31.getLine(), eq31.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq31.getText());
                        }
                    }
                )?
            )?
        )
        |
        com:"comment"
        {
            if (axis == Constants.ATTRIBUTE_AXIS)
                throw new XPathException(n, "Cannot test for comment() on the attribute axis");
            test= new TypeTest(Type.COMMENT);
            ast = com;
        }
        |
        #( pi:"processing-instruction"
        {
            if (axis == Constants.ATTRIBUTE_AXIS)
                throw new XPathException(n, "Cannot test for processing-instruction() on the attribute axis");
            test= new TypeTest(Type.PROCESSING_INSTRUCTION);
            ast = pi;
        }
            (
                ncpi:NCNAME
                {
                    QName qname;
                    qname= new QName(ncpi.getText(), "", null);
                    test= new NameTest(Type.PROCESSING_INSTRUCTION, qname);
                }
                |
                slpi:STRING_LITERAL
                {
                    QName qname;
                    qname= new QName(slpi.getText(), "", null);
                    test= new NameTest(Type.PROCESSING_INSTRUCTION, qname);
                }
            )?
        )
        |
        nt:"namespace-node"
        {
            if (axis == Constants.ATTRIBUTE_AXIS)
                throw new XPathException(n, "Cannot test for namespace-node() on the attribute axis");
            test= new TypeTest(Type.NAMESPACE);
            ast = nt;
        }
        |
        dn:"document-node"
        {
            test= new TypeTest(Type.DOCUMENT);
            ast = dn;
        }
            (
                #( "element"
                    (
                    dneq:EQNAME
                        {
                            try {
                                QName qname= QName.parse(staticContext, dneq.getText());
                                test= new NameTest(Type.DOCUMENT, qname);
                            } catch (final IllegalQNameException iqe) {
                                throw new XPathException(dneq.getLine(), dneq.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + dneq.getText());
                            }
                        }
                    |
                    WILDCARD
                        ( dneq1:EQNAME
                            {
                                try {
                                    QName qname= QName.parse(staticContext, dneq1.getText());
                                    test= new TypeTest(Type.getType(qname));
                                } catch (final IllegalQNameException iqe) {
                                    throw new XPathException(dneq1.getLine(), dneq1.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + dneq1.getText());
                                }
                            }
                        )?
                    )?
                )
                |
                #( "schema-element" EQNAME )
            )?
    // === XQuery 4.0 JNode Kind Tests in path steps (version-gated) ===
    |
    jn1:JSON_NODE_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn1, ErrorCodes.XPST0003, "json-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_NODE); ast = jn1;
    }
    |
    jn2:JSON_OBJECT_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn2, ErrorCodes.XPST0003, "object-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_OBJECT); ast = jn2;
    }
    |
    jn3:JSON_ARRAY_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn3, ErrorCodes.XPST0003, "array-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_ARRAY); ast = jn3;
    }
    |
    jn4:JSON_STRING_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn4, ErrorCodes.XPST0003, "string-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_STRING); ast = jn4;
    }
    |
    jn5:JSON_NUMBER_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn5, ErrorCodes.XPST0003, "number-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_NUMBER); ast = jn5;
    }
    |
    jn6:JSON_BOOLEAN_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn6, ErrorCodes.XPST0003, "boolean-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_BOOLEAN); ast = jn6;
    }
    |
    jn7:JSON_NULL_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn7, ErrorCodes.XPST0003, "null-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_NULL); ast = jn7;
    }
    |
    jn8:JSON_MEMBER_TEST
    {
        if (staticContext.getXQueryVersion() < 40) {
            throw new XPathException(jn8, ErrorCodes.XPST0003, "member-node() requires xquery version \"4.0\"");
        }
        test = new TypeTest(Type.JSON_MEMBER); ast = jn8;
    }
    // === End XQuery 4.0 JNode Kind Tests ===
    )
    {
        step= new LocationStep(context, axis, test);
        path.add(step);
        if (ast != null)
            step.setASTNode(ast);
    }
    ( predicate [(LocationStep) step] )*
    |
    at:AT
    { NodeTest test = null; }
    (
        attr:EQNAME
        {
          try {
            QName qname = QName.parse(staticContext, attr.getText(), "");
            qname = new QName(qname, ElementValue.ATTRIBUTE);
            test = new NameTest(Type.ATTRIBUTE, qname);
          } catch (final IllegalQNameException iqe) {
              throw new XPathException(attr.getLine(), attr.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + attr.getText());
          }
        }
        |
        #( PREFIX_WILDCARD nc2:NCNAME )
        {
          final QName qname = new QName.WildcardNamespaceURIQName(nc2.getText(), ElementValue.ATTRIBUTE);
          test = new NameTest(Type.ATTRIBUTE, qname);
        }
        |
        #( nc3:NCNAME WILDCARD )
        {
            final String namespaceURI = staticContext.getURIForPrefix(nc3.getText());
            if (namespaceURI == null) {
                throw new EXistException("No namespace defined for prefix " + nc3.getText());
            }
            final QName qname = new QName.WildcardLocalPartQName(namespaceURI, ElementValue.ATTRIBUTE);
            test = new NameTest(Type.ATTRIBUTE, qname);
        }
        |
        WILDCARD
        {
            test = new TypeTest(Type.ATTRIBUTE);
        }
        |
        adn:"document-node"
        {
            test = FailTest.INSTANCE;
            // ast = adn;
        }
            (
                #( "element"
                    (
                    adneq:EQNAME
                    |
                    WILDCARD
                    ( adneq1:EQNAME)?
                    )?
                )
                |
                #( "schema-element" EQNAME )
            )?
        |
        #( ae:"element"
            {
                test = FailTest.INSTANCE;
                // ast = ae;
            }
            (
                aeq2:EQNAME
                |
                WILDCARD
                ( aeq21:EQNAME )?
            )?
        )
        |
        #( aatt:ATTRIBUTE_TEST
            {
                test = new TypeTest(Type.ATTRIBUTE);
                // ast = aatt;
            }
            (
                aeq3:EQNAME
                {
                    try {
                        QName qname = QName.parse(staticContext, eq3.getText());
                        qname = new QName(qname, ElementValue.ATTRIBUTE);
                        test = new NameTest(Type.ATTRIBUTE, qname);
                    } catch (final IllegalQNameException iqe) {
                        throw new XPathException(eq3.getLine(), eq3.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq3.getText());
                    }
                }
                |
                WILDCARD
                ( aeq31:EQNAME
                    {
                        try {
                            QName qname = QName.parse(staticContext, eq31.getText());
                            qname = new QName(qname, ElementValue.ATTRIBUTE);
                            test = new TypeTest(Type.getType(qname));
                        } catch (final IllegalQNameException iqe) {
                            throw new XPathException(eq31.getLine(), eq31.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + eq31.getText());
                        }
                    }
                )?
            )?
        )
        |
        #( aapi:"processing-instruction"
        {
            test = FailTest.INSTANCE;
            // ast = aapi;
        }
            (
                ancpi:NCNAME
                |
                aslpi:STRING_LITERAL
            )?
        )
        |
        acom:"comment"
        {
            test = FailTest.INSTANCE;
            // ast = acom;
        }
        |
        aat:"text"
        {
            test = FailTest.INSTANCE;
            // ast = aat;
        }
        |
        ant:"namespace-node"
        {
            test = FailTest.INSTANCE;
            // ast = ant;
        }
        |
        an:"node"
        {
            test = new TypeTest(Type.ATTRIBUTE);
            // ast = an;
        }
    )
    {
        step = new LocationStep(context, Constants.ATTRIBUTE_AXIS, test);
        step.setASTNode(at);
        path.add(step);
    }
    ( predicate [(LocationStep) step] )*
    |
    SELF
    {
        step = new ContextItemExpression(context);
        step.setASTNode(pathExpr_AST_in);
        path.add(step);
    }
    ( predicate [(LocationStep) step] )*
    |
    PARENT
    {
        step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
        path.add(step);
    }
    ( predicate [(LocationStep) step] )*
    |
    #(
        BANG
        {
            PathExpr left = new PathExpr(context);
            left.setASTNode(pathExpr_AST_in);

            PathExpr right = new PathExpr(context);
            right.setASTNode(pathExpr_AST_in);
        }
        expr [left] expr [right]
        {
            OpSimpleMap map = new OpSimpleMap(context, left, right);
            path.add(map);
            step = path;
        }
    )
    |
    #(
        SLASH step=expr [path]
        {
            path.setHasSlash();
        }
        (
            rightStep=expr [path]
            {
                if (rightStep instanceof LocationStep) {
                    if(((LocationStep) rightStep).getAxis() == Constants.UNKNOWN_AXIS)
                        ((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
                } else {
                    if (rightStep.getPrimaryAxis() == Constants.UNKNOWN_AXIS)
                        rightStep.setPrimaryAxis(Constants.CHILD_AXIS);
                    if(rightStep instanceof VariableReference) {
                        rightStep = new SimpleStep(context, Constants.CHILD_AXIS, rightStep);
                        path.replaceLastExpression(rightStep);
                    }
                }
            }
        )?
    )
    {
        if (step instanceof LocationStep && ((LocationStep) step).getAxis() == Constants.UNKNOWN_AXIS)
             ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
    }
    |
    #(
        DSLASH step=expr [path]
        {
            path.setHasSlash();
        }
        (
            rightStep=expr [path]
            {
                if (rightStep instanceof LocationStep) {
                    LocationStep rs= (LocationStep) rightStep;
                    if (rs.getAxis() == Constants.ATTRIBUTE_AXIS ||
                        (rs.getTest().getType() == Type.ATTRIBUTE && rs.getAxis() == Constants.CHILD_AXIS)) {
                        rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
                    } else if (rs.getAxis() == Constants.CHILD_AXIS && rs.getTest().isWildcardTest()) {
                        rs.setAxis(Constants.DESCENDANT_AXIS);
                    } else if (rs.getAxis() == Constants.SELF_AXIS) {
                        rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
                    } else if (rs.getAxis() <= Constants.PRECEDING_SIBLING_AXIS) {
                        // Reverse axis: cannot merge with descendant-or-self,
                        // insert explicit descendant-or-self::node() step before the reverse axis step
                        LocationStep descStep = new LocationStep(context, Constants.DESCENDANT_SELF_AXIS, new TypeTest(Type.NODE));
                        descStep.setAbbreviated(true);
                        path.replaceLastExpression(descStep);
                        path.add(rightStep);
                    } else {
                        rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
                        rs.setAbbreviated(true);
                    }

                } else if (rightStep instanceof VariableReference) {
                    rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
                    rightStep = new SimpleStep(context, Constants.DESCENDANT_SELF_AXIS, rightStep);
                    path.replaceLastExpression(rightStep);
                } else if (rightStep instanceof FilteredExpression) {
                    rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
                    ((FilteredExpression)rightStep).setAbbreviated(true);
                } else {
                    // For other non-LocationStep expressions (e.g., PathExpr wrapping
                    // parenthesized expressions like //(@x) or //(a | b)), insert an
                    // explicit descendant-or-self::node() step. We must NOT call
                    // setPrimaryAxis here because it would corrupt inner axes (e.g.,
                    // overwriting an attribute axis in //(@x)).
                    LocationStep descStep = new LocationStep(context, Constants.DESCENDANT_SELF_AXIS, new TypeTest(Type.NODE));
                    path.replaceLastExpression(descStep);
                    path.add(rightStep);
                }
            }
        )?
    )
    {
        if (step instanceof LocationStep && ((LocationStep) step).getAxis() == Constants.UNKNOWN_AXIS) {
             ((LocationStep) step).setAxis(Constants.DESCENDANT_SELF_AXIS);
             ((LocationStep) step).setAbbreviated(true);
        }
    }
    ;

literalExpr [PathExpr path]
returns [Expression step]
throws XPathException
{ step= null; }
:
    c:STRING_LITERAL
    {
        StringValue val = new StringValue(c.getText());
        val.expand();
        step= new LiteralValue(context, val);
        step.setASTNode(c);
    }
    |
    i:INTEGER_LITERAL
    {
        String itext = i.getText().replace("_", "");
        java.math.BigInteger intVal;
        if (itext.startsWith("0x") || itext.startsWith("0X")) {
            intVal = new java.math.BigInteger(itext.substring(2), 16);
        } else if (itext.startsWith("0b") || itext.startsWith("0B")) {
            intVal = new java.math.BigInteger(itext.substring(2), 2);
        } else {
            intVal = new java.math.BigInteger(itext);
        }
        step= new LiteralValue(context, new IntegerValue(intVal));
        step.setASTNode(i);
    }
    |
    (
        dec:DECIMAL_LITERAL
        {
            step= new LiteralValue(context, new DecimalValue(dec.getText().replace("_", "")));
            step.setASTNode(dec);
        }
        |
        dbl:DOUBLE_LITERAL
        {
            step= new LiteralValue(context,
                new DoubleValue(Double.parseDouble(dbl.getText().replace("_", ""))));
            step.setASTNode(dbl);
        }
    )
    ;

numericExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
    PathExpr left= new PathExpr(context);
    left.setASTNode(numericExpr_AST_in);

    PathExpr right= new PathExpr(context);
    right.setASTNode(numericExpr_AST_in);
}
:
    #( plus:PLUS step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.ADDITION);
        op.setASTNode(plus);
        path.addPath(op);
        step= op;
    }
    |
    #( minus:MINUS step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.SUBTRACTION);
        op.setASTNode(minus);
        path.addPath(op);
        step= op;
    }
    |
    #( uminus:UNARY_MINUS step=expr [left] )
    {
        UnaryExpr unary= new UnaryExpr(context, ArithmeticOperator.SUBTRACTION);
        unary.setASTNode(uminus);
        unary.add(left);
        path.addPath(unary);
        step= unary;
    }
    |
    #( uplus:UNARY_PLUS step=expr [left] )
    {
        UnaryExpr unary= new UnaryExpr(context, ArithmeticOperator.ADDITION);
        unary.setASTNode(uplus);
        unary.add(left);
        path.addPath(unary);
        step= unary;
    }
    |
    #( div:"div" step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.DIVISION);
        op.setASTNode(div);
        path.addPath(op);
        step= op;
    }
    |
    #( idiv:"idiv" step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.DIVISION_INTEGER);
        op.setASTNode(idiv);
        path.addPath(op);
        step= op;
    }
    |
    #( mod:"mod" step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.MODULUS);
        op.setASTNode(mod);
        path.addPath(op);
        step= op;
    }
    |
    #( mult:STAR step=expr [left] step=expr [right] )
    {
        OpNumeric op= new OpNumeric(context, left, right, ArithmeticOperator.MULTIPLICATION);
        op.setASTNode(mult);
        path.addPath(op);
        step= op;
    }
    ;

/**
 * Handles predicates and dynamic function calls:
 * PostfixExpr       ::=       PrimaryExpr (Predicate | ArgumentList)*
 */
postfixExpr [Expression expression]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= expression;
}
:
    (
        step = lookup [step]
        |
        step = filterExprAM [step]
        |
        #(
            fam:FILTER_AM
            {
                PathExpr filterPred = new PathExpr(context);
                filterPred.setASTNode(postfixExpr_AST_in);
            }
            expr [filterPred]
            {
                step = new FilterExprAM(context, step, filterPred.simplify());
                step.setASTNode(fam);
            }
        )
        |
        #(
            PREDICATE
            {
                FilteredExpression filter = new FilteredExpression(context, step);
                filter.setASTNode(postfixExpr_AST_in);
                step= filter;
                Predicate predicateExpr= new Predicate(context);
                predicateExpr.setASTNode(postfixExpr_AST_in);
            }
            expr [predicateExpr]
            {
                filter.addPredicate(predicateExpr);
            }
        )
        |
        #(
            fn:DYNAMIC_FCALL
            {
                List<Expression> params= new ArrayList<Expression>(5);
                boolean isPartial = false;
            }
            (
                (
                    QUESTION {
                        params.add(new Function.Placeholder(context));
                        isPartial = true;
                    }
                    |
                    {
                        PathExpr pathExpr = new PathExpr(context);
                        pathExpr.setASTNode(postfixExpr_AST_in);
                    }
                    expr [pathExpr] { params.add(pathExpr); }
                )
            )*
            {
                step = new DynamicFunctionCall(context, step, params, isPartial);
            }
        )
    )*
;

predicate [LocationStep step]
throws PermissionDeniedException, EXistException, XPathException
:
    #(
        PREDICATE
        {
            PathExpr path = new PathExpr(context);
            path.setASTNode(predicate_AST_in);
        }
        expr [path]
        {
            Predicate predicateExpr= new Predicate(context);
            predicateExpr.setASTNode(predicate_AST_in);
            predicateExpr.addPath(path);
            step.addPredicate(predicateExpr);
        }
    )
    ;

// === XQuery 4.0: Array/Map Filter Expression (?[expr]) ===
filterExprAM [Expression leftExpr]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
:
    #(
        filterAM:FILTER_AM
        {
            PathExpr predExpr = new PathExpr(context);
        }
        ( expr [predExpr] )+
        {
            step = new FilterExprAM(context, leftExpr, predExpr);
            step.setASTNode(filterAM);
        }
    )
    ;

lookup [Expression leftExpr]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
:
    #(
        lookup:LOOKUP
        {
            PathExpr lookupExpr = new PathExpr(context);
            lookupExpr.setASTNode(lookup_AST_in);
            int position = 0;
        }
        (
            pos:INTEGER_VALUE { position = Integer.parseInt(pos.getText()); }
            |
            // XQ4: string literal as key selector (?"first value")
            strKey:STRING_LITERAL
            {
                lookupExpr.add(new LiteralValue(context, new StringValue(strKey.getText())));
            }
            |
            // XQ4: decimal literal as key selector (?1.2)
            decKey:DECIMAL_LITERAL
            {
                lookupExpr.add(new LiteralValue(context, new DecimalValue(decKey.getText().replace("_", ""))));
            }
            |
            // XQ4: double literal as key selector (?1.2e0)
            dblKey:DOUBLE_LITERAL
            {
                lookupExpr.add(new LiteralValue(context, new DoubleValue(Double.parseDouble(dblKey.getText().replace("_", "")))));
            }
            |
            // XQ4: variable reference as key selector (?$var)
            varKey:VARIABLE_REF
            {
                final QName varQn;
                try {
                    varQn = QName.parse(staticContext, varKey.getText(), null);
                } catch (final IllegalQNameException iqe) {
                    throw new XPathException(varKey.getLine(), varKey.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + varKey.getText());
                }
                lookupExpr.add(new VariableReference(context, varQn));
            }
            |
            // XQ4: context item as key selector (?.)
            ctxKey:SELF
            {
                lookupExpr.add(new ContextItemExpression(context));
            }
            |
            // XQ4: QName literal as key selector (?#name)
            qnKey:QNAME_LITERAL
            {
                final String qnText = qnKey.getText();
                final QName qnQName;
                try {
                    qnQName = QName.parse(staticContext, qnText);
                } catch (final IllegalQNameException iqe) {
                    throw new XPathException(qnKey.getLine(), qnKey.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + qnText);
                }
                lookupExpr.add(new LiteralValue(context, new QNameValue(context, qnQName)));
            }
            |
            ( expr [lookupExpr] )+
        )?
        {
            if (lookupExpr.getLength() == 0) {
                if (lookup.getText().equals("?*")) {
                    step = new Lookup(context, leftExpr);
                } else if (position == 0) {
                    step = new Lookup(context, leftExpr, lookup.getText());
                } else {
                    step = new Lookup(context, leftExpr, position);
                }
            } else {
                step = new Lookup(context, leftExpr, lookupExpr);
            }
            step.setASTNode(lookup);
        }
    )
    ;

functionCall [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    PathExpr pathExpr;
    step= null;
    boolean isPartial = false;
}
:
    #(
        fn:FUNCTION
        { List params= new ArrayList(2); }
        (
            {
                pathExpr= new PathExpr(context);
                pathExpr.setASTNode(functionCall_AST_in);
            }
            (
                QUESTION {
                    params.add(new Function.Placeholder(context));
                    isPartial = true;
                }
                |
                #(
                    kw:KEYWORD_ARG
                    {
                        if (staticContext.getXQueryVersion() < 40) {
                            throw new XPathException(kw, ErrorCodes.XPST0003,
                                "Keyword arguments require xquery version \"4.0\"");
                        }
                    }
                    (
                        QUESTION {
                            // Keyword argument with placeholder value: name := ?
                            params.add(new KeywordArgumentExpression(context, kw.getText(),
                                new Function.Placeholder(context)));
                            isPartial = true;
                        }
                        |
                        {
                            PathExpr kwExpr = new PathExpr(context);
                            kwExpr.setASTNode(functionCall_AST_in);
                        }
                        expr [kwExpr]
                        {
                            params.add(new KeywordArgumentExpression(context, kw.getText(), kwExpr));
                        }
                    )
                )
                |
                expr [pathExpr] { params.add(pathExpr); }
            )
        )*
    )
    {
        step = FunctionFactory.createFunction(context, fn, path, params);
        if (isPartial) {
            if (!(step instanceof FunctionCall)) {
                if (step instanceof CastExpression) {
                    step = ((CastExpression)step).toFunction();
                }
                step = FunctionFactory.wrap(context, (Function)step);
            }
            step = new PartialFunctionApplication(context, (FunctionCall) step);
        }
    }
    ;

functionReference [PathExpr path]
returns  [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}:
    #(
        name:HASH
        arity:INTEGER_LITERAL
        {
            QName qname;
            try {
                qname = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
            } catch (final IllegalQNameException iqe) {
                throw new XPathException(name.getLine(), name.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + name.getText());
            }
            NamedFunctionReference ref = new NamedFunctionReference(context, qname, Integer.parseInt(arity.getText().replace("_", "")));
            step = ref;
        }
    )
    ;

forwardAxis returns [int axis]
throws PermissionDeniedException, EXistException
{ axis= Constants.UNKNOWN_AXIS; }
:
    "child" { axis= Constants.CHILD_AXIS; }
    |
    "attribute" { axis= Constants.ATTRIBUTE_AXIS; }
    |
    "self" { axis= Constants.SELF_AXIS; }
    |
    "parent" { axis= Constants.PARENT_AXIS; }
    |
    "descendant" { axis= Constants.DESCENDANT_AXIS; }
    |
    "descendant-or-self" { axis= Constants.DESCENDANT_SELF_AXIS; }
    |
    "following-sibling-or-self" { axis= Constants.FOLLOWING_SIBLING_OR_SELF_AXIS; }
    |
    "following-sibling" { axis= Constants.FOLLOWING_SIBLING_AXIS; }
    |
    "following-or-self" { axis= Constants.FOLLOWING_OR_SELF_AXIS; }
    |
    "following" { axis= Constants.FOLLOWING_AXIS; }
    |
    "preceding-sibling-or-self" { axis= Constants.PRECEDING_SIBLING_OR_SELF_AXIS; }
    |
    "preceding-sibling" { axis= Constants.PRECEDING_SIBLING_AXIS; }
    |
    "preceding-or-self" { axis= Constants.PRECEDING_OR_SELF_AXIS; }
    |
    "preceding" { axis= Constants.PRECEDING_AXIS; }
    |
    "ancestor" { axis= Constants.ANCESTOR_AXIS; }
    |
    "ancestor-or-self" { axis= Constants.ANCESTOR_SELF_AXIS; }
    |
    "following-or-self" { axis= Constants.FOLLOWING_OR_SELF_AXIS; }
    |
    "preceding-or-self" { axis= Constants.PRECEDING_OR_SELF_AXIS; }
    |
    "following-sibling-or-self" { axis= Constants.FOLLOWING_SIBLING_OR_SELF_AXIS; }
    |
    "preceding-sibling-or-self" { axis= Constants.PRECEDING_SIBLING_OR_SELF_AXIS; }
    ;

valueComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
    PathExpr left= new PathExpr(context);
    left.setASTNode(valueComp_AST_in);

    PathExpr right= new PathExpr(context);
    right.setASTNode(valueComp_AST_in);
}
:
    #(
        eq:"eq" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.EQ);
            step.setASTNode(eq);
            path.add(step);
        }
    )
    |
    #(
        ne:"ne" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.NEQ);
            step.setASTNode(ne);
            path.add(step);
        }
    )
    |
    #(
        lt:"lt" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.LT);
            step.setASTNode(lt);
            path.add(step);
        }
    )
    |
    #(
        le:"le" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.LTEQ);
            step.setASTNode(le);
            path.add(step);
        }
    )
    |
    #(
        gt:"gt" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.GT);
            step.setASTNode(gt);
            path.add(step);
        }
    )
    |
    #(
        ge:"ge" step=expr [left]
        step=expr [right]
        {
            step= new ValueComparison(context, left, right, Comparison.GTEQ);
            step.setASTNode(ge);
            path.add(step);
        }
    )
    ;

generalComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;

    PathExpr left= new PathExpr(context);
    left.setASTNode(generalComp_AST_in);

    PathExpr right= new PathExpr(context);
    right.setASTNode(generalComp_AST_in);

}
:
    #(
        eq:EQ step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.EQ);
            step.setASTNode(eq);
            path.add(step);
        }
    )
    |
    #(
        neq:NEQ step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.NEQ);
            step.setASTNode(neq);
            path.add(step);
        }
    )
    |
    #(
        lt:LT step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.LT);
            step.setASTNode(lt);
            path.add(step);
        }
    )
    |
    #(
        lteq:LTEQ step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.LTEQ);
            step.setASTNode(lteq);
            path.add(step);
        }
    )
    |
    #(
        gt:GT step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.GT);
            step.setASTNode(gt);
            path.add(step);
        }
    )
    |
    #(
        gteq:GTEQ step=expr [left]
        step=expr [right]
        {
            step= new GeneralComparison(context, left, right, Comparison.GTEQ);
            step.setASTNode(gteq);
            path.add(step);
        }
    )
    ;

nodeComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;

    PathExpr left= new PathExpr(context);
    left.setASTNode(nodeComp_AST_in);

    PathExpr right= new PathExpr(context);
    right.setASTNode(nodeComp_AST_in);

}
:
    #(
        is:"is" step=expr [left] step=expr [right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.IS);
            step.setASTNode(is);
            path.add(step);
        }
    )
    |
    #(
        before:BEFORE step=expr[left] step=expr[right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.BEFORE);
            step.setASTNode(before);
            path.add(step);
        }
    )
    |
    #(
        after:AFTER step=expr[left] step=expr[right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.AFTER);
            step.setASTNode(after);
            path.add(step);
        }
    )
    |
    // XQuery 4.0 node comparison operators
    #(
        isnot:"is-not" step=expr[left] step=expr[right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.IS_NOT);
            step.setASTNode(isnot);
            path.add(step);
        }
    )
    |
    #(
        foi:"follows-or-is" step=expr[left] step=expr[right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.FOLLOWS_OR_IS);
            step.setASTNode(foi);
            path.add(step);
        }
    )
    |
    #(
        poi:"precedes-or-is" step=expr[left] step=expr[right]
        {
            step = new NodeComparison(context, left, right, NodeComparisonOperator.PRECEDES_OR_IS);
            step.setASTNode(poi);
            path.add(step);
        }
    )
    ;

// === Full Text (W3C XQuery and XPath Full Text 3.0) ===

ftContainsExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    PathExpr source = new PathExpr(context);
    source.setASTNode(ftContainsExpr_AST_in);
    FTSelection ftSel = null;
    Expression ignoreExpr = null;
}
:
    #(
        ft:FT_CONTAINS
        step=expr [source]
        ftSel=ftSelectionExpr
        ( ignoreExpr=ftIgnoreExpr )?
        {
            FTContainsExpr ftContains = new FTContainsExpr(context);
            ftContains.setASTNode(ft);
            ftContains.setSearchSource(source);
            ftContains.setFTSelection(ftSel);
            ftContains.setIgnoreExpr(ignoreExpr);
            path.add(ftContains);
            step = ftContains;
        }
    )
    ;

ftSelectionExpr
returns [FTSelection ftSel]
throws PermissionDeniedException, EXistException, XPathException
{
    ftSel = new FTSelection(context);
    ftSel.setASTNode(ftSelectionExpr_AST_in);
    Expression ftOr = null;
    Expression posFilter = null;
}
:
    #(
        FT_SELECTION
        ftOr=ftOrExpr
        { ftSel.setFTOr(ftOr); }
        ( posFilter=ftPosFilterExpr { ftSel.addPosFilter(posFilter); } )*
    )
    ;

ftOrExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    Expression operand = null;
    FTOr ftOr = null;
}
:
    #(
        FT_OR
        {
            ftOr = new FTOr(context);
            ftOr.setASTNode(ftOrExpr_AST_in);
        }
        ( operand=ftAndExpr { ftOr.addOperand(operand); } )+
        { step = ftOr; }
    )
    |
    step=ftAndExpr
    ;

ftAndExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    Expression operand = null;
    FTAnd ftAnd = null;
}
:
    #(
        FT_AND
        {
            ftAnd = new FTAnd(context);
            ftAnd.setASTNode(ftAndExpr_AST_in);
        }
        ( operand=ftMildNotExpr { ftAnd.addOperand(operand); } )+
        { step = ftAnd; }
    )
    |
    step=ftMildNotExpr
    ;

ftMildNotExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    Expression operand = null;
    FTMildNot ftMildNot = null;
}
:
    #(
        FT_MILD_NOT
        {
            ftMildNot = new FTMildNot(context);
            ftMildNot.setASTNode(ftMildNotExpr_AST_in);
        }
        ( operand=ftUnaryNotExpr { ftMildNot.addOperand(operand); } )+
        { step = ftMildNot; }
    )
    |
    step=ftUnaryNotExpr
    ;

ftUnaryNotExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    Expression operand = null;
}
:
    #(
        FT_UNARY_NOT
        operand=ftPrimaryWithOptionsExpr
        {
            FTUnaryNot ftNot = new FTUnaryNot(context);
            ftNot.setASTNode(ftUnaryNotExpr_AST_in);
            ftNot.setOperand(operand);
            step = ftNot;
        }
    )
    |
    step=ftPrimaryWithOptionsExpr
    ;

ftPrimaryWithOptionsExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    Expression primary = null;
    FTMatchOptions matchOpts = null;
    Expression weightExpr = null;
}
:
    #(
        FT_PRIMARY_WITH_OPTIONS
        primary=ftPrimaryExpr
        ( matchOpts=ftMatchOptionsExpr )?
        ( weightExpr=ftWeightExpr )?
        {
            FTPrimaryWithOptions pwo = new FTPrimaryWithOptions(context);
            pwo.setASTNode(ftPrimaryWithOptionsExpr_AST_in);
            pwo.setPrimary(primary);
            pwo.setMatchOptions(matchOpts);
            pwo.setWeight(weightExpr);
            step = pwo;
        }
    )
    |
    step=ftPrimaryExpr
    ;

ftPrimaryExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}
:
    step=ftWordsExpr
    |
    step=ftSelectionExpr
    |
    step=ftExtensionSelectionExpr
    ;

ftWordsExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    PathExpr wordsValue = new PathExpr(context);
    FTWords.AnyallMode mode = FTWords.AnyallMode.ANY;
    FTTimes ftTimes = null;
}
:
    #(
        FT_WORDS
        step=expr [wordsValue]
        ( aa:FT_ANYALL_OPTION { mode = FTWords.AnyallMode.fromString(aa.getText()); } )?
        ( ftTimes=ftTimesExpr )?
        {
            FTWords ftWords = new FTWords(context);
            ftWords.setASTNode(ftWordsExpr_AST_in);
            ftWords.setWordsValue(wordsValue);
            ftWords.setMode(mode);
            ftWords.setFTTimes(ftTimes);
            step = ftWords;
        }
    )
    ;

// XQFT 3.0 3.4.8: FTExtensionSelection -- pragmas wrapping an optional FTSelection.
// Pragmas are parsed but ignored (no FT-specific pragmas are recognized).
// If the body is empty, XQST0079 is raised. If the body is present,
// the pragmas are discarded and the inner FTSelection is returned.
// Namespace prefix validation is performed via context.getPragma().
ftExtensionSelectionExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    FTSelection innerSel = null;
}
:
    #(
        FT_EXTENSION_SELECTION
        // Validate pragma namespace prefixes (raises XPST0081 for undeclared prefixes).
        // We don't recognize any FT-specific pragmas, so the result is always null.
        (
            #( p:PRAGMA ( c:PRAGMA_END )? )
            {
                // Validates namespace prefix; throws XPST0081 if prefix is undeclared
                context.getPragma(p.getText(), c != null ? c.getText() : "");
            }
        )*
        ( innerSel=ftSelectionExpr )?
        {
            if (innerSel == null) {
                // XQST0079: all pragmas unrecognized and no fallback body
                throw new XPathException(ftExtensionSelectionExpr_AST_in,
                    ErrorCodes.XQST0079,
                    "No recognized pragmas in FTExtensionSelection and no fallback expression");
            }
            step = innerSel;
        }
    )
    ;

ftTimesExpr
returns [FTTimes step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    FTRange range = null;
}
:
    #(
        FT_TIMES
        range=ftRangeExpr
        {
            step = new FTTimes(context);
            step.setASTNode(ftTimesExpr_AST_in);
            step.setRange(range);
        }
    )
    ;

ftRangeExpr
returns [FTRange step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = new FTRange(context);
    PathExpr e1 = new PathExpr(context);
    PathExpr e2 = new PathExpr(context);
    Expression tmp = null;
}
:
    #(
        r:FT_RANGE
        {
            String rangeMode = r.getText();
            switch (rangeMode) {
                case "exactly": step.setMode(FTRange.RangeMode.EXACTLY); break;
                case "at least": step.setMode(FTRange.RangeMode.AT_LEAST); break;
                case "at most": step.setMode(FTRange.RangeMode.AT_MOST); break;
                case "from": step.setMode(FTRange.RangeMode.FROM_TO); break;
            }
        }
        tmp=expr [e1] { step.setExpr1(e1); }
        ( tmp=expr [e2] { step.setExpr2(e2); } )?
    )
    ;

ftPosFilterExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}
:
    o:FT_ORDER
    {
        FTOrder order = new FTOrder(context);
        order.setASTNode(o);
        step = order;
    }
    |
    step=ftWindowExpr
    |
    step=ftDistanceExpr
    |
    s:FT_SCOPE
    {
        FTScope scope = new FTScope(context);
        scope.setASTNode(s);
        String scopeText = s.getText();
        if (scopeText.startsWith("same")) {
            scope.setScopeType(FTScope.ScopeType.SAME);
        } else {
            scope.setScopeType(FTScope.ScopeType.DIFFERENT);
        }
        if (scopeText.endsWith("sentence")) {
            scope.setBigUnit(FTScope.BigUnit.SENTENCE);
        } else {
            scope.setBigUnit(FTScope.BigUnit.PARAGRAPH);
        }
        step = scope;
    }
    |
    c:FT_CONTENT
    {
        FTContent content = new FTContent(context);
        content.setASTNode(c);
        switch (c.getText()) {
            case "at start": content.setContentType(FTContent.ContentType.AT_START); break;
            case "at end": content.setContentType(FTContent.ContentType.AT_END); break;
            case "entire content": content.setContentType(FTContent.ContentType.ENTIRE_CONTENT); break;
        }
        step = content;
    }
    ;

ftWindowExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    PathExpr winExpr = new PathExpr(context);
    Expression tmp = null;
}
:
    #(
        w:FT_WINDOW
        tmp=expr [winExpr]
        u1:. // ftUnit token (words|sentences|paragraphs)
        {
            FTWindow win = new FTWindow(context);
            win.setASTNode(w);
            win.setWindowExpr(winExpr);
            win.setUnit(FTUnit.fromString(u1.getText()));
            step = win;
        }
    )
    ;

ftDistanceExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    FTRange range = null;
}
:
    #(
        d:FT_DISTANCE
        range=ftRangeExpr
        u2:. // ftUnit token (words|sentences|paragraphs)
        {
            FTDistance dist = new FTDistance(context);
            dist.setASTNode(d);
            dist.setRange(range);
            dist.setUnit(FTUnit.fromString(u2.getText()));
            step = dist;
        }
    )
    ;

ftMatchOptionsExpr
returns [FTMatchOptions opts]
throws PermissionDeniedException, EXistException, XPathException
{
    opts = new FTMatchOptions();
}
:
    (
        co:FT_CASE_OPTION
        {
            switch (co.getText()) {
                case "sensitive": opts.setCaseMode(FTMatchOptions.CaseMode.SENSITIVE); break;
                case "insensitive": opts.setCaseMode(FTMatchOptions.CaseMode.INSENSITIVE); break;
                case "lowercase": opts.setCaseMode(FTMatchOptions.CaseMode.LOWERCASE); break;
                case "uppercase": opts.setCaseMode(FTMatchOptions.CaseMode.UPPERCASE); break;
            }
        }
        |
        di:FT_DIACRITICS_OPTION
        {
            switch (di.getText()) {
                case "sensitive": opts.setDiacriticsMode(FTMatchOptions.DiacriticsMode.SENSITIVE); break;
                case "insensitive": opts.setDiacriticsMode(FTMatchOptions.DiacriticsMode.INSENSITIVE); break;
            }
        }
        |
        st:FT_STEM_OPTION
        { opts.setStemming("stemming".equals(st.getText())); }
        |
        #( FT_LANGUAGE_OPTION lang:STRING_LITERAL { opts.setLanguage(lang.getText()); } )
        |
        wc:FT_WILDCARD_OPTION
        { opts.setWildcards("wildcards".equals(wc.getText())); }
        |
        #( thesOpt:FT_THESAURUS_OPTION
            {
                final String thesText = thesOpt.getText();
                if ("no thesaurus".equals(thesText)) {
                    opts.setNoThesaurus(true);
                } else {
                    opts.setNoThesaurus(false);
                    AST thesChild = thesOpt.getFirstChild();
                    while (thesChild != null) {
                        if (thesChild.getType() == FT_THESAURUS_ID) {
                            final String idText = thesChild.getText();
                            if ("default".equals(idText)) {
                                opts.getThesaurusIDs().add(
                                    new FTMatchOptions.ThesaurusID(null, null, 0, Integer.MAX_VALUE));
                            } else {
                                // "at" -- children: STRING_LITERAL (uri), optional STRING_LITERAL (rel), optional FT_RANGE
                                String uri = null;
                                String relationship = null;
                                int minLevels = 0;
                                int maxLevels = Integer.MAX_VALUE;
                                AST idChild = thesChild.getFirstChild();
                                if (idChild != null && idChild.getType() == STRING_LITERAL) {
                                    uri = idChild.getText();
                                    idChild = idChild.getNextSibling();
                                }
                                if (idChild != null && idChild.getType() == STRING_LITERAL) {
                                    relationship = idChild.getText();
                                    idChild = idChild.getNextSibling();
                                }
                                if (idChild != null && idChild.getType() == FT_RANGE) {
                                    final String rangeType = idChild.getText();
                                    AST rangeChild = idChild.getFirstChild();
                                    if (rangeChild != null) {
                                        final int val1 = Integer.parseInt(rangeChild.getText());
                                        switch (rangeType) {
                                            case "exactly":
                                                minLevels = val1;
                                                maxLevels = val1;
                                                break;
                                            case "at least":
                                                minLevels = val1;
                                                break;
                                            case "at most":
                                                maxLevels = val1;
                                                break;
                                            case "from":
                                                minLevels = val1;
                                                AST rangeChild2 = rangeChild.getNextSibling();
                                                if (rangeChild2 != null) {
                                                    maxLevels = Integer.parseInt(rangeChild2.getText());
                                                }
                                                break;
                                        }
                                    }
                                }
                                if (uri != null) {
                                    opts.getThesaurusIDs().add(
                                        new FTMatchOptions.ThesaurusID(uri, relationship, minLevels, maxLevels));
                                    opts.getThesaurusURIs().add(uri);
                                }
                            }
                        }
                        thesChild = thesChild.getNextSibling();
                    }
                }
            }
        )
        |
        #( sw:FT_STOP_WORD_OPTION
            {
                final String swText = sw.getText();
                if ("no stop words".equals(swText)) {
                    opts.setNoStopWords(true);
                } else {
                    if ("stop words default".equals(swText)) {
                        opts.setUseDefaultStopWords(true);
                    }
                    // Walk children to extract stop words (union and except)
                    AST swChild = sw.getFirstChild();
                    while (swChild != null) {
                        if (swChild.getType() == FT_STOP_WORDS_EXCEPT) {
                            // Except wrapper -- inner child is FT_STOP_WORDS
                            AST exceptInner = swChild.getFirstChild();
                            while (exceptInner != null) {
                                if (exceptInner.getType() == FT_STOP_WORDS) {
                                    final String swMode = exceptInner.getText();
                                    AST swWordNode = exceptInner.getFirstChild();
                                    while (swWordNode != null) {
                                        if ("at".equals(swMode)) {
                                            opts.getExceptStopWordURIs().add(swWordNode.getText());
                                        } else {
                                            opts.getExceptInlineStopWords().add(swWordNode.getText());
                                        }
                                        swWordNode = swWordNode.getNextSibling();
                                    }
                                }
                                exceptInner = exceptInner.getNextSibling();
                            }
                        } else if (swChild.getType() == FT_STOP_WORDS) {
                            // Union stop words (primary or union-added)
                            final String swMode = swChild.getText();
                            AST swWordNode = swChild.getFirstChild();
                            while (swWordNode != null) {
                                if ("at".equals(swMode)) {
                                    opts.getStopWordURIs().add(swWordNode.getText());
                                } else {
                                    opts.getInlineStopWords().add(swWordNode.getText());
                                }
                                swWordNode = swWordNode.getNextSibling();
                            }
                        }
                        swChild = swChild.getNextSibling();
                    }
                }
            }
            ( . )*
        )
        |
        #( eo:FT_EXTENSION_OPTION ( . )*
            {
                // XQFT 3.0 §4.10: validate namespace prefix for extension option.
                // Raises XPST0081 if the prefix is not declared.
                final String extOptName = eo.getText();
                try {
                    QName.parse(staticContext, extOptName);
                } catch (final QName.IllegalQNameException e) {
                    throw new XPathException(eo.getLine(), eo.getColumn(),
                        ErrorCodes.XPST0081,
                        "No namespace defined for prefix in extension option: " + extOptName);
                }
            }
        )
    )+
    ;

ftWeightExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    PathExpr weightPath = new PathExpr(context);
}
:
    #(
        FT_WEIGHT
        step=expr [weightPath]
        { step = weightPath; }
    )
    ;

ftIgnoreExpr
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
    PathExpr ignorePath = new PathExpr(context);
}
:
    #(
        FT_IGNORE_OPTION
        step=expr [ignorePath]
        { step = ignorePath; }
    )
    ;

constructor [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
    PathExpr elementContent= null;
    Expression contentExpr= null;
    Expression qnameExpr = null;
}
:
    // computed element constructor
    #(
        qn:COMP_ELEM_CONSTRUCTOR
        {
            ElementConstructor c= new ElementConstructor(context);
            c.setASTNode(qn);
            step= c;
            SequenceConstructor construct = new SequenceConstructor(context);
            EnclosedExpr enclosed = new EnclosedExpr(context);
            enclosed.setASTNode(constructor_AST_in);
            enclosed.addPath(construct);
            c.setContent(enclosed);
            PathExpr qnamePathExpr = new PathExpr(context);
            qnamePathExpr.setASTNode(constructor_AST_in);
            c.setNameExpr(qnamePathExpr);
        }

        qnameExpr=expr [qnamePathExpr]
        (
            { elementContent = new PathExpr(context); }
            contentExpr=ec:expr[elementContent]
            {
              elementContent.setASTNode(ec);
              construct.addPathIfNotFunction(elementContent);
            }
        )*
    )
    |
    #(
        qns:COMP_NS_CONSTRUCTOR
        {
            NamespaceConstructor c = new NamespaceConstructor(context);
            c.setASTNode(qns);
            step = c;
            PathExpr qnamePathExpr = new PathExpr(context);
            qnamePathExpr.setASTNode(constructor_AST_in);
            c.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            c.setContentExpr(elementContent);
        }
        qnameExpr=expr [qnamePathExpr]
        (
            contentExpr=expr [elementContent]
        )?
    )
    |
    #(
        attr:COMP_ATTR_CONSTRUCTOR
        {
            DynamicAttributeConstructor a= new DynamicAttributeConstructor(context);
            a.setASTNode(attr);
            step = a;
            PathExpr qnamePathExpr = new PathExpr(context);
            qnamePathExpr.setASTNode(constructor_AST_in);
            a.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            a.setContentExpr(elementContent);
        }
        qnameExpr=qna:expr [qnamePathExpr]
        {
            try {
                QName qname = QName.parse(staticContext, qna.getText());
                if (Namespaces.XMLNS_NS.equals(qname.getNamespaceURI())
                    || ("".equals(qname.getNamespaceURI()) && qname.getLocalPart().equals(XMLConstants.XMLNS_ATTRIBUTE)))
                    throw new XPathException(constructor_AST_in, ErrorCodes.XQDY0044, "The node-name property of the node constructed by a computed attribute constructor is in the namespace http://www.w3.org/2000/xmlns/ (corresponding to namespace prefix xmlns), or is in no namespace and has local name xmlns.");
            } catch (final IllegalQNameException iqe) {
                // Computed attribute constructors evaluate the name dynamically (XQuery 3.1 §3.9.3.1).
                // An undeclared prefix is therefore a dynamic error XQDY0074, not the static XPST0081.
                throw new XPathException(qna.getLine(), qna.getColumn(), ErrorCodes.XQDY0074, "'" + qna.getText() + "' is not a valid attribute name");
            }
        }
        #( LCURLY
            (
                contentExpr=expr [elementContent]
            )?
        )
    )
    |
    #(
        pid:COMP_PI_CONSTRUCTOR
        {
            DynamicPIConstructor pd= new DynamicPIConstructor(context);
            pd.setASTNode(pid);
            step = pd;
            PathExpr qnamePathExpr = new PathExpr(context);
            qnamePathExpr.setASTNode(constructor_AST_in);
            pd.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            pd.setContentExpr(elementContent);
        }
        qnameExpr=expr [qnamePathExpr]
        #( LCURLY
            (
                contentExpr=ex:expr [elementContent]
                {
                    if (ex.getText() != null && ex.getText().indexOf("?>") > Constants.STRING_NOT_FOUND)
                throw new XPathException(constructor_AST_in, ErrorCodes.XQDY0026, "Content expression of a computed processing instruction constructor contains the string '?>' which is not allowed.");
                }
            )?
        )
    )
    |
    // direct element constructor
    #(
        e:ELEMENT
        {
            ElementConstructor c= new ElementConstructor(context, e.getText());
            c.setASTNode(e);
            step= c;
            staticContext.pushInScopeNamespaces();
        }
        (
            #(
                attrName:ATTRIBUTE
                {
                    AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
                    attrib.setASTNode(attrName);
                }
                (
                    attrVal:ATTRIBUTE_CONTENT
                    {
                        attrib.addValue(StringValue.expand(attrVal.getText(), attrib));
                    }
                    |
                    #(
                        LCURLY
                        {
                            PathExpr enclosed= new PathExpr(context);
                            enclosed.setASTNode(constructor_AST_in);
                        }
                        expr [enclosed]
                        { attrib.addEnclosedExpr(enclosed); }
                    )
                )*
                { c.addAttribute(attrib);
                                  if (attrib.isNamespaceDeclaration()) {
                                      try {
                                          String nsPrefix = attrib.getQName().equals(XMLConstants.XMLNS_ATTRIBUTE) ?
                                                  "" : QName.extractLocalName(attrib.getQName());
                                          staticContext.declareInScopeNamespace(nsPrefix,attrib.getLiteralValue());
                                      } catch (final IllegalQNameException iqe) {
                                          throw new XPathException(attrib.getLine(), attrib.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + attrib.getQName());
                                      }
                                  }

                                }
            )
        )*
        (
            {
                if (elementContent == null) {
                    elementContent= new PathExpr(context);
                    elementContent.setASTNode(constructor_AST_in);
                    c.setContent(elementContent);
                }
            }
            contentExpr=constructor [elementContent]
            { elementContent.add(contentExpr); }
        )*
                {
                   staticContext.popInScopeNamespaces();
                }
    )
    |
    #(
        pcdata:TEXT
        {
            TextConstructor text= new TextConstructor(context, pcdata.getText());
            text.setASTNode(pcdata);
            step= text;
        }
    )
    |
    #(
        t:COMP_TEXT_CONSTRUCTOR
        {
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            DynamicTextConstructor text = new DynamicTextConstructor(context, elementContent);
            text.setASTNode(t);
            step= text;
        }
        contentExpr=expr [elementContent]
    )
    |
    #(
        tc:COMP_COMMENT_CONSTRUCTOR
        {
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            DynamicCommentConstructor comment = new DynamicCommentConstructor(context, elementContent);
            comment.setASTNode(t);
            step= comment;
        }
        (contentExpr=expr [elementContent])?
    )
    |
    #(
        d:COMP_DOC_CONSTRUCTOR
        {
            elementContent = new PathExpr(context);
            elementContent.setASTNode(constructor_AST_in);
            DocumentConstructor doc = new DocumentConstructor(context, elementContent);
            doc.setASTNode(d);
            step= doc;
        }
        (contentExpr=expr [elementContent])?
    )
    |
    #(
        cdata:XML_COMMENT
        {
            CommentConstructor comment= new CommentConstructor(context, cdata.getText());
            comment.setASTNode(cdata);
            step= comment;
        }
    )
    |
    #(
        p:XML_PI
        {
            PIConstructor pi= new PIConstructor(context, p.getText());
            pi.setASTNode(p);
            step= pi;
        }
    )
    |
    #(
        cdataSect:XML_CDATA
        {
            CDATAConstructor cd = new CDATAConstructor(context, cdataSect.getText());
            cd.setASTNode(cdataSect);
            step= cd;
        }
    )
    |
    // enclosed expression within element content
    #(
        l:LCURLY {
            EnclosedExpr subexpr= new EnclosedExpr(context);
            subexpr.setASTNode(l);
        }
        step=expr [subexpr]
        { step= subexpr; }
    )
    ;

arrowOp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
}:
    #(
        arrowAST:ARROW_OP
        {
            PathExpr leftExpr = new PathExpr(context);
            leftExpr.setASTNode(arrowOp_AST_in);
        }
        expr [leftExpr]
        {
            ArrowOperator op = new ArrowOperator(context, leftExpr.simplify());
            op.setASTNode(arrowAST);
            path.add(op);
            step = op;

            PathExpr nameExpr = new PathExpr(context);
            nameExpr.setASTNode(arrowOp_AST_in);
            String name = null;
        }
        (
            eq:EQNAME
            { name = eq.toString(); }
            |
            expr [nameExpr]
        )
        { List<Expression> params = new ArrayList<Expression>(5); }
        (
            {
                PathExpr pathExpr = new PathExpr(context);
                pathExpr.setASTNode(arrowOp_AST_in);
            }
            expr [pathExpr] { params.add(pathExpr.simplify()); }
        )*
        {
            if (name == null) {
                op.setArrowFunction(nameExpr, params);
            } else {
                op.setArrowFunction(name, params);
            }
        }
    )
    ;

mappingArrowOp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
}:
    #(
        mapArrowAST:MAPPING_ARROW_OP
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(mapArrowAST, ErrorCodes.XPST0003,
                    "The mapping arrow operator (=>!) requires xquery version \"4.0\"");
            }
            PathExpr leftExpr = new PathExpr(context);
            leftExpr.setASTNode(mappingArrowOp_AST_in);
        }
        expr [leftExpr]
        {
            MappingArrowOperator op = new MappingArrowOperator(context, leftExpr.simplify());
            op.setASTNode(mapArrowAST);
            path.add(op);
            step = op;

            PathExpr nameExpr = new PathExpr(context);
            nameExpr.setASTNode(mappingArrowOp_AST_in);
            String name = null;
        }
        (
            eq:EQNAME
            { name = eq.toString(); }
            |
            expr [nameExpr]
        )
        { List<Expression> params = new ArrayList<Expression>(5); }
        (
            {
                PathExpr pathExpr = new PathExpr(context);
                pathExpr.setASTNode(mappingArrowOp_AST_in);
            }
            expr [pathExpr] { params.add(pathExpr.simplify()); }
        )*
        {
            if (name == null) {
                op.setArrowFunction(nameExpr, params);
            } else {
                op.setArrowFunction(name, params);
            }
        }
    )
    ;

pipelineOp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}:
    #(
        pipeAST:PIPELINE_OP
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(pipeAST, ErrorCodes.XPST0003,
                    "The pipeline operator (->) requires xquery version \"4.0\"");
            }
            PathExpr leftExpr = new PathExpr(context);
            leftExpr.setASTNode(pipelineOp_AST_in);
        }
        expr [leftExpr]
        {
            PathExpr rightExpr = new PathExpr(context);
            rightExpr.setASTNode(pipelineOp_AST_in);
        }
        expr [rightExpr]
        {
            step = new PipelineExpression(context, leftExpr.simplify(), rightExpr.simplify());
            step.setASTNode(pipeAST);
            path.add(step);
        }
    )
    ;

methodCallOp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}:
    #(
        mcAST:METHOD_CALL_OP
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(mcAST, ErrorCodes.XPST0003,
                    "The method call operator (=?>) requires xquery version \"4.0\"");
            }
            PathExpr leftExpr = new PathExpr(context);
            leftExpr.setASTNode(methodCallOp_AST_in);
        }
        expr [leftExpr]
        mn:NCNAME
        {
            MethodCallOperator op = new MethodCallOperator(context, leftExpr.simplify());
            op.setASTNode(mcAST);
            path.add(op);
            step = op;

            List<Expression> params = new ArrayList<Expression>(5);
        }
        (
            {
                PathExpr pathExpr = new PathExpr(context);
                pathExpr.setASTNode(methodCallOp_AST_in);
            }
            expr [pathExpr] { params.add(pathExpr.simplify()); }
        )*
        {
            op.setMethod(mn.getText(), params);
        }
    )
    ;

otherwiseExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step = null;
}:
    #(
        owAST:LITERAL_otherwise
        {
            if (staticContext.getXQueryVersion() < 40) {
                throw new XPathException(owAST, ErrorCodes.XPST0003,
                    "The 'otherwise' operator requires xquery version \"4.0\"");
            }
            PathExpr leftExpr = new PathExpr(context);
            leftExpr.setASTNode(otherwiseExpr_AST_in);
        }
        expr [leftExpr]
        {
            PathExpr rightExpr = new PathExpr(context);
            rightExpr.setASTNode(otherwiseExpr_AST_in);
        }
        expr [rightExpr]
        {
            step = new OtherwiseExpression(context, leftExpr.simplify(), rightExpr.simplify());
            step.setASTNode(owAST);
            path.add(step);
        }
    )
    ;

typeCastExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
    step= null;
}:
    #(
        castAST:"cast"
        {
            PathExpr expr= new PathExpr(context);
            expr.setASTNode(typeCastExpr_AST_in);
            Cardinality cardinality= Cardinality.EXACTLY_ONE;
        }
        step=expr [expr]
        (
            #(
                CHOICE_TYPE
                {
                    List<Integer> choiceTypes = new ArrayList<Integer>();
                }
                (
                    ct:ATOMIC_TYPE
                    {
                        try {
                            QName qn = QName.parse(staticContext, ct.getText());
                            choiceTypes.add(Type.getType(qn));
                        } catch (final XPathException e) {
                            throw new XPathException(ct.getLine(), ct.getColumn(), ErrorCodes.XPST0051, "Unknown simple type " + ct.getText());
                        } catch (final IllegalQNameException e) {
                            throw new XPathException(ct.getLine(), ct.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + ct.getText());
                        }
                    }
                )+
            )
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                int[] types = new int[choiceTypes.size()];
                for (int ci = 0; ci < choiceTypes.size(); ci++) { types[ci] = choiceTypes.get(ci); }
                ChoiceCastExpression castExpr = new ChoiceCastExpression(context, expr, types, cardinality);
                castExpr.setASTNode(castAST);
                path.add(castExpr);
                step = castExpr;
            }
            |
            t:ATOMIC_TYPE
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                try {
                    QName qn= QName.parse(staticContext, t.getText());
                    int code= Type.getType(qn);
                    CastExpression castExpr= new CastExpression(context, expr, code, cardinality);
                    castExpr.setASTNode(castAST);
                    path.add(castExpr);
                    step = castExpr;
                } catch (final XPathException e) {
                    throw new XPathException(t.getLine(), t.getColumn(), ErrorCodes.XPST0051, "Unknown simple type " + t.getText());
                } catch (final IllegalQNameException e) {
                    throw new XPathException(t.getLine(), t.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + t.getText());
                }
            }
            |
            enumCast:ENUM_TYPE
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                String[] enumVals = enumCast.getText().split(",", -1);
                EnumCastExpression enumCastExpr = new EnumCastExpression(context, expr, enumVals, cardinality, false);
                enumCastExpr.setASTNode(castAST);
                path.add(enumCastExpr);
                step = enumCastExpr;
            }
        )
    )
    |
    #(
        castableAST:"castable"
        {
            PathExpr expr= new PathExpr(context);
            expr.setASTNode(typeCastExpr_AST_in);
            Cardinality cardinality= Cardinality.EXACTLY_ONE;
        }
        step=expr [expr]
        (
            #(
                CHOICE_TYPE
                {
                    List<Integer> choiceTypes2 = new ArrayList<Integer>();
                }
                (
                    ct2:ATOMIC_TYPE
                    {
                        try {
                            QName qn = QName.parse(staticContext, ct2.getText());
                            choiceTypes2.add(Type.getType(qn));
                        } catch (final XPathException e) {
                            throw new XPathException(ct2.getLine(), ct2.getColumn(), ErrorCodes.XPST0051, "Unknown simple type " + ct2.getText());
                        } catch (final IllegalQNameException e) {
                            throw new XPathException(ct2.getLine(), ct2.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + ct2.getText());
                        }
                    }
                )+
            )
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                int[] types2 = new int[choiceTypes2.size()];
                for (int ci = 0; ci < choiceTypes2.size(); ci++) { types2[ci] = choiceTypes2.get(ci); }
                ChoiceCastableExpression castExpr = new ChoiceCastableExpression(context, expr, types2, cardinality);
                castExpr.setASTNode(castableAST);
                path.add(castExpr);
                step = castExpr;
            }
            |
            t2:ATOMIC_TYPE
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                try {
                    QName qn= QName.parse(staticContext, t2.getText());
                    int code= Type.getType(qn);
                    CastableExpression castExpr= new CastableExpression(context, expr, code, cardinality);
                    castExpr.setASTNode(castableAST);
                    path.add(castExpr);
                    step = castExpr;
                } catch (final XPathException e) {
                    throw new XPathException(t2.getLine(), t2.getColumn(), ErrorCodes.XPST0051, "Unknown simple type " + t2.getText());
                } catch (final IllegalQNameException e) {
                    throw new XPathException(t2.getLine(), t2.getColumn(), ErrorCodes.XPST0081, "No namespace defined for prefix " + t2.getText());
                }
            }
            |
            enumCastable:ENUM_TYPE
            (
                QUESTION
                { cardinality= Cardinality.ZERO_OR_ONE; }
            )?
            {
                String[] enumVals2 = enumCastable.getText().split(",", -1);
                EnumCastExpression enumCastExpr2 = new EnumCastExpression(context, expr, enumVals2, cardinality, true);
                enumCastExpr2.setASTNode(castableAST);
                path.add(enumCastExpr2);
                step = enumCastExpr2;
            }
        )
    )
    ;

extensionExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
    step = null;
    PathExpr pathExpr = new PathExpr(context);
    pathExpr.setASTNode(extensionExpr_AST_in);
    ExtensionExpression ext = null;
}:
    (
        #(
            p:PRAGMA
            ( c:PRAGMA_END )?
            {
                Pragma pragma = context.getPragma(p.getText(), c.getText());
                if (pragma != null) {
                    if (ext == null) {
                        ext = new ExtensionExpression(context);
                        ext.setASTNode(extensionExpr_AST_in);
                    }
                    ext.addPragma(pragma);
                }
            }
        )
    )+
    expr [pathExpr]
    {
        if (ext != null) {
            ext.setExpression(pathExpr);
            path.add(ext);
            step = ext;
        } else {
            path.add(pathExpr);
            step = pathExpr;
        }
    }
    ;

// === Legacy update (DEPRECATED - use W3C XQuery Update Facility 3.0 syntax instead) ===
// To remove legacy update support, delete this rule and the updateExpr
// alternative in the expr dispatch above.

updateExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( updateAST:"update"
        {
            context.markLegacyUpdate(updateAST);

            PathExpr p1 = new PathExpr(context);
            p1.setASTNode(updateExpr_AST_in);

            PathExpr p2 = new PathExpr(context);
            p2.setASTNode(updateExpr_AST_in);

            int type;
            int position = Insert.INSERT_APPEND;
        }
        (
            "replace" { type = 0; }
            |
            "value" { type = 1; }
            |
            "insert"{ type = 2; }
            |
            "delete" { type = 3; }
            |
            "rename" { type = 4; }
        )
        step=expr [p1]
        (
            "preceding" { position = Insert.INSERT_BEFORE; }
            |
            "following" { position = Insert.INSERT_AFTER; }
            |
            "into" { position = Insert.INSERT_APPEND; }
        )?
        ( step=expr [p2] )?
        {
            Modification mod;
            if (type == 0)
                mod = new Replace(context, p1, p2);
            else if (type == 1)
                mod = new Update(context, p1, p2);
            else if (type == 2)
                mod = new Insert(context, p2, p1, position);
            else if (type == 3)
                mod = new Delete(context, p1);
            else
                mod = new Rename(context, p1, p2);
            mod.setASTNode(updateAST);
            path.add(mod);
            step = mod;
        }
    )
    ;

// === W3C XQuery Update Facility 3.0 tree walker rules ===

xqufInsertExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( insertAST:"insert"
        {
            context.markXQUFUpdate(insertAST);

            PathExpr sourceExpr = new PathExpr(context);
            sourceExpr.setASTNode(xqufInsertExpr_AST_in);

            PathExpr targetExpr = new PathExpr(context);
            targetExpr.setASTNode(xqufInsertExpr_AST_in);

            int mode = XQUFInsertExpr.INSERT_INTO;
        }
        step=expr [sourceExpr]
        (
            "first" { mode = XQUFInsertExpr.INSERT_INTO_AS_FIRST; }
            |
            "last" { mode = XQUFInsertExpr.INSERT_INTO_AS_LAST; }
            |
            "into" { mode = XQUFInsertExpr.INSERT_INTO; }
            |
            "before" { mode = XQUFInsertExpr.INSERT_BEFORE; }
            |
            "after" { mode = XQUFInsertExpr.INSERT_AFTER; }
        )
        step=expr [targetExpr]
        {
            XQUFInsertExpr ins = new XQUFInsertExpr(context, sourceExpr, targetExpr, mode);
            ins.setASTNode(insertAST);
            path.add(ins);
            step = ins;
        }
    )
    ;

xqufDeleteExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( deleteAST:"delete"
        {
            context.markXQUFUpdate(deleteAST);

            PathExpr targetExpr = new PathExpr(context);
            targetExpr.setASTNode(xqufDeleteExpr_AST_in);
        }
        step=expr [targetExpr]
        {
            XQUFDeleteExpr del = new XQUFDeleteExpr(context, targetExpr);
            del.setASTNode(deleteAST);
            path.add(del);
            step = del;
        }
    )
    ;

xqufReplaceExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( replaceAST:"replace"
        {
            context.markXQUFUpdate(replaceAST);

            PathExpr targetExpr = new PathExpr(context);
            targetExpr.setASTNode(xqufReplaceExpr_AST_in);

            PathExpr withExpr = new PathExpr(context);
            withExpr.setASTNode(xqufReplaceExpr_AST_in);

            boolean isValueOf = false;
        }
        (
            "value" { isValueOf = true; }
        )?
        step=expr [targetExpr]
        step=expr [withExpr]
        {
            Expression replExpr;
            if (isValueOf) {
                replExpr = new XQUFReplaceValueExpr(context, targetExpr, withExpr);
            } else {
                replExpr = new XQUFReplaceNodeExpr(context, targetExpr, withExpr);
            }
            replExpr.setASTNode(replaceAST);
            path.add(replExpr);
            step = replExpr;
        }
    )
    ;

xqufRenameExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( renameAST:"rename"
        {
            context.markXQUFUpdate(renameAST);

            PathExpr targetExpr = new PathExpr(context);
            targetExpr.setASTNode(xqufRenameExpr_AST_in);

            PathExpr nameExpr = new PathExpr(context);
            nameExpr.setASTNode(xqufRenameExpr_AST_in);
        }
        step=expr [targetExpr]
        step=expr [nameExpr]
        {
            XQUFRenameExpr ren = new XQUFRenameExpr(context, targetExpr, nameExpr);
            ren.setASTNode(renameAST);
            path.add(ren);
            step = ren;
        }
    )
    ;

xqufTransformExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #( copyAST:"copy"
        {
            context.markXQUFUpdate(copyAST);

            java.util.List copyBindings = new java.util.ArrayList();

            PathExpr modifyExpr = new PathExpr(context);
            modifyExpr.setASTNode(xqufTransformExpr_AST_in);

            PathExpr returnExpr = new PathExpr(context);
            returnExpr.setASTNode(xqufTransformExpr_AST_in);
        }
        (
            #( VARIABLE_BINDING
                {
                    PathExpr bindingExpr = new PathExpr(context);
                    bindingExpr.setASTNode(xqufTransformExpr_AST_in);
                    String varName = #VARIABLE_BINDING.getText();
                }
                step=expr [bindingExpr]
                {
                    final org.exist.dom.QName copyVarQName;
                    try {
                        copyVarQName = org.exist.dom.QName.parse(context, varName, null);
                    } catch (final org.exist.dom.QName.IllegalQNameException e) {
                        throw new XPathException(xqufTransformExpr_AST_in, ErrorCodes.XPST0081,
                            "Invalid variable name in copy binding: " + varName);
                    }
                    copyBindings.add(new XQUFTransformExpr.CopyBinding(copyVarQName, bindingExpr));
                }
            )
        )+
        step=expr [modifyExpr]
        step=expr [returnExpr]
        {
            XQUFTransformExpr trans = new XQUFTransformExpr(context, copyBindings, modifyExpr, returnExpr);
            trans.setASTNode(copyAST);
            path.add(trans);
            step = trans;
        }
    )
    ;

mapConstr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
	#(
		t:MAP
		{
			MapExpr expr = new MapExpr(context);
			expr.setASTNode(mapConstr_AST_in);
			step = expr;
		}
		(
			#(
				COLON
				{
					PathExpr key = new PathExpr(context);
                    key.setASTNode(mapConstr_AST_in);

					PathExpr value = new PathExpr(context);
                    value.setASTNode(mapConstr_AST_in);
				}
				expr[key]
				expr[value]
				{ expr.map(key, value); }
			)
			|
			#(
				MAP_CONTENT
				{
					PathExpr content = new PathExpr(context);
					content.setASTNode(mapConstr_AST_in);
				}
				expr[content]
				{ expr.content(content); }
			)
		)*
	)
	;

arrayConstr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
    #(
        t:ARRAY
        {
            String type = t.getText();
            ArrayConstructor array;
            if (type.equals("[")) {
                array = new ArrayConstructor(context, ArrayConstructor.ConstructorType.SQUARE_ARRAY);
            } else {
                array = new ArrayConstructor(context, ArrayConstructor.ConstructorType.CURLY_ARRAY);
            }
            step = array;
        }
        (
            {
                PathExpr arg = new PathExpr(context);
                arg.setASTNode(arrayConstr_AST_in);
            }
            expr[arg]
            { array.addArgument(arg); }
        )*
    )
    ;
