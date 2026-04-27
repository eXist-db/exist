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
package org.exist.xquery.parser.next;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.Constants.ArithmeticOperator;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.next.XQ4Expressions.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written recursive descent parser for XQuery (Phase 3).
 *
 * <p>Builds eXist's Expression tree directly — no intermediate AST.
 * Supports: prolog (version, namespaces, imports, function/variable decls),
 * annotations, inline functions, function references, try/catch/finally,
 * full FLWOR, constructors, typeswitch, switch, quantified expressions,
 * type expressions, and all Phase 1-2 features.</p>
 */
public final class XQueryParser {

    private final XQueryContext context;
    private final XQueryLexer lexer;
    private Token current;
    private Token previous;
    private Token bufferedNext;

    /** The PathExpr that accumulates prolog declarations and the body. */
    private PathExpr rootExpr;

    /** Track whether we're inside a function body (declared or inline) for XPDY0002 */
    private boolean inFunctionBody = false;

    /** True if the query is a library module (starts with 'module namespace'). */
    private boolean isLibraryModule = false;

    /** Track declared decimal format names for XQST0097 duplicate detection */
    private final java.util.Set<String> declaredDecimalFormats = new java.util.HashSet<>();
    private boolean defaultDecimalFormatDeclared = false;

    public boolean isLibraryModule() { return isLibraryModule; }

    /** Returns true if the query declares xquery version "4.0". */
    private boolean isXQ4() {
        return context.getXQueryVersion() >= 40;
    }

