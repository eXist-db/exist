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
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CastExpression represents cast expressions as well as all type 
 * constructors.
 * 
 * @author wolf
 */
public class CastExpression extends AbstractExpression {
    
    private Expression expression;
	private Cardinality cardinality;
	private final int requiredType;

    /**
	 * Constructor. When calling {@link #eval(Sequence, Item)} 
	 * the passed expression will be cast into the required type and cardinality.
	 * 
	 * @param context current context
     * @param expr expression to cast
     * @param requiredType the {@link Type} expected
     * @param cardinality the {@link Cardinality} expected
	 */
	public CastExpression(final XQueryContext context, final Expression expr, final int requiredType, final Cardinality cardinality) {
		super(context);
		this.requiredType = requiredType;
		this.cardinality = cardinality;
        setExpression(expr);
	}

	protected Expression getInnerExpression() {
		return expression;
	}

    public void setExpression(Expression expr) {
        this.expression = expr;
    }

	@Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
        contextInfo.setStaticReturnType(requiredType);
    }

    @Override
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
		//Should be handled by the parser
        if (requiredType == Type.ANY_ATOMIC_TYPE || (requiredType == Type.NOTATION && expression.returnsType() != Type.NOTATION)) {
			throw new XPathException(this, ErrorCodes.XPST0080, "cannot cast to " +
					Type.getTypeName(requiredType));
        }
        if (requiredType == Type.ANY_SIMPLE_TYPE || expression.returnsType() == Type.ANY_SIMPLE_TYPE || requiredType == Type.UNTYPED || expression.returnsType() == Type.UNTYPED) {
			throw new XPathException(this, ErrorCodes.XPST0051, "cannot cast to " +
					Type.getTypeName(requiredType));
        }

        Sequence result;
		final Sequence seq = Atomize.atomize(expression.eval(contextSequence, contextItem));
		if (seq.isEmpty()) {
			if (cardinality.atLeastOne())
				{throw new XPathException(this, "Type error: empty sequence is not allowed here");}
			else
                {result = Sequence.EMPTY_SEQUENCE;}
		} else {        
            final Item item = seq.itemAt(0);

            if (seq.hasMany() && Type.subTypeOf(requiredType, Type.ANY_ATOMIC_TYPE))
				{throw new XPathException(this, 
				        ErrorCodes.XPTY0004, 
				        "cardinality error: sequence with more than one item is not allowed here");}
            try {
                // casting to QName needs special treatment
                if(requiredType == Type.QNAME) {
                    if (item.getType() == Type.QNAME)
                        {result = item.toSequence();}
                    
                    else if(item.getType() == Type.ANY_ATOMIC_TYPE || Type.subTypeOf(item.getType(), Type.STRING)) {
                        result = new QNameValue(this, context, item.getStringValue());
                    
                    } else {
                        throw new XPathException(this, 
                            ErrorCodes.XPTY0004, 
                            "Cannot cast " + Type.getTypeName(item.getType()) + " to xs:QName");
                    }
                } else
                    {result = item.convertTo(requiredType);}
    		
            } catch(final XPathException e) {
                e.setLocation(e.getLine(), e.getColumn());
                throw e;
            }
        }

        if (context.getProfiler().isEnabled())           
            {context.getProfiler().end(this, "", result);}   
     
        return result;         
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(" cast as ");
        dumper.display(Type.getTypeName(requiredType));
    }
    
    public String toString() {
        return expression.toString() + " cast as " + Type.getTypeName(requiredType);
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
        return expression.getDependencies() | Dependency.CONTEXT_ITEM;
	}
	
	@Override
	public Cardinality getCardinality() {
		return Cardinality.ZERO_OR_ONE;
	}
	
	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		expression.setContextDocSet(contextSet);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
	}

	public void accept(ExpressionVisitor visitor) {
		visitor.visitCastExpr(this);
	}

    public Function toFunction() throws XPathException {
        final String typeName = Type.getTypeName(CastExpression.this.requiredType);
	    try {
            final QName qname = QName.parse(context, typeName);
            final FunctionSignature signature = new FunctionSignature(qname);
            final SequenceType argType = new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE);
            signature.setArgumentTypes(new SequenceType[]{argType});
            signature.setReturnType(new SequenceType(CastExpression.this.requiredType, CastExpression.this.cardinality));
            return new FunctionWrapper(this, signature);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + typeName);
        }
    }

    private static class FunctionWrapper extends Function {
        private final CastExpression castExpression;

        protected FunctionWrapper(final CastExpression castExpression, final FunctionSignature signature) throws XPathException {
            super(castExpression.getContext(), signature);
            final List<Expression> args = new ArrayList<>(1);
            args.add(new Function.Placeholder(castExpression.getContext()));
            super.setArguments(args);
            this.castExpression = castExpression;
        }

        @Override
        public void setArguments(final List<Expression> arguments) throws XPathException {
            castExpression.setExpression(arguments.get(0));
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            return castExpression.eval(contextSequence, null);
        }
    }
}
