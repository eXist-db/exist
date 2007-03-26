package org.exist.xquery.functions.util;

import java.io.StringReader;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

public class Compile extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("compile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically evaluates the XPath/XQuery expression specified in $a within " +
			"the current instance of the query engine.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	public Compile(XQueryContext context) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// get the query expression
		String expr = args[0].getStringValue();
		if ("".equals(expr.trim()))
		  return new EmptySequence();
		context.pushNamespaceContext();
		LOG.debug("eval: " + expr);
		XQueryLexer lexer = new XQueryLexer(context, new StringReader(expr));
		XQueryParser parser = new XQueryParser(lexer);
		// shares the context of the outer expression
		XQueryTreeParser astParser = new XQueryTreeParser(context);
		try {
		    parser.xpath();
			if(parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new XPathException("error found while executing expression: " +
					parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			
			PathExpr path = new PathExpr(context);
			astParser.xpath(ast, path);
			if(astParser.foundErrors()) {
				throw new XPathException("error found while executing expression: " +
						astParser.getErrorMessage(), astParser.getLastException());
			}
			path.analyze(new AnalyzeContextInfo());
		} catch (RecognitionException e) {			
			return new StringValue(e.toString());
		} catch (TokenStreamException e) {
			return new StringValue(e.toString());
		} catch (XPathException e) {
			return new StringValue(e.toString());
		} catch (Exception e) {
			return new StringValue(e.getMessage());
		} finally {
			context.popNamespaceContext();
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
