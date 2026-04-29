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
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Runtime-value check for untyped atomic values. Converts a value to the
 * required type if possible.
 * 
 * @author wolf
 */
public class UntypedValueCheck extends AbstractExpression {

	private final Expression expression;
	private final int requiredType;
	private final Error error;
    private final boolean atomize;
    
    public UntypedValueCheck(XQueryContext context, int requiredType) {
        this(context, requiredType, (Expression) null);
    }
    
    public UntypedValueCheck(XQueryContext context, int requiredType, final Expression expression) {
        this(context, requiredType, expression, new Error(Error.TYPE_MISMATCH));
    }
    
	public UntypedValueCheck(XQueryContext context, int requiredType, Error error) {
        this(context, requiredType, null, error);
    }
    
	public UntypedValueCheck(XQueryContext context, int requiredType, final Expression expression, Error error) {
		super(context);
		this.requiredType = requiredType;
        if (expression instanceof Atomize && !Type.subTypeOf(requiredType, Type.ANY_ATOMIC_TYPE)) {
            this.expression = ((Atomize)expression).getExpression();
            this.atomize = true;
        } else {
            this.expression = expression;
            this.atomize = false;
        }
        this.error = error;
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		final Sequence seq = expression.eval(contextSequence, contextItem);
        Sequence result = null;
        if (seq.hasOne()) {
            final Item item = convert(seq.itemAt(0));
            if (item != null)
                {result = item.toSequence();}
        } else {
            result = new ValueSequence();
            for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                //Type untyped values or... refine existing type
                item = convert(item);
                result.add(item);
            }
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        return result; 
	}

    private Item convert(Item item) throws XPathException {
        // XQ3.1: only coerce when target is a top-level NUMERIC member
        // (xs:decimal, xs:double, xs:float, xs:integer). Derived subtypes like
        // xs:positiveInteger were skipped, leaving items unrelabeled.
        // XQ4: coerce whenever both source and target are numeric subtypes,
        // letting convertTo() validate the value space (relabeling). This
        // fixes function-call-coercion tests like DynamicFunctionCall-080
        // while preserving XQ3.1 strict-typing behavior.
        final boolean numericCoerce = context.getXQueryVersion() >= 40
                ? (Type.subTypeOfUnion(requiredType, Type.NUMERIC)
                    && Type.subTypeOfUnion(item.getType(), Type.NUMERIC))
                : (Type.hasMember(Type.NUMERIC, requiredType)
                    && Type.subTypeOfUnion(item.getType(), Type.NUMERIC));
        if (atomize || item.getType() == Type.UNTYPED_ATOMIC || numericCoerce) {
            try {
                if (Type.subTypeOf(item.getType(), requiredType)) {
                    return item;
                }
                if (context.getXQueryVersion() < 40
                        && item.getType() == Type.INTEGER
                        && requiredType == Type.POSITIVE_INTEGER) {
                    // Preserve XQ3.1 strict rejection of integer →
                    // positiveInteger relabeling (eXist legacy).
                    throw new XPathException(this, ErrorCodes.FORG0001,
                            "cannot convert '"
                                    + Type.getTypeName(item.getType())
                                    + " ("
                                    + item.getStringValue()
                                    + ")' into "
                                    + Type.getTypeName(requiredType));
                }
                // Let convertTo() validate the value — IntegerValue.convertTo
                // checks the value is in the required type's value space and
                // throws FORG0001 if it is not (e.g. -5 cast to xs:positiveInteger).
                item = item.convertTo(requiredType);
            } catch (final XPathException e) {
                error.addArgs(ExpressionDumper.dump(expression), Type.getTypeName(requiredType),
                    Type.getTypeName(item.getType()));
                throw new XPathException(expression, e.getErrorCode(), error.toString());
            }
        }
        return item;
    }

    /* (non-Javadoc)
      * @see org.exist.xquery.Expression#preselect(org.exist.dom.persistent.DocumentSet, org.exist.xquery.StaticContext)
      */
	public DocumentSet preselect(DocumentSet in_docs)
		throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("untyped-value-check[");
        dumper.display(Type.getTypeName(requiredType));
        dumper.display(", ");        
        expression.dump(dumper);
        dumper.display("]");
    }
    
    public String toString() {
        return expression.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return requiredType;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return expression.getDependencies();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
	}
	
	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		expression.setContextDocSet(contextSet);
	}
	
	public void accept(ExpressionVisitor visitor) {
		expression.accept(visitor);
	}

    public void setASTNode(XQueryAST ast) {
        expression.setASTNode(ast);
    }

    public void setLocation(int line, int column) {
        expression.setLocation(line, column);
    }

    public int getLine() {
        return expression.getLine();
    }

    public int getColumn() {
        return expression.getColumn();
    }

    public int getSubExpressionCount() {
    	return 1;
    }
    
    public Expression getSubExpression(int index) {
    	if (index == 0) {return expression;}
    	
	    throw new IndexOutOfBoundsException("Index: "+index+", Size: "+getSubExpressionCount());
    }
}