    /** Throws a helpful error when XQ4 syntax is used in a 3.1 query. */
    private XPathException xq4Required(final String feature) {
        return new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                feature + " requires xquery version \"4.0\". " +
                "Add 'xquery version \"4.0\";' to enable XQuery 4.0 features.");
    }

    public XQueryParser(final XQueryContext context, final String source) {
        this.context = context;
        this.lexer = new XQueryLexer(source);
        this.current = lexer.nextToken();
        this.previous = current;
    }

    /**
     * Parses a complete XQuery module (prolog + body) or a simple expression.
     */
    public Expression parse() throws XPathException {
        rootExpr = new PathExpr(context);

        // Check for version declaration
        if (checkKeyword(Keywords.XQUERY)) {
            parseVersionDecl();
        }

        // Check for module declaration (library module)
        if (checkKeyword(Keywords.MODULE)) {
            parseModuleDecl();
            // Parse prolog declarations
            parseProlog();
            expect(Token.EOF, "end of input");
            return rootExpr;
        }

        // Parse prolog declarations (if any)
        parseProlog();

        // Parse body expression
        if (!check(Token.EOF)) {
            final Expression body = parseExpr();
            rootExpr.add(body);
        }

        expect(Token.EOF, "end of input");
        return rootExpr;
    }

    /**
     * Parses a single expression (no prolog). Used for evaluating standalone expressions.
     */
    public Expression parseExpression() throws XPathException {
        final Expression expr = parseExpr();
        expect(Token.EOF, "end of input");
        return expr;
    }

    // ========================================================================
    // Prolog parsing
    // ========================================================================

    /**
     * Parses: xquery version "3.1" [encoding "..."];
     */
    private void parseVersionDecl() throws XPathException {
        matchKeyword(Keywords.XQUERY);
        expectKeyword(Keywords.VERSION);
        if (!check(Token.STRING_LITERAL)) throw error("Expected version string");
        final String version = current.value;
        advance();
        context.setXQueryVersion(parseVersionNumber(version));

        // Optional encoding
        if (matchKeyword(Keywords.ENCODING)) {
            if (!check(Token.STRING_LITERAL)) throw error("Expected encoding string");
            advance(); // consume encoding string (not used by context currently)
        }
        expect(Token.SEMICOLON, "';'");
    }

    private int parseVersionNumber(final String version) {
        switch (version) {
            case "1.0": return 10;
            case "3.0": return 30;
            case "3.1": return 31;
            case "4.0": return 40;
            default: return 31; // default to 3.1
        }
    }

    /**
     * Parses: module namespace prefix = "uri";
     */
    private void parseModuleDecl() throws XPathException {
        isLibraryModule = true;
        matchKeyword(Keywords.MODULE);
        expectKeyword(Keywords.NAMESPACE);
        final String prefix = expectName("module prefix");
        expect(Token.EQ, "'='");
        if (!check(Token.STRING_LITERAL)) throw error("Expected module namespace URI");
        final String uri = current.value;
        advance();
        expect(Token.SEMICOLON, "';'");

        try {
            // Set the module namespace on the context (critical for library modules)
            if (context instanceof ModuleContext) {
                ((ModuleContext) context).setModuleNamespace(prefix, uri);
            }
            context.declareNamespace(prefix, uri);
        } catch (final XPathException e) {
            throw error("Error declaring module namespace: " + e.getMessage());
        }
    }

    /**
     * Parses prolog declarations until the body expression begins.
     * Handles: namespace decls, imports, function/variable decls, options.
     */
    private void parseProlog() throws XPathException {
        while (checkKeyword(Keywords.DECLARE) || checkKeyword(Keywords.IMPORT)) {
            if (matchKeyword(Keywords.DECLARE)) {
                parseDeclare();
            } else if (matchKeyword(Keywords.IMPORT)) {
                parseImport();
            }
        }
    }

    private void parseDeclare() throws XPathException {
        // Parse annotations: %name or %name("value") before function/variable
        List<Annotation> annotations = null;
        if (checkAnnotationStart()) {
            annotations = parseAnnotations();
        }

        if (checkKeyword(Keywords.NAMESPACE)) {
            parseNamespaceDecl();
        } else if (checkKeyword(Keywords.DEFAULT)) {
            parseDefaultDecl();
        } else if (checkKeyword(Keywords.FUNCTION)) {
            parseFunctionDecl(annotations);
        } else if (checkKeyword(Keywords.VARIABLE)) {
            parseVariableDecl(annotations);
        } else if (checkKeyword(Keywords.OPTION)) {
            parseOptionDecl();
        } else if (checkKeyword(Keywords.CONTEXT)) {
            // declare context item [as type] [:= expr | external [:= expr]] ;
            advance(); // consume 'context'
            expectKeyword(Keywords.ITEM);
            SequenceType type = null;
            if (matchKeyword(Keywords.AS)) {
                type = parseSequenceType();
            }
            final boolean isExternal = matchKeyword(Keywords.EXTERNAL);
            Expression defaultExpr = null;
            if (match(Token.COLON_EQ)) {
                defaultExpr = parseExprSingle();
            }
            expect(Token.SEMICOLON, "';'");
            // Register context item declaration on the context
            final PathExpr enclosed = defaultExpr != null ? new PathExpr(context) : null;
            if (enclosed != null) enclosed.add(defaultExpr);
            final ContextItemDeclaration cid = new ContextItemDeclaration(context, type, isExternal, enclosed);
            context.setContextItemDeclaration(cid);
        } else if (checkKeyword("decimal-format")) {
            advance(); // consume "decimal-format"
            // Named decimal format: declare decimal-format name property = value ... ;
            final String dfName = expectName("decimal format name");
            final QName dfQName = resolveQName(dfName, null);
            final String dfKey = dfQName.getNamespaceURI() + ":" + dfQName.getLocalPart();
            if (!declaredDecimalFormats.add(dfKey)) {
                throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                        "Duplicate decimal format declaration: " + dfName);
            }
            final DecimalFormat df = parseDecimalFormatProperties();
            context.setStaticDecimalFormat(dfQName, df);
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword("ft-option")) {
            // declare ft-option using ... ;
            advance(); // consume "ft-option"
            parseFTOptionDecl();
        } else if (checkKeyword(Keywords.BOUNDARY_SPACE)) {
            // declare boundary-space preserve|strip;
            advance(); // consume boundary-space
            if (matchKeyword(Keywords.PRESERVE)) {
                context.setStripWhitespace(false);
            } else if (matchKeyword(Keywords.STRIP)) {
                context.setStripWhitespace(true);
            }
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword(Keywords.CONSTRUCTION)) {
            // declare construction preserve|strip;
            advance(); // consume construction
            if (matchKeyword(Keywords.PRESERVE)) {
                context.setPreserveNamespaces(true);
            } else {
                matchKeyword(Keywords.STRIP);
            }
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword(Keywords.ORDERING)) {
            // declare ordering ordered|unordered;
            advance();
            matchKeyword(Keywords.ORDERED);
            matchKeyword(Keywords.UNORDERED);
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword(Keywords.COPY_NAMESPACES)) {
            // declare copy-namespaces preserve|no-preserve, inherit|no-inherit;
            advance();
            if (matchKeyword(Keywords.PRESERVE)) {
                context.setPreserveNamespaces(true);
            } else if (matchKeyword("no-preserve")) {
                context.setPreserveNamespaces(false);
            }
            expect(Token.COMMA, "','");
            if (matchKeyword(Keywords.INHERIT)) {
                context.setInheritNamespaces(true);
            } else if (matchKeyword("no-inherit")) {
                context.setInheritNamespaces(false);
            }
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword(Keywords.BASE_URI)) {
            // declare base-uri "uri";
            advance();
            if (check(Token.STRING_LITERAL)) {
                context.setBaseURI(new AnyURIValue(current.value));
                advance();
            }
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword("revalidation")) {
            // XQUF: declare revalidation strict|lax|skip;
            advance(); // consume 'revalidation'
            matchKeyword("strict");
            matchKeyword("lax");
            matchKeyword("skip");
            expect(Token.SEMICOLON, "';'");
        } else {
            final String keyword = check(Token.NCNAME) ? current.value : "???";
            throw error("Unknown prolog declaration: declare " + keyword);
        }
    }

    private boolean checkAnnotationStart() {
        return check(Token.PERCENT);
    }

    private List<Annotation> parseAnnotations() throws XPathException {
        final List<Annotation> annotations = new ArrayList<>();
        while (match(Token.PERCENT)) {
            final String annotName = expectName("annotation name");
            final QName qname = resolveQName(annotName, context.getDefaultFunctionNamespace());

            // Optional parenthesized literal values
            final List<LiteralValue> values = new ArrayList<>();
            if (match(Token.LPAREN)) {
                if (!check(Token.RPAREN)) {
                    values.add(parseAnnotationValue());
                    while (match(Token.COMMA)) {
                        values.add(parseAnnotationValue());
                    }
                }
                expect(Token.RPAREN, "')'");
            }

            // Annotation needs a signature — will be set when attached to function
            annotations.add(new Annotation(qname, values.toArray(new LiteralValue[0]), null));
        }
        return annotations;
    }

    private LiteralValue parseAnnotationValue() throws XPathException {
        if (check(Token.STRING_LITERAL)) {
            final Token token = current;
            advance();
            return new LiteralValue(context, new StringValue(token.value));
        }
        // Negated numeric literal: -42, -3.14, -1e5
        if (check(Token.MINUS)) {
            advance(); // consume -
            if (check(Token.INTEGER_LITERAL)) {
                final Token token = current;
                advance();
                return new LiteralValue(context, new IntegerValue("-" + token.value.replace("_", "")));
            }
            if (check(Token.DECIMAL_LITERAL)) {
                final Token token = current;
                advance();
                return new LiteralValue(context, new DecimalValue("-" + token.value.replace("_", "")));
            }
            if (check(Token.DOUBLE_LITERAL)) {
                final Token token = current;
                advance();
                return new LiteralValue(context, new DoubleValue("-" + token.value.replace("_", "")));
            }
            throw error("Expected numeric literal after '-' in annotation");
        }
        if (check(Token.INTEGER_LITERAL)) {
            final Token token = current;
            advance();
            return new LiteralValue(context, new IntegerValue(token.value.replace("_", "")));
        }
        if (check(Token.DECIMAL_LITERAL)) {
            final Token token = current;
            advance();
            return new LiteralValue(context, new DecimalValue(token.value.replace("_", "")));
        }
        if (check(Token.DOUBLE_LITERAL)) {
            final Token token = current;
            advance();
            return new LiteralValue(context, new DoubleValue(token.value.replace("_", "")));
        }
        // true() and false() as annotation values
        if (check(Token.NCNAME) && ("true".equals(current.value) || "false".equals(current.value))) {
            final String boolVal = current.value;
            advance(); // consume true/false
            if (match(Token.LPAREN)) {
                expect(Token.RPAREN, "')'");
            }
            return new LiteralValue(context, new StringValue(boolVal));
        }
        throw error("Expected literal value in annotation");
    }

    private void parseNamespaceDecl() throws XPathException {
        matchKeyword(Keywords.NAMESPACE);
        final String prefix = expectNCName("namespace prefix");
        expect(Token.EQ, "'='");
        if (!check(Token.STRING_LITERAL)) throw error("Expected namespace URI");
        final String uri = current.value;
        advance();
        expect(Token.SEMICOLON, "';'");

        try {
            context.declareNamespace(prefix, uri);
        } catch (final XPathException e) {
            throw error("Error declaring namespace: " + e.getMessage());
        }
    }

    private void parseDefaultDecl() throws XPathException {
        matchKeyword(Keywords.DEFAULT);

        if (matchKeyword(Keywords.ELEMENT)) {
            expectKeyword(Keywords.NAMESPACE);
            if (!check(Token.STRING_LITERAL)) throw error("Expected namespace URI");
            final String uri = current.value;
            advance();
            expect(Token.SEMICOLON, "';'");
            context.setDefaultElementNamespace(uri, null); // schema=null
        } else if (matchKeyword(Keywords.FUNCTION)) {
            expectKeyword(Keywords.NAMESPACE);
            if (!check(Token.STRING_LITERAL)) throw error("Expected namespace URI");
            final String uri = current.value;
            advance();
            expect(Token.SEMICOLON, "';'");
            context.setDefaultFunctionNamespace(uri);
        } else if (matchKeyword(Keywords.COLLATION)) {
            if (!check(Token.STRING_LITERAL)) throw error("Expected collation URI");
            final String uri = current.value;
            advance();
            expect(Token.SEMICOLON, "';'");
            context.setDefaultCollation(uri);
        } else if (matchKeyword(Keywords.ORDER)) {
            expectKeyword(Keywords.EMPTY);
            if (matchKeyword(Keywords.GREATEST)) {
                context.setOrderEmptyGreatest(true);
            } else if (matchKeyword(Keywords.LEAST)) {
                context.setOrderEmptyGreatest(false);
            } else {
                throw error("Expected 'greatest' or 'least'");
            }
            expect(Token.SEMICOLON, "';'");
        } else if (checkKeyword("decimal-format")) {
            advance(); // consume "decimal-format"
            if (defaultDecimalFormatDeclared) {
                throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                        "Duplicate default decimal format declaration");
            }
            defaultDecimalFormatDeclared = true;
            final DecimalFormat df = parseDecimalFormatProperties();
            // // context.setDefaultStaticDecimalFormat(df); // TODO: requires v2/declare-decimal-format // TODO: requires v2/declare-decimal-format
            expect(Token.SEMICOLON, "';'");
        } else {
            throw error("Expected 'element', 'function', 'collation', 'order', or 'decimal-format' after 'default'");
        }
    }

    /**
     * Parses decimal-format property=value pairs.
     * Returns a DecimalFormat with all specified properties.
     */
    private DecimalFormat parseDecimalFormatProperties() throws XPathException {
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

        while (check(Token.NCNAME) && !check(Token.SEMICOLON)) {
            final String prop = current.value;
            advance();
            expect(Token.EQ, "'='");
            if (!check(Token.STRING_LITERAL)) throw error("Expected string value for decimal-format property");
            final String value = current.value;
            advance();
            switch (prop) {
                case "decimal-separator": decimalSeparator = requireSingleChar(prop, value); break;
                case "grouping-separator": groupingSeparator = requireSingleChar(prop, value); break;
                case "infinity": infinity = value; break;
                case "minus-sign": minusSign = requireSingleChar(prop, value); break;
                case "NaN": nan = value; break;
                case "percent": percent = requireSingleChar(prop, value); break;
                case "per-mille": perMille = requireSingleChar(prop, value); break;
                case "zero-digit":
                    final int zd = requireSingleChar(prop, value);
                    if (Character.getType(zd) != Character.DECIMAL_DIGIT_NUMBER || Character.getNumericValue(zd) != 0) {
                        throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                                "zero-digit must be a Unicode digit with numeric value zero, got: \"" + value + "\"");
                    }
                    zeroDigit = zd;
                    break;
                case "digit": digit = requireSingleChar(prop, value); break;
                case "pattern-separator": patternSeparator = requireSingleChar(prop, value); break;
                case "exponent-separator": exponentSeparator = requireSingleChar(prop, value); break;
                default: break; // unknown property — skip
            }
        }

        final DecimalFormat df = new DecimalFormat(decimalSeparator, exponentSeparator, groupingSeparator,
                percent, perMille, zeroDigit, digit, patternSeparator, infinity, nan, minusSign);
        // Validate distinct picture-string characters (XQST0098)
        final int[] chars = { decimalSeparator, groupingSeparator, percent, perMille,
                              zeroDigit, digit, patternSeparator, exponentSeparator };
        final String[] names = { "decimal-separator", "grouping-separator", "percent", "per-mille",
                                 "zero-digit", "digit", "pattern-separator", "exponent-separator" };
        for (int i = 0; i < chars.length; i++) {
            for (int j = i + 1; j < chars.length; j++) {
                if (chars[i] == chars[j]) {
                    throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                            "Decimal-format properties '" + names[i] + "' and '" + names[j] +
                            "' must have distinct values, but both are: '" +
                            new String(Character.toChars(chars[i])) + "'");
                }
            }
        }
        return df;
    }

    private int requireSingleChar(final String prop, final String value) throws XPathException {
        if (value.codePointCount(0, value.length()) != 1) {
            throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                    "The value of decimal-format property '" + prop + "' must be a single character, got: \"" + value + "\"");
        }
        return value.codePointAt(0);
    }

    private void parseFunctionDecl(final List<Annotation> annotations) throws XPathException {
        matchKeyword(Keywords.FUNCTION);
        final int line = previous.line, col = previous.column;

        // Function name
        final String funcName = expectName("function name");
        final QName qname;
        if (!funcName.contains(":") && context.getXQueryVersion() >= 40) {
            qname = new QName(funcName, "");
        } else {
            qname = resolveQName(funcName, context.getDefaultFunctionNamespace());
        }

        // Create signature and function
        final FunctionSignature signature = new FunctionSignature(qname);
        final UserDefinedFunction func = new UserDefinedFunction(context, signature);
        func.setLocation(line, col);

        // Apply annotations — re-create with correct signature reference
        if (annotations != null && !annotations.isEmpty()) {
            final Annotation[] anns = new Annotation[annotations.size()];
            for (int i = 0; i < annotations.size(); i++) {
                final Annotation a = annotations.get(i);
                anns[i] = new Annotation(a.getName(), a.getValue(), signature);
            }
            signature.setAnnotations(anns);
        }

        // Parameters
        expect(Token.LPAREN, "'('");
        final List<FunctionParameterSequenceType> params = new ArrayList<>();

        if (!check(Token.RPAREN)) {
            parseFunctionParam(params);
            while (match(Token.COMMA)) {
                parseFunctionParam(params);
            }
        }
        expect(Token.RPAREN, "')'");

        // Set parameter types on signature and add variable names to function
        final SequenceType[] paramTypes = new SequenceType[params.size()];
        for (int i = 0; i < params.size(); i++) {
            paramTypes[i] = params.get(i);
            func.addVariable(params.get(i).getAttributeName());
        }
        signature.setArgumentTypes(paramTypes);

        // Return type
        if (matchKeyword(Keywords.AS)) {
            signature.setReturnType(parseSequenceType());
        }

        // Function body or external
        if (matchKeyword(Keywords.EXTERNAL)) {
            // External function — no body
        } else {
            expect(Token.LBRACE, "'{'");
            final PathExpr body = new PathExpr(context);
            final boolean savedInFunctionBody = inFunctionBody;
            inFunctionBody = true;
            try {
                if (!check(Token.RBRACE)) {
                    body.add(parseExpr());
                }
                expect(Token.RBRACE, "'}'");
                func.setFunctionBody(body);
            } finally {
                inFunctionBody = savedInFunctionBody;
            }
        }

        expect(Token.SEMICOLON, "';'");

        // Register function
        context.declareFunction(func);
    }

    private void parseFunctionParam(final List<FunctionParameterSequenceType> params) throws XPathException {
        expect(Token.DOLLAR, "'$'");
        final String paramName = expectName("parameter name");

        int type = Type.ITEM;
        Cardinality card = Cardinality.ZERO_OR_MORE;

        if (matchKeyword(Keywords.AS)) {
            final SequenceType seqType = parseSequenceType();
            type = seqType.getPrimaryType();
            card = seqType.getCardinality();
        }

        final FunctionParameterSequenceType param =
                new FunctionParameterSequenceType(paramName, type, card, "");

        // XQ4: default parameter value
        if (check(Token.COLON_EQ)) {
            // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
            advance();
            // // param.setDefaultValue(parseExprSingle()); // TODO: requires v2/xquery-4.0-parser
parseExprSingle(); // parse but discard // TODO: requires v2/xquery-4.0-parser
parseExprSingle(); // parse but discard
        }

        params.add(param);
    }

    private void parseVariableDecl(final List<Annotation> annotations) throws XPathException {
        matchKeyword(Keywords.VARIABLE);
        final int line = previous.line, col = previous.column;

        expect(Token.DOLLAR, "'$'");
        final String varName = expectName("variable name");
        final QName qname = resolveQName(varName, null);

        // Optional type
        SequenceType type = null;
        if (matchKeyword(Keywords.AS)) {
            type = parseSequenceType();
        }

        // Value or external
        Expression valueExpr = null;
        boolean isExternal = false;
        if (match(Token.COLON_EQ)) {
            valueExpr = parseExprSingle();
        } else if (matchKeyword(Keywords.EXTERNAL)) {
            isExternal = true;
            // Optional default value for external: external := expr
            if (match(Token.COLON_EQ)) {
                valueExpr = parseExprSingle();
            }
        } else {
            throw error("Expected ':=' or 'external' in variable declaration");
        }

        expect(Token.SEMICOLON, "';'");

        if (isExternal) {
            // Try to resolve the variable from the static context (pre-declared externals)
            Variable external = null;
            try {
                external = context.resolveVariable(qname);
                if (external != null && type != null) {
                    external.setSequenceType(type);
                }
            } catch (final XPathException ignored) {
            }
            // Only add VariableDeclaration if the variable wasn't pre-declared
            if (external == null) {
                // Pass null for no default value, PathExpr for default value
                final Expression defaultVal;
                if (valueExpr != null) {
                    final PathExpr enclosed = new PathExpr(context);
                    enclosed.add(valueExpr);
                    defaultVal = enclosed;
                } else {
                    defaultVal = null;
                }
                final VariableDeclaration decl = new VariableDeclaration(context, qname, defaultVal);
                decl.setLocation(line, col);
                if (type != null) decl.setSequenceType(type);
                rootExpr.add(decl);
            }
        } else {
            final PathExpr enclosed = new PathExpr(context);
            enclosed.add(valueExpr);
            final VariableDeclaration decl = new VariableDeclaration(context, qname, enclosed);
            decl.setLocation(line, col);
            if (type != null) decl.setSequenceType(type);
            rootExpr.add(decl);
        }
    }

    private void parseOptionDecl() throws XPathException {
        matchKeyword(Keywords.OPTION);
        final String optionName = expectName("option name");
        if (!check(Token.STRING_LITERAL)) throw error("Expected option value");
        final String optionValue = current.value;
        advance();
        expect(Token.SEMICOLON, "';'");

        final QName qname = resolveQName(optionName, context.getDefaultFunctionNamespace());
        try {
            context.addOption(qname.toString(), optionValue);
        } catch (final XPathException e) {
            // option not recognized — ignore
        }
    }

    private void parseImport() throws XPathException {
        if (matchKeyword(Keywords.MODULE)) {
            parseModuleImport();
        } else if (matchKeyword(Keywords.SCHEMA)) {
            // Schema imports not supported — skip to semicolon
            while (!check(Token.SEMICOLON) && !check(Token.EOF)) {
                advance();
            }
            expect(Token.SEMICOLON, "';'");
        } else {
            throw error("Expected 'module' or 'schema' after 'import'");
        }
    }

    private void parseModuleImport() throws XPathException {
        expectKeyword(Keywords.NAMESPACE);
        final String prefix = expectName("module prefix");
        expect(Token.EQ, "'='");

        if (!check(Token.STRING_LITERAL)) throw error("Expected module namespace URI");
        final String uri = current.value;
        advance();

        // Optional location hints: at "location1", "location2"
        final List<AnyURIValue> locations = new ArrayList<>();
        if (matchKeyword(Keywords.AT)) {
            if (!check(Token.STRING_LITERAL)) throw error("Expected module location");
            locations.add(new AnyURIValue(current.value));
            advance();
            while (match(Token.COMMA)) {
                if (!check(Token.STRING_LITERAL)) throw error("Expected module location");
                locations.add(new AnyURIValue(current.value));
                advance();
            }
        }

        expect(Token.SEMICOLON, "';'");

        // Import the module
        try {
            context.importModule(uri, prefix, locations.toArray(new AnyURIValue[0]));
            context.declareNamespace(prefix, uri);
        } catch (final XPathException e) {
            throw new XPathException(previous.line, previous.column, e.getErrorCode(),
                    "Error importing module '" + uri + "': " + e.getMessage());
        }
    }

    // ========================================================================
    // Top-level expressions
    // ========================================================================

    Expression parseExpr() throws XPathException {
        final Expression first = parseExprSingle();
        if (!check(Token.COMMA)) {
            return first;
        }
        final SequenceConstructor seq = new SequenceConstructor(context);
        seq.setLocation(first.getLine(), first.getColumn());
        seq.add(first);
        while (match(Token.COMMA)) {
            seq.add(parseExprSingle());
        }
        return seq;
    }

    Expression parseExprSingle() throws XPathException {
        if (checkKeyword(Keywords.FOR) || checkKeyword(Keywords.LET)) {
            return parseFLWOR();
        }
        if (checkKeyword(Keywords.IF)) {
            return parseIfExpr();
        }
        if (checkKeyword(Keywords.SOME) && peekIs(Token.DOLLAR)) {
            return parseQuantified(QuantifiedExpression.SOME);
        }
        if (checkKeyword(Keywords.EVERY) && peekIs(Token.DOLLAR)) {
            return parseQuantified(QuantifiedExpression.EVERY);
        }
        if (checkKeyword(Keywords.SWITCH)) {
            return parseSwitchExpr();
        }
        if (checkKeyword(Keywords.TYPESWITCH)) {
            return parseTypeswitchExpr();
        }
        if (checkKeyword(Keywords.TRY)) {
            return parseTryCatchExpr();
        }
        // XQUF update expressions
        if (checkKeyword(Keywords.COPY)) {
            return parseTransformExpr();
        }
        // XQUF keywords — only treat as update expressions when NOT followed by (
        // (insert/delete/replace/rename are also valid function names)
        if (checkKeyword(Keywords.INSERT) && !peekIs(Token.LPAREN)) {
            return parseInsertExpr();
        }
        if (checkKeyword(Keywords.DELETE) && !peekIs(Token.LPAREN)) {
            return parseDeleteExpr();
        }
        if (checkKeyword(Keywords.REPLACE) && !peekIs(Token.LPAREN)) {
            return parseReplaceExpr();
        }
        if (checkKeyword(Keywords.RENAME) && !peekIs(Token.LPAREN)) {
            return parseRenameExpr();
        }
        // eXist legacy update syntax: update insert/replace/delete/rename/value
        if (checkKeyword(Keywords.UPDATE)) {
            return parseLegacyUpdateExpr();
        }
        return parseOrExpr();
    }

    // ========================================================================
    // Full FLWOR expression
    // ========================================================================

    /**
     * Parses a complete FLWOR expression with clause chaining.
     * Supports: for, let, where, order by, group by, count, while, for member.
     */
    Expression parseFLWOR() throws XPathException {
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            // Parse the first clause (may be a chain from comma-separated bindings)
            FLWORClause firstClause = parseFLWORInitialClause();
            FLWORClause lastClause = findLastInChain(firstClause);

            // Parse additional clauses until 'return'
            while (!checkKeyword(Keywords.RETURN)) {
                FLWORClause nextClause = null;

                if (checkKeyword(Keywords.FOR) || checkKeyword(Keywords.LET)) {
                    nextClause = parseFLWORInitialClause();
                } else if (matchKeyword(Keywords.WHERE)) {
                    nextClause = parseWhereClause();
                } else if (checkKeyword(Keywords.ORDER) || checkKeyword("stable")) {
                    nextClause = parseOrderByClause();
                } else if (matchKeyword(Keywords.GROUP)) {
                    expectKeyword(Keywords.BY);
                    nextClause = parseGroupByClause();
                } else if (matchKeyword(Keywords.COUNT)) {
                    nextClause = parseCountClause();
                } else if (checkKeyword(Keywords.WHILE)) {
                    // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
                    advance();
                    nextClause = parseWhileClause();
                } else {
                    throw error("Expected FLWOR clause or 'return'");
                }

                // Chain: lastClause's return is nextClause
                lastClause.setReturnExpression(nextClause);
                nextClause.setPreviousClause(lastClause);
                lastClause = findLastInChain(nextClause);
            }

            // 'return' — uses parseExprSingle (not parseExpr!) because the
            // FLWOR return clause must not consume commas that belong to the
            // enclosing expression (e.g., function argument separators):
            //   string-join(for $r in $result return string($r), ' ')
            //                                                   ^ this comma is NOT part of return
            expectKeyword(Keywords.RETURN);
            final Expression returnExpr = parseExprSingle();
            lastClause.setReturnExpression(new DebuggableExpression(returnExpr));

            return firstClause;
        } finally {
            context.popLocalVariables(mark);
        }
    }

    private FLWORClause parseFLWORInitialClause() throws XPathException {
        FLWORClause first;
        if (matchKeyword(Keywords.FOR)) {
            if (checkKeyword(Keywords.MEMBER)) {
                // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
                advance();
                first = parseForMemberBinding();
            } else if (checkKeyword(Keywords.TUMBLING) || checkKeyword(Keywords.SLIDING)) {
                first = parseWindowClause();
            } else {
                first = parseForBinding();
            }
        } else if (matchKeyword(Keywords.LET)) {
            first = parseLetBinding();
        } else {
            throw error("Expected 'for' or 'let'");
        }
        return first;
    }

    /**
     * Returns the last clause in a chain of comma-separated bindings.
     */
    private FLWORClause findLastInChain(FLWORClause clause) {
        while (clause.getReturnExpression() instanceof FLWORClause) {
            clause = (FLWORClause) clause.getReturnExpression();
        }
        return clause;
    }

    private FLWORClause parseForBinding() throws XPathException {
        final int startLine = previous.line;
        final int startCol = previous.column;

        expect(Token.DOLLAR, "'$'");
        final String varName = expectName("variable name");
        final QName qname = resolveQName(varName, null);

        // Optional type annotation: as SequenceType
        SequenceType forType = null;
        if (matchKeyword(Keywords.AS)) {
            forType = parseSequenceType();
        }

        // Optional allowing empty (XQ 3.0)
        boolean allowingEmpty = false;
        if (matchKeyword(Keywords.ALLOWING)) {
            matchKeyword(Keywords.EMPTY);
            allowingEmpty = true;
        }

        // Optional positional variable: at $pos
        QName posVar = null;
        if (matchKeyword(Keywords.AT)) {
            expect(Token.DOLLAR, "'$'");
            posVar = resolveQName(expectName("positional variable name"), null);
        }

        // Optional FT score variable: score $s
        QName scoreVar = null;
        if (matchKeyword("score")) {
            expect(Token.DOLLAR, "'$'");
            scoreVar = resolveQName(expectName("score variable name"), null);
        }

        expectKeyword(Keywords.IN);
        final Expression inputSeq = parseExprSingle();

        final ForExpr forExpr = new ForExpr(context, allowingEmpty);
        forExpr.setLocation(startLine, startCol);
        forExpr.setVariable(qname);
        forExpr.setInputSequence(inputSeq);
        if (posVar != null) {
            forExpr.setPositionalVariable(posVar);
        }
        if (scoreVar != null) {
            // // forExpr.setScoreVariable(scoreVar); // TODO: requires v2/xqft-phase2 // TODO: requires v2/xqft-phase2
        }

        // Register the variable so it's visible in subsequent clauses/return
        final LocalVariable var = forExpr.createVariable(qname);
        context.declareVariableBinding(var);

        // Handle comma-separated bindings: for $x in ..., $y in ...
        if (check(Token.COMMA) && !checkKeyword(Keywords.RETURN)) {
            // Peek past comma to see if it's another binding ($) or an expression
            if (peekAfterCommaIsDollar()) {
                match(Token.COMMA);
                final FLWORClause next = parseForBinding();
                forExpr.setReturnExpression(next);
                next.setPreviousClause(forExpr);
                return forExpr;
            }
        }

        return forExpr;
    }

    /**
     * Parses a tumbling/sliding window clause:
     * for tumbling/sliding window $w in EXPR start ... end ... return EXPR
     */
    private FLWORClause parseWindowClause() throws XPathException {
        final int line = previous.line, col = previous.column;
        final boolean tumbling = matchKeyword(Keywords.TUMBLING);
        if (!tumbling) matchKeyword(Keywords.SLIDING);
        expectKeyword(Keywords.WINDOW);

        expect(Token.DOLLAR, "'$'");
        final String varName = expectName("window variable");
        final QName qname = resolveQName(varName, null);

        // Optional type
        if (matchKeyword(Keywords.AS)) {
            parseSequenceType(); // consume type but not used for WindowExpr construction
        }

        expectKeyword(Keywords.IN);
        final Expression inputSeq = parseExprSingle();

        // Parse window conditions: start when/end when with variables
        // Start condition: required in XQ3.1, optional in XQ4 (defaults to always-true)
        final WindowCondition startCond;
        if (checkKeyword(Keywords.START)) {
            startCond = parseWindowCondition(Keywords.START);
        } else {
            // XQ4: implicit start — matches at every position
            startCond = new WindowCondition(context, false, null, null, null, null,
                    new LiteralValue(context, BooleanValue.TRUE));
        }
        final WindowCondition endCond = checkKeyword(Keywords.END) || checkKeyword(Keywords.ONLY) ?
                parseWindowCondition(Keywords.END) : null;

        final WindowExpr window = new WindowExpr(context,
                tumbling ? WindowExpr.WindowType.TUMBLING_WINDOW : WindowExpr.WindowType.SLIDING_WINDOW,
                startCond, endCond);
        window.setLocation(line, col);
        window.setVariable(qname);
        window.setInputSequence(inputSeq);

        final LocalVariable var = window.createVariable(qname);
        context.declareVariableBinding(var);

        return window;
    }

    private WindowCondition parseWindowCondition(final String keyword) throws XPathException {
        boolean only = false;
        if (matchKeyword(Keywords.ONLY)) {
            only = true;
        }
        matchKeyword(keyword); // start or end

        // Optional variable bindings: $var at $pos previous $prev next $next
        QName condVar = null;
        QName posVar = null;
        QName prevVar = null;
        QName nextVar = null;

        if (check(Token.DOLLAR)) {
            advance();
            condVar = resolveQName(expectName("window condition variable"), null);
        }
        if (matchKeyword(Keywords.AT)) {
            expect(Token.DOLLAR, "'$'");
            posVar = resolveQName(expectName("position variable"), null);
        }
        if (matchKeyword(Keywords.PREVIOUS)) {
            expect(Token.DOLLAR, "'$'");
            prevVar = resolveQName(expectName("previous variable"), null);
        }
        if (matchKeyword(Keywords.NEXT)) {
            expect(Token.DOLLAR, "'$'");
            nextVar = resolveQName(expectName("next variable"), null);
        }

        // XQ4: when clause is optional — defaults to true() (always matches)
        final Expression whenExpr;
        if (matchKeyword(Keywords.WHEN)) {
            whenExpr = parseExprSingle();
        } else {
            whenExpr = new LiteralValue(context, BooleanValue.TRUE);
        }

        return new WindowCondition(context, only, condVar, posVar, prevVar, nextVar, whenExpr);
    }

    private FLWORClause parseForMemberBinding() throws XPathException {
        final int startLine = previous.line;
        final int startCol = previous.column;

        expect(Token.DOLLAR, "'$'");
        final String varName = expectName("variable name");
        final QName qname = resolveQName(varName, null);

        // Optional type annotation: as SequenceType
        if (matchKeyword(Keywords.AS)) {
            parseSequenceType(); // consume type (ForMemberExpr doesn't store it yet)
        }

        // Optional positional variable: at $pos
        if (matchKeyword(Keywords.AT)) {
            expect(Token.DOLLAR, "'$'");
            expectName("positional variable name"); // consume (ForMemberExpr doesn't store it yet)
        }

        expectKeyword(Keywords.IN);
        final Expression inputSeq = parseExprSingle();

        final ForMemberExpr forMember = new ForMemberExpr(context);
        forMember.setLocation(startLine, startCol);
        forMember.setVariable(qname);
        forMember.setInputSequence(inputSeq);

        final LocalVariable var = forMember.createVariable(qname);
        context.declareVariableBinding(var);

        return forMember;
    }

    private FLWORClause parseLetBinding() throws XPathException {
        final int startLine = previous.line;
        final int startCol = previous.column;

        // XQFT 3.0: let score $s := expr
        final boolean isScore = matchKeyword("score");

        expect(Token.DOLLAR, "'$'");

        // XQ4: destructuring let — $( ), $[ ], ${ }
        if (!isScore && (check(Token.LPAREN) || check(Token.LBRACKET) || check(Token.LBRACE))) {
            return parseLetDestructure(startLine, startCol);
        }

        final String varName = expectName("variable name");
        final QName qname = resolveQName(varName, null);

        // Optional type annotation: as SequenceType (not for score bindings)
        SequenceType seqType = null;
        if (!isScore && matchKeyword(Keywords.AS)) {
            seqType = parseSequenceType();
        }

        expect(Token.COLON_EQ, "':='");

        final Expression inputSeq = parseExprSingle();

        final LetExpr letExpr = new LetExpr(context);
        letExpr.setLocation(startLine, startCol);
        letExpr.setVariable(qname);
        if (seqType != null) letExpr.setSequenceType(seqType);
        letExpr.setInputSequence(inputSeq);
        // // if (isScore) letExpr.setScoreBinding(true); // TODO: requires v2/xqft-phase2 // TODO: requires v2/xqft-phase2

        final LocalVariable var = letExpr.createVariable(qname);
        context.declareVariableBinding(var);

        // Handle comma-separated bindings: let $x := ..., $y := ...
        if (check(Token.COMMA) && !checkKeyword(Keywords.RETURN)) {
            if (peekAfterCommaIsDollar()) {
                match(Token.COMMA);
                final FLWORClause next = parseLetBinding();
                letExpr.setReturnExpression(next);
                next.setPreviousClause(letExpr);
                return letExpr;
            }
        }

        return letExpr;
    }

    /**
     * Parses XQ4 destructuring let bindings:
     *   let $($x, $y) := (1, 2)       — sequence destructuring
     *   let $[$x, $y] := [1, 2]       — array destructuring
     *   let ${$x, $y} := map{...}     — map destructuring
     * Current token is the opening delimiter after $.
     */
    private FLWORClause parseLetDestructure(final int startLine, final int startCol) throws XPathException {
        final LetDestructureExpr.DestructureMode mode;
        final int closeToken;

        if (match(Token.LPAREN)) {
            mode = LetDestructureExpr.DestructureMode.SEQUENCE;
            closeToken = Token.RPAREN;
        } else if (match(Token.LBRACKET)) {
            mode = LetDestructureExpr.DestructureMode.ARRAY;
            closeToken = Token.RBRACKET;
        } else {
            advance(); // consume {
            mode = LetDestructureExpr.DestructureMode.MAP;
            closeToken = Token.RBRACE;
        }

        final LetDestructureExpr destructure = new LetDestructureExpr(context, mode);
        destructure.setLocation(startLine, startCol);

        // Parse comma-separated variable list: $x [as Type], $y [as Type], ...
        do {
            expect(Token.DOLLAR, "'$'");
            final String vn = expectName("variable name");
            final QName qname = resolveQName(vn, null);
            SequenceType varType = null;
            if (matchKeyword(Keywords.AS)) {
                varType = parseSequenceType();
            }
            destructure.addVariable(qname, varType);
        } while (match(Token.COMMA));

        if (current.type != closeToken) {
            final String closeChar = closeToken == Token.RPAREN ? ")" :
                    closeToken == Token.RBRACKET ? "]" : "}";
            throw error("Expected '" + closeChar + "' to close destructuring pattern");
        }
        advance(); // consume closing delimiter

        // Optional overall type annotation
        if (matchKeyword(Keywords.AS)) {
            destructure.setOverallType(parseSequenceType());
        }

        expect(Token.COLON_EQ, "':='");
        destructure.setInputSequence(parseExprSingle());

        // Declare all destructured variables
        for (final QName qn : destructure.getTupleStreamVariables()) {
            context.declareVariableBinding(new LocalVariable(qn));
        }

        // Handle comma-separated bindings after destructure
        if (check(Token.COMMA) && !checkKeyword(Keywords.RETURN)) {
            if (peekAfterCommaIsDollar()) {
                match(Token.COMMA);
                final FLWORClause next = parseLetBinding();
                destructure.setReturnExpression(next);
                next.setPreviousClause(destructure);
                return destructure;
            }
        }

        return destructure;
    }

    private WhereClause parseWhereClause() throws XPathException {
        final int line = previous.line;
        final int col = previous.column;
        final Expression whereExpr = parseExprSingle();
        final WhereClause clause = new WhereClause(context, new DebuggableExpression(whereExpr));
        clause.setLocation(line, col);
        return clause;
    }

    private OrderByClause parseOrderByClause() throws XPathException {
        final int line = current.line;
        final int col = current.column;
        matchKeyword("stable"); // optional 'stable' before 'order by'
        matchKeyword(Keywords.ORDER);
        expectKeyword(Keywords.BY);

        final List<OrderSpec> specs = new ArrayList<>();
        do {
            final Expression sortExpr = parseExprSingle();
            final OrderSpec spec = new OrderSpec(context, sortExpr);
            int modifiers = 0;

            // ascending/descending
            if (matchKeyword(Keywords.DESCENDING)) {
                modifiers |= OrderSpec.DESCENDING_ORDER;
            } else {
                matchKeyword(Keywords.ASCENDING); // optional, default
            }

            // empty greatest/least
            if (matchKeyword(Keywords.EMPTY)) {
                if (matchKeyword(Keywords.GREATEST)) {
                    // EMPTY_GREATEST is 0, so just clear the EMPTY_LEAST bit
                    modifiers &= ~OrderSpec.EMPTY_LEAST;
                } else if (matchKeyword(Keywords.LEAST)) {
                    modifiers |= OrderSpec.EMPTY_LEAST;
                } else {
                    throw error("Expected 'greatest' or 'least' after 'empty'");
                }
            }

            spec.setModifiers(modifiers);
            specs.add(spec);
        } while (match(Token.COMMA));

        final OrderByClause clause = new OrderByClause(context, specs);
        clause.setLocation(line, col);
        return clause;
    }

    private GroupByClause parseGroupByClause() throws XPathException {
        final int line = previous.line;
        final int col = previous.column;

        final List<GroupSpec> specs = new ArrayList<>();
        do {
            expect(Token.DOLLAR, "'$'");
            final String varName = expectName("grouping variable");
            final QName qname = resolveQName(varName, null);

            Expression groupExpr = null;
            if (match(Token.COLON_EQ)) {
                groupExpr = parseExprSingle();
            }

            specs.add(new GroupSpec(context, groupExpr, qname, null));
        } while (match(Token.COMMA));

        final GroupByClause clause = new GroupByClause(context);
        clause.setLocation(line, col);
        clause.setGroupSpecs(specs.toArray(new GroupSpec[0]));
        return clause;
    }

    private CountClause parseCountClause() throws XPathException {
        final int line = previous.line;
        final int col = previous.column;
        expect(Token.DOLLAR, "'$'");
        final String varName = expectName("count variable");
        final QName qname = resolveQName(varName, null);
        final CountClause clause = new CountClause(context, qname);
        clause.setLocation(line, col);
        return clause;
    }

    private WhileClause parseWhileClause() throws XPathException {
        final int line = previous.line;
        final int col = previous.column;
        // XQ4 spec: WhileClause ::= "while" ExprSingle (no parens required)
        // But accept optional parens for backwards compatibility
        final boolean hasParens = match(Token.LPAREN);
        final Expression condition = parseExprSingle();
        if (hasParens) expect(Token.RPAREN, "')'");
        final WhileClause clause = new WhileClause(context, new DebuggableExpression(condition));
        clause.setLocation(line, col);
        return clause;
    }

    /**
     * Checks if after the current comma token, a '$' follows (binding continuation).
     */
    private boolean peekAfterCommaIsDollar() {
        if (bufferedNext == null) {
            bufferedNext = lexer.nextToken();
        }
        return bufferedNext.type == Token.DOLLAR;
    }

    // ========================================================================
    // If expression (including braced if for XQ4)
    // ========================================================================

    Expression parseIfExpr() throws XPathException {
        final int startLine = current.line;
        final int startCol = current.column;
        matchKeyword(Keywords.IF);

        expect(Token.LPAREN, "'('");
        final Expression condition = parseExpr();
        expect(Token.RPAREN, "')'");

        // Braced if: if (cond) { expr } — no else clause (XQ4, accepted in all versions)
        if (check(Token.LBRACE) && !checkKeyword(Keywords.THEN)) {
            match(Token.LBRACE);
            final Expression thenExpr = parseExpr();
            expect(Token.RBRACE, "'}'");
            // Braced if returns empty sequence when false
            final PathExpr empty = new PathExpr(context);
            final ConditionalExpression ifExpr = new ConditionalExpression(context, condition, thenExpr, empty);
            ifExpr.setLocation(startLine, startCol);
            return ifExpr;
        }

        expectKeyword(Keywords.THEN);
        final Expression thenExpr = parseExprSingle();

        expectKeyword(Keywords.ELSE);
        final Expression elseExpr = parseExprSingle();

        final ConditionalExpression ifExpr = new ConditionalExpression(context, condition, thenExpr, elseExpr);
        ifExpr.setLocation(startLine, startCol);
        return ifExpr;
    }

    // ========================================================================
    // Quantified expressions: some/every
    // ========================================================================

    Expression parseQuantified(final int mode) throws XPathException {
        final int startLine = current.line;
        final int startCol = current.column;
        advance(); // consume 'some' or 'every'

        final LocalVariable mark = context.markLocalVariables(false);
        try {
            expect(Token.DOLLAR, "'$'");
            final String varName = expectName("variable name");
            final QName qname = resolveQName(varName, null);

            // Optional type annotation: as SequenceType
            if (matchKeyword(Keywords.AS)) {
                parseSequenceType(); // consume type but not used
            }

            expectKeyword(Keywords.IN);
            final Expression inputSeq = parseExprSingle();

            expectKeyword(Keywords.SATISFIES);

            final QuantifiedExpression quant = new QuantifiedExpression(context, mode);
            quant.setLocation(startLine, startCol);
            quant.setVariable(qname);
            quant.setInputSequence(inputSeq);

            final LocalVariable var = quant.createVariable(qname);
            context.declareVariableBinding(var);

            final Expression satisfiesExpr = parseExprSingle();
            quant.setReturnExpression(satisfiesExpr);

            return quant;
        } finally {
            context.popLocalVariables(mark);
        }
    }

    // ========================================================================
    // Switch expression
    // ========================================================================

    Expression parseSwitchExpr() throws XPathException {
        final int startLine = current.line;
        final int startCol = current.column;
        matchKeyword(Keywords.SWITCH);

        expect(Token.LPAREN, "'('");

        // XQ4: switch() with no operand — boolean mode
        final boolean booleanMode = check(Token.RPAREN);
        final Expression operand;
        if (booleanMode) {
            operand = new PathExpr(context); // empty operand
        } else {
            operand = parseExpr();
        }
        expect(Token.RPAREN, "')'");

        final SwitchExpression switchExpr = new SwitchExpression(context, operand);
        switchExpr.setLocation(startLine, startCol);
        if (booleanMode) {
            switchExpr.setBooleanMode(true);
        }

        // XQ4: optional braces around case/default clauses
        final boolean braced = match(Token.LBRACE);

        // case clauses
        while (checkKeyword(Keywords.CASE)) {
            matchKeyword(Keywords.CASE);
            final List<Expression> caseOperands = new ArrayList<>();
            caseOperands.add(parseExprSingle());

            // Multiple case values: case "a" case "b" return ...
            while (checkKeyword(Keywords.CASE)) {
                matchKeyword(Keywords.CASE);
                caseOperands.add(parseExprSingle());
            }

            expectKeyword(Keywords.RETURN);
            final Expression returnExpr = parseExprSingle();
            switchExpr.addCase(caseOperands, returnExpr);
        }

        // default clause
        expectKeyword(Keywords.DEFAULT);
        expectKeyword(Keywords.RETURN);
        final Expression defaultExpr = parseExprSingle();
        switchExpr.setDefault(defaultExpr);

        if (braced) {
            expect(Token.RBRACE, "'}'");
        }

        return switchExpr;
    }

    // ========================================================================
    // Typeswitch expression
    // ========================================================================

    Expression parseTypeswitchExpr() throws XPathException {
        final int startLine = current.line;
        final int startCol = current.column;
        matchKeyword(Keywords.TYPESWITCH);

        expect(Token.LPAREN, "'('");
        final Expression operand = parseExpr();
        expect(Token.RPAREN, "')'");

        final TypeswitchExpression tswitch = new TypeswitchExpression(context, operand);
        tswitch.setLocation(startLine, startCol);

        // XQ4: optional braces around case/default clauses
        final boolean braced = match(Token.LBRACE);

        // case clauses
        while (checkKeyword(Keywords.CASE)) {
            matchKeyword(Keywords.CASE);

            // Optional variable: case $var as type
            QName caseVar = null;
            if (check(Token.DOLLAR)) {
                final int savedLine = current.line;
                // Peek ahead to see if this is $var as Type or just a type
                // If $ name 'as' follows, it's a variable binding
                if (peekIs(Token.NCNAME)) {
                    // Save state; speculatively consume $name and check for 'as'
                    final Token dollarTok = current;
                    final Token savedBuffered = bufferedNext;
                    advance(); // $
                    final String name = current.value;
                    advance(); // name
                    if (checkKeyword(Keywords.AS)) {
                        matchKeyword(Keywords.AS);
                        caseVar = resolveQName(name, null);
                    } else {
                        // Not a variable binding, put tokens back
                        // This is tricky with our forward-only lexer; fallback
                        // Actually this case shouldn't happen in valid XQuery
                        throw error("Expected 'as' after variable in typeswitch case");
                    }
                }
            }

            // Parse sequence type(s) — support union types: case xs:string | xs:integer
            final List<SequenceType> types = new ArrayList<>();
            types.add(parseSequenceType());
            while (match(Token.PIPE)) {
                types.add(parseSequenceType());
            }

            expectKeyword(Keywords.RETURN);
            final Expression returnExpr = parseExprSingle();

            tswitch.addCase(types.toArray(new SequenceType[0]), caseVar, returnExpr);
        }

        // default clause
        expectKeyword(Keywords.DEFAULT);

        // Optional variable in default: default $var return ...
        QName defaultVar = null;
        if (check(Token.DOLLAR)) {
            match(Token.DOLLAR);
            defaultVar = resolveQName(expectName("default variable"), null);
        }

        expectKeyword(Keywords.RETURN);
        final Expression defaultExpr = parseExprSingle();
        tswitch.setDefault(defaultVar, defaultExpr);

        if (braced) {
            expect(Token.RBRACE, "'}'");
        }

        return tswitch;
    }

    // ========================================================================
    // Try/catch/finally expression
    // ========================================================================

    Expression parseTryCatchExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.TRY);

        // Try body: { expr }
        expect(Token.LBRACE, "'{'");
        final PathExpr tryExpr = new PathExpr(context);
        tryExpr.add(parseExpr());
        expect(Token.RBRACE, "'}'");

        final TryCatchExpression tryCatch = new TryCatchExpression(context, tryExpr);
        tryCatch.setLocation(line, col);

        // Catch clauses: catch errorCode { expr }
        // or catch errorCode ($code, $desc, $val) { expr }
        while (checkKeyword(Keywords.CATCH)) {
            matchKeyword(Keywords.CATCH);

            // Error code list: *, *:local, prefix:*, or QName (| ...)*
            final List<QName> errorCodes = new ArrayList<>();
            errorCodes.add(parseCatchNameTest());
            while (match(Token.PIPE)) {
                errorCodes.add(parseCatchNameTest());
            }

            // Optional explicit catch variable bindings: ($code, $desc, $val)
            final List<QName> catchVars = new ArrayList<>(3);
            if (match(Token.LPAREN)) {
                // Custom catch variable names
                expect(Token.DOLLAR, "'$'");
                catchVars.add(resolveQName(expectName("catch error code variable"), null));
                if (match(Token.COMMA)) {
                    expect(Token.DOLLAR, "'$'");
                    catchVars.add(resolveQName(expectName("catch error description variable"), null));
                    if (match(Token.COMMA)) {
                        expect(Token.DOLLAR, "'$'");
                        catchVars.add(resolveQName(expectName("catch error value variable"), null));
                    }
                }
                expect(Token.RPAREN, "')'");
            } else {
                // Default err:code, err:description, err:value
                catchVars.add(new QName("code", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, "err"));
                catchVars.add(new QName("description", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, "err"));
                catchVars.add(new QName("value", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, "err"));
            }

            // Catch body: { expr }
            expect(Token.LBRACE, "'{'");

            final LocalVariable mark = context.markLocalVariables(false);
            try {
                for (final QName cv : catchVars) {
                    context.declareVariableBinding(new LocalVariable(cv));
                }

                final PathExpr catchExpr = new PathExpr(context);
                catchExpr.add(parseExpr());
                expect(Token.RBRACE, "'}'");

                tryCatch.addCatchClause(errorCodes, catchVars, catchExpr);
            } finally {
                context.popLocalVariables(mark);
            }
        }

        // Optional finally clause (XQ4 only)
        // Supports try { } finally { } without catch clauses
        if (checkKeyword(Keywords.FINALLY)) {
            // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
            advance();
            expect(Token.LBRACE, "'{'");
            final PathExpr finallyExpr = new PathExpr(context);
            finallyExpr.add(parseExpr());
            expect(Token.RBRACE, "'}'");
            // // tryCatch.setFinallyExpr(finallyExpr); // TODO: requires v2/xquery-4.0-parser // TODO: requires v2/xquery-4.0-parser
        }

        return tryCatch;
    }

    // ========================================================================
    // Inline functions and function references
    // ========================================================================

    /**
     * Parses an inline function expression:
     * function($param) { body }
     * function($param as type) as returnType { body }
     */
    Expression parseInlineFunction() throws XPathException {
        final int line = previous.line, col = previous.column;

        final FunctionSignature signature = new FunctionSignature(InlineFunction.INLINE_FUNCTION_QNAME);
        final UserDefinedFunction func = new UserDefinedFunction(context, signature);
        func.setLocation(line, col);

        // Parameters
        expect(Token.LPAREN, "'('");
        final List<FunctionParameterSequenceType> params = new ArrayList<>();
        if (!check(Token.RPAREN)) {
            parseFunctionParam(params);
            while (match(Token.COMMA)) {
                parseFunctionParam(params);
            }
        }
        expect(Token.RPAREN, "')'");

        // Set parameter types
        final SequenceType[] paramTypes = new SequenceType[params.size()];
        for (int i = 0; i < params.size(); i++) {
            paramTypes[i] = params.get(i);
            func.addVariable(params.get(i).getAttributeName());
        }
        signature.setArgumentTypes(paramTypes);

        // Optional return type
        if (matchKeyword(Keywords.AS)) {
            signature.setReturnType(parseSequenceType());
        }

        // Function body
        expect(Token.LBRACE, "'{'");
        final LocalVariable mark = context.markLocalVariables(false);
        final boolean savedInFunctionBody = inFunctionBody;
        inFunctionBody = true;
        try {
            // Declare parameter variables in scope
            for (final FunctionParameterSequenceType param : params) {
                context.declareVariableBinding(new LocalVariable(
                        resolveQName(param.getAttributeName(), null)));
            }

            final PathExpr body = new PathExpr(context);
            if (!check(Token.RBRACE)) {
                body.add(parseExpr());
            }
            expect(Token.RBRACE, "'}'");

            func.setFunctionBody(body);
        } finally {
            inFunctionBody = savedInFunctionBody;
            context.popLocalVariables(mark);
        }

        final InlineFunction inline = new InlineFunction(context, func);
        inline.setLocation(line, col);
        return inline;
    }

    /**
     * Parses a named function reference: name#arity
     * e.g., fn:count#1, local:greet#1
     */
    /** Parses an error code QName — handles NCName, QName, and EQName (Q{uri}local). */
    /**
     * Parses a name test in catch error list: *, *:local, prefix:*, or QName.
     */
    private QName parseCatchNameTest() throws XPathException {
        if (match(Token.STAR)) {
            if (match(Token.COLON)) {
                // *:local — wildcard namespace
                final String local = expectName("error code local part");
                return new QName.WildcardNamespaceURIQName(local);
            }
            return QName.WildcardQName.getInstance();
        }
        return parseErrorCodeQName();
    }

    private QName parseErrorCodeQName() throws XPathException {
        if (check(Token.BRACED_URI_LITERAL)) {
            final String eqname = parseEQName();
            final int braceEnd = eqname.indexOf('}');
            return new QName(eqname.substring(braceEnd + 1), eqname.substring(2, braceEnd));
        }
        // Handle wildcard name tests: prefix:*, *:local
        if (check(Token.NCNAME) && peekIs(Token.COLON)) {
            final String prefix = current.value;
            advance(); // consume prefix
            advance(); // consume :
            if (match(Token.STAR)) {
                // prefix:* — wildcard local part
                final String nsURI = context.getURIForPrefix(prefix);
                return new QName.WildcardLocalPartQName(nsURI != null ? nsURI : "", prefix);
            }
            // prefix:local — regular QName
            final String local = expectName("error code local part");
            return resolveQName(prefix + ":" + local, Namespaces.XPATH_FUNCTIONS_NS);
        }
        if (check(Token.STAR)) {
            advance(); // consume *
            if (match(Token.COLON)) {
                // *:local — wildcard namespace
                final String local = expectName("error code local part");
                return new QName.WildcardNamespaceURIQName(local);
            }
            // Bare * — already handled by the caller, but just in case
            return QName.WildcardQName.getInstance();
        }
        final String errorName = expectName("error code");
        return resolveQName(errorName, Namespaces.XPATH_FUNCTIONS_NS);
    }

    Expression parseNamedFunctionRef(final String name) throws XPathException {
        final int line = previous.line, col = previous.column;
        // # already consumed, expect integer arity
        if (!check(Token.INTEGER_LITERAL)) throw error("Expected arity after '#'");
        final int arity = Integer.parseInt(current.value);
        advance();

        final QName qname;
        if (name.startsWith("Q{")) {
            // EQName: Q{uri}local
            final int braceEnd = name.indexOf('}');
            final String uri = name.substring(2, braceEnd);
            final String local = name.substring(braceEnd + 1);
            qname = new QName(local, uri);
        } else {
            qname = resolveQName(name, context.getDefaultFunctionNamespace());
        }
        final NamedFunctionReference ref = new NamedFunctionReference(context, qname, arity);
        ref.setLocation(line, col);
        return ref;
    }

    // ========================================================================
    // ========================================================================
    // XQUF: Update expressions
    // ========================================================================

    Expression parseTransformExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.COPY);

        final LocalVariable mark = context.markLocalVariables(false);
        try {
            // Parse copy bindings: $var := expr (, $var := expr)*
            final List<XQUFExpressions.CopyBinding> bindings = new ArrayList<>();
            do {
                expect(Token.DOLLAR, "'$'");
                final String varName = expectName("copy variable name");
                final QName qname = resolveQName(varName, null);
                expect(Token.COLON_EQ, "':='");
                final Expression sourceExpr = parseExprSingle();
                bindings.add(new XQUFExpressions.CopyBinding(qname, sourceExpr));

                final LocalVariable var = new LocalVariable(qname);
                context.declareVariableBinding(var);
            } while (match(Token.COMMA));

            // modify clause
            expectKeyword(Keywords.MODIFY);
            final Expression modifyExpr = parseExprSingle();

            // return clause
            expectKeyword(Keywords.RETURN);
            final Expression returnExpr = parseExprSingle();

            final XQUFExpressions.TransformExpr transform =
                    new XQUFExpressions.TransformExpr(context, bindings, modifyExpr, returnExpr);
            transform.setLocation(line, col);
            return transform;
        } finally {
            context.popLocalVariables(mark);
        }
    }

    Expression parseInsertExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.INSERT);

        // "node" or "nodes"
        if (!matchKeyword(Keywords.NODE) && !matchKeyword(Keywords.NODES)) {
            throw error("Expected 'node' or 'nodes' after 'insert'");
        }

        final Expression source = parseExprSingle();

        // Position: into, as first into, as last into, before, after
        int mode;
        if (matchKeyword(Keywords.INTO)) {
            mode = XQUFExpressions.InsertExpr.INSERT_INTO;
        } else if (matchKeyword(Keywords.AS)) {
            if (matchKeyword(Keywords.FIRST)) {
                expectKeyword(Keywords.INTO);
                mode = XQUFExpressions.InsertExpr.INSERT_INTO_AS_FIRST;
            } else if (matchKeyword(Keywords.LAST)) {
                expectKeyword(Keywords.INTO);
                mode = XQUFExpressions.InsertExpr.INSERT_INTO_AS_LAST;
            } else {
                throw error("Expected 'first' or 'last' after 'as'");
            }
        } else if (matchKeyword(Keywords.BEFORE)) {
            mode = XQUFExpressions.InsertExpr.INSERT_BEFORE;
        } else if (matchKeyword(Keywords.AFTER)) {
            mode = XQUFExpressions.InsertExpr.INSERT_AFTER;
        } else {
            throw error("Expected 'into', 'before', 'after', or 'as first/last into'");
        }

        final Expression target = parseExprSingle();
        final XQUFExpressions.InsertExpr insert = new XQUFExpressions.InsertExpr(context, source, target, mode);
        insert.setLocation(line, col);
        return insert;
    }

    Expression parseDeleteExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.DELETE);

        if (!matchKeyword(Keywords.NODE) && !matchKeyword(Keywords.NODES)) {
            throw error("Expected 'node' or 'nodes' after 'delete'");
        }

        final Expression target = parseExprSingle();
        final XQUFExpressions.DeleteExpr delete = new XQUFExpressions.DeleteExpr(context, target);
        delete.setLocation(line, col);
        return delete;
    }

    Expression parseReplaceExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.REPLACE);

        // "value of node" or "node"
        if (matchKeyword(Keywords.VALUE)) {
            expectKeyword(Keywords.OF);
            expectKeyword(Keywords.NODE);
            final Expression target = parseExprSingle();
            expectKeyword(Keywords.WITH);
            final Expression value = parseExprSingle();
            final XQUFExpressions.ReplaceValueExpr replace =
                    new XQUFExpressions.ReplaceValueExpr(context, target, value);
            replace.setLocation(line, col);
            return replace;
        } else {
            expectKeyword(Keywords.NODE);
            final Expression target = parseExprSingle();
            expectKeyword(Keywords.WITH);
            final Expression replacement = parseExprSingle();
            final XQUFExpressions.ReplaceNodeExpr replace =
                    new XQUFExpressions.ReplaceNodeExpr(context, target, replacement);
            replace.setLocation(line, col);
            return replace;
        }
    }

    Expression parseRenameExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.RENAME);
        expectKeyword(Keywords.NODE);

        final Expression target = parseExprSingle();
        expectKeyword(Keywords.AS);
        final Expression newName = parseExprSingle();

        final XQUFExpressions.RenameExpr rename = new XQUFExpressions.RenameExpr(context, target, newName);
        rename.setLocation(line, col);
        return rename;
    }

    /**
     * Parses eXist's legacy update syntax:
     * update replace EXPR1 EXPR2
     * update value EXPR1 EXPR2
     * update insert EXPR1 [preceding|following|into] EXPR2
     * update delete EXPR1
     * update rename EXPR1 EXPR2
     */
    Expression parseLegacyUpdateExpr() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.UPDATE);

        final int type;
        if (matchKeyword(Keywords.REPLACE)) { type = 0; }
        else if (matchKeyword(Keywords.VALUE)) { type = 1; }
        else if (matchKeyword(Keywords.INSERT)) { type = 2; }
        else if (matchKeyword(Keywords.DELETE)) { type = 3; }
        else if (matchKeyword(Keywords.RENAME)) { type = 4; }
        else { throw error("Expected 'replace', 'value', 'insert', 'delete', or 'rename' after 'update'"); }

        final Expression p1 = parseExprSingle();

        // For insert: optional position keyword
        int position = org.exist.xquery.update.Insert.INSERT_APPEND;
        if (type == 2) {
            if (matchKeyword(Keywords.PRECEDING)) {
                position = org.exist.xquery.update.Insert.INSERT_BEFORE;
            } else if (matchKeyword(Keywords.FOLLOWING)) {
                position = org.exist.xquery.update.Insert.INSERT_AFTER;
            } else if (matchKeyword(Keywords.INTO)) {
                position = org.exist.xquery.update.Insert.INSERT_APPEND;
            }
        }

        // Separator keyword and second expression (not for delete)
        Expression p2 = null;
        if (type != 3) {
            // replace/value use 'with', rename uses 'as', insert has no separator (position keyword already consumed)
            if (type == 0 || type == 1) {
                matchKeyword(Keywords.WITH); // consume 'with' between expressions
            } else if (type == 4) {
                matchKeyword(Keywords.AS); // consume 'as' between expressions
            }
            p2 = parseExprSingle();
        }

        final org.exist.xquery.update.Modification mod;
        switch (type) {
            case 0: mod = new org.exist.xquery.update.Replace(context, p1, p2); break;
            case 1: mod = new org.exist.xquery.update.Update(context, p1, p2); break;
            case 2: mod = new org.exist.xquery.update.Insert(context, p2, p1, position); break;
            case 3: mod = new org.exist.xquery.update.Delete(context, p1); break;
            case 4: mod = new org.exist.xquery.update.Rename(context, p1, p2); break;
            default: throw error("Unknown update type");
        }
        mod.setLocation(line, col);
        return mod;
    }

    // ========================================================================
    // XQFT: Full-text expressions
    // ========================================================================

    /**
     * Parses the "contains text" expression.
     * Called from the precedence chain between comparison and otherwise.
     */
    Expression parseFTContainsExpr(final Expression source) throws XPathException {
        final int line = previous.line, col = previous.column;

        final FTExpressions.ContainsExpr ftContains = new FTExpressions.ContainsExpr(context);
        ftContains.setLocation(line, col);
        ftContains.setSearchSource(source);

        // Parse FT selection: ftOr with optional positional filters
        final FTExpressions.Selection ftSel = new FTExpressions.Selection(context);
        ftSel.setFTOr(parseFTOr());

        // Positional filters: ordered, window, distance, at start/end, entire content, occurs, scope
        parseFTPositionalFilters(ftSel);

        ftContains.setFTSelection(ftSel);
        return ftContains;
    }

    private Expression parseFTOr() throws XPathException {
        Expression left = parseFTAnd();
        while (matchKeyword(Keywords.FTOR)) {
            final FTExpressions.Or or = new FTExpressions.Or(context);
            or.addOperand(left);
            or.addOperand(parseFTAnd());
            left = or;
        }
        return left;
    }

    private Expression parseFTAnd() throws XPathException {
        Expression left = parseFTMildNot();
        while (matchKeyword(Keywords.FTAND)) {
            final FTExpressions.And and = new FTExpressions.And(context);
            and.addOperand(left);
            and.addOperand(parseFTMildNot());
            left = and;
        }
        return left;
    }

    private Expression parseFTMildNot() throws XPathException {
        Expression left = parseFTUnaryNot();
        while (checkKeyword(Keywords.NOT) && peekIsKeyword(Keywords.IN)) {
            advance(); // consume "not"
            advance(); // consume "in"
            final FTExpressions.MildNot mildNot = new FTExpressions.MildNot(context);
            mildNot.addOperand(left);
            mildNot.addOperand(parseFTUnaryNot());
            left = mildNot;
        }
        return left;
    }

    private Expression parseFTUnaryNot() throws XPathException {
        if (matchKeyword(Keywords.FTNOT)) {
            final FTExpressions.UnaryNot unaryNot = new FTExpressions.UnaryNot(context);
            unaryNot.setOperand(parseFTPrimaryWithOptions());
            return unaryNot;
        }
        return parseFTPrimaryWithOptions();
    }

    private Expression parseFTPrimaryWithOptions() throws XPathException {
        final FTExpressions.PrimaryWithOptions pwo = new FTExpressions.PrimaryWithOptions(context);

        // FT primary: string literal, {expr}, or parenthesized FT expression
        if (check(Token.STRING_LITERAL) || check(Token.LBRACE)) {
            final FTExpressions.Words words = new FTExpressions.Words(context);
            if (check(Token.LBRACE)) {
                // Enclosed expression: { expr }
                advance(); // consume {
                words.setWordsValue(parseExpr());
                expect(Token.RBRACE, "'}'");
            } else {
                words.setWordsValue(parseStringLiteral());
            }

            // Optional any/all/phrase mode
            if (matchKeyword(Keywords.ANY)) {
                if (matchKeyword(Keywords.WORD)) {
                    words.setMode(FTExpressions.Words.AnyallMode.ANY_WORD);
                } else {
                    words.setMode(FTExpressions.Words.AnyallMode.ANY);
                }
            } else if (matchKeyword(Keywords.ALL)) {
                if (matchKeyword(Keywords.WORDS)) {
                    words.setMode(FTExpressions.Words.AnyallMode.ALL_WORDS);
                } else {
                    words.setMode(FTExpressions.Words.AnyallMode.ALL);
                }
            } else if (matchKeyword(Keywords.PHRASE)) {
                words.setMode(FTExpressions.Words.AnyallMode.PHRASE);
            }

            // Optional FTTimes: "occurs" FTRange "times"
            if (checkKeyword("occurs")) {
                advance(); // consume "occurs"
                final FTExpressions.Times ftTimes = new FTExpressions.Times(context);
                ftTimes.setRange(parseFTRange());
                matchKeyword("times");
                words.setFTTimes(ftTimes);
            }

            // Optional FTTimes: "occurs" FTRange "times"
            if (checkKeyword("occurs")) {
                advance(); // consume "occurs"
                final FTExpressions.Times ftTimes = new FTExpressions.Times(context);
                ftTimes.setRange(parseFTRange());
                matchKeyword("times");
                words.setFTTimes(ftTimes);
            }

            pwo.setPrimary(words);
        } else if (match(Token.LPAREN)) {
            pwo.setPrimary(parseFTOr());
            expect(Token.RPAREN, "')'");
        } else {
            throw error("Expected string literal, '{', or '(' in full-text expression");
        }

        // Match options: using stemming, using language "en", using wildcards, etc.
        if (checkKeyword(Keywords.USING)) {
            final FTExpressions.MatchOptions opts = new FTExpressions.MatchOptions();
            while (matchKeyword(Keywords.USING)) {
                if (matchKeyword(Keywords.STEMMING)) {
                    opts.setStemming(true);
                } else if (matchKeyword(Keywords.WILDCARDS)) {
                    opts.setWildcards(true);
                } else if (matchKeyword(Keywords.LANGUAGE)) {
                    if (!check(Token.STRING_LITERAL)) throw error("Expected language code");
                    opts.setLanguage(current.value);
                    advance();
                } else if (matchKeyword(Keywords.DIACRITICS)) {
                    if (matchKeyword(Keywords.INSENSITIVE)) {
                        opts.setDiacriticsMode(FTExpressions.MatchOptions.DiacriticsMode.INSENSITIVE);
                    } else if (matchKeyword(Keywords.SENSITIVE)) {
                        opts.setDiacriticsMode(FTExpressions.MatchOptions.DiacriticsMode.SENSITIVE);
                    }
                } else if (checkKeyword("case")) {
                    advance(); // consume 'case'
                    if (matchKeyword(Keywords.INSENSITIVE)) {
                        opts.setCaseMode(FTExpressions.MatchOptions.CaseMode.INSENSITIVE);
                    } else if (matchKeyword(Keywords.SENSITIVE)) {
                        opts.setCaseMode(FTExpressions.MatchOptions.CaseMode.SENSITIVE);
                    }
                } else if (checkKeyword("no")) {
                    advance(); // consume 'no'
                    matchKeyword(Keywords.STEMMING);
                    matchKeyword(Keywords.WILDCARDS);
                    matchKeyword(Keywords.STOP);
                    if (checkKeyword(Keywords.WORDS)) advance();
                } else if (matchKeyword(Keywords.STOP)) {
                    matchKeyword(Keywords.WORDS);
                    // skip stop word details
                    while (!checkKeyword(Keywords.USING) && !check(Token.RBRACKET)
                            && !check(Token.RPAREN) && !check(Token.EOF)) advance();
                } else if (matchKeyword(Keywords.THESAURUS)) {
                    // skip thesaurus details
                    while (!checkKeyword(Keywords.USING) && !check(Token.RBRACKET)
                            && !check(Token.RPAREN) && !check(Token.EOF)) advance();
                } else {
                    // Unknown match option — skip it
                    advance();
                }
            }
            pwo.setMatchOptions(opts);
        }

        return pwo;
    }

    // Focus function, QName literal, keyword arguments
    // ========================================================================

    Expression parseFocusFunction() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'fn'

        expect(Token.LBRACE, "'{'");

        // Create a UserDefinedFunction with a single implicit parameter
        final FunctionSignature sig = new FunctionSignature(InlineFunction.INLINE_FUNCTION_QNAME);
        sig.setArgumentTypes(new SequenceType[]{
                new FunctionParameterSequenceType(FocusFunction.FOCUS_PARAM_NAME,
                        Type.ITEM, Cardinality.ZERO_OR_MORE, "focus parameter")
        });
        final UserDefinedFunction func = new UserDefinedFunction(context, sig);
        func.setLocation(line, col);
        func.addVariable(FocusFunction.FOCUS_PARAM_NAME);

        // Parse body with context item in scope
        final LocalVariable mark = context.markLocalVariables(false);
        final boolean savedInFunctionBody = inFunctionBody;
        inFunctionBody = true;
        try {
            final PathExpr body = new PathExpr(context);
            body.add(parseExpr());
            expect(Token.RBRACE, "'}'");
            func.setFunctionBody(body);
        } finally {
            inFunctionBody = savedInFunctionBody;
            context.popLocalVariables(mark);
        }

        final FocusFunction focus = new FocusFunction(context, func);
        focus.setLocation(line, col);
        return focus;
    }

    /**
     * Parses a string constructor ``[content `{expr}` more]`` using character-level scanning.
     */
    Expression parseStringConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        xp = current.endOffset; // right after ``[
        xln = current.line;
        xcl = current.column + 3;
        bufferedNext = null;

        final StringConstructor sc = new StringConstructor(context);
        sc.setLocation(line, col);
        final StringBuilder text = new StringBuilder();

        while (xp < lexer.getLength()) {
            final int ch = xchar();

            // ]`` — end of string constructor
            if (ch == ']' && xpeek(1) == '`' && xpeek(2) == '`') {
                if (text.length() > 0) { sc.addContent(text.toString()); text.setLength(0); }
                xp += 3; xcl += 3;
                syncLexer(xp, xln, xcl);
                return sc;
            }

            // `{ — start interpolation
            if (ch == '`' && xpeek(1) == '{') {
                if (text.length() > 0) { sc.addContent(text.toString()); text.setLength(0); }
                xp += 2; xcl += 2;
                final Expression expr = scanEnclosedExpr();
                sc.addInterpolation(expr instanceof PathExpr ? ((PathExpr) expr).simplify() : expr);
                // After the enclosed expr, skip the closing }`
                if (xp < lexer.getLength() && lexer.charAt(xp) == '`') { xp++; xcl++; }
                continue;
            }

            // Escaped backtick: `` → `
            if (ch == '`' && xpeek(1) == '`'
                    && (xp + 2 >= lexer.getLength() || lexer.charAt(xp + 2) != '[')) {
                text.append('`'); xp += 2; xcl += 2;
                continue;
            }

            text.appendCodePoint(ch);
            if (ch == '\n') { xln++; xcl = 1; } else { xcl++; }
            xp++;
        }

        throw new XPathException(xln, xcl, ErrorCodes.XPST0003, "Unterminated string constructor");
    }

    /**
     * Parses an XQ4 string template: `text {expr} more text`
     * Uses character-level scanning. Escapes: {{ → {, }} → }, `` → `
     */
    Expression parseStringTemplate() throws XPathException {
        if (!isXQ4()) {
            throw xq4Required("String templates");
        }
        final int line = current.line, col = current.column;
        xp = current.endOffset; // right after opening `
        xln = current.line;
        xcl = current.column + 1;
        bufferedNext = null;

        final StringConstructor sc = new StringConstructor(context);
        sc.setLocation(line, col);
        final StringBuilder text = new StringBuilder();

        while (xp < lexer.getLength()) {
            final int ch = xchar();

            // Closing backtick — end of string template
            if (ch == '`') {
                // Check for escaped backtick: `` → `
                if (xpeek(1) == '`') {
                    text.append('`'); xp += 2; xcl += 2;
                    continue;
                }
                if (text.length() > 0) { sc.addContent(text.toString()); text.setLength(0); }
                xp++; xcl++;
                syncLexer(xp, xln, xcl);
                return sc;
            }

            // { — start interpolation (unless escaped as {{)
            if (ch == '{') {
                if (xpeek(1) == '{') {
                    text.append('{'); xp += 2; xcl += 2;
                    continue;
                }
                if (text.length() > 0) { sc.addContent(text.toString()); text.setLength(0); }
                xp++; xcl++; // skip {
                final Expression expr = scanEnclosedExpr();
                sc.addInterpolation(expr instanceof PathExpr ? ((PathExpr) expr).simplify() : expr);
                continue;
            }

            // }} → }
            if (ch == '}' && xpeek(1) == '}') {
                text.append('}'); xp += 2; xcl += 2;
                continue;
            }

            text.appendCodePoint(ch);
            if (ch == '\n') { xln++; xcl = 1; } else { xcl++; }
            xp++;
        }

        throw new XPathException(xln, xcl, ErrorCodes.XPST0003, "Unterminated string template");
    }

    Expression parseQNameLiteral() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume '#'
        final String name = expectName("QName");
        final QName qname = resolveQName(name, context.getDefaultFunctionNamespace());
        final LiteralValue lit = new LiteralValue(context, new QNameValue(context, qname));
        lit.setLocation(line, col);
        return lit;
    }

    // ========================================================================
    // Operator precedence ladder
    // ========================================================================

    Expression parseOrExpr() throws XPathException {
        Expression left = parseAndExpr();
        while (matchKeyword(Keywords.OR)) {
            final Expression right = parseAndExpr();
            final OpOr or = new OpOr(context);
            or.setLocation(left.getLine(), left.getColumn());
            or.add(left);
            or.add(right);
            left = or;
        }

        // XQ4 ternary conditional: condition ?? thenExpr !! elseExpr
        if (check(Token.DOUBLE_QUESTION)) {
            if (!isXQ4()) {
                throw xq4Required("Ternary conditional operator (?? !!)");
            }
            advance(); // consume ??
            final Expression thenExpr = parseExprSingle();
            expect(Token.DOUBLE_BANG, "'!!'");
            final Expression elseExpr = parseExprSingle();
            final PathExpr testPath = wrapInPathExpr(left);
            final PathExpr thenPath = wrapInPathExpr(thenExpr);
            final PathExpr elsePath = wrapInPathExpr(elseExpr);
            final ConditionalExpression cond = new ConditionalExpression(context,
                    testPath, thenPath, elsePath);
            cond.setLocation(left.getLine(), left.getColumn());
            return cond;
        }

        return left;
    }

    Expression parseAndExpr() throws XPathException {
        Expression left = parseComparisonExpr();
        while (matchKeyword(Keywords.AND)) {
            final Expression right = parseComparisonExpr();
            final OpAnd and = new OpAnd(context);
            and.setLocation(left.getLine(), left.getColumn());
            and.add(left);
            and.add(right);
            left = and;
        }
        return left;
    }

    Expression parseComparisonExpr() throws XPathException {
        Expression left = parseFTContainsOrInstanceOf();

        // Node comparisons must be checked BEFORE general / value comparisons:
        //   `<<` and `>>` would otherwise be eaten as `<` / `>` (LT/GT) by
        //   matchGeneralComp, leaving the second angle bracket dangling.
        if (check(Token.LT) && peekIs(Token.LT)) {
            advance(); advance(); // consume <<
            final Expression right = parseFTContainsOrInstanceOf();
            final NodeComparison cmp = new NodeComparison(context, left, right, Constants.NodeComparisonOperator.BEFORE);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }
        if (check(Token.GT) && peekIs(Token.GT)) {
            advance(); advance(); // consume >>
            final Expression right = parseFTContainsOrInstanceOf();
            final NodeComparison cmp = new NodeComparison(context, left, right, Constants.NodeComparisonOperator.AFTER);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }
        if (matchKeyword("is")) {
            final Expression right = parseFTContainsOrInstanceOf();
            final NodeComparison cmp = new NodeComparison(context, left, right, Constants.NodeComparisonOperator.IS);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }
        // XQ4 keyword forms: `follows` ≡ `>>`, `precedes` ≡ `<<`.
        if (isXQ4() && matchKeyword("follows")) {
            final Expression right = parseFTContainsOrInstanceOf();
            final NodeComparison cmp = new NodeComparison(context, left, right, Constants.NodeComparisonOperator.AFTER);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }
        if (isXQ4() && matchKeyword("precedes")) {
            final Expression right = parseFTContainsOrInstanceOf();
            final NodeComparison cmp = new NodeComparison(context, left, right, Constants.NodeComparisonOperator.BEFORE);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }

        final Comparison generalOp = matchGeneralComp();
        if (generalOp != null) {
            final Expression right = parseFTContainsOrInstanceOf();
            final GeneralComparison cmp = new GeneralComparison(context, left, right, generalOp);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }

        final Comparison valueOp = matchValueComp();
        if (valueOp != null) {
            final Expression right = parseFTContainsOrInstanceOf();
            final ValueComparison cmp = new ValueComparison(context, left, right, valueOp);
            cmp.setLocation(left.getLine(), left.getColumn());
            return cmp;
        }

        return left;
    }

    private Comparison matchGeneralComp() {
        if (match(Token.EQ)) return Comparison.EQ;
        if (match(Token.NEQ)) return Comparison.NEQ;
        if (match(Token.LT)) return Comparison.LT;
        if (match(Token.LTEQ)) return Comparison.LTEQ;
        if (match(Token.GT)) return Comparison.GT;
        if (match(Token.GTEQ)) return Comparison.GTEQ;
        return null;
    }

    private Comparison matchValueComp() {
        if (matchKeyword(Keywords.EQ)) return Comparison.EQ;
        if (matchKeyword(Keywords.NE)) return Comparison.NEQ;
        if (matchKeyword(Keywords.LT)) return Comparison.LT;
        if (matchKeyword(Keywords.LE)) return Comparison.LTEQ;
        if (matchKeyword(Keywords.GT)) return Comparison.GT;
        if (matchKeyword(Keywords.GE)) return Comparison.GTEQ;
        return null;
    }

    // ========================================================================
    // Type expressions: instance of, treat as, castable as, cast as
    // ========================================================================

    /**
     * Handles "contains text" between comparison and instance of.
     */
    Expression parseFTContainsOrInstanceOf() throws XPathException {
        Expression left = parseUnionExpr();
        // Check for "contains text"
        if (checkKeyword(Keywords.CONTAINS) && peekIsKeyword(Keywords.TEXT)) {
            matchKeyword(Keywords.CONTAINS);
            matchKeyword(Keywords.TEXT);
            left = parseFTContainsExpr(left);
        }
        return left;
    }

    Expression parseUnionExpr() throws XPathException {
        Expression left = parseIntersectExceptExpr();
        while (matchKeyword(Keywords.UNION) || match(Token.PIPE)) {
            final Expression right = parseIntersectExceptExpr();
            final PathExpr union = new PathExpr(context);
            union.setLocation(left.getLine(), left.getColumn());
            union.add(new Union(context, wrapInPathExpr(left), wrapInPathExpr(right)));
            left = union;
        }
        return left;
    }

    Expression parseIntersectExceptExpr() throws XPathException {
        Expression left = parseInstanceOfExpr();
        while (true) {
            if (matchKeyword(Keywords.INTERSECT)) {
                final Expression right = parseInstanceOfExpr();
                left = new Intersect(context, wrapInPathExpr(left), wrapInPathExpr(right));
                ((AbstractExpression) left).setLocation(previous.line, previous.column);
            } else if (matchKeyword(Keywords.EXCEPT)) {
                final Expression right = parseInstanceOfExpr();
                left = new Except(context, wrapInPathExpr(left), wrapInPathExpr(right));
                ((AbstractExpression) left).setLocation(previous.line, previous.column);
            } else {
                break;
            }
        }
        return left;
    }

    Expression parseInstanceOfExpr() throws XPathException {
        Expression left = parseTreatExpr();

        if (matchKeyword(Keywords.INSTANCE)) {
            expectKeyword(Keywords.OF);
            final SequenceType type = parseSequenceType();
            left = new InstanceOfExpression(context, left, type);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }

        return left;
    }

    Expression parseTreatExpr() throws XPathException {
        Expression left = parseCastableExpr();

        if (matchKeyword(Keywords.TREAT)) {
            expectKeyword(Keywords.AS);
            final SequenceType type = parseSequenceType();
            left = new TreatAsExpression(context, left, type);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }

        return left;
    }

    Expression parseCastableExpr() throws XPathException {
        Expression left = parseCastExpr();

        if (matchKeyword(Keywords.CASTABLE)) {
            expectKeyword(Keywords.AS);
            final int targetType = parseAtomicType();
            Cardinality card = Cardinality.EXACTLY_ONE;
            if (match(Token.QUESTION)) {
                card = Cardinality.ZERO_OR_ONE;
            }
            left = new CastableExpression(context, left, targetType, card);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }

        return left;
    }

    Expression parseCastExpr() throws XPathException {
        Expression left = parseOtherwiseExpr();

        if (matchKeyword(Keywords.CAST)) {
            expectKeyword(Keywords.AS);
            final int targetType = parseAtomicType();
            Cardinality card = Cardinality.EXACTLY_ONE;
            if (match(Token.QUESTION)) {
                card = Cardinality.ZERO_OR_ONE;
            }
            left = new CastExpression(context, left, targetType, card);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }

        return left;
    }

    /**
     * Parses an atomic type name (e.g., xs:integer, xs:string).
     * Returns the Type constant.
     */
    private int parseAtomicType() throws XPathException {
        final String typeName;
        if (check(Token.QNAME)) {
            typeName = current.value;
            advance();
        } else if (check(Token.NCNAME)) {
            typeName = current.value;
            advance();
        } else {
            throw error("Expected type name");
        }
        final QName qname = resolveQName(typeName, context.getDefaultFunctionNamespace());
        final int type;
        try {
            type = Type.getType(qname);
        } catch (final XPathException e) {
            throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0051,
                    "Unknown simple type " + typeName);
        }
        // Per QT4: kind tests in cast position (item(), node(), ...) — when
        // the parsed name resolves to Type.ITEM and is followed by `(`, this
        // is a syntax error (kind tests are not atomic types). Raise
        // XPST0003 to match ANTLR/XQTS expectations.
        if (type == Type.ITEM && check(Token.LPAREN)) {
            throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0003,
                    "Kind test '" + typeName + "()' is not valid in cast position");
        }
        if (type == Type.ITEM || !Type.subTypeOf(type, Type.ANY_ATOMIC_TYPE)) {
            throw new XPathException(previous.line, previous.column, ErrorCodes.XPST0051,
                    "Unknown simple type " + typeName);
        }
        return type;
    }

    /**
     * Parses a SequenceType: ItemType OccurrenceIndicator?
     */
    SequenceType parseSequenceType() throws XPathException {
        // empty-sequence()
        if (checkKeyword(Keywords.EMPTY_SEQUENCE) && peekIs(Token.LPAREN)) {
            advance(); advance(); // empty-sequence (
            expect(Token.RPAREN, "')'");
            return new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE);
        }

        final int itemType = parseItemType();
        Cardinality card = Cardinality.EXACTLY_ONE;

        if (match(Token.QUESTION)) {
            card = Cardinality.ZERO_OR_ONE;
        } else if (match(Token.STAR)) {
            card = Cardinality.ZERO_OR_MORE;
        } else if (match(Token.PLUS)) {
            card = Cardinality.ONE_OR_MORE;
        }

        return new SequenceType(itemType, card);
    }

    /**
     * Parses an ItemType: AtomicType | KindTest | 'item()' | 'function(...)' | 'map(...)' | 'array(...)'
     * Also handles parenthesized types: (function(...) as type)
     */
    private int parseItemType() throws XPathException {
        // ChoiceItemType: (ItemType | ItemType | ...)
        // or parenthesized type: (ItemType)
        if (check(Token.LPAREN)) {
            advance(); // consume (
            final int innerType = parseItemType();
            // Handle | for choice types (XQ4)
            while (match(Token.PIPE)) {
                parseItemType(); // consume additional types (use first type as approximation)
            }
            // Skip any nested content until closing )
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (check(Token.LPAREN)) depth++;
                if (check(Token.RPAREN)) depth--;
                if (depth > 0) advance();
            }
            if (check(Token.RPAREN)) advance();
            return innerType;
        }

        // EnumerationType: enum('val1', 'val2', ...)
        if (checkKeyword("enum") && peekIs(Token.LPAREN)) {
            advance(); advance(); // consume 'enum' '('
            // Parse string literal list
            while (!check(Token.RPAREN) && !check(Token.EOF)) {
                advance(); // consume each literal/comma
            }
            expect(Token.RPAREN, "')'");
            return Type.STRING; // enum values are strings
        }

        // item()
        if (checkKeyword(Keywords.ITEM) && peekIs(Token.LPAREN)) {
            advance(); advance();
            expect(Token.RPAREN, "')'");
            return Type.ITEM;
        }

        // function(*) or function(type, type) as returnType
        if (checkKeyword(Keywords.FUNCTION) && peekIs(Token.LPAREN)) {
            advance(); advance(); // consume 'function' '('
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (match(Token.LPAREN)) depth++;
                else if (match(Token.RPAREN)) depth--;
                else advance();
            }
            if (matchKeyword(Keywords.AS)) {
                parseSequenceType();
            }
            return Type.FUNCTION;
        }

        // map(KeyType, ValueType) or map(*)
        if (checkKeyword(Keywords.MAP) && peekIs(Token.LPAREN)) {
            advance(); advance(); // consume 'map' '('
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (check(Token.LPAREN)) depth++;
                if (check(Token.RPAREN)) { depth--; if (depth == 0) break; }
                advance();
            }
            expect(Token.RPAREN, "')'");
            return Type.MAP_ITEM;
        }

        // array(MemberType) or array(*)
        if (checkKeyword(Keywords.ARRAY) && peekIs(Token.LPAREN)) {
            advance(); advance(); // consume 'array' '('
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (check(Token.LPAREN)) depth++;
                if (check(Token.RPAREN)) { depth--; if (depth == 0) break; }
                advance();
            }
            expect(Token.RPAREN, "')'");
            return Type.ARRAY_ITEM;
        }

        // RecordType: record(key as type, ...) or record(*)
        if (checkKeyword(Keywords.RECORD) && peekIs(Token.LPAREN)) {
            advance(); advance(); // consume 'record' '('
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (check(Token.LPAREN)) depth++;
                if (check(Token.RPAREN)) { depth--; if (depth == 0) break; }
                advance();
            }
            expect(Token.RPAREN, "')'");
            return Type.MAP_ITEM; // record is a specialization of map
        }

        // node(), element(), attribute(), text(), comment(), etc.
        if (check(Token.NCNAME) && isKindTest(current.value) && peekIs(Token.LPAREN)) {
            final String kind = current.value;
            advance(); advance(); // kind name + (
            // Skip content with depth tracking (handles nested parens)
            int depth = 1;
            while (depth > 0 && !check(Token.EOF)) {
                if (check(Token.LPAREN)) depth++;
                if (check(Token.RPAREN)) { depth--; if (depth == 0) break; }
                advance();
            }
            expect(Token.RPAREN, "')'");
            return kindNameToType(kind);
        }

        // QName atomic type (e.g. xs:integer)
        return parseAtomicType();
    }

    private int kindNameToType(final String kind) {
        switch (kind) {
            case Keywords.NODE: return Type.NODE;
            case Keywords.ELEMENT: return Type.ELEMENT;
            case Keywords.ATTRIBUTE: return Type.ATTRIBUTE;
            case Keywords.TEXT: return Type.TEXT;
            case Keywords.COMMENT: return Type.COMMENT;
            case Keywords.DOCUMENT_NODE: return Type.DOCUMENT;
            case Keywords.PROCESSING_INSTRUCTION: return Type.PROCESSING_INSTRUCTION;
            case "namespace-node": return Type.NAMESPACE;
            case "schema-element": return Type.ELEMENT;
            case "schema-attribute": return Type.ATTRIBUTE;
            default: return Type.ITEM;
        }
    }

    // ========================================================================
    // String concat, range, arithmetic
    // ========================================================================

    Expression parseOtherwiseExpr() throws XPathException {
        Expression left = parseStringConcatExpr();
        while (checkKeyword(Keywords.OTHERWISE)) {
            if (!isXQ4()) {
                throw xq4Required("'otherwise' operator");
            }
            advance();
            final Expression right = parseStringConcatExpr();
            left = new OtherwiseExpression(context, left, right);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }
        return left;
    }

    Expression parseStringConcatExpr() throws XPathException {
        Expression left = parseRangeExpr();
        if (!check(Token.CONCAT)) return left;

        final ConcatExpr concat = new ConcatExpr(context);
        concat.setLocation(left.getLine(), left.getColumn());
        concat.add(left);
        while (match(Token.CONCAT)) {
            concat.add(parseRangeExpr());
        }
        return concat;
    }

    Expression parseRangeExpr() throws XPathException {
        final Expression left = parseAdditiveExpr();
        if (matchKeyword(Keywords.TO)) {
            final Expression right = parseAdditiveExpr();
            final RangeExpression range = new RangeExpression(context);
            range.setLocation(left.getLine(), left.getColumn());
            final List<Expression> args = new ArrayList<>(2);
            args.add(left);
            args.add(right);
            range.setArguments(args);
            return range;
        }
        return left;
    }

    Expression parseAdditiveExpr() throws XPathException {
        Expression left = parseMultiplicativeExpr();
        while (check(Token.PLUS) || check(Token.MINUS)) {
            final ArithmeticOperator op = match(Token.PLUS)
                    ? ArithmeticOperator.ADDITION
                    : (match(Token.MINUS) ? ArithmeticOperator.SUBTRACTION : null);
            if (op == null) break;
            final Expression right = parseMultiplicativeExpr();
            final OpNumeric numeric = new OpNumeric(context, left, right, op);
            numeric.setLocation(left.getLine(), left.getColumn());
            left = numeric;
        }
        return left;
    }

    Expression parseMultiplicativeExpr() throws XPathException {
        Expression left = parsePipelineExpr();
        while (true) {
            ArithmeticOperator op = null;
            if (match(Token.STAR)) op = ArithmeticOperator.MULTIPLICATION;
            else if (matchKeyword(Keywords.DIV)) op = ArithmeticOperator.DIVISION;
            else if (matchKeyword(Keywords.IDIV)) op = ArithmeticOperator.DIVISION_INTEGER;
            else if (matchKeyword(Keywords.MOD)) op = ArithmeticOperator.MODULUS;
            if (op == null) break;
            final Expression right = parsePipelineExpr();
            final OpNumeric numeric = new OpNumeric(context, left, right, op);
            numeric.setLocation(left.getLine(), left.getColumn());
            left = numeric;
        }
        return left;
    }

    /**
     * Unary expression — handles leading {@code -} / {@code +} sign(s) then
     * dispatches to {@link #parseSimpleMapExpr()}. Per the XQuery 4.0 grammar,
     * {@code unaryExpr} sits inside {@code arrowExpr} and {@code unaryExpr}
     * wraps the simple-map operator so that {@code -1!2} parses as
     * {@code -(1!2)}, and so {@code 1 -> -2} parses with unary on the RHS.
     */
    Expression parseUnaryExpr() throws XPathException {
        if (match(Token.MINUS)) {
            final int line = previous.line, col = previous.column;
            final Expression operand = parseUnaryExpr();
            final UnaryExpr unary = new UnaryExpr(context, ArithmeticOperator.SUBTRACTION);
            unary.setLocation(line, col);
            unary.add(operand);
            return unary;
        }
        if (match(Token.PLUS)) {
            final int line = previous.line, col = previous.column;
            final Expression operand = parseUnaryExpr();
            final UnaryExpr unary = new UnaryExpr(context, ArithmeticOperator.ADDITION);
            unary.setLocation(line, col);
            unary.add(operand);
            return unary;
        }
        return parseSimpleMapExpr();
    }

    Expression parseSimpleMapExpr() throws XPathException {
        Expression left = parsePostfixExpr();
        while (match(Token.BANG)) {
            final PathExpr leftPath = wrapInPathExpr(left);
            // Simple map creates a new context — allow absolute paths on RHS
            final boolean savedInFunctionBody = inFunctionBody;
            inFunctionBody = false;
            final PathExpr rightPath = wrapInPathExpr(parsePostfixExpr());
            inFunctionBody = savedInFunctionBody;
            left = new OpSimpleMap(context, leftPath, rightPath);
            ((AbstractExpression) left).setLocation(previous.line, previous.column);
        }
        return left;
    }

    Expression parsePipelineExpr() throws XPathException {
        Expression left = parseArrowExpr();
        while (check(Token.PIPELINE)) {
            if (!isXQ4()) {
                throw xq4Required("'->' pipeline operator");
            }
            final int line = current.line, col = current.column;
            advance();
            final Expression right = parseArrowExpr();
            final PipelineExpression pipe = new PipelineExpression(
                    context, wrapInPathExpr(left).simplify(), wrapInPathExpr(right).simplify());
            pipe.setLocation(line, col);
            left = pipe;
        }
        return left;
    }

    Expression parseArrowExpr() throws XPathException {
        Expression left = parseUnaryExpr();

        while (check(Token.ARROW) || check(Token.MAPPING_ARROW)) {
            if (match(Token.ARROW)) {
                left = parseArrowCall(left, false);
            } else if (check(Token.MAPPING_ARROW)) {
                // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
                advance();
                left = parseArrowCall(left, true);
            }
        }
        return left;
    }

    /**
     * Parses the function call part of an arrow expression: => funcName(args)
     */
    private Expression parseArrowCall(final Expression leftExpr, final boolean mapping) throws XPathException {
        final int line = previous.line, col = previous.column;

        // Function name or expression
        String funcName = null;
        PathExpr funcExpr = null;

        if (checkKeyword(Keywords.FN) && (peekIs(Token.LBRACE) || peekIs(Token.LPAREN))) {
            // Focus function: => fn { expr }(args)
            // Inline function shorthand: => fn($x){...}(args)
            funcExpr = new PathExpr(context);
            if (peekIs(Token.LBRACE)) {
                funcExpr.add(parseFocusFunction());
            } else {
                advance(); // consume 'fn'
                funcExpr.add(parseInlineFunction());
            }
        } else if (checkKeyword(Keywords.FUNCTION) && (peekIs(Token.LPAREN) || peekIs(Token.LBRACE))) {
            // Inline function: => function($x) { ... }(args)
            // Or focus function via 'function': => function { expr }(args)
            funcExpr = new PathExpr(context);
            advance(); // consume 'function'
            if (check(Token.LBRACE)) {
                // function { } is a focus function form
                funcExpr.add(parseFocusFunction());
            } else {
                funcExpr.add(parseInlineFunction());
            }
        } else if (check(Token.PERCENT)) {
            // Annotated inline/focus function: => %ann function(...) { }(args)
            funcExpr = new PathExpr(context);
            parseAnnotations(); // consume annotations (applied at function decl level)
            if (checkKeyword(Keywords.FN) && peekIs(Token.LBRACE)) {
                funcExpr.add(parseFocusFunction());
            } else if (checkKeyword(Keywords.FUNCTION)) {
                advance();
                if (check(Token.LBRACE)) {
                    funcExpr.add(parseFocusFunction());
                } else {
                    funcExpr.add(parseInlineFunction());
                }
            } else {
                throw error("Expected 'function' or 'fn' after annotation in arrow expression");
            }
        } else if (checkKeyword(Keywords.MAP) && peekIs(Token.LBRACE)) {
            // Map constructor as arrow specifier: => map { }(args)
            funcExpr = new PathExpr(context);
            funcExpr.add(parseMapConstructor());
        } else if (checkKeyword(Keywords.ARRAY) && peekIs(Token.LBRACE)) {
            // Curly array constructor: => array { }(args)
            funcExpr = new PathExpr(context);
            funcExpr.add(parseCurlyArrayConstructor());
        } else if (check(Token.LBRACKET)) {
            // Square array constructor: => [](args)
            funcExpr = new PathExpr(context);
            funcExpr.add(parseSquareArrayConstructor());
        } else if (check(Token.NCNAME) || check(Token.QNAME)) {
            funcName = current.value;
            advance();
        } else if (check(Token.BRACED_URI_LITERAL)) {
            funcName = parseEQName();
        } else if (match(Token.DOLLAR)) {
            // Variable reference as function
            funcExpr = new PathExpr(context);
            funcExpr.add(parseVariableRef());
        } else if (check(Token.LPAREN)) {
            // Parenthesized expression as function: => (function($s){...})()
            funcExpr = new PathExpr(context);
            funcExpr.add(parsePrimaryExpr());
            // The parenthesized expr might be followed by () for invocation
            // which gets consumed below as the argument list
        } else {
            throw error("Expected function name after arrow operator");
        }

        // Arguments
        expect(Token.LPAREN, "'('");
        final List<Expression> args = new ArrayList<>();
        if (!check(Token.RPAREN)) {
            args.add(parseExprSingle());
            while (match(Token.COMMA)) {
                args.add(parseExprSingle());
            }
        }
        expect(Token.RPAREN, "')'");

        // For EQName, declare the namespace prefix so QName.parse works
        if (funcName != null && funcName.startsWith("Q{")) {
            final int braceEnd = funcName.indexOf('}');
            final String uri = funcName.substring(2, braceEnd);
            final String local = funcName.substring(braceEnd + 1);
            // Use a synthetic prefix for arrow calls with EQNames
            final String prefix = "__arrow" + System.identityHashCode(funcName);
            try { context.declareNamespace(prefix, uri); } catch (final XPathException ignored) { }
            funcName = prefix + ":" + local;
        }

        if (mapping) {
            final MappingArrowOperator op = new MappingArrowOperator(context, leftExpr);
            op.setLocation(line, col);
            if (funcName != null) {
                op.setArrowFunction(funcName, args);
            } else {
                op.setArrowFunction(funcExpr, args);
            }
            return op;
        } else {
            final ArrowOperator op = new ArrowOperator(context, leftExpr);
            op.setLocation(line, col);
            if (funcName != null) {
                op.setArrowFunction(funcName, args);
            } else {
                op.setArrowFunction(funcExpr, args);
            }
            return op;
        }
    }

    private PathExpr wrapInPathExpr(final Expression expr) {
        if (expr instanceof PathExpr) {
            return (PathExpr) expr;
        }
        final PathExpr path = new PathExpr(context);
        path.setLocation(expr.getLine(), expr.getColumn());
        path.add(expr);
        return path;
    }

    // ========================================================================
    // Postfix & Path expressions
    // ========================================================================

    Expression parsePostfixExpr() throws XPathException {
        Expression expr = parsePathExpr();
        while (true) {
            if (check(Token.LBRACKET)) {
                expr = parsePredicate(expr);
            } else if (check(Token.QUESTION) && peekIs(Token.LBRACKET)) {
                // XQ4 Filter alternation mode: expr?[predicate]
                advance(); // consume ?
                advance(); // consume [
                final Expression predExpr = parseExprSingle();
                expect(Token.RBRACKET, "']'");
                expr = new FilterExprAM(context, expr, predExpr);
                expr.setLocation(previous.line, previous.column);
            } else if (check(Token.QUESTION) && !peekIs(Token.QUESTION)) {
                // Lookup: expr?key, expr?1, expr?(expr), expr?*
                expr = parseLookup(expr);
            } else if (check(Token.LPAREN) && isDynamicCallContext(expr)) {
                expr = parseDynamicFunctionCall(expr);
            } else if (match(Token.METHOD_CALL)) {
                // Method call: expr =?> methodName(args)
                final String methodName = expectName("method name");
                expect(Token.LPAREN, "'('");
                final List<Expression> args = new ArrayList<>();
                if (!check(Token.RPAREN)) {
                    final PathExpr argExpr = new PathExpr(context);
                    argExpr.add(parseExprSingle());
                    args.add(argExpr.simplify());
                    while (match(Token.COMMA)) {
                        final PathExpr nextArg = new PathExpr(context);
                        nextArg.add(parseExprSingle());
                        args.add(nextArg.simplify());
                    }
                }
                expect(Token.RPAREN, "')'");
                final MethodCallOperator op = new MethodCallOperator(context, expr);
                op.setLocation(previous.line, previous.column);
                op.setMethod(methodName, args);
                expr = op;
            } else if (check(Token.SLASH) || check(Token.DSLASH)) {
                // Path continuation after postfix: $f()/path, $arr[1]/child, etc.
                final PathExpr path = new PathExpr(context);
                path.setLocation(expr.getLine(), expr.getColumn());
                path.add(expr);
                parseRelativePathSteps(path);
                expr = path;
            } else {
                break;
            }
        }
        return expr;
    }

    /**
     * Checks if the expression could be a dynamic function call target.
     */
    private boolean isDynamicCallContext(final Expression expr) {
        return expr instanceof VariableReference
                || expr instanceof InlineFunction
                || expr instanceof NamedFunctionReference
                || expr instanceof DynamicFunctionCall
                || expr instanceof FilteredExpression
                || expr instanceof FunctionCall
                || expr instanceof InternalFunctionCall
                || expr instanceof Lookup
                || expr instanceof ContextItemExpression  // .(args) — context item as function
                || expr instanceof org.exist.xquery.functions.array.ArrayConstructor
                || expr instanceof PathExpr;  // parenthesized expressions, sequences
    }

    /**
     * Parses a dynamic function call: expr(arg1, arg2, ...)
     */
    private Expression parseDynamicFunctionCall(final Expression funcExpr) throws XPathException {
        expect(Token.LPAREN, "'('");
        final List<Expression> args = new ArrayList<>();
        if (!check(Token.RPAREN)) {
            args.add(parseExprSingle());
            while (match(Token.COMMA)) {
                args.add(parseExprSingle());
            }
        }
        expect(Token.RPAREN, "')'");

        final DynamicFunctionCall call = new DynamicFunctionCall(context, funcExpr, args, false);
        call.setLocation(funcExpr.getLine(), funcExpr.getColumn());
        return call;
    }

    private Expression parsePredicate(final Expression base) throws XPathException {
        expect(Token.LBRACKET, "'['");
        final Predicate pred = new Predicate(context);
        pred.setLocation(previous.line, previous.column);
        pred.add(parseExpr());
        expect(Token.RBRACKET, "']'");

        if (base instanceof Step) {
            ((Step) base).addPredicate(pred);
            return base;
        }
        if (base instanceof FilteredExpression) {
            ((FilteredExpression) base).addPredicate(pred);
            return base;
        }
        final FilteredExpression filtered = new FilteredExpression(context, base);
        filtered.setLocation(base.getLine(), base.getColumn());
        filtered.addPredicate(pred);
        return filtered;
    }

    Expression parsePathExpr() throws XPathException {
        if (match(Token.SLASH)) {
            if (inFunctionBody) {
                throw new XPathException(previous.line, previous.column, ErrorCodes.XPDY0002,
                        "Leading '/' selects nothing, ContextItem is absent in function body");
            }
            final PathExpr path = new PathExpr(context);
            path.setLocation(previous.line, previous.column);
            path.add(new RootNode(context));
            if (isStepStart()) {
                path.add(parseStepExpr());
                parseRelativePathSteps(path);
            }
            return path;
        }
        if (match(Token.DSLASH)) {
            if (inFunctionBody) {
                throw new XPathException(previous.line, previous.column, ErrorCodes.XPDY0002,
                        "Leading '//' selects nothing, ContextItem is absent in function body");
            }
            final PathExpr path = new PathExpr(context);
            path.setLocation(previous.line, previous.column);
            path.add(new RootNode(context));
            path.add(new LocationStep(context, Constants.DESCENDANT_SELF_AXIS, new AnyNodeTest()));
            path.add(parseStepExpr());
            parseRelativePathSteps(path);
            return path;
        }

        final Expression step = parseStepExpr();
        if (check(Token.SLASH) || check(Token.DSLASH)) {
            final PathExpr path = new PathExpr(context);
            path.setLocation(step.getLine(), step.getColumn());
            path.add(step);
            parseRelativePathSteps(path);
            return path;
        }
        return step;
    }

    private void parseRelativePathSteps(final PathExpr path) throws XPathException {
        while (true) {
            if (match(Token.SLASH)) {
                path.add(parseStepExpr());
            } else if (match(Token.DSLASH)) {
                path.add(new LocationStep(context, Constants.DESCENDANT_SELF_AXIS, new AnyNodeTest()));
                path.add(parseStepExpr());
            } else {
                break;
            }
        }
    }

    Expression parseStepExpr() throws XPathException {
        // Axis step: axis::nodeTest
        final int axis = matchAxis();
        if (axis >= 0) {
            expect(Token.COLONCOLON, "'::'");
            final NodeTest test = parseNodeTest(axis);
            final LocationStep step = new LocationStep(context, axis, test);
            step.setLocation(previous.line, previous.column);
            while (check(Token.LBRACKET)) parsePredicate(step);
            return step;
        }

        // @attr
        if (match(Token.AT)) {
            final NodeTest test = parseNodeTest(Constants.ATTRIBUTE_AXIS);
            final LocationStep step = new LocationStep(context, Constants.ATTRIBUTE_AXIS, test);
            step.setLocation(previous.line, previous.column);
            while (check(Token.LBRACKET)) parsePredicate(step);
            return step;
        }

        // ..
        if (match(Token.DOT_DOT)) {
            return new LocationStep(context, Constants.PARENT_AXIS, new AnyNodeTest());
        }

        // . (context item)
        if (check(Token.DOT)) {
            match(Token.DOT);
            final ContextItemExpression ctx = new ContextItemExpression(context);
            ctx.setLocation(previous.line, previous.column);
            while (check(Token.LBRACKET)) parsePredicate(ctx);
            return ctx;
        }

        // * (wildcard child step) or *:local
        if (check(Token.STAR) && !isBinaryOperatorContext()) {
            match(Token.STAR);
            NodeTest wildTest;
            if (check(Token.COLON) && peekIsNameStart()) {
                // *:local wildcard
                advance(); // consume :
                final String local = current.value;
                advance();
                wildTest = new NameTest(Type.ELEMENT, new QName.WildcardNamespaceURIQName(local));
            } else {
                wildTest = new TypeTest(Type.ELEMENT);
            }
            final LocationStep step = new LocationStep(context, Constants.CHILD_AXIS, wildTest);
            step.setLocation(previous.line, previous.column);
            while (check(Token.LBRACKET)) parsePredicate(step);
            return step;
        }

        // Direct XML comment constructor: <!-- content -->
        if (check(Token.XML_COMMENT)) {
            String content = current.value;
            // Strip <!-- and --> delimiters — CommentConstructor expects just the content
            if (content.startsWith("<!--")) content = content.substring(4);
            if (content.endsWith("-->")) content = content.substring(0, content.length() - 3);
            advance();
            final CommentConstructor comment = new CommentConstructor(context, content);
            comment.setLocation(previous.line, previous.column);
            return comment;
        }

        // Direct processing instruction: <?target content?>
        if (check(Token.XML_PI)) {
            String piData = current.value;
            // Strip <? and ?> delimiters — PIConstructor expects "target content"
            if (piData.startsWith("<?")) piData = piData.substring(2);
            if (piData.endsWith("?>")) piData = piData.substring(0, piData.length() - 2);
            piData = piData.trim();
            final int piLine = current.line, piCol = current.column;
            advance();
            final PIConstructor pi = new PIConstructor(context, piData);
            pi.setLocation(piLine, piCol);
            return pi;
        }

        // Direct element constructor: <elem ...>
        if (check(Token.LT) && peekIsNameStart()) {
            return parseDirectElementConstructor();
        }

        // EQName: Q{uri}local — dispatch to parsePrimaryExpr for function call/reference
        if (check(Token.BRACED_URI_LITERAL)) {
            return parsePrimaryExpr();
        }

        // NCName or QName — could be name test, function call, keyword, or computed constructor
        if (check(Token.NCNAME) || check(Token.QNAME)) {
            // Computed constructors
            // Map and array constructors
            if (checkKeyword(Keywords.MAP) && peekIs(Token.LBRACE)) {
                return parsePrimaryExpr();
            }
            if (checkKeyword(Keywords.ARRAY) && peekIs(Token.LBRACE)) {
                return parsePrimaryExpr();
            }

            if (checkKeyword(Keywords.ELEMENT) && peekIsConstructorStart()) {
                return parseComputedElementConstructor();
            }
            if (checkKeyword(Keywords.ATTRIBUTE) && peekIsConstructorStart()) {
                return parseComputedAttributeConstructor();
            }
            if (checkKeyword(Keywords.TEXT) && peekIs(Token.LBRACE)) {
                return parseComputedTextConstructor();
            }
            if (checkKeyword(Keywords.COMMENT) && peekIs(Token.LBRACE)) {
                return parseComputedCommentConstructor();
            }
            if (checkKeyword(Keywords.DOCUMENT) && peekIs(Token.LBRACE)) {
                return parseComputedDocumentConstructor();
            }
            if (checkKeyword(Keywords.PROCESSING_INSTRUCTION) && peekIsConstructorStart()) {
                return parseComputedPIConstructor();
            }
            if (checkKeyword(Keywords.NAMESPACE) && peekIsConstructorStart()) {
                return parseComputedNamespaceConstructor();
            }

            // ordered { expr } — evaluation order hint
            if (checkKeyword(Keywords.ORDERED) && peekIs(Token.LBRACE)) {
                advance(); // consume 'ordered'
                expect(Token.LBRACE, "'{'");
                final Expression inner = parseExpr();
                expect(Token.RBRACE, "'}'");
                return inner; // pass through — eXist doesn't differentiate ordered/unordered
            }
            // unordered { expr } — evaluation order hint
            if (checkKeyword(Keywords.UNORDERED) && peekIs(Token.LBRACE)) {
                advance(); // consume 'unordered'
                expect(Token.LBRACE, "'{'");
                final Expression inner = parseExpr();
                expect(Token.RBRACE, "'}'");
                return inner; // pass through
            }

            // validate expression — eXist is not schema-aware, so parse and pass through
            // validate strict { expr }, validate lax { expr }, validate type QName { expr }
            if (checkKeyword(Keywords.VALIDATE)) {
                advance(); // consume 'validate'
                matchKeyword("strict");
                matchKeyword("lax");
                if (matchKeyword("type")) {
                    // consume the type name
                    if (check(Token.NCNAME) || check(Token.QNAME)) advance();
                    else if (check(Token.BRACED_URI_LITERAL)) parseEQName();
                }
                expect(Token.LBRACE, "'{'");
                final Expression inner = parseExpr();
                expect(Token.RBRACE, "'}'");
                return inner; // pass through — no validation wrapper
            }

            // Kind test: text(), node(), element(), attribute(), comment(), etc.
            // Must check BEFORE function call since text() looks like a function call
            if (isKindTest(current.value) && peekIs(Token.LPAREN)) {
                final NodeTest test = parseKindTest();
                final LocationStep step = new LocationStep(context, Constants.CHILD_AXIS, test);
                step.setLocation(current.line, current.column);
                while (check(Token.LBRACKET)) parsePredicate(step);
                return step;
            }

            // Function call: name(args) or function reference: name#arity
            if (isFunctionCallStart() || peekIs(Token.HASH)) {
                return parsePrimaryExpr();
            }

            // Check if it's a keyword that starts a sub-expression
            if (isKeywordExprStart()) {
                return parsePrimaryExpr();
            }

            // Inline function keyword or focus function (function { })
            if (checkKeyword(Keywords.FUNCTION) && (peekIs(Token.LPAREN) || peekIs(Token.LBRACE))) {
                return parsePrimaryExpr();
            }

            // Focus function: fn { expr }
            if (checkKeyword(Keywords.FN) && peekIs(Token.LBRACE)) {
                return parsePrimaryExpr();
            }

            // Name test (abbreviated child::name) — handle prefix:* wildcards
            final Token nameToken = current;
            advance();

            NodeTest nameTest;
            if (check(Token.COLON) && peekIs(Token.STAR)) {
                // prefix:* wildcard
                advance(); // consume :
                advance(); // consume *
                final String nsURI = context.getURIForPrefix(nameToken.value);
                nameTest = new NameTest(Type.ELEMENT,
                        new QName.WildcardLocalPartQName(nsURI != null ? nsURI : "", nameToken.value));
            } else {
                nameTest = new NameTest(Type.ELEMENT, resolveElementName(nameToken.value));
            }

            final LocationStep step = new LocationStep(context, Constants.CHILD_AXIS, nameTest);
            step.setLocation(nameToken.line, nameToken.column);
            while (check(Token.LBRACKET)) parsePredicate(step);
            return step;
        }

        return parsePrimaryExpr();
    }

    // ========================================================================
    // Computed constructors
    // ========================================================================

    Expression parseComputedElementConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'element'

        final ElementConstructor elem = new ElementConstructor(context);
        elem.setLocation(line, col);

        // Name: QName, EQName (Q{uri}local), or { expr }
        final PathExpr nameExpr = new PathExpr(context);
        if (match(Token.LBRACE)) {
            nameExpr.add(parseExpr());
            expect(Token.RBRACE, "'}'");
        } else if (check(Token.BRACED_URI_LITERAL)) {
            nameExpr.add(new LiteralValue(context, new StringValue(parseEQName())));
        } else {
            final String name = expectName("element name");
            nameExpr.add(new LiteralValue(context, new StringValue(name)));
        }
        elem.setNameExpr(nameExpr);

        // Content: { expr, expr, ... }
        expect(Token.LBRACE, "'{'");
        final SequenceConstructor construct = new SequenceConstructor(context);
        final EnclosedExpr enclosed = new EnclosedExpr(context);
        enclosed.addPath(construct);
        elem.setContent(enclosed);

        if (!check(Token.RBRACE)) {
            // Parse comma-separated content expressions
            final PathExpr contentExpr = new PathExpr(context);
            contentExpr.add(parseExprSingle());
            construct.addPathIfNotFunction(contentExpr);

            while (match(Token.COMMA)) {
                final PathExpr nextContent = new PathExpr(context);
                nextContent.add(parseExprSingle());
                construct.addPathIfNotFunction(nextContent);
            }
        }
        expect(Token.RBRACE, "'}'");

        return elem;
    }

    Expression parseComputedAttributeConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'attribute'

        final DynamicAttributeConstructor attr = new DynamicAttributeConstructor(context);
        attr.setLocation(line, col);

        // Name: QName, EQName (Q{uri}local), or { expr }
        if (match(Token.LBRACE)) {
            final PathExpr nameExpr = new PathExpr(context);
            nameExpr.add(parseExpr());
            expect(Token.RBRACE, "'}'");
            attr.setNameExpr(nameExpr);
        } else if (check(Token.BRACED_URI_LITERAL)) {
            attr.setNameExpr(new LiteralValue(context, new StringValue(parseEQName())));
        } else {
            final String name = expectName("attribute name");
            attr.setNameExpr(new LiteralValue(context, new StringValue(name)));
        }

        // Content: { expr }
        expect(Token.LBRACE, "'{'");
        final PathExpr contentExpr = new PathExpr(context);
        if (!check(Token.RBRACE)) {
            contentExpr.add(parseExpr());
        }
        attr.setContentExpr(contentExpr);
        expect(Token.RBRACE, "'}'");

        return attr;
    }

    Expression parseComputedTextConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'text'

        expect(Token.LBRACE, "'{'");
        final PathExpr contentExpr = new PathExpr(context);
        contentExpr.add(parseExpr());
        expect(Token.RBRACE, "'}'");

        final DynamicTextConstructor text = new DynamicTextConstructor(context, contentExpr);
        text.setLocation(line, col);
        return text;
    }

        Expression parseComputedNamespaceConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'namespace'

        final NamespaceConstructor ns = new NamespaceConstructor(context);
        ns.setLocation(line, col);

        // Namespace prefix: static name or { expr }
        if (match(Token.LBRACE)) {
            final PathExpr nameExpr = new PathExpr(context);
            nameExpr.add(parseExpr());
            expect(Token.RBRACE, "'}'");
            ns.setNameExpr(nameExpr);
        } else {
            final String prefix = expectName("namespace prefix");
            ns.setNameExpr(new LiteralValue(context, new StringValue(prefix)));
        }

        // URI: { expr }
        expect(Token.LBRACE, "'{'");
        final PathExpr uriExpr = new PathExpr(context);
        if (!check(Token.RBRACE)) {
            uriExpr.add(parseExpr());
        }
        expect(Token.RBRACE, "'}'");
        ns.setContentExpr(uriExpr);

        return ns;
    }

    Expression parseComputedCommentConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'comment'

        expect(Token.LBRACE, "'{'");
        final PathExpr contentExpr = new PathExpr(context);
        contentExpr.add(parseExpr());
        expect(Token.RBRACE, "'}'");

        final DynamicCommentConstructor comment = new DynamicCommentConstructor(context, contentExpr);
        comment.setLocation(line, col);
        return comment;
    }

    Expression parseComputedDocumentConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'document'

        expect(Token.LBRACE, "'{'");
        final PathExpr contentExpr = new PathExpr(context);
        contentExpr.add(parseExpr());
        expect(Token.RBRACE, "'}'");

        final DocumentConstructor doc = new DocumentConstructor(context, contentExpr);
        doc.setLocation(line, col);
        return doc;
    }

    Expression parseComputedPIConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume 'processing-instruction'

        final DynamicPIConstructor pi = new DynamicPIConstructor(context);
        pi.setLocation(line, col);

        // PI target: static name or { expr }
        if (match(Token.LBRACE)) {
            final PathExpr nameExpr = new PathExpr(context);
            nameExpr.add(parseExpr());
            expect(Token.RBRACE, "'}'");
            pi.setNameExpr(nameExpr);
        } else {
            final String target = expectName("PI target");
            pi.setNameExpr(new LiteralValue(context, new StringValue(target)));
        }

        // Content: { expr }
        expect(Token.LBRACE, "'{'");
        final PathExpr contentExpr = new PathExpr(context);
        if (!check(Token.RBRACE)) {
            contentExpr.add(parseExpr());
        }
        expect(Token.RBRACE, "'}'");
        pi.setContentExpr(contentExpr);

        return pi;
    }

    // ========================================================================
    // Direct element constructor — fully character-level scanning
    // ========================================================================

    /** Mutable position state for character-level XML scanning. */
    private int xp;   // position in codepoint array
    private int xln;  // line number
    private int xcl;  // column number

    /**
     * Parses a direct element constructor. Called when current token is LT
     * and the next token/character is a name start character.
     * Switches entirely to character-level scanning for the element tree.
     * Only returns to token mode after the outermost closing tag.
     */
    Expression parseDirectElementConstructor() throws XPathException {
        final int line = current.line, col = current.column;

        // The LT token's endOffset tells us where '<' ended in the raw input.
        // The element name starts right at that position.
        xp = current.endOffset;
        xln = current.line;
        xcl = current.column + 1;
        // Discard any buffered/current tokens — we're in character mode now
        bufferedNext = null;

        final Expression elem = scanDirectElement(line, col);

        // Re-sync the lexer to token mode at our current raw position
        syncLexer(xp, xln, xcl);
        return elem;
    }

    /**
     * Scans a complete direct element at character level.
     * xp/xln/xcl must be positioned at the first char of the element name
     * (right after '<').
     */
    private Expression scanDirectElement(final int line, final int col) throws XPathException {
        // Scan element name
        final int nameStart = xp;
        while (xp < lexer.getLength() && (XQueryLexer.isNameChar(lexer.charAt(xp)) || lexer.charAt(xp) == ':')) {
            xp++; xcl++;
        }
        if (xp == nameStart) throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                "Expected element name after '<'");
        final String elemName = lexer.substring(nameStart, xp);

        final ElementConstructor elem = new ElementConstructor(context, elemName);
        elem.setLocation(line, col);

        context.pushInScopeNamespaces();
        try {
            // Scan attributes
            while (true) {
                skipXMLWhitespace();

                if (xp >= lexer.getLength())
                    throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                            "Unclosed start tag '<" + elemName + "'");

                // Self-closing />
                if (xchar() == '/' && xpeek(1) == '>') {
                    xp += 2; xcl += 2;
                    return elem;
                }
                // End of start tag >
                if (xchar() == '>') {
                    xp++; xcl++;
                    break;
                }

                // Attribute
                if (!XQueryLexer.isNameStartChar(xchar()))
                    throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                            "Expected attribute name or '>' in element '<" + elemName + "'");

                scanAttribute(elem);
            }

            // Scan element content — wrapped in EnclosedExpr > SequenceConstructor
            // to match ANTLR 2 behavior. EnclosedExpr handles document context,
            // atomic value spacing, node copying.
            final SequenceConstructor construct = new SequenceConstructor(context);
            scanElementContent(construct, elemName);
            final EnclosedExpr enclosed = new EnclosedExpr(context);
            enclosed.addPath(construct);
            elem.setContent(enclosed);

            return elem;
        } finally {
            context.popInScopeNamespaces();
        }
    }

    private void scanAttribute(final ElementConstructor elem) throws XPathException {
        final int aStart = xp;
        while (xp < lexer.getLength() && (XQueryLexer.isNameChar(xchar()) || xchar() == ':')) { xp++; xcl++; }
        final String attrName = lexer.substring(aStart, xp);

        skipXMLWhitespace();
        xexpect('=', "Expected '=' after attribute '" + attrName + "'");
        skipXMLWhitespace();

        final int quote = xchar();
        if (quote != '"' && quote != '\'')
            throw new XPathException(xln, xcl, ErrorCodes.XPST0003, "Expected quote for attribute value");
        xp++; xcl++;

        final AttributeConstructor attr = new AttributeConstructor(context, attrName);
        final StringBuilder avt = new StringBuilder();

        while (xp < lexer.getLength() && xchar() != quote) {
            if (xchar() == '{') {
                if (xpeek(1) == '{') {
                    avt.append('{'); xp += 2; xcl += 2;
                } else {
                    if (avt.length() > 0) { attr.addValue(avt.toString()); avt.setLength(0); }
                    xp++; xcl++;
                    attr.addEnclosedExpr(scanEnclosedExpr());
                }
            } else if (xchar() == '&') {
                avt.append(scanXMLReference());
            } else {
                if (xchar() == '\n') { xln++; xcl = 0; }
                avt.appendCodePoint(xchar()); xp++; xcl++;
            }
        }
        if (xp < lexer.getLength()) { xp++; xcl++; } // closing quote

        if (avt.length() > 0) attr.addValue(avt.toString());
        elem.addAttribute(attr);

        if (attr.isNamespaceDeclaration()) {
            final String nsPrefix = attrName.equals("xmlns") ? ""
                    : attrName.substring(attrName.indexOf(':') + 1);
            context.declareInScopeNamespace(nsPrefix, attr.getLiteralValue());
        }
    }

    /**
     * Scans element content until the matching close tag is found.
     * Handles text, nested elements, enclosed expressions, comments, PIs, CDATA.
     */
    private void scanElementContent(final PathExpr content, final String elemName)
            throws XPathException {
        final StringBuilder text = new StringBuilder();

        while (xp < lexer.getLength()) {
            final int ch = xchar();

            if (ch == '<') {
                flushText(content, text);

                if (xpeek(1) == '/') {
                    // End tag </name>
                    xp += 2; xcl += 2;
                    final int cs = xp;
                    while (xp < lexer.getLength() && xchar() != '>' && !isXMLWhitespace(xchar())) { xp++; xcl++; }
                    final String closeName = lexer.substring(cs, xp);
                    skipXMLWhitespace();
                    if (xp < lexer.getLength() && xchar() == '>') { xp++; xcl++; }
                    if (!closeName.equals(elemName))
                        throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                                "Mismatched closing tag: expected '</" + elemName
                                        + ">' but found '</" + closeName + ">'");
                    return;
                } else if (xpeek(1) == '!' && xpeek(2) == '-' && xpeek(3) == '-') {
                    // <!-- comment --> — create CommentConstructor
                    flushText(content, text);
                    final int cmtLine = xln, cmtCol = xcl;
                    xp += 4; xcl += 4;
                    final StringBuilder cmtData = new StringBuilder();
                    while (xp + 2 < lexer.getLength()
                            && !(xchar() == '-' && xpeek(1) == '-' && xpeek(2) == '>')) {
                        cmtData.appendCodePoint(xchar());
                        if (xchar() == '\n') { xln++; xcl = 1; } else { xcl++; }
                        xp++;
                    }
                    if (xp + 2 < lexer.getLength()) { xp += 3; xcl += 3; }
                    final CommentConstructor cmt = new CommentConstructor(context, cmtData.toString());
                    cmt.setLocation(cmtLine, cmtCol);
                    content.add(cmt);
                } else if (xp + 8 < lexer.getLength()
                        && lexer.substring(xp + 1, xp + 9).equals("![CDATA[")) {
                    // <![CDATA[...]]> — use CDATAConstructor, not TextConstructor
                    // CDATA content must NOT go through StringValue.expand()
                    flushText(content, text);
                    final int cdataLine = xln, cdataCol = xcl;
                    xp += 9; xcl += 9;
                    final StringBuilder cdataText = new StringBuilder();
                    while (xp + 2 < lexer.getLength()
                            && !(xchar() == ']' && xpeek(1) == ']' && xpeek(2) == '>')) {
                        cdataText.appendCodePoint(xchar());
                        if (xchar() == '\n') { xln++; xcl = 1; } else { xcl++; }
                        xp++;
                    }
                    if (xp + 2 < lexer.getLength()) { xp += 3; xcl += 3; }
                    final CDATAConstructor cdata = new CDATAConstructor(context, cdataText.toString());
                    cdata.setLocation(cdataLine, cdataCol);
                    content.add(cdata);
                } else if (xpeek(1) == '?') {
                    // <?target content?> — create PIConstructor
                    flushText(content, text);
                    final int piLine = xln, piCol = xcl;
                    xp += 2; xcl += 2;
                    final StringBuilder piData = new StringBuilder();
                    while (xp + 1 < lexer.getLength()
                            && !(xchar() == '?' && xpeek(1) == '>')) {
                        piData.appendCodePoint(xchar());
                        if (xchar() == '\n') { xln++; xcl = 1; } else { xcl++; }
                        xp++;
                    }
                    if (xp + 1 < lexer.getLength()) { xp += 2; xcl += 2; }
                    final PIConstructor pi = new PIConstructor(context, piData.toString());
                    pi.setLocation(piLine, piCol);
                    content.add(pi);
                } else if (XQueryLexer.isNameStartChar(xpeek(1))) {
                    // Nested element — fully recursive, stays in character mode
                    xp++; xcl++; // skip '<'
                    content.add(scanDirectElement(xln, xcl - 1));
                } else {
                    text.append('<'); xp++; xcl++;
                }
            } else if (ch == '{') {
                if (xpeek(1) == '{') {
                    text.append('{'); xp += 2; xcl += 2;
                } else {
                    flushText(content, text);
                    xp++; xcl++;
                    content.add(scanEnclosedExpr());
                }
            } else if (ch == '}') {
                if (xpeek(1) == '}') {
                    text.append('}'); xp += 2; xcl += 2;
                } else {
                    throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                            "Unexpected '}' in element content");
                }
            } else if (ch == '&') {
                // Keep entity reference in raw form for proper boundary-space detection.
                // TextConstructor.StringValue.expand() handles expansion at runtime.
                // If we pre-expand, &#32; → ' ' would be wrongly classified as
                // strippable whitespace by TextConstructor.isWhitespaceOnly.
                text.append(scanXMLReferenceRaw());
            } else {
                text.appendCodePoint(ch);
                if (ch == '\n') { xln++; xcl = 1; } else { xcl++; }
                xp++;
            }
        }
        throw new XPathException(xln, xcl, ErrorCodes.XPST0003,
                "Unclosed element '<" + elemName + ">' — expected '</" + elemName + ">'");
    }

    /**
     * Scans an enclosed expression { expr } from within XML mode.
     * Switches to token mode for the expression, then returns to character mode.
     * xp must be positioned right after the opening '{'.
     */
    private Expression scanEnclosedExpr() throws XPathException {
        syncLexer(xp, xln, xcl);
        final Expression expr = parseExpr();
        // After parseExpr, current should be RBRACE
        if (current.type == Token.RBRACE) {
            xp = current.endOffset;
            xln = current.line;
            xcl = current.column + 1;
        } else {
            xp = lexer.getPosition();
            xln = lexer.getLine();
            xcl = lexer.getColumn();
        }
        bufferedNext = null;
        return expr;
    }

    private void flushText(final PathExpr content, final StringBuilder text) throws XPathException {
        if (text.length() > 0) {
            content.add(new TextConstructor(context, text.toString()));
            text.setLength(0);
        }
    }

    // ---- Character-level helpers for XML scanning ----

    private int xchar() { return xp < lexer.getLength() ? lexer.charAt(xp) : 0; }
    private int xpeek(int n) { return xp + n < lexer.getLength() ? lexer.charAt(xp + n) : 0; }

    private void xexpect(final int ch, final String msg) throws XPathException {
        if (xp >= lexer.getLength() || xchar() != ch)
            throw new XPathException(xln, xcl, ErrorCodes.XPST0003, msg);
        xp++; xcl++;
    }

    private void skipXMLWhitespace() {
        while (xp < lexer.getLength() && isXMLWhitespace(xchar())) {
            if (xchar() == '\n') { xln++; xcl = 1; } else { xcl++; }
            xp++;
        }
    }

    /**
     * Scans an XML entity/character reference at position xp (which is at '&').
     * Updates xp/xcl past the reference.
     */
    /**
     * Scans an XML reference (&amp;...;) and returns the RAW text including &amp; and ;
     * Used in element content where TextConstructor.StringValue.expand() handles expansion.
     */
    private String scanXMLReferenceRaw() throws XPathException {
        final int start = xp;
        // Validate the reference (advances xp past it)
        scanXMLReference();
        // Return the raw text from '&' to ';' inclusive
        return lexer.substring(start, xp);
    }

    private String scanXMLReference() throws XPathException {
        final int refStart = xp;
        xp++; xcl++; // skip &
        if (xp >= lexer.getLength()) throw error("Unterminated reference");
        if (xchar() == '#') {
            xp++; xcl++;
            int value;
            if (xp < lexer.getLength() && xchar() == 'x') {
                xp++; xcl++;
                final int start = xp;
                while (xp < lexer.getLength() && XQueryLexer.isHexDigit(xchar())) { xp++; xcl++; }
                value = Integer.parseInt(lexer.substring(start, xp), 16);
            } else {
                final int start = xp;
                while (xp < lexer.getLength() && XQueryLexer.isDigit(xchar())) { xp++; xcl++; }
                value = Integer.parseInt(lexer.substring(start, xp));
            }
            if (xp < lexer.getLength() && xchar() == ';') { xp++; xcl++; }
            return new String(Character.toChars(value));
        }
        final int start = xp;
        while (xp < lexer.getLength() && xchar() != ';') { xp++; xcl++; }
        final String name = lexer.substring(start, xp);
        if (xp < lexer.getLength()) { xp++; xcl++; } // skip ;
        switch (name) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "quot": return "\"";
            case "apos": return "'";
            default: throw error("Unknown entity: &" + name + ";");
        }
    }

    /**
     * Syncs the lexer to a raw position after character-level scanning.
     * Re-initializes the lexer and parser state for token-based parsing.
     */
    private void syncLexer(final int pos, final int line, final int col) {
        lexer.setPosition(pos);
        lexer.setLineColumn(line, col);
        bufferedNext = null;
        current = lexer.nextToken();
        previous = current;
    }

    private static boolean isXMLWhitespace(final int ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
    }

    // ========================================================================
    // ========================================================================
    // Array constructors, map constructors, and lookup operators
    // ========================================================================

    /**
     * Square bracket array constructor: [1, 2, 3]
     */
    Expression parseSquareArrayConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        expect(Token.LBRACKET, "'['");
        final org.exist.xquery.functions.array.ArrayConstructor array =
                new org.exist.xquery.functions.array.ArrayConstructor(context,
                        org.exist.xquery.functions.array.ArrayConstructor.ConstructorType.SQUARE_ARRAY);
        array.setLocation(line, col);

        if (!check(Token.RBRACKET)) {
            final PathExpr arg = new PathExpr(context);
            arg.add(parseExprSingle());
            array.addArgument(arg);
            while (match(Token.COMMA)) {
                final PathExpr nextArg = new PathExpr(context);
                nextArg.add(parseExprSingle());
                array.addArgument(nextArg);
            }
        }
        expect(Token.RBRACKET, "']'");
        return array;
    }

    /**
     * Curly array constructor: array { expr }
     */
    Expression parseCurlyArrayConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.ARRAY);
        expect(Token.LBRACE, "'{'");
        final org.exist.xquery.functions.array.ArrayConstructor array =
                new org.exist.xquery.functions.array.ArrayConstructor(context,
                        org.exist.xquery.functions.array.ArrayConstructor.ConstructorType.CURLY_ARRAY);
        array.setLocation(line, col);

        if (!check(Token.RBRACE)) {
            final PathExpr arg = new PathExpr(context);
            arg.add(parseExpr());
            array.addArgument(arg);
        }
        expect(Token.RBRACE, "'}'");
        return array;
    }

    /**
     * Map constructor: map { "key": value, "key2": value2 }
     */
    /**
     * Checks if the current '{' starts a bare map constructor (XQ4).
     * Uses lookahead: { RBRACE (empty map) or { expr COLON (key:value map).
     * Saves and restores parser state for backtracking.
     */
    private boolean isBareMapConstructorStart() {
        if (!check(Token.LBRACE)) return false;

        // { } is an empty map
        if (peekIs(Token.RBRACE)) return true;

        // Save state for backtracking
        final Token savedCurrent = current;
        final Token savedPrevious = previous;
        final Token savedBuffered = bufferedNext;
        final int savedLexerPos = lexer.getPosition();

        try {
            advance(); // consume {

            // Check for patterns that indicate map entries:
            // { "string" : ... } or { number : ... } or { $var : ... } or { name : ... }
            // Skip the first "key" expression — simple cases only
            if (check(Token.STRING_LITERAL) || check(Token.INTEGER_LITERAL)
                    || check(Token.DECIMAL_LITERAL) || check(Token.DOUBLE_LITERAL)) {
                advance(); // consume literal
                return check(Token.COLON);
            }
            if (check(Token.DOLLAR)) {
                advance(); // $
                if (check(Token.NCNAME) || check(Token.QNAME)) {
                    advance(); // var name
                    return check(Token.COLON);
                }
            }
            if (check(Token.NCNAME) || check(Token.QNAME)) {
                final String name = current.value;
                advance(); // consume name
                // name followed by : (but not :: which is an axis)
                if (check(Token.COLON) && !peekIs(Token.COLON)) return true;
                // name(...) : — function call as key
                if (check(Token.LPAREN)) {
                    // Skip balanced parens
                    int depth = 0;
                    while (!check(Token.EOF)) {
                        if (match(Token.LPAREN)) depth++;
                        else if (match(Token.RPAREN)) { depth--; if (depth <= 0) break; }
                        else advance();
                    }
                    return check(Token.COLON);
                }
            }
            // Can't determine — not a bare map
            return false;
        } finally {
            // Restore parser state
            current = savedCurrent;
            previous = savedPrevious;
            bufferedNext = savedBuffered;
            lexer.setPosition(savedLexerPos);
        }
    }

    /**
     * Parses a bare map constructor: { key: value, ... }
     * Called when isBareMapConstructorStart() returns true.
     */
    Expression parseBareMapConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        expect(Token.LBRACE, "'{'");

        final org.exist.xquery.functions.map.MapExpr mapExpr =
                new org.exist.xquery.functions.map.MapExpr(context);
        mapExpr.setLocation(line, col);

        if (!check(Token.RBRACE)) {
            parseMapEntry(mapExpr);
            while (match(Token.COMMA)) {
                parseMapEntry(mapExpr);
            }
        }
        expect(Token.RBRACE, "'}'");
        return mapExpr;
    }

    Expression parseMapConstructor() throws XPathException {
        final int line = current.line, col = current.column;
        matchKeyword(Keywords.MAP);
        expect(Token.LBRACE, "'{'");
        final org.exist.xquery.functions.map.MapExpr mapExpr =
                new org.exist.xquery.functions.map.MapExpr(context);
        mapExpr.setLocation(line, col);

        if (!check(Token.RBRACE)) {
            parseMapEntry(mapExpr);
            while (match(Token.COMMA)) {
                parseMapEntry(mapExpr);
            }
        }
        expect(Token.RBRACE, "'}'");
        return mapExpr;
    }

    private void parseMapEntry(final org.exist.xquery.functions.map.MapExpr mapExpr)
            throws XPathException {
        final PathExpr key = new PathExpr(context);
        key.add(parseExprSingle());
        expect(Token.COLON, "':'");
        final PathExpr value = new PathExpr(context);
        value.add(parseExprSingle());
        mapExpr.map(key, value);
    }

    /**
     * Lookup operator: expr?key, expr?1, expr?(expr), expr?*
     */
    Expression parseLookup(final Expression leftExpr) throws XPathException {
        final int line = current.line, col = current.column;
        expect(Token.QUESTION, "'?'");

        Expression result;

        if (match(Token.STAR)) {
            // Wildcard lookup: expr?*
            result = new Lookup(context, leftExpr);
        } else if (check(Token.INTEGER_LITERAL)) {
            // Integer position lookup: expr?1
            final int position = Integer.parseInt(current.value);
            advance();
            result = new Lookup(context, leftExpr, position);
        } else if (check(Token.NCNAME)) {
            // String key lookup: expr?key
            final String key = current.value;
            advance();
            result = new Lookup(context, leftExpr, key);
        } else if (check(Token.STRING_LITERAL)) {
            // String literal key lookup: expr?"key"
            final String key = current.value;
            advance();
            result = new Lookup(context, leftExpr, key);
        } else if (match(Token.LPAREN)) {
            // Computed lookup: expr?(expr)
            final PathExpr keyExpr = new PathExpr(context);
            keyExpr.add(parseExpr());
            expect(Token.RPAREN, "')'");
            result = new Lookup(context, leftExpr, keyExpr);
        } else {
            // Bare ? — treat as wildcard
            result = new Lookup(context, leftExpr);
        }

        result.setLocation(line, col);
        return result;
    }

    /**
     * Unary lookup: ?key (applied to context item)
     */
    Expression parseUnaryLookup() throws XPathException {
        return parseLookup(null);
    }

    // ========================================================================
    // Primary expressions
    // ========================================================================

    Expression parsePrimaryExpr() throws XPathException {
        if (check(Token.STRING_LITERAL)) return parseStringLiteral();
        if (check(Token.INTEGER_LITERAL)) return parseIntegerLiteral();
        if (check(Token.DECIMAL_LITERAL)) return parseDecimalLiteral();
        if (check(Token.DOUBLE_LITERAL)) return parseDoubleLiteral();

        if (match(Token.DOLLAR)) return parseVariableRef();
        if (match(Token.LPAREN)) return parseParenthesized();

        // Square bracket array constructor: [1, 2, 3]
        if (check(Token.LBRACKET)) return parseSquareArrayConstructor();

        // Map constructor: map { "key": value }
        if (checkKeyword(Keywords.MAP) && peekIs(Token.LBRACE)) return parseMapConstructor();

        // XQ4 bare map constructor: { "key": value } (without 'map' keyword)
        // Disambiguated from enclosed expression by lookahead: { expr : indicates map
        if (check(Token.LBRACE) && isBareMapConstructorStart()) {
            return parseBareMapConstructor();
        }

        // Curly array constructor: array { expr }
        if (checkKeyword(Keywords.ARRAY) && peekIs(Token.LBRACE)) return parseCurlyArrayConstructor();

        // Unary lookup: ?key (context item lookup)
        if (check(Token.QUESTION) && !peekIs(Token.QUESTION)) {
            return parseUnaryLookup();
        }

        if (match(Token.DOT)) {
            final ContextItemExpression ctx = new ContextItemExpression(context);
            ctx.setLocation(previous.line, previous.column);
            return ctx;
        }

        // Annotated inline/focus function: %ann function(...) { } or %ann fn { }
        if (check(Token.PERCENT)) {
            parseAnnotations(); // consume annotations
            if (checkKeyword(Keywords.FN) && (peekIs(Token.LBRACE) || peekIs(Token.LPAREN))) {
                if (peekIs(Token.LBRACE)) {
                    return parseFocusFunction();
                }
                advance(); // consume 'fn'
                return parseInlineFunction();
            }
            if (checkKeyword(Keywords.FUNCTION)) {
                if (peekIs(Token.LBRACE)) {
                    return parseFocusFunction();
                }
                advance(); // consume 'function'
                return parseInlineFunction();
            }
            throw error("Expected 'function' or 'fn' after annotation");
        }

        // Inline function: function($x) { ... }
        // or focus function: function { expr } (XQ4)
        if (checkKeyword(Keywords.FUNCTION) && (peekIs(Token.LPAREN) || peekIs(Token.LBRACE))) {
            if (peekIs(Token.LBRACE)) {
                // function { expr } — XQ4 focus function form
                return parseFocusFunction();
            }
            advance(); // consume 'function'
            return parseInlineFunction();
        }

        // XQ4 inline function shorthand: fn ($x) { expr }, equivalent to
        // function ($x) { expr }. Or focus function: fn { expr }.
        if (checkKeyword(Keywords.FN) && (peekIs(Token.LBRACE) || peekIs(Token.LPAREN))) {
            // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
            if (peekIs(Token.LBRACE)) {
                return parseFocusFunction();
            }
            advance(); // consume 'fn' — parseInlineFunction expects LPAREN next
            return parseInlineFunction();
        }

        // QName literal: #prefix:local — XQ4 only
        if (check(Token.HASH) && peekIsNameStart()) {
            // XQ4 feature accepted in all versions (matching ANTLR 2 behavior)
            return parseQNameLiteral();
        }

        // Pragma / extension expression: (# name content #) { expr }
        if (check(Token.PRAGMA_START)) {
            return parsePragmaExpr();
        }

        // String constructor: ``[content `{expr}` more]`` — XQuery 3.1 (W3C §3.11.4)
        if (check(Token.STRING_CONSTRUCTOR_START)) {
            return parseStringConstructor();
        }

        // String template: `text {expr} more` — XQ4 (W3C §3.11.5)
        if (check(Token.STRING_TEMPLATE_START)) {
            return parseStringTemplate();
        }

        // EQName: Q{uri}local — function call, function reference, or variable
        if (check(Token.BRACED_URI_LITERAL)) {
            final String eqname = parseEQName();
            if (match(Token.HASH)) {
                // Q{uri}name#arity — named function reference
                return parseNamedFunctionRef(eqname);
            }
            if (match(Token.LPAREN)) {
                // Q{uri}name(args) — function call
                return parseEQNameFunctionCall(eqname);
            }
            throw error("Expected '(' or '#' after EQName '" + eqname + "'");
        }

        // Function call or function reference: name(args) or name#arity
        if (check(Token.NCNAME) || check(Token.QNAME)) {
            // Function reference: name#arity
            if (peekIs(Token.HASH)) {
                final String name = current.value;
                advance(); // consume name
                advance(); // consume #
                return parseNamedFunctionRef(name);
            }

            // Function call: name(args)
            if (isFunctionCallStart()) {
                return parseFunctionCall();
            }

            // Bare NCName — error with suggestion
            final String name = current.value;
            final String suggestion = Keywords.suggestKeyword(name);
            if (suggestion != null && !suggestion.equals(name)) {
                throw errorWithSuggestion("Unexpected '" + name + "'", suggestion);
            }
            throw error("Unexpected '" + name + "'");
        }

        throw error("Expected expression");
    }

    private Expression parseStringLiteral() {
        final Token token = current;
        advance();
        final LiteralValue lit = new LiteralValue(context, new StringValue(token.value));
        lit.setLocation(token.line, token.column);
        return lit;
    }

    private Expression parseIntegerLiteral() throws XPathException {
        final Token token = current;
        advance();
        final LiteralValue lit = new LiteralValue(context, new IntegerValue(stripUnderscores(token.value)));
        lit.setLocation(token.line, token.column);
        return lit;
    }

    private Expression parseDecimalLiteral() throws XPathException {
        final Token token = current;
        advance();
        final LiteralValue lit = new LiteralValue(context, new DecimalValue(stripUnderscores(token.value)));
        lit.setLocation(token.line, token.column);
        return lit;
    }

    private Expression parseDoubleLiteral() throws XPathException {
        final Token token = current;
        advance();
        final LiteralValue lit = new LiteralValue(context, new DoubleValue(stripUnderscores(token.value)));
        lit.setLocation(token.line, token.column);
        return lit;
    }

    /**
     * Strips XQuery 4.0 numeric separators ('_'). Avoids the regex-backed
     * String.replace(CharSequence,CharSequence) when no underscore is present —
     * the common case.
     */
    private static String stripUnderscores(final String value) {
        return value.indexOf('_') < 0 ? value : value.replace("_", "");
    }

    private Expression parseVariableRef() throws XPathException {
        final int line = previous.line, col = previous.column;
        final String varName = check(Token.NCNAME) || check(Token.QNAME) ? current.value : null;
        if (varName == null) throw error("Expected variable name after '$'");
        advance();
        final VariableReference ref = new VariableReference(context, resolveQName(varName, null));
        ref.setLocation(line, col);
        return ref;
    }

    private Expression parseParenthesized() throws XPathException {
        final int line = previous.line, col = previous.column;
        if (match(Token.RPAREN)) {
            final PathExpr empty = new PathExpr(context);
            empty.setLocation(line, col);
            return empty;
        }
        final Expression expr = parseExpr();
        expect(Token.RPAREN, "')'");
        return expr;
    }

    /**
     * Parses a Q{uri}local EQName — returns the combined string for QName resolution.
     * Consumes BRACED_URI_LITERAL + NCNAME tokens.
     */
    private String parseEQName() throws XPathException {
        final String bracedUri = current.value; // Q{...}
        advance(); // consume BRACED_URI_LITERAL
        if (!check(Token.NCNAME)) throw error("Expected local name after braced URI");
        final String local = current.value;
        advance(); // consume local name
        // Return in a format QName.parse can handle
        return bracedUri + local;
    }

    /**
     * Parses a function call with an EQName (Q{uri}local(args)).
     * LPAREN already consumed.
     */
    private Expression parseEQNameFunctionCall(final String eqname) throws XPathException {
        final Token nameToken = previous; // the local name token
        final List<Expression> args = new ArrayList<>();
        if (!check(Token.RPAREN)) {
            args.add(parseFunctionArg());
            while (match(Token.COMMA)) {
                args.add(parseFunctionArg());
            }
        }
        expect(Token.RPAREN, "')'");

        final XQueryAST ast = new XQueryAST(0, eqname);
        ast.setLine(nameToken.line);
        ast.setColumn(nameToken.column);

        // Parse Q{uri}local into namespace URI + local name
        final int braceEnd = eqname.indexOf('}');
        final String uri = eqname.substring(2, braceEnd); // skip Q{
        final String local = eqname.substring(braceEnd + 1);
        final QName qname = new QName(local, uri);
        final PathExpr parent = new PathExpr(context);
        Expression fn = FunctionFactory.createFunction(context, qname, ast, parent, args);
        if (fn instanceof AbstractExpression) {
            ((AbstractExpression) fn).setLocation(nameToken.line, nameToken.column);
        }

        // Check for partial application — also check inside keyword args
        boolean isPartial = false;
        for (final Expression arg : args) {
            if (arg instanceof Function.Placeholder) {
                isPartial = true;
                break;
            }
            if (arg instanceof KeywordArgumentExpression &&
                    ((KeywordArgumentExpression) arg).getArgument() instanceof Function.Placeholder) {
                isPartial = true;
                break;
            }
        }
        if (isPartial) {
            if (!(fn instanceof FunctionCall)) {
                if (fn instanceof CastExpression) {
                    fn = ((CastExpression) fn).toFunction();
                }
                fn = FunctionFactory.wrap(context, (Function) fn);
            }
            fn = new PartialFunctionApplication(context, (FunctionCall) fn);
            ((AbstractExpression) fn).setLocation(nameToken.line, nameToken.column);
        }

        return fn;
    }

    Expression parseFunctionCall() throws XPathException {
        final Token nameToken = current;
        advance();
        expect(Token.LPAREN, "'('");

        final List<Expression> args = new ArrayList<>();
        if (!check(Token.RPAREN)) {
            args.add(parseFunctionArg());
            while (match(Token.COMMA)) {
                args.add(parseFunctionArg());
            }
        }
        expect(Token.RPAREN, "')'");

        final XQueryAST ast = new XQueryAST(0, nameToken.value);
        ast.setLine(nameToken.line);
        ast.setColumn(nameToken.column);

        final QName qname = resolveQName(nameToken.value, context.getDefaultFunctionNamespace());
        final PathExpr parent = new PathExpr(context);
        Expression fn = FunctionFactory.createFunction(context, qname, ast, parent, args);
        if (fn instanceof AbstractExpression) {
            ((AbstractExpression) fn).setLocation(nameToken.line, nameToken.column);
        }
        // Check for partial application — if any argument is a placeholder
        // Must also check inside KeywordArgumentExpression which wraps placeholders
        boolean isPartial = false;
        for (final Expression arg : args) {
            if (arg instanceof Function.Placeholder) {
                isPartial = true;
                break;
            }
            if (arg instanceof KeywordArgumentExpression &&
                    ((KeywordArgumentExpression) arg).getArgument() instanceof Function.Placeholder) {
                isPartial = true;
                break;
            }
        }
        if (isPartial) {
            if (!(fn instanceof FunctionCall)) {
                if (fn instanceof CastExpression) {
                    fn = ((CastExpression) fn).toFunction();
                }
                fn = FunctionFactory.wrap(context, (Function) fn);
            }
            fn = new PartialFunctionApplication(context, (FunctionCall) fn);
            ((AbstractExpression) fn).setLocation(nameToken.line, nameToken.column);
        }

        return fn;
    }

    /**
     * Parses a function argument — either a regular expression or a keyword argument (name := value).
     */
    private Expression parseFunctionArg() throws XPathException {
        // Placeholder argument: ? for partial function application
        if (check(Token.QUESTION) && !peekIs(Token.QUESTION)) {
            // Check if this is a placeholder (followed by comma or rparen)
            // vs a lookup on context item (followed by key)
            if (peekIs(Token.COMMA) || peekIs(Token.RPAREN)) {
                advance(); // consume ?
                return new Function.Placeholder(context);
            }
        }

        // Keyword argument: name := value
        // Accepted regardless of version — no valid XQ3.1 syntax starts with name :=
        // in a function argument position, and the ANTLR 2 parser accepts them too.
        if (check(Token.NCNAME) && peekIs(Token.COLON_EQ)) {
            final String keyName = current.value;
            advance(); // consume name
            advance(); // consume :=
            // Check for ? placeholder as keyword argument value: name := ?
            if (check(Token.QUESTION) && (peekIs(Token.COMMA) || peekIs(Token.RPAREN))) {
                advance(); // consume ?
                return new KeywordArgumentExpression(context, keyName,
                        new Function.Placeholder(context));
            }
            final Expression value = parseExprSingle();
            return new KeywordArgumentExpression(context, keyName, value);
        }

        // Regular positional argument
        final PathExpr argExpr = new PathExpr(context);
        argExpr.add(parseExprSingle());
        return argExpr;
    }

    // ========================================================================
    // Node tests and axes
    // ========================================================================

    /** Saves parser + lexer state for backtracking. */
    private int[] saveParserState() {
        return new int[]{ lexer.getPosition() };
    }

    /** Restores parser + lexer state for backtracking. */
    private void restoreParserState(final Token savedCurrent, final Token savedPrevious,
                                     final Token savedBuffered, final int[] lexerState) {
        current = savedCurrent;
        previous = savedPrevious;
        bufferedNext = savedBuffered;
        lexer.setPosition(lexerState[0]);
    }

    private int matchAxis() {
        if (current.type != Token.NCNAME) return -1;

        final String name = current.value;

        // Handle hyphenated axis names: following-sibling, preceding-sibling,
        // descendant-or-self, ancestor-or-self
        if (("following".equals(name) || "preceding".equals(name) || "descendant".equals(name)
                || "ancestor".equals(name)) && peekIs(Token.MINUS)) {
            // Save full state for backtrack
            final Token savedCurrent = current;
            final Token savedPrevious = previous;
            final Token savedBuffered = bufferedNext;
            final int[] lexerState = saveParserState();

            advance(); // consume axis-start (e.g., "following")
            advance(); // consume MINUS

            if (current.type == Token.NCNAME) {
                final String suffix = current.value;
                if ("sibling".equals(suffix)) {
                    // following-sibling, preceding-sibling,
                    // or XQ4: following-sibling-or-self, preceding-sibling-or-self
                    advance(); // consume "sibling"
                    if (peekIs(Token.COLONCOLON)) {
                        // following-sibling or preceding-sibling
                        return axisFromName(name + "-sibling");
                    }
                    // Check for -or-self suffix (XQ4 combined axes)
                    if (current.type == Token.MINUS) {
                        advance(); // consume "-"
                        if (current.type == Token.NCNAME && "or".equals(current.value)) {
                            advance(); // consume "or"
                            if (current.type == Token.MINUS) {
                                advance(); // consume "-"
                                if (current.type == Token.NCNAME && "self".equals(current.value)) {
                                    final String compound = name + "-sibling-or-self";
                                    if (peekIs(Token.COLONCOLON)) {
                                        advance(); // consume "self"
                                        return axisFromName(compound);
                                    }
                                }
                            }
                        }
                    }
                } else if ("or".equals(suffix)) {
                    // descendant-or-self, ancestor-or-self,
                    // or XQ4: following-or-self, preceding-or-self
                    advance(); // consume "or"
                    if (current.type == Token.MINUS) {
                        advance(); // consume "-"
                        if (current.type == Token.NCNAME && "self".equals(current.value)) {
                            final String compound = name + "-or-self";
                            if (peekIs(Token.COLONCOLON)) {
                                advance(); // consume "self"
                                return axisFromName(compound);
                            }
                        }
                    }
                }
            }
            // Backtrack — not a valid axis
            restoreParserState(savedCurrent, savedPrevious, savedBuffered, lexerState);
            return -1;
        }

        final int axis = axisFromName(name);
        if (axis < 0) return -1;
        if (peekIs(Token.COLONCOLON)) {
            advance();
            return axis;
        }
        return -1;
    }

    private static int axisFromName(final String name) {
        switch (name) {
            case Keywords.CHILD: return Constants.CHILD_AXIS;
            case Keywords.DESCENDANT: return Constants.DESCENDANT_AXIS;
            case Keywords.DESCENDANT_OR_SELF: return Constants.DESCENDANT_SELF_AXIS;
            case Keywords.PARENT: return Constants.PARENT_AXIS;
            case Keywords.ANCESTOR: return Constants.ANCESTOR_AXIS;
            case Keywords.ANCESTOR_OR_SELF: return Constants.ANCESTOR_SELF_AXIS;
            case Keywords.SELF: return Constants.SELF_AXIS;
            case Keywords.FOLLOWING: return Constants.FOLLOWING_AXIS;
            case Keywords.FOLLOWING_SIBLING: return Constants.FOLLOWING_SIBLING_AXIS;
            case Keywords.PRECEDING: return Constants.PRECEDING_AXIS;
            case Keywords.PRECEDING_SIBLING: return Constants.PRECEDING_SIBLING_AXIS;
            case Keywords.ATTRIBUTE: return Constants.ATTRIBUTE_AXIS;
            // XQ4 combined axes
            case "following-or-self": return Constants.FOLLOWING_OR_SELF_AXIS;
            case "preceding-or-self": return Constants.PRECEDING_OR_SELF_AXIS;
            case "following-sibling-or-self": return Constants.FOLLOWING_SIBLING_OR_SELF_AXIS;
            case "preceding-sibling-or-self": return Constants.PRECEDING_SIBLING_OR_SELF_AXIS;
            default: return -1;
        }
    }

    private NodeTest parseNodeTest(final int axis) throws XPathException {
        final int nodeType = axis == Constants.ATTRIBUTE_AXIS ? Type.ATTRIBUTE : Type.ELEMENT;

        if (match(Token.STAR)) {
            // Check for *:local wildcard
            if (check(Token.COLON) && peekIsNameStart()) {
                advance(); // consume :
                final String local = current.value;
                advance();
                return new NameTest(nodeType, new QName.WildcardNamespaceURIQName(local));
            }
            return new TypeTest(nodeType);
        }
        if (check(Token.NCNAME)) {
            final String name = current.value;
            if (isKindTest(name) && peekIs(Token.LPAREN)) {
                return parseKindTest();
            }
            // Check for prefix:* wildcard
            if (peekIs(Token.COLON)) {
                advance(); // consume name
                advance(); // consume :
                if (match(Token.STAR)) {
                    final String nsURI = context.getURIForPrefix(name);
                    return new NameTest(nodeType,
                            new QName.WildcardLocalPartQName(nsURI != null ? nsURI : "", name));
                }
                // prefix:local — it's a regular QName, already consumed prefix and :
                final String local = current.value;
                advance();
                return new NameTest(nodeType, resolveQName(name + ":" + local,
                        axis == Constants.ATTRIBUTE_AXIS ? null : context.getURIForPrefix("")));
            }
            advance();
            return new NameTest(nodeType, axis == Constants.ATTRIBUTE_AXIS
                    ? resolveQName(name, null) : resolveElementName(name));
        }
        if (check(Token.QNAME)) {
            final Token nameToken = current;
            advance();
            return new NameTest(nodeType, resolveQName(nameToken.value,
                    axis == Constants.ATTRIBUTE_AXIS ? null : context.getURIForPrefix("")));
        }
        // EQName: Q{uri}local as node test
        if (check(Token.BRACED_URI_LITERAL)) {
            final String eqname = parseEQName();
            final int braceEnd = eqname.indexOf('}');
            final String uri = eqname.substring(2, braceEnd);
            final String local = eqname.substring(braceEnd + 1);
            return new NameTest(nodeType, new QName(local, uri));
        }
        throw error("Expected node test");
    }

    /**
     * Parses a pragma/extension expression: (# name content #) { expr }
     * For eXist's (#exist:optimize#) pragma, the expression inside { } is returned.
     */
    private Expression parsePragmaExpr() throws XPathException {
        final int line = current.line, col = current.column;
        advance(); // consume PRAGMA_START (#

        // Skip pragma content until #)
        while (!check(Token.PRAGMA_END) && !check(Token.EOF)) {
            advance();
        }
        if (check(Token.PRAGMA_END)) advance(); // consume #)

        // Parse the pragma body: { expr }
        expect(Token.LBRACE, "'{'");
        final Expression body = parseExpr();
        expect(Token.RBRACE, "'}'");

        // Return an ExtensionExpression wrapping the body
        final ExtensionExpression ext = new ExtensionExpression(context);
        ext.setLocation(line, col);
        ext.setExpression(body);
        return ext;
    }

    /**
     * Parses FT positional filters and match options, adding them to the selection.
     */
    private void parseFTPositionalFilters(final FTExpressions.Selection ftSel)
            throws XPathException {
        while (check(Token.NCNAME)) {
            final String kw = current.value;
            if ("ordered".equals(kw)) {
                advance();
                ftSel.addPosFilter(new FTExpressions.Order(context));
            } else if ("window".equals(kw)) {
                advance();
                final FTExpressions.Window win = new FTExpressions.Window(context);
                win.setWindowExpr(parseExprSingle());
                win.setUnit(parseFTUnit());
                ftSel.addPosFilter(win);
            } else if ("distance".equals(kw)) {
                advance();
                final FTExpressions.Distance dist = new FTExpressions.Distance(context);
                dist.setRange(parseFTRange());
                dist.setUnit(parseFTUnit());
                ftSel.addPosFilter(dist);
            } else if ("at".equals(kw)) {
                advance();
                final FTExpressions.Content content = new FTExpressions.Content(context);
                if (matchKeyword("start")) {
                    content.setContentType(FTExpressions.Content.ContentType.AT_START);
                } else if (matchKeyword("end")) {
                    content.setContentType(FTExpressions.Content.ContentType.AT_END);
                }
                ftSel.addPosFilter(content);
            } else if ("entire".equals(kw)) {
                advance();
                matchKeyword("content");
                final FTExpressions.Content content = new FTExpressions.Content(context);
                content.setContentType(FTExpressions.Content.ContentType.ENTIRE_CONTENT);
                ftSel.addPosFilter(content);
            } else if ("exactly".equals(kw) || "from".equals(kw)) {
                // FTRange used as positional filter (rare)
                final FTExpressions.Range range = parseFTRange();
                ftSel.addPosFilter(range);
            } else if ("same".equals(kw) || "different".equals(kw)) {
                // FTScope: "same"/"different" ("sentence"|"paragraph")
                advance();
                final FTExpressions.Scope scope = new FTExpressions.Scope(context);
                if (matchKeyword("sentence")) { /* default */ }
                else matchKeyword("paragraph");
                ftSel.addPosFilter(scope);
            } else if ("using".equals(kw)) {
                // Match options handled separately
                break;
            } else {
                break;
            }
        }
    }

    private FTExpressions.Unit parseFTUnit() {
        if (matchKeyword("words")) return FTExpressions.Unit.WORDS;
        if (matchKeyword("sentences")) return FTExpressions.Unit.SENTENCES;
        if (matchKeyword("paragraphs")) return FTExpressions.Unit.PARAGRAPHS;
        return FTExpressions.Unit.WORDS; // default
    }

    private FTExpressions.Range parseFTRange() throws XPathException {
        final FTExpressions.Range range = new FTExpressions.Range(context);
        if (matchKeyword("exactly")) {
            range.setMode(FTExpressions.Range.RangeMode.EXACTLY);
            range.setExpr1(parseExprSingle());
        } else if (checkKeyword("at")) {
            advance();
            if (matchKeyword("least")) {
                range.setMode(FTExpressions.Range.RangeMode.AT_LEAST);
                range.setExpr1(parseExprSingle());
            } else if (matchKeyword("most")) {
                range.setMode(FTExpressions.Range.RangeMode.AT_MOST);
                range.setExpr1(parseExprSingle());
            }
        } else if (matchKeyword("from")) {
            range.setMode(FTExpressions.Range.RangeMode.FROM_TO);
            range.setExpr1(parseExprSingle());
            matchKeyword("to");
            range.setExpr2(parseExprSingle());
        }
        return range;
    }

    /**
     * Parses: declare ft-option using ... ;
     * Sets default match options on the context.
     */
    private void parseFTOptionDecl() throws XPathException {
        final FTExpressions.MatchOptions opts = new FTExpressions.MatchOptions();
        while (matchKeyword(Keywords.USING)) {
            if (matchKeyword(Keywords.STEMMING)) {
                opts.setStemming(true);
            } else if (matchKeyword(Keywords.WILDCARDS)) {
                opts.setWildcards(true);
            } else if (matchKeyword(Keywords.LANGUAGE)) {
                if (check(Token.STRING_LITERAL)) { opts.setLanguage(current.value); advance(); }
            } else if (matchKeyword(Keywords.DIACRITICS)) {
                if (matchKeyword(Keywords.INSENSITIVE)) {
                    opts.setDiacriticsMode(FTExpressions.MatchOptions.DiacriticsMode.INSENSITIVE);
                } else { matchKeyword(Keywords.SENSITIVE);
                    opts.setDiacriticsMode(FTExpressions.MatchOptions.DiacriticsMode.SENSITIVE);
                }
            } else if (checkKeyword("case")) {
                advance();
                if (matchKeyword(Keywords.INSENSITIVE)) {
                    opts.setCaseMode(FTExpressions.MatchOptions.CaseMode.INSENSITIVE);
                } else if (matchKeyword(Keywords.SENSITIVE)) {
                    opts.setCaseMode(FTExpressions.MatchOptions.CaseMode.SENSITIVE);
                }
            } else if (checkKeyword("no")) {
                advance();
                if (matchKeyword(Keywords.STEMMING)) opts.setStemming(false);
                else if (matchKeyword(Keywords.WILDCARDS)) opts.setWildcards(false);
                else if (matchKeyword("stop")) { matchKeyword(Keywords.WORDS); }
                else if (matchKeyword("thesaurus")) { /* skip */ }
            } else {
                advance(); // skip unknown option
            }
        }
        expect(Token.SEMICOLON, "';'");
        // // context.setDefaultFTMatchOptions(opts); // TODO: requires v2/xqft-phase2 // TODO: requires v2/xqft-phase2
    }

    private boolean isFTPositionalKeyword(final String name) {
        switch (name) {
            case "ordered": case "window": case "distance": case "at":
            case "entire": case "occurs": case "same": case "different": case "using":
                return true;
            default: return false;
        }
    }

    private boolean isKindTest(final String name) {
        switch (name) {
            case Keywords.NODE: case Keywords.TEXT: case Keywords.ELEMENT:
            case Keywords.ATTRIBUTE: case Keywords.COMMENT:
            case Keywords.DOCUMENT_NODE: case Keywords.PROCESSING_INSTRUCTION:
            case "namespace-node": case "schema-element": case "schema-attribute":
            case "gnode":
                return true;
            default: return false;
        }
    }

    private NodeTest parseKindTest() throws XPathException {
        final String kind = current.value;
        advance(); advance(); // kind name + '('
        NodeTest test;
        switch (kind) {
            case Keywords.NODE: test = new AnyNodeTest(); break;
            case Keywords.TEXT: test = new TypeTest(Type.TEXT); break;
            case Keywords.COMMENT: test = new TypeTest(Type.COMMENT); break;
            case Keywords.DOCUMENT_NODE: test = new TypeTest(Type.DOCUMENT); break;
            case Keywords.PROCESSING_INSTRUCTION:
                if (check(Token.STRING_LITERAL)) {
                    advance(); // consume PI target name (not used in TypeTest)
                } else if (check(Token.NCNAME)) {
                    advance(); // consume PI target name
                }
                test = new TypeTest(Type.PROCESSING_INSTRUCTION);
                break;
            case Keywords.ELEMENT:
                if (check(Token.NCNAME) || check(Token.QNAME) || check(Token.STAR)) {
                    if (match(Token.STAR)) { test = new TypeTest(Type.ELEMENT); }
                    else { final Token n = current; advance(); test = new NameTest(Type.ELEMENT, resolveElementName(n.value)); }
                } else { test = new TypeTest(Type.ELEMENT); }
                break;
            case Keywords.ATTRIBUTE:
                if (check(Token.NCNAME) || check(Token.QNAME) || check(Token.STAR)) {
                    if (match(Token.STAR)) { test = new TypeTest(Type.ATTRIBUTE); }
                    else { final Token n = current; advance(); test = new NameTest(Type.ATTRIBUTE, resolveQName(n.value, null)); }
                } else { test = new TypeTest(Type.ATTRIBUTE); }
                break;
            case "namespace-node": test = new TypeTest(Type.NAMESPACE); break;
            case "schema-element":
                if (check(Token.NCNAME) || check(Token.QNAME)) { advance(); }
                test = new TypeTest(Type.ELEMENT); break;
            case "schema-attribute":
                if (check(Token.NCNAME) || check(Token.QNAME)) { advance(); }
                test = new TypeTest(Type.ATTRIBUTE); break;
            case "gnode":
                test = new AnyNodeTest(); break;
            default: throw error("Unknown kind test: " + kind);
        }
        expect(Token.RPAREN, "')'");
        return test;
    }

    // ========================================================================
    // Token matching helpers
    // ========================================================================

    private boolean check(final int type) { return current.type == type; }
    private boolean checkKeyword(final String kw) { return current.type == Token.NCNAME && kw.equals(current.value); }

    private boolean match(final int type) {
        if (current.type == type) { advance(); return true; }
        return false;
    }

    private boolean matchKeyword(final String kw) {
        if (current.type == Token.NCNAME && kw.equals(current.value)) { advance(); return true; }
        return false;
    }

    private void expect(final int type, final String expected) throws XPathException {
        if (current.type != type) {
            throw new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                    "Expected " + expected + " but found " + describeToken(current));
        }
        advance();
    }

    private void expectKeyword(final String keyword) throws XPathException {
        if (!matchKeyword(keyword)) {
            final String found = describeToken(current);
            final String suggestion = Keywords.suggestKeyword(current.value);
            if (suggestion != null && suggestion.equals(keyword)) {
                throw new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                        "Expected '" + keyword + "' but found " + found + ". Did you mean '" + keyword + "'?");
            }
            throw new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                    "Expected '" + keyword + "' but found " + found);
        }
    }

    private String expectNCName(final String what) throws XPathException {
        if (current.type != Token.NCNAME) {
            throw new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                    "Expected " + what + " but found " + describeToken(current));
        }
        final String value = current.value;
        advance();
        return value;
    }

    private String expectName(final String what) throws XPathException {
        if (current.type == Token.NCNAME || current.type == Token.QNAME) {
            final String value = current.value;
            advance();
            return value;
        }
        throw new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                "Expected " + what + " but found " + describeToken(current));
    }

    private void advance() {
        previous = current;
        if (bufferedNext != null) {
            current = bufferedNext;
            bufferedNext = null;
        } else {
            current = lexer.nextToken();
        }
    }

    private String describeToken(final Token token) {
        if (token.type == Token.EOF) return "end of input";
        if (token.type == Token.NCNAME || token.type == Token.QNAME) return "'" + token.value + "'";
        if (token.type == Token.STRING_LITERAL) return "string \"" + token.value + "\"";
        if (token.type == Token.INTEGER_LITERAL) return "number " + token.value;
        return Token.typeName(token.type);
    }

    // ========================================================================
    // Lookahead helpers
    // ========================================================================

    private boolean isFunctionCallStart() {
        return peekIs(Token.LPAREN);
    }

    private boolean peekIs(final int type) {
        if (bufferedNext == null) bufferedNext = lexer.nextToken();
        return bufferedNext.type == type;
    }

    private boolean peekIsKeyword(final String kw) {
        if (bufferedNext == null) bufferedNext = lexer.nextToken();
        return bufferedNext.type == Token.NCNAME && kw.equals(bufferedNext.value);
    }

    /**
     * Checks if the peek token could start a computed constructor body ({ or QName).
     */
    private boolean peekIsConstructorStart() {
        if (bufferedNext == null) bufferedNext = lexer.nextToken();
        return bufferedNext.type == Token.LBRACE
                || bufferedNext.type == Token.NCNAME
                || bufferedNext.type == Token.QNAME;
    }

    /**
     * Checks if peek token is a name start character (for direct element constructors).
     */
    private boolean peekIsNameStart() {
        if (bufferedNext == null) bufferedNext = lexer.nextToken();
        return bufferedNext.type == Token.NCNAME || bufferedNext.type == Token.QNAME;
    }

    private boolean isStepStart() {
        return check(Token.NCNAME) || check(Token.QNAME) || check(Token.STAR)
                || check(Token.AT) || check(Token.DOT) || check(Token.DOT_DOT)
                || check(Token.STRING_LITERAL) || check(Token.INTEGER_LITERAL)
                || check(Token.DECIMAL_LITERAL) || check(Token.DOUBLE_LITERAL)
                || check(Token.DOLLAR) || check(Token.LPAREN) || check(Token.LT);
    }

    private boolean isKeywordExprStart() {
        if (current.type != Token.NCNAME) return false;
        switch (current.value) {
            case Keywords.FOR: case Keywords.LET: case Keywords.IF:
            case Keywords.SOME: case Keywords.EVERY:
            case Keywords.SWITCH: case Keywords.TYPESWITCH: case Keywords.TRY:
                return true;
            default: return false;
        }
    }

    /**
     * Checks if we're in a context where * would be a binary multiply operator.
     * (e.g. after a closing paren, bracket, number literal, name)
     */
    private boolean isBinaryOperatorContext() {
        if (previous == null) return false;
        switch (previous.type) {
            case Token.RPAREN: case Token.RBRACKET: case Token.DOT:
            case Token.INTEGER_LITERAL: case Token.DECIMAL_LITERAL:
            case Token.DOUBLE_LITERAL: case Token.STRING_LITERAL:
            case Token.NCNAME: case Token.QNAME:
                return true;
            default:
                return false;
        }
    }

    // ========================================================================
    // Name resolution
    // ========================================================================

    private QName resolveQName(final String name, final String defaultNS) throws XPathException {
        try {
            return QName.parse(context, name, defaultNS);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(current.line, current.column, ErrorCodes.XPST0081,
                    "Invalid name: " + name + ". " + e.getMessage());
        }
    }

    private QName resolveElementName(final String name) throws XPathException {
        return resolveQName(name, context.getURIForPrefix(""));
    }

    // ========================================================================
    // Error reporting
    // ========================================================================

    /** Skips tokens until a semicolon is found and consumed. */
    private void skipToSemicolon() throws XPathException {
        while (!check(Token.SEMICOLON) && !check(Token.EOF)) advance();
        if (check(Token.SEMICOLON)) advance();
    }

    private XPathException error(final String message) {
        return new XPathException(current.line, current.column, ErrorCodes.XPST0003, message);
    }

    private XPathException errorWithSuggestion(final String message, final String suggestion) {
        return new XPathException(current.line, current.column, ErrorCodes.XPST0003,
                message + ". Did you mean '" + suggestion + "'?");
    }
}
