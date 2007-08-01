package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunRoundHalfToEven extends Function {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("round-half-to-even", Function.BUILTIN_FUNCTION_NS),
					"The first signature of this function produces the same " + 
					"result as the second signature with $b=0.",
					new SequenceType[] { 
						new SequenceType(Type.NUMBER, Cardinality.ZERO_OR_ONE) }, 
					new SequenceType(Type.NUMBER, Cardinality.EXACTLY_ONE)),
			
			new FunctionSignature(new QName("round-half-to-even",
					Function.BUILTIN_FUNCTION_NS),
					"The value returned is the nearest (that is, numerically closest) " +
					"numeric to $a that is a multiple of ten to the power of minus "+
					"$b. If two such values are equally near (e.g. if the "+
					"fractional part in $a is exactly .500...), returns the one whose "+
					"least significant digit is even.",
					new SequenceType[] { 
						new SequenceType( Type.NUMBER, Cardinality.ZERO_OR_ONE ),
						new SequenceType( Type.NUMBER, Cardinality.ZERO_OR_ONE ) }, 
						new SequenceType( Type.NUMBER, Cardinality.EXACTLY_ONE) ) 
	};

	public FunRoundHalfToEven(XQueryContext context,
			FunctionSignature signatures) {
		super(context, signatures);
	}

	public int returnsType() {
		return Type.DOUBLE;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }               
        
        Sequence result;
		IntegerValue precision = new IntegerValue(0);
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
        else {		
            if (contextItem != null) 
    			contextSequence = contextItem.toSequence();
            
    		if (getSignature().getArgumentCount() > 1) {
    			precision = (IntegerValue) getArgument(1).eval(contextSequence, contextItem).itemAt(0).convertTo(Type.INTEGER);
    		}
            
    		NumericValue value = (NumericValue) seq.itemAt(0).convertTo(Type.NUMBER);
    		return value.round(precision);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;           
	}
}
