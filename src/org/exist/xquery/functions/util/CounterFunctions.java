package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.storage.Counters;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 *
 */
public class CounterFunctions extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(CounterFunctions.class);

	public final static FunctionSignature createCounter =
		new FunctionSignature(
			new QName("create-counter", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Creates a unique counter by the name specified",
			new SequenceType[] {
				new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
			},
			new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, "the value of the newly created counter")
			);
	
	public final static FunctionSignature createCounterAndInit =
		new FunctionSignature(
			new QName("create-counter", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Creates a unique counter by the name specified and initializes it to 'init-value'.",
			new SequenceType[] {
				new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database."),
				new FunctionParameterSequenceType("init-value", Type.LONG, Cardinality.EXACTLY_ONE, "The initial value of the counter.")
			},
			new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, "the value of the newly created counter")
			);
	
	public final static FunctionSignature nextValue =
		new FunctionSignature(
			new QName("next-value", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Increments the specified counter and returns its' new value afterwards",
			new SequenceType[] {
				new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
			},
			new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, "the new value of the specified counter")
			);
	
	public final static FunctionSignature destroyCounter =
		new FunctionSignature(
			new QName("destroy-counter", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Destroys the specified counter",
			new SequenceType[] {
				new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "Boolean value containing true if removal succeeded. False if removal failed.")
			);
	
	public CounterFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
		String counterName = args[0].getStringValue();
		
		if (getSignature().equals(createCounter)) {
			try {
				if (counterName.contains(Counters.delimiter)) {
					throw new EXistException("Invalid name for counter, character '"+Counters.delimiter+"' is not allowed.");
				} else {
					return new IntegerValue(context.getBroker().getBrokerPool().getCounters().createCounter(counterName), Type.LONG);
				}
			} catch (EXistException e) {
				logger.error(e.getMessage());
				return Sequence.EMPTY_SEQUENCE;
			}
		} else if (getSignature().equals(createCounterAndInit)) {
			try {
				Long initValue = Long.parseLong(args[1].getStringValue());
				if (counterName.contains(Counters.delimiter)) {
					throw new EXistException("Invalid name for counter, character '"+Counters.delimiter+"' is not allowed.");
				} else {
					return new IntegerValue(context.getBroker().getBrokerPool().getCounters().createCounter(counterName,initValue), Type.LONG);
				}
			} catch (EXistException e) {
				logger.error(e.getMessage());
				return Sequence.EMPTY_SEQUENCE;
			}
		} else {
			try {
				if (getSignature().equals(destroyCounter)) {
					return new BooleanValue(context.getBroker().getBrokerPool().getCounters().destroyCounter(counterName));
				} else if (getSignature().equals(nextValue)) {
					context.getBroker().getBrokerPool().getCounters();
					return new IntegerValue(context.getBroker().getBrokerPool().getCounters().nextValue(counterName), Type.LONG);
				} else {
					return Sequence.EMPTY_SEQUENCE;
				}
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
				return Sequence.EMPTY_SEQUENCE;
			}
		}
    }
}
