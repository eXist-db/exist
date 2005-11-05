package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

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
		if (args[0].getLength() == 0) return Sequence.EMPTY_SEQUENCE;
		AbstractDateTimeValue time = (AbstractDateTimeValue) args[0].itemAt(0);
		DayTimeDurationValue offset = null;
		if (getSignature().getArgumentCount() == 2) {
			if (args[1].getLength() == 0) return time.withoutTimezone();
			offset = (DayTimeDurationValue) args[1].itemAt(0);
		}
		return time.adjustedToTimezone(offset);
	}

}
