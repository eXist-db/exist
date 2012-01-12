/*
 * Exist Open Source Native XML Database
 * Copyright (C) 2000-2011 The eXist Project
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
 * base sur 4568, modif boris GB 
 *  $Id$
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
	import java.util.Stack;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.Namespaces;
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.util.XMLChar;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.fn.*;
	import org.exist.xquery.update.*;
	import org.exist.storage.ElementValue;
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
	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;
	protected Map declaredNamespaces = new HashMap();
	protected Set declaredGlobalVars = new TreeSet();

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
		StringBuffer buf= new StringBuffer();
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
		String varName;
		SequenceType sequenceType= null;
		String posVar= null;
		Expression inputSequence;
		Expression action;
		boolean isForClause= true;
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
		RootNode root = new RootNode(context);
		p.add(root);
		Function fun= new FunId(context, FunId.signature[0]);
		List params= new ArrayList(1);
		params.add(new LiteralValue(context, new StringValue(nc.getText())));
		fun.setArguments(params);
		p.addPath(fun);
		path.add(p);
	}
	;
//	exception catch [RecognitionException e]
//	{ handleException(e); }
	exception catch [EXistException e]
	{ handleException(e); }
	catch [PermissionDeniedException e]
	{ handleException(e); }
//	catch [XPathException e]
//	{ handleException(e); }

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
//	catch [XPathException e]
//	{ handleException(e); }


module [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
    ( 
        #(
            v:VERSION_DECL
            {
                if (v.getText().equals("3.0")) {
                    context.setXQueryVersion(30);
                } else if (v.getText().equals("1.0")) {
                    context.setXQueryVersion(10);
                } else {
                    throw new XPathException(v, "err:XQST0031: Wrong XQuery version: require 1.0 or 3.0");
                }
            }
            ( enc:STRING_LITERAL )?
            {
                if (enc != null) {
                    if (!XMLChar.isValidIANAEncoding(enc.getText())) {
                        throw new XPathException(enc, "err:XQST0087: Unknown or wrong encoding not adhering to required XML 1.0 EncName.");
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
                if (myModule == null)
                    myModule = new ExternalModuleImpl(uri.getText(), m.getText());
                else
                    myModule.setNamespace(m.getText(), uri.getText());
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

}:
	(
		#(
			prefix:NAMESPACE_DECL uri:STRING_LITERAL
			{ 
				if (declaredNamespaces.get(prefix.getText()) != null)
					throw new XPathException(prefix, "err:XQST0033: Prolog contains " +
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
					throw new XPathException("err:XQST0068: Boundary-space already declared.");
                boundaryspace = true;
                context.setStripWhitespace(false);
                }
				|
				"strip" 
                {
                if (boundaryspace)
					throw new XPathException("err:XQST0068: Boundary-space already declared.");
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
                    throw new XPathException("err:XQST0065: Ordering mode already declared.");
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
                    throw new XPathException("err:XQST0055: Copy-namespaces mode already declared.");
                copynamespaces = true;
            }
		)
            exception catch [RecognitionException se]
        {throw new XPathException("err:XPST0003: XQuery syntax error.");}
		|
		#(
			"base-uri" base:STRING_LITERAL
			{ 
                context.setBaseURI(new AnyURIValue(StringValue.expand(base.getText())), true);
                if (baseuri)
                    throw new XPathException(base, "err:XQST0032: Base URI is already declared.");
                baseuri = true;
            }
		)
		|
		#(
			"ordering" ( "ordered" | "unordered" )	
            {
                // ignored
                if (ordering)
                    throw new XPathException("err:XQST0065: Ordering already declared.");
                ordering = true;
            }
		)
		|
		#(
			"construction" ( "preserve" | "strip" )	// ignored
            {
                // ignored
                if (construction)
                    throw new XPathException("err:XQST0069: Construction already declared.");
                construction = true;
            }
		)
		|
		#(
			DEF_NAMESPACE_DECL defu:STRING_LITERAL
            { // Use setDefaultElementNamespace()
                context.declareNamespace("", defu.getText());
                staticContext.declareNamespace("",defu.getText());
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
                    throw new XPathException("err:XQST0038: Default collation already declared.");
                defaultcollation = true;
                try {
                    context.setDefaultCollation(defc.getText());
                } catch (XPathException xp) {
                    throw new XPathException(defc, "err:XQST0038: the value specified by a default collation declaration is not present in statically known collations.");
                }
            }
		)
		|
		#(
			qname:GLOBAL_VAR
			{
				PathExpr enclosed= new PathExpr(context);
				SequenceType type= null;
				QName qn = QName.parse(staticContext, qname.getText());
				if (declaredGlobalVars.contains(qn))
					throw new XPathException(qname, "err:XQST0049: It is a " +
						"static error if more than one variable declared or " +
						"imported by a module has the same expanded QName. " +
						"Variable: " + qn.toString());
				declaredGlobalVars.add(qn);
			}
                        { List annots = new ArrayList(); }
                        (annotations [annots]
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
					VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
					decl.setSequenceType(type);
					decl.setASTNode(e);
					path.add(decl);
					if(myModule != null) {
						myModule.declareVariable(qn, decl);
					}
				}
				|
				"external"
				{
				    Variable decl = null;
					boolean isDeclared = false;
					try {
						decl = context.resolveVariable(qname.getText());
						isDeclared = (decl != null);
					} catch (XPathException ignoredException) {
					}
					
					if (!isDeclared)
                        decl = context.declareVariable(qname.getText(), null);
                        
                    if (decl != null)                        
                        decl.setSequenceType(type);
				}
			)
		)
		|
		#(
			qname2:OPTION
			content:STRING_LITERAL
			{
				context.addOption(qname2.getText(), content.getText());
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
			String location = null;
            List uriList= new ArrayList(2);
		}
		( pfx:NCNAME { modulePrefix = pfx.getText(); } )? 
		moduleURI:STRING_LITERAL 
		( uriList [uriList] )?
		{
			if (modulePrefix != null) {
				if (declaredNamespaces.get(modulePrefix) != null)
					throw new XPathException(i, "err:XQST0033: Prolog contains " +
						"multiple declarations for namespace prefix: " + modulePrefix);
				declaredNamespaces.put(modulePrefix, moduleURI.getText());
			}
            try {
                if (uriList.size() > 0) {
			    for (Iterator j= uriList.iterator(); j.hasNext();) {
                   try {
                        location= ((AnyURIValue) j.next()).getStringValue();
                       context.importModule(moduleURI.getText(), modulePrefix, location);
                        staticContext.declareNamespace(modulePrefix, moduleURI.getText());
                    } catch(XPathException xpe) {
                        if (!j.hasNext()) {
                            throw xpe;
                        }
                    }
                }
                } else {
                    context.importModule(moduleURI.getText(), modulePrefix, location);
                    staticContext.declareNamespace(modulePrefix, moduleURI.getText());
                }
            } catch(XPathException xpe) {
                xpe.prependMessage("error found while loading module " + modulePrefix + ": ");
                throw xpe;
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
                    throw new XPathException(s, "err:XQST0057: A schema without target namespace (zero-length string target namespace) may not bind a namespace prefix: " + nsPrefix);
            }
            if (nsPrefix != null) {
                if (declaredNamespaces.get(nsPrefix) != null)
                    throw new XPathException(s, "err:XQST0033: Prolog contains " +
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
            //throw new XPathException(s, "err:XQST0009: the eXist XQuery implementation does not support the Schema Import Feature quite yet.");
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
                    QName qn= null;
                    try {
                        qn = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
                    } catch(XPathException e) {
                        // throw exception with correct source location
                        e.setLocation(name.getLine(), name.getColumn());
                        throw e;
                    }
                    
                    String ns = qn.getNamespaceURI();

                    //TODO add in handling for %private and %public in the fn namespace
                    if(
                        ns.equals(Namespaces.XML_NS)
                        || ns.equals(Namespaces.SCHEMA_NS)
                        || ns.equals(Namespaces.SCHEMA_INSTANCE_NS)
                        || ns.equals(Namespaces.XPATH_FUNCTIONS_NS)
                        || ns.equals(Namespaces.XPATH_FUNCTIONS_MATH_NS)
                        || ns.equals(Namespaces.XQUERY_OPTIONS_NS)
                    ) {
                        throw new XPathException(ErrorCodes.XQST0045, name.getLine(), name.getColumn());
                    }

                    annotList.add(qn);
                }
                ( { PathExpr annotPath = new PathExpr(context); }
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
 * Parse a declared function.
 */
