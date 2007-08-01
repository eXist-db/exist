package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunAdjustTimezone extends BasicFunction {

	public final static FunctionSignature fnAdjustDateTimeToTimezone[] = {
		new FunctionSignature(
			new QName("adjust-dateTime-to-timezone", Function.BUILTIN_FUNCTION_NS),
			"Adjusts an xs:dateTime value to the implicit timezone of the current locale.",
			new SequenceType[] { 
					new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature(
				new QName("adjust-dateTime-to-timezone", Function.BUILTIN_FUNCTION_NS),
				"Adjusts an xs:dateTime value to a specific timezone, or to no timezone at all. " +
				"If $b is the empty sequence, returns an xs:dateTime without a timezone.",
				new SequenceType[] { 
						new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE),
						new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE))
	};

	public final static FunctionSignature fnAdjustDateToTimezone[] = {
		new FunctionSignature(
			new QName("adjust-date-to-timezone", Function.BUILTIN_FUNCTION_NS),
			"Adjusts an xs:date value to the implicit timezone of the current locale.",
			new SequenceType[] { 
					new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature(
				new QName("adjust-date-to-timezone", Function.BUILTIN_FUNCTION_NS),
				"Adjusts an xs:date value to a specific timezone, or to no timezone at all. " +
				"If $b is the empty sequence, returns an xs:date without a timezone.",
				new SequenceType[] { 
						new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE),
						new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE))
	};
	
	public final static FunctionSignature fnAdjustTimeToTimezone[] = {
		new FunctionSignature(
			new QName("adjust-time-to-timezone", Function.BUILTIN_FUNCTION_NS),
			"Adjusts an xs:time value to the implicit timezone of the current locale.",
			new SequenceType[] { 
					new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature(
				new QName("adjust-time-to-timezone", Function.BUILTIN_FUNCTION_NS),
				"Adjusts an xs:time value to a specific timezone, or to no timezone at all. " +
				"If $b is the empty sequence, returns an xs:time without a timezone.",
				new SequenceType[] { 
						new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE),
						new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.TIME, Cardinality.ZERO_OR_ONE))
	};

	public FunAdjustTimezone(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
		if (args[0].isEmpty()) 
            result =Sequence.EMPTY_SEQUENCE;
        else {
    		AbstractDateTimeValue time = (AbstractDateTimeValue) args[0].itemAt(0);    		 
    		if (getSignature().getArgumentCount() == 2) {
    			if (args[1].isEmpty()) 
    			    result = time.withoutTimezone();
                else {
                    DayTimeDurationValue offset = (DayTimeDurationValue) args[1].itemAt(0);
                    result = time.adjustedToTimezone(offset);
                }
    		}
            else
                result = time.adjustedToTimezone(null);
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
	}

}
