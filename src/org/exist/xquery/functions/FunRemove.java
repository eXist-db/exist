package org.exist.xquery.functions;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:remove function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunRemove extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove", Function.BUILTIN_FUNCTION_NS),
			"Returns a new sequence constructed from the value of the target sequence" +
			"with the item at the position specified removed.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.INTEGER, Cardinality.ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));



	public FunRemove(XQueryContext context) {
		super(context, signature);
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
        Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty()) 
            result = Sequence.EMPTY_SEQUENCE;
        else {            
            //TODO : explain this Double conversion -pb
    		int pos = ((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
    		if (pos < 1 || pos > seq.getItemCount()) 
                result= seq;
            else {
        		pos--;
        		if (seq instanceof NodeSet) {
        			result = new ExtArrayNodeSet();
        			result.addAll((NodeSet) seq);
        			result = ((NodeSet)result).except((NodeSet) seq.itemAt(pos));
        		} else {
        			result = new ValueSequence();
        			for (int i = 0; i < seq.getItemCount(); i++) {
        				if (i != pos) result.add(seq.itemAt(i));
        			}        			
        		}
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;         
	}

	public int getDependencies() {
		return Dependency.NO_DEPENDENCY;
	}

}