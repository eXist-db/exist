package org.exist.xquery.modules.counters;

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
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

    private Counters counters = null;
    

    protected static final Logger logger = Logger.getLogger(CounterFunctions.class);
    
    public final static FunctionSignature createCounter =
            new FunctionSignature(
            new QName("create", CountersModule.NAMESPACE_URI, CountersModule.PREFIX),
            "Creates a unique counter by the name specified",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "The node or a string path pointing to a resource in the database.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
            "the value of the newly created counter"));

    public final static FunctionSignature createCounterAndInit =
            new FunctionSignature(
            new QName("create", CountersModule.NAMESPACE_URI, CountersModule.PREFIX),
            "Creates a unique counter by the name specified and initializes it to 'init-value'.",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, 
                        "The node or a string path pointing to a resource in the database."),

                new FunctionParameterSequenceType("init-value", Type.LONG, Cardinality.EXACTLY_ONE,
                        "The initial value of the counter.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
            "the value of the newly created counter"));

    public final static FunctionSignature nextValue =
            new FunctionSignature(
            new QName("next-value", CountersModule.NAMESPACE_URI, CountersModule.PREFIX),
            "Increments the specified counter and returns its' new value afterwards",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "The node or a string path pointing to a resource in the database.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
            "the new value of the specified counter"));

    public final static FunctionSignature destroyCounter =
            new FunctionSignature(
            new QName("destroy", CountersModule.NAMESPACE_URI, CountersModule.PREFIX),
            "Destroys the specified counter",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "The node or a string path pointing to a resource in the database.")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
            "Boolean value containing true if removal succeeded. False if removal failed."));

    public CounterFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);

        // Setup counters
        String counterDir = (String) context.getBroker().getConfiguration()
                                                        .getProperty(BrokerPool.PROPERTY_DATA_DIR);

        logger.debug("Counters directory: "+counterDir);

        // Get reference to counters object
        try {
            if(counters==null){
                counters = Counters.getInstance(counterDir);
            }

        } catch (EXistException ex) {
            logger.error("Unable to initialize counters. "+ex.getMessage(), ex);
        }
		
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        // Precondition check
        if(counters==null){
            throw new XPathException("Counters are not initialized.");
        }

        String counterName = args[0].getStringValue();

        if (getSignature().equals(createCounter)) {
            try {
                if (counterName.contains(Counters.DELIMITER)) {
                    throw new EXistException("Invalid name for counter, character '"
                            + Counters.DELIMITER + "' is not allowed.");

                } else {
                    return new IntegerValue(counters.createCounter(counterName), Type.LONG);
                }

            } catch (EXistException e) {
                logger.error(e.getMessage());
                return Sequence.EMPTY_SEQUENCE;
            }

        } else if (getSignature().equals(createCounterAndInit)) {
            try {
                Long initValue = Long.parseLong(args[1].getStringValue());
                if (counterName.contains(Counters.DELIMITER)) {
                    throw new EXistException("Invalid name for counter, character '"
                            + Counters.DELIMITER + "' is not allowed.");
                    
                } else {
                    return new IntegerValue( counters.createCounter(counterName, initValue), Type.LONG);
                }

            } catch (EXistException e) {
                logger.error(e.getMessage());
                return Sequence.EMPTY_SEQUENCE;
            }

        } else {
            try {
                if (getSignature().equals(destroyCounter)) {
                    return new BooleanValue(counters.destroyCounter(counterName));

                } else if (getSignature().equals(nextValue)) {
                    return new IntegerValue(counters.nextValue(counterName), Type.LONG);

                } else {
                    return Sequence.EMPTY_SEQUENCE;
                }
                
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return Sequence.EMPTY_SEQUENCE;
            }
        }
    }
}