functionDecl [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
	#(
		name:FUNCTION_DECL { PathExpr body= new PathExpr(context); }
		{
			QName qn= null;
			try {
				qn = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
			} catch(XPathException e) {
				// throw exception with correct source location
				e.setLocation(name.getLine(), name.getColumn());
				throw e;
			}
			FunctionSignature signature= new FunctionSignature(qn);
			UserDefinedFunction func= new UserDefinedFunction(context, signature);
			func.setASTNode(name);
			List varList= new ArrayList(3);
		}
                { List annots = new ArrayList(); }
                (annotations [annots] 
                 { int i, j; 
                   System.out.println("annotations nb: " + annots.size());
                   for (i = 0; i < annots.size(); i++)
                   { PathExpr annotPath = null;
                     System.out.println("annotation name: " + ((List)annots.get(i)).get(0).toString());
                     if (((List)annots.get(i)).size() > 1)
                     {
                       annotPath = (PathExpr)((List)annots.get(i)).get(1);
                       for (j = 0; j < annotPath.getLength(); j++) {
                         Expression value = annotPath.getExpression(j);
                         System.out.println("literal expr id: " + value.getExpressionId());
                       }
                     }
                   }
                  }
                )?
		( paramList [varList] )?
		{
			SequenceType[] types= new SequenceType[varList.size()];
			int j= 0;
			for (Iterator i= varList.iterator(); i.hasNext(); j++) {
				FunctionParameterSequenceType param= (FunctionParameterSequenceType) i.next();
				types[j]= param;
				func.addVariable(param.getAttributeName());
			}
			signature.setArgumentTypes(types);
		}
		(
			#(
				"as"
				{ SequenceType type= new SequenceType(); }
				sequenceType [type]
				{ signature.setReturnType(type); }
			)
		)?
		(
			// the function body:
			#(
				LCURLY step=expr [body]
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
 * Parse params in function declaration.
 */
paramList [List vars]
throws XPathException
:
	param [vars] ( param [vars] )*
	;

/**
 * Single function param.
 */
param [List vars]
throws XPathException
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
				sequenceType [var]
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
catchErrorList [List catchErrors]
throws XPathException
:
    catchError [catchErrors] ( catchError [catchErrors] )*
	;

/**
 * Single catchError.
 */
catchError [List catchErrors]
throws XPathException
:
	(
		#(wc:WILDCARD
		{
			catchErrors.add(wc.toString());
		}
        )
        |
        #(qn:QNAME
        {
			catchErrors.add(qn.toString());
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
				QName qn= QName.parse(staticContext, t.getText());
				int code= Type.getType(qn);
				if(!Type.subTypeOf(code, Type.ATOMIC))
					throw new XPathException(t, "Type " + qn.toString() + " is not an atomic type");
				type.setPrimaryType(code);
			}
		)
		|
		#(
			"empty"
			{
				type.setPrimaryType(Type.EMPTY);
				type.setCardinality(Cardinality.EMPTY);
			}
		)
		|
		#(
			"empty-sequence"
			{
				type.setPrimaryType(Type.EMPTY);
				type.setCardinality(Cardinality.EMPTY);
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
			"element" 
			{ type.setPrimaryType(Type.ELEMENT); }
			(
				WILDCARD
				|
				qn1:QNAME
				{ 
					QName qname= QName.parse(staticContext, qn1.getText());
					type.setNodeName(qname);
				}
				( qn12:QNAME
					{
                        QName qname12= QName.parse(staticContext, qn12.getText());
                        TypeTest test = new TypeTest(Type.getType(qname12));
					}
				)?
			)?
		)
		|
		#(
			ATTRIBUTE_TEST 
			{ type.setPrimaryType(Type.ATTRIBUTE); }
			(
				qn2:QNAME
				{
                    QName qname= QName.parse(staticContext, qn2.getText(), "");
                    qname.setNameType(ElementValue.ATTRIBUTE);
					type.setNodeName(qname);
				}
				|
				WILDCARD
				( qn21:QNAME
					{
                        QName qname21= QName.parse(staticContext, qn21.getText());
                        TypeTest test = new TypeTest(Type.getType(qname21));
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
                QName qname= new QName(value, "", null);
                qname.setNamespaceURI(null);
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
			"document-node"
            { type.setPrimaryType(Type.DOCUMENT); }
            (
                #( "element"
                    (
                    dnqn:QNAME
                    {
					    QName qname= QName.parse(staticContext, dnqn.getText());
                        type.setNodeName(qname);
                        NameTest test= new NameTest(Type.DOCUMENT, qname);
                    }
                    | 
                    WILDCARD
                    {
                        TypeTest test= new TypeTest(Type.DOCUMENT);
                    }
                        ( dnqn2:QNAME
                            {
                            QName qname = QName.parse(staticContext, dnqn2.getText());
                            test = new TypeTest(Type.getType(qname));
                            }
                        )?
                    )?
                )
                |
                #( "schema-element" QNAME )
            )?
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
                throw new XPathException(eof, "err:XPST0003: EOF or zero-length string found where a valid XPath expression was expected.");     

        }
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
	   { PathExpr seqPath = new PathExpr(context); }
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
		{ ConcatExpr concat = new ConcatExpr(context); }
		(
			{ PathExpr strPath = new PathExpr(context); }
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
			PathExpr right= new PathExpr(context);
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
	// quantified expression: some
	#(
		"some"
		{
			List clauses= new ArrayList();
			PathExpr satisfiesExpr = new PathExpr(context);
		}
		(
			#(
				someVarName:VARIABLE_BINDING
				{
					ForLetClause clause= new ForLetClause();
					PathExpr inputSequence = new PathExpr(context);
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
					clause.varName= someVarName.getText();
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
		}
		(
			#(
				everyVarName:VARIABLE_BINDING
				{
					ForLetClause clause= new ForLetClause();
					PathExpr inputSequence = new PathExpr(context);
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
					clause.varName= everyVarName.getText();
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
		}
		step=expr [tryTargetExpr]
        {
  	    	TryCatchExpression cond = new TryCatchExpression(context, tryTargetExpr);
			cond.setASTNode(astTry);
            path.add(cond);
        }
        (
			{
				List<String> catchErrorList = new ArrayList<String>(2);
                List<QName> catchVars = new ArrayList<QName>(3);
				PathExpr catchExpr = new PathExpr(context);
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
                        qncode = QName.parse(staticContext, code.getText());
                        catchVars.add(qncode);
                    }     
                    (
                        desc:CATCH_ERROR_DESC
                        {
                            qndesc = QName.parse(staticContext, desc.getText());
                            catchVars.add(qndesc);
                        }

                        (
                            val:CATCH_ERROR_VAL
                            {
                                qnval = QName.parse(staticContext, val.getText());
                                catchVars.add(qnval);
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
		)+

		{
			step = cond;
		}
	)
	|
	// FLWOR expressions: let and for
	#(
		r:"return"
		{
			List clauses= new ArrayList();
			Expression action= new PathExpr(context);
			action.setASTNode(r);
			PathExpr whereExpr= null;
			List orderBy= null;
            //bv : variables for groupBy 
            List groupBy= null; 
            String toGroupVar = null; 
            String groupVar = null; 
            String groupKeyVar = null; 			
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
						}
						(
							#(
								"as"
								{ clause.sequenceType= new SequenceType(); }
								sequenceType [clause.sequenceType]
							)
						)?
						(
							posVar:POSITIONAL_VAR
							{ clause.posVar= posVar.getText(); }
						)?
						step=expr [inputSequence]
						{
							clause.varName= varName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
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
							clause.isForClause= false;
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
							clause.varName= letVarName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
					)
				)+
			)
		)+
		(
			w:"where"
			{ 
				whereExpr= new PathExpr(context); 
				whereExpr.setASTNode(w);
			}
			step=expr [whereExpr]
		)?
        //bv : group by clause 
		(
			#(
                GROUP_BY { groupBy= new ArrayList(3); } 
                ( 
                    #( toGroupVarName:VARIABLE_REF 
                      { toGroupVar= toGroupVarName.getText(); } 
                    ) 
                )!                 
                ( 
                    #( groupVarName:VARIABLE_BINDING 
                      { groupVar= groupVarName.getText(); } 
                    ) 
                )! 
                (             
                    { PathExpr groupSpecExpr= new PathExpr(context); } 
                    step=expr [groupSpecExpr] 
                    { 
                    } 
                    #( groupKeyVarName:VARIABLE_BINDING 
                      { groupKeyVar = groupKeyVarName.getText(); 
                        GroupSpec groupSpec= new GroupSpec(context, groupSpecExpr, groupKeyVar); 
                        groupBy.add(groupSpec); 
                      } 
                    ) 
                )+ 
            ) 
        )? 
         
        ( 
            #( 			
				ORDER_BY { orderBy= new ArrayList(3); }
				(
					{ PathExpr orderSpecExpr= new PathExpr(context); }
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
			)
		)?
		step=expr [(PathExpr) action]
		{
            //bv : save the "real" return expression (used in groupBy) 
            PathExpr groupReturnExpr = (PathExpr) action; 
			for (int i= clauses.size() - 1; i >= 0; i--) {
				ForLetClause clause= (ForLetClause) clauses.get(i);
				BindingExpression expr;
				if (clause.isForClause)
					expr= new ForExpr(context);
				else
					expr= new LetExpr(context);
				expr.setASTNode(clause.ast);
				expr.setVariable(clause.varName);
				expr.setSequenceType(clause.sequenceType);
				expr.setInputSequence(clause.inputSequence);
                if (!(action instanceof BindingExpression))
                    expr.setReturnExpression(new DebuggableExpression(action));
                else
                    expr.setReturnExpression(action);
				if (clause.isForClause)
					 ((ForExpr) expr).setPositionalVariable(clause.posVar);
				if (whereExpr != null) {
					expr.setWhereExpression(new DebuggableExpression(whereExpr));
					whereExpr= null;
				}
				action= expr;
			}
			if (orderBy != null) {
				OrderSpec orderSpecs[]= new OrderSpec[orderBy.size()];
				int k= 0;
				for (Iterator j= orderBy.iterator(); j.hasNext(); k++) {
					OrderSpec orderSpec= (OrderSpec) j.next();
					orderSpecs[k]= orderSpec;
				}
				((BindingExpression)action).setOrderSpecs(orderSpecs);
			}
            // bv : group by initialisation 
            if (groupBy != null) { 
                GroupSpec groupSpecs[]= new GroupSpec[groupBy.size()]; 
                int k= 0; 
                for (Iterator j= groupBy.iterator(); j.hasNext(); k++) { 
                    GroupSpec groupSpec= (GroupSpec) j.next(); 
                    groupSpecs[k]= groupSpec; 
                } 
                ((BindingExpression)action).setGroupSpecs(groupSpecs); 
                ((BindingExpression)action).setGroupVariable(groupVar); 
                ((BindingExpression)action).setGroupReturnExpr(groupReturnExpr); 
                ((BindingExpression)action).setToGroupVariable(toGroupVar); 
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
			SequenceType type= new SequenceType(); 
		}
		step=expr [expr]
		sequenceType [type]
		{ 
			step = new InstanceOfExpression(context, expr, type); 
			path.add(step);
		}
	)
	|
	// treat as:
	#(
		"treat"
		{ 
			PathExpr expr = new PathExpr(context);
			SequenceType type= new SequenceType(); 
		}
		step=expr [expr]
		sequenceType [type]
		{ 
			step = new TreatAsExpression(context, expr, type); 
			path.add(step);
		}
	)
	|
	// switch
	#(
		switchAST:"switch"
		{
			PathExpr operand = new PathExpr(context);
		}
		step=expr [operand]
		{ 
			SwitchExpression switchExpr = new SwitchExpression(context, operand);
            switchExpr.setASTNode(switchAST);
			path.add(switchExpr); 
		}
		(
			{
				List caseOperands = new ArrayList<Expression>(2);
				PathExpr returnExpr = new PathExpr(context);
			}
             ((
               { PathExpr caseOperand = new PathExpr(context); }
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
		}
		step=expr [operand]
		{ 
			TypeswitchExpression tswitch = new TypeswitchExpression(context, operand);
			path.add(tswitch); 
		}
		(
			{
				SequenceType type = new SequenceType();
				PathExpr returnExpr = new PathExpr(context);
				QName qn = null;
			}
			#(
				"case"                     
				(
					var:VARIABLE_BINDING
					{ qn = QName.parse(staticContext, var.getText()); }
				)?
                sequenceType [type]
                // Need return as root in following to disambiguate 
                // e.g. ( case a xs:integer ( * 3 3 ) )
                // which gives xs:integer* and no operator left for 3 3 ...
                // Now ( case a xs:integer ( return ( + 3 3 ) ) ) /ljo
                #( "return"
		        step= expr [returnExpr]
				{ tswitch.addCase(type, qn, returnExpr); }
                )
			)

		)+
        (
			"default"
			{ 
				PathExpr returnExpr = new PathExpr(context);
				QName qn = null;
			}
			(
				dvar:VARIABLE_BINDING
				{ qn = QName.parse(staticContext, dvar.getText()); }
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
		{ PathExpr left= new PathExpr(context);	}
		step=expr [left]
		{ PathExpr right= new PathExpr(context); } 
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
			PathExpr right= new PathExpr(context);
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
			PathExpr right= new PathExpr(context);
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
			PathExpr right = new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		Intersection intersect = new Intersection(context, left, right);
		path.add(intersect);
		step = intersect;
	}
	|
	#( "except"
		{
			PathExpr left = new PathExpr(context);
			PathExpr right = new PathExpr(context);
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
			RootNode root= new RootNode(context);
			path.add(root);
		}
		(
			step=expr [path]
			{
				if (step instanceof LocationStep) {
					LocationStep s= (LocationStep) step;
					if (s.getAxis() == Constants.ATTRIBUTE_AXIS ||
						s.getTest().getType() == Type.ATTRIBUTE)
						// combines descendant-or-self::node()/attribute:*
						s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else {
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
			PathExpr end= new PathExpr(context);
			List args= new ArrayList(2);
			args.add(start);
			args.add(end);
		}
		step=expr [start]
		step=expr [end]
		{
			RangeExpression range= new RangeExpression(context);
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
	step=fulltextComp [path]
	|
	step=primaryExpr [path]
	|
	step=pathExpr [path]
	|
	step=extensionExpr [path]
	|
	step=numericExpr [path]
	|
	step=updateExpr [path]
	;

/**
 * Process a primary expression like function calls,
 * variable references, value constructors etc.
 */
primaryExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step = null;
}:
	step=constructor [path]
	step=predicates [step]
	{
		path.add(step);
	}
	|
	#(
		PARENTHESIZED
		{ PathExpr pathExpr= new PathExpr(context); }
		( step=expr [pathExpr] )?
	)
	step=predicates [pathExpr]
	{ path.add(step); }
	|
	step=literalExpr [path]
	step=predicates [step]
	{ path.add(step); }
	|
	v:VARIABLE_REF
	{ 
        step= new VariableReference(context, v.getText());
        step.setASTNode(v);
    }
	step=predicates [step]
	{ path.add(step); }
	|
	step=functionCall [path]
	step=predicates [step]
	{ path.add(step); }
	;
	
pathExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	Expression rightStep= null;
	step= null;
	int axis= Constants.CHILD_AXIS;
}
:
	( axis=forwardAxis )?
	{ NodeTest test; }
	(
		qn:QNAME
		{
			QName qname= QName.parse(staticContext, qn.getText());
			if (axis == Constants.ATTRIBUTE_AXIS) {
                qname.setNamespaceURI(null);
                test= new NameTest(Type.ATTRIBUTE, qname);
                qname.setNameType(ElementValue.ATTRIBUTE);
            } else {
                test= new NameTest(Type.ELEMENT, qname);
            }
		}
		|
		#( PREFIX_WILDCARD nc1:NCNAME )
		{
			QName qname= new QName(nc1.getText(), null, null);
			qname.setNamespaceURI(null);
			test= new NameTest(Type.ELEMENT, qname);
			if (axis == Constants.ATTRIBUTE_AXIS)
				test.setType(Type.ATTRIBUTE);
		}
		|
		#( nc:NCNAME WILDCARD )
		{
			String namespaceURI= staticContext.getURIForPrefix(nc.getText());
			QName qname= new QName(null, namespaceURI, nc.getText());
			test= new NameTest(Type.ELEMENT, qname);
			if (axis == Constants.ATTRIBUTE_AXIS)
				test.setType(Type.ATTRIBUTE);
		}
		|
		WILDCARD
		{ 
			if (axis == Constants.ATTRIBUTE_AXIS)
				test= new TypeTest(Type.ATTRIBUTE);
			else
				test= new TypeTest(Type.ELEMENT);
		}
		|
		n:"node"
		{
			if (axis == Constants.ATTRIBUTE_AXIS) {
			//	throw new XPathException(n, "Cannot test for node() on the attribute axis");
			   test= new TypeTest(Type.ATTRIBUTE);
                        } else {
			   test= new AnyNodeTest(); 
                        }
		}
		|
		"text"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for text() on the attribute axis"); 
			test= new TypeTest(Type.TEXT); 
		}
		|
		#( "element"
			{
				if (axis == Constants.ATTRIBUTE_AXIS)
					throw new XPathException(n, "Cannot test for element() on the attribute axis"); 
				test= new TypeTest(Type.ELEMENT); 
			}
			(
				qn2:QNAME 
				{ 
					QName qname= QName.parse(staticContext, qn2.getText());
					test= new NameTest(Type.ELEMENT, qname);
				}
				|
				WILDCARD
				( qn21:QNAME
					{
                        QName qname= QName.parse(staticContext, qn21.getText());
                        test = new TypeTest(Type.getType(qname));
					}
				)?
			)?
		)
		|
		#( ATTRIBUTE_TEST
			{ test= new TypeTest(Type.ATTRIBUTE); }
			(
				qn3:QNAME 
				{ 
					QName qname= QName.parse(staticContext, qn3.getText());
					test= new NameTest(Type.ATTRIBUTE, qname);
					qname.setNameType(ElementValue.ATTRIBUTE);
					axis= Constants.ATTRIBUTE_AXIS;
				}
				|
				WILDCARD
				( qn31:QNAME
					{
                        QName qname= QName.parse(staticContext, qn31.getText());
                        test = new TypeTest(Type.getType(qname));
					}
				)?
			)?
		)
		|
		"comment"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for comment() on the attribute axis");
			test= new TypeTest(Type.COMMENT); 
		}
		|
		#( "processing-instruction"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for processing-instruction() on the attribute axis");
			test= new TypeTest(Type.PROCESSING_INSTRUCTION); 
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
		"document-node"
		{ test= new TypeTest(Type.DOCUMENT); }
            (
                #( "element"
                    (
                    dnqn:QNAME
                        {
                        QName qname= QName.parse(staticContext, dnqn.getText());
                        test= new NameTest(Type.DOCUMENT, qname);
                        }
                    |
                    WILDCARD
                        ( dnqn1:QNAME
                            {
                            QName qname= QName.parse(staticContext, dnqn1.getText());
                            test= new TypeTest(Type.getType(qname));
                            }
                        )?
                    )?
                )
                |
                #( "schema-element" QNAME )
            )?
	)
	{
		step= new LocationStep(context, axis, test);
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	AT
	{ QName qname= null; }
	(
		attr:QNAME
		{
          qname= QName.parse(staticContext, attr.getText(), "");
          qname.setNameType(ElementValue.ATTRIBUTE);
        }
		|
		#( PREFIX_WILDCARD nc2:NCNAME )
		{ 
          qname= new QName(nc2.getText(), null, null);
          qname.setNamespaceURI(null);
          qname.setNameType(ElementValue.ATTRIBUTE);
		}
		|
		#( nc3:NCNAME WILDCARD )
		{
			String namespaceURI= staticContext.getURIForPrefix(nc3.getText());
			if (namespaceURI == null)
				throw new EXistException("No namespace defined for prefix " + nc3.getText());
			qname= new QName(null, namespaceURI, null);
			qname.setNameType(ElementValue.ATTRIBUTE);
		}
		|
		WILDCARD
	)
	{
		NodeTest test= qname == null ? new TypeTest(Type.ATTRIBUTE) : new NameTest(Type.ATTRIBUTE, qname);
		step= new LocationStep(context, Constants.ATTRIBUTE_AXIS, test);
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	SELF
	{
		step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
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
		SLASH step=expr [path]
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
		(
			rightStep=expr [path]
			{
				if (rightStep instanceof LocationStep) {
					LocationStep rs= (LocationStep) rightStep;
					if (rs.getAxis() == Constants.ATTRIBUTE_AXIS || 
						rs.getTest().getType() == Type.ATTRIBUTE) {
						rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					} else if (rs.getAxis() == Constants.CHILD_AXIS && rs.getTest().isWildcardTest()) {
						rs.setAxis(Constants.DESCENDANT_AXIS);
					} else if (rs.getAxis() == Constants.SELF_AXIS) {
						rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
					} else {
						rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
						rs.setAbbreviated(true);
					}

				} else {
					rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
					if(rightStep instanceof VariableReference) {
						rightStep = new SimpleStep(context, Constants.DESCENDANT_SELF_AXIS, rightStep);
						path.replaceLastExpression(rightStep);
					} else if (rightStep instanceof FilteredExpression)
						((FilteredExpression)rightStep).setAbbreviated(true);

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
        step= new LiteralValue(context, new IntegerValue(i.getText()));
        step.setASTNode(i);
    }
	|
	(
		dec:DECIMAL_LITERAL
		{ 
            step= new LiteralValue(context, new DecimalValue(dec.getText()));
            step.setASTNode(dec);
        }
		|
		dbl:DOUBLE_LITERAL
		{ 
            step= new LiteralValue(context, 
                new DoubleValue(Double.parseDouble(dbl.getText())));
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
	PathExpr right= new PathExpr(context);
}
:
	#( plus:PLUS step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
        op.setASTNode(plus);
		path.addPath(op);
		step= op;
	}
	|
	#( minus:MINUS step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
        op.setASTNode(minus);
		path.addPath(op);
		step= op;
	}
	|
	#( uminus:UNARY_MINUS step=expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
        unary.setASTNode(uminus);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( uplus:UNARY_PLUS step=expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
        unary.setASTNode(uplus);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( div:"div" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
        op.setASTNode(div);
		path.addPath(op);
		step= op;
	}
	|
	#( idiv:"idiv" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
        op.setASTNode(idiv);
		path.addPath(op);
		step= op;
	}
	|
	#( mod:"mod" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
        op.setASTNode(mod);
		path.addPath(op);
		step= op;
	}
	|
	#( mult:STAR step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MULT);
        op.setASTNode(mult);
		path.addPath(op);
		step= op;
	}
	;

predicates [Expression expression]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	FilteredExpression filter= null;
	step= expression;
}
:
	(
		#(
			PREDICATE
			{
				if (filter == null) {
					filter= new FilteredExpression(context, step);
					step= filter;
				}
				Predicate predicateExpr= new Predicate(context);
			}
			expr [predicateExpr]
			{
				filter.addPredicate(predicateExpr);
			}
		)
	)*
	;

