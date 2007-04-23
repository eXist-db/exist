package org.exist.xquery.functions;

import java.util.Date;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunImplicitTimezone extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("implicit-timezone", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of the implicit timezone property from the dynamic context.",
			null,
			new SequenceType(Type.DAY_TIME_DURATION, Cardinality.EXACTLY_ONE));

	public FunImplicitTimezone(XQueryContext context) {
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
        
        //Sequence result = new DayTimeDurationValue(TimeUtils.getInstance().getLocalTimezoneOffsetMillis());
        Sequence result = new DateTimeValue(new Date(context.getWatchDog().getStartTime())).getTimezone();
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;         
	}

}
