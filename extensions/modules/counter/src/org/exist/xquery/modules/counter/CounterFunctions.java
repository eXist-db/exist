package org.exist.xquery.modules.counter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

import java.nio.file.Path;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 *
 */
public class CounterFunctions extends BasicFunction {

    private Counters counters = null;
    

    protected static final Logger logger = LogManager.getLogger(CounterFunctions.class);
    
    public final static FunctionSignature createCounter =
            new FunctionSignature(
            new QName("create", CounterModule.NAMESPACE_URI, CounterModule.PREFIX),
            "Create a unique counter named $counter-name.",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "Name of the counter.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
                                                        "the value of the newly created counter."));

    public final static FunctionSignature createCounterAndInit =
            new FunctionSignature(
            new QName("create", CounterModule.NAMESPACE_URI, CounterModule.PREFIX),
            "Create a unique counter named $counter-name and initialize it with value $init-value.",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE, 
                        "Name of the counter."),

                new FunctionParameterSequenceType("init-value", Type.LONG, Cardinality.EXACTLY_ONE,
                        "The initial value of the counter.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
                                                        "the value of the newly created counter."));

    public final static FunctionSignature nextValue =
            new FunctionSignature(
            new QName("next-value", CounterModule.NAMESPACE_URI, CounterModule.PREFIX),
            "Increment the counter $counter-name and return its new value.",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "Name of the counter.")
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, 
                                                        "the new value of the specified counter," +
                                                        " or -1 if the counter does not exist."));

    public final static FunctionSignature destroyCounter =
            new FunctionSignature(
            new QName("destroy", CounterModule.NAMESPACE_URI, CounterModule.PREFIX),
            "Destroy the counter named $counter-name.",
            new SequenceType[]{
                new FunctionParameterSequenceType("counter-name", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "Name of the counter.")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
            "boolean value true() if removal as successful, otherwise return value false()."));

    public CounterFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);

        // Setup counters
        final Path counterDir = (Path) context.getBroker().getConfiguration()
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