predicate [LocationStep step]
throws PermissionDeniedException, EXistException, XPathException
:
	#(
		PREDICATE
		{ Predicate predicateExpr= new Predicate(context); }
		expr [predicateExpr]
		{ step.addPredicate(predicateExpr); }
	)
	;

functionCall [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	PathExpr pathExpr;
	step= null;
}
:
	#(
		fn:FUNCTION
		{ List params= new ArrayList(2); }
		(
			{ pathExpr= new PathExpr(context); }
			expr [pathExpr]
			{ params.add(pathExpr); }
		)*
	)
	{ step= FunctionFactory.createFunction(context, fn, path, params); }
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
	"following-sibling" { axis= Constants.FOLLOWING_SIBLING_AXIS; }
    |
    "following" { axis= Constants.FOLLOWING_AXIS; }
    |
	"preceding-sibling" { axis= Constants.PRECEDING_SIBLING_AXIS; }
    |
    "preceding" { axis= Constants.PRECEDING_AXIS; }
	|
	"ancestor" { axis= Constants.ANCESTOR_AXIS; }
	|
	"ancestor-or-self" { axis= Constants.ANCESTOR_SELF_AXIS; }
	;

fulltextComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr nodes= new PathExpr(context);
	PathExpr query= new PathExpr(context);
}
:
	#( ANDEQ step=expr [nodes] step=expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	|
	#( OREQ step=expr [nodes] step=expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_OR);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	;

valueComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#(
		eq:"eq" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.EQ);
            step.setASTNode(eq);
			path.add(step);
		}
	)
	|
	#(
		ne:"ne" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.NEQ);
            step.setASTNode(ne);
			path.add(step);
		}
	)
	|
	#(
		lt:"lt" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.LT);
            step.setASTNode(lt);
			path.add(step);
		}
	)
	|
	#(
		le:"le" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.LTEQ);
            step.setASTNode(le);
			path.add(step);
		}
	)
	|
	#(
		gt:"gt" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.GT);
            step.setASTNode(gt);
			path.add(step);
		}
	)
	|
	#(
		ge:"ge" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.GTEQ);
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
	PathExpr right= new PathExpr(context);
}
:
	#(
		eq:EQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.EQ);
            step.setASTNode(eq);
			path.add(step);
		}
	)
	|
	#(
		neq:NEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.NEQ);
            step.setASTNode(neq);
			path.add(step);
		}
	)
	|
	#(
		lt:LT step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LT);
            step.setASTNode(lt);
			path.add(step);
		}
	)
	|
	#(
		lteq:LTEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LTEQ);
            step.setASTNode(lteq);
			path.add(step);
		}
	)
	|
	#(
		gt:GT step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GT);
            step.setASTNode(gt);
			path.add(step);
		}
	)
	|
	#(
		gteq:GTEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GTEQ);
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
	PathExpr right= new PathExpr(context);
}
:
	#(
		is:"is" step=expr [left] step=expr [right]
		{
			step = new NodeComparison(context, left, right, Constants.IS);
            step.setASTNode(is);
			path.add(step);
		}
	)
	|
	#(
		isnot:"isnot" step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.ISNOT);
            step.setASTNode(isnot);
			path.add(step);
		}
	)
	|
	#(
		before:BEFORE step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.BEFORE);
            step.setASTNode(before);
			path.add(step);
		}
	)
	|
	#(
		after:AFTER step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.AFTER);
            step.setASTNode(after);
			path.add(step);
		}
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
			enclosed.addPath(construct);
			c.setContent(enclosed);
			PathExpr qnamePathExpr = new PathExpr(context);
			c.setNameExpr(qnamePathExpr);
		}
		
		qnameExpr=expr [qnamePathExpr]
		(
			#( prefix:COMP_NS_CONSTRUCTOR uri:STRING_LITERAL )
			{
				c.addNamespaceDecl(prefix.getText(), uri.getText());
			}
			|
			{ elementContent = new PathExpr(context); }
			contentExpr=expr[elementContent]
			{ construct.addPath(elementContent); }
		)*
	)
	|
	#(
		attr:COMP_ATTR_CONSTRUCTOR
		{
			DynamicAttributeConstructor a= new DynamicAttributeConstructor(context);
            a.setASTNode(attr);
            step = a;
            PathExpr qnamePathExpr = new PathExpr(context);
            a.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            a.setContentExpr(elementContent);
		}
		qnameExpr=qna:expr [qnamePathExpr]
        {
            QName qname = QName.parse(staticContext, qna.getText());
            if (Namespaces.XMLNS_NS.equals(qname.getNamespaceURI()) 
                || ("".equals(qname.getNamespaceURI()) && qname.getLocalName().equals("xmlns")))
                throw new XPathException("err:XQDY0044: the node-name property of the node constructed by a computed attribute constructor is in the namespace http://www.w3.org/2000/xmlns/ (corresponding to namespace prefix xmlns), or is in no namespace and has local name xmlns.");
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
            pd.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            pd.setContentExpr(elementContent);
		}
		qnameExpr=expr [qnamePathExpr]
        #( LCURLY
            (
                contentExpr=ex:expr [elementContent]
                {
                    if (ex.getText() != null && ex.getText().indexOf("?>") > Constants.STRING_NOT_FOUND)
                throw new XPathException("err:XQDY0026: content expression of a computed processing instruction constructor contains the string '?>' which is not allowed.");
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
						attrib.addValue(StringValue.expand(attrVal.getText())); 
					}
					|
					#(
						LCURLY { PathExpr enclosed= new PathExpr(context); }
						expr [enclosed]
						{ attrib.addEnclosedExpr(enclosed); }
					)
				)*
				{ c.addAttribute(attrib); 
                                  if (attrib.isNamespaceDeclaration()) {
                                     String nsPrefix = attrib.getQName().equals("xmlns") ?
                                        "" : QName.extractLocalName(attrib.getQName());
                                     staticContext.declareInScopeNamespace(nsPrefix,attrib.getLiteralValue());
                                  }

                                }
			)
		)*
		(
			{
				if (elementContent == null) {
					elementContent= new PathExpr(context);
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
			DynamicCommentConstructor comment = new DynamicCommentConstructor(context, elementContent);
			comment.setASTNode(t);
			step= comment;
		}
		contentExpr=expr [elementContent]
	)
	|
	#(
		d:COMP_DOC_CONSTRUCTOR
		{
			elementContent = new PathExpr(context);
			DocumentConstructor doc = new DocumentConstructor(context, elementContent);
			doc.setASTNode(d);
			step= doc;
		}
		contentExpr=expr [elementContent]
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
	
typeCastExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ 
	step= null;
	PathExpr expr= new PathExpr(context);
	int cardinality= Cardinality.EXACTLY_ONE;
}:
	#(
		castAST:"cast"
		step=expr [expr]
		t:ATOMIC_TYPE
		(
			QUESTION
			{ cardinality= Cardinality.ZERO_OR_ONE; }
		)?
		{
			QName qn= QName.parse(staticContext, t.getText());
			int code= Type.getType(qn);
			CastExpression castExpr= new CastExpression(context, expr, code, cardinality);
			castExpr.setASTNode(castAST);
			path.add(castExpr);
			step = castExpr;
		}
	)
	|
	#(
		castableAST:"castable"
		step=expr [expr]
		t2:ATOMIC_TYPE
		(
			QUESTION
			{ cardinality= Cardinality.ZERO_OR_ONE; }
		)?
		{
			QName qn= QName.parse(staticContext, t2.getText());
			int code= Type.getType(qn);
			CastableExpression castExpr= new CastableExpression(context, expr, code, cardinality);
			castExpr.setASTNode(castAST);
			path.add(castExpr);
			step = castExpr;
		}
	)
	;
	
extensionExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
	step = null;
	PathExpr pathExpr = new PathExpr(context);
	ExtensionExpression ext = null;
}:
	(
		#(
			p:PRAGMA
			( c:PRAGMA_END )?
			{
				Pragma pragma = context.getPragma(p.getText(), c.getText());
				if (pragma != null) {
					if (ext == null)
						ext = new ExtensionExpression(context);
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

updateExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
	#( updateAST:"update"
		{ 
			PathExpr p1 = new PathExpr(context);
			PathExpr p2 = new PathExpr(context);
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
