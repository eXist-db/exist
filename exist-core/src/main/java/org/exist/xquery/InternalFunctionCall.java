package org.exist.xquery;

import java.io.Writer;
import java.util.List;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
* Wrapper for internal modules in order to
* perform access control checks on internal
* module function calls.  It delegates to
* the wrapped <code>Function</code> for
* everything, but checks permission before
* delegating <code>eval</code>
*/
public class InternalFunctionCall extends Function
{
	private final Function function;
	
	public InternalFunctionCall(Function f)
	{
		super(f.getContext(), f.getSignature());
		this.function = f;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
        context.proceed(this);

        final long start = System.currentTimeMillis();
        if (context.getProfiler().traceFunctions())
            {context.getProfiler().traceFunctionStart(this);}
        
        context.stackEnter(this);
        try {
            return function.eval(contextSequence, contextItem);
        } catch (final XPathException e) {
            if (e.getLine() <= 0)
                {e.setLocation(line, column, getSource());}
            throw e;
        } finally {
            context.stackLeave(this);
            
            if (context.getProfiler().traceFunctions())
                {context.getProfiler().traceFunctionEnd(this, System.currentTimeMillis() - start);}
        }
    }

    public Function getFunction() {
        return function;
    }
    
	public int getArgumentCount()
	{
		return function.getArgumentCount();
	}
	public QName getName()
	{
		return function.getName();
	}
	public int returnsType()
	{
		return function.returnsType();
	}
	public int getCardinality()
	{
		return function.getCardinality();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException
	{
		contextInfo.setParent(this);
        try {
            function.analyze(contextInfo);
        } catch (final XPathException e) {
            if (e.getLine() <= 0)
                {e.setLocation(line, column, getSource());}
            throw e;
        }
    }
	
	public void setParent(Expression parent)
	{
		function.setParent(parent);
	}
	public Expression getParent()
	{
		return function.getParent();
	}
	
	public XQueryContext getContext()
	{
		return function.getContext();
	}

    public int getLine() {
        return function.getLine();
    }

    public int getColumn() {
        return function.getColumn();
    }

    public void setASTNode(XQueryAST ast) {
        function.setASTNode(ast);
    }

    public void setLocation(int line, int column) {
        function.setLocation(line, column);
    }

    public void add(Expression s)
	{
		function.add(s);
	}
	public void add(PathExpr path)
	{
		function.add(path);
	}
	public void addPath(PathExpr path)
	{
		function.addPath(path);
	}
	public void addPredicate(Predicate pred)
	{
		function.addPredicate(pred);
	}
	public void dump(ExpressionDumper dumper)
	{
		function.dump(dumper);
	}
	public void dump(Writer writer)
	{
		function.dump(writer);
	}
	public Expression getArgument(int pos)
	{
		return function.getArgument(pos);
	}
	public Sequence[] getArguments(Sequence contextSequence, Item contextItem) throws XPathException
	{
		return function.getArguments(contextSequence, contextItem);
	}
	public DocumentSet getContextDocSet()
	{
		return function.getContextDocSet();
	}
	public int getDependencies()
	{
		return function.getDependencies();
	}
	public DocumentSet getDocumentSet()
	{
		return function.getDocumentSet();
	}
	public Expression getExpression(int pos)
	{
		return function.getExpression(pos);
	}
	public Expression getLastExpression()
	{
		return function.getLastExpression();
	}
	public int getLength()
	{
		return function.getLength();
	}
	public String getLiteralValue()
	{
		return function.getLiteralValue();
	}
	public FunctionSignature getSignature()
	{
		return function.getSignature();
	}
	public boolean isCalledAs(String localName)
	{
		return function.isCalledAs(localName);
	}
	public void replaceLastExpression(Expression s)
	{
		function.replaceLastExpression(s);
	}
	public void reset()
	{
		function.reset();
	}
	public void resetState(boolean postOptimization)
	{
		function.resetState(postOptimization);
	}
	public void setArguments(List<Expression> arguments) throws XPathException
	{
		function.setArguments(arguments);
	}
	public void setContext(XQueryContext context)
	{
		function.setContext(context);
	}
	public void setContextDocSet(DocumentSet contextSet)
	{
		function.setContextDocSet(contextSet);
	}
	public String toString()
	{
		return function.toString();
	}

    public void accept(ExpressionVisitor visitor) {
        function.accept(visitor);
    }
}