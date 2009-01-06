package org.exist.xquery.functions.system;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.ProcessMonitor;

import java.util.Iterator;
import java.util.Date;

public class GetRunningJobs extends BasicFunction {

    final static String NAMESPACE_URI                       = SystemModule.NAMESPACE_URI;
    final static String PREFIX                              = SystemModule.PREFIX;

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName( "get-running-jobs", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of running jobs (dba role only).",
                null,
                new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
        );

    public GetRunningJobs(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if( !context.getUser().hasDbaRole() ) {
            throw( new XPathException( getASTNode(), "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to get the list of running xqueries" ) );
        }

        MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "jobs", NAMESPACE_URI, PREFIX ), null );

        ProcessMonitor monitor = context.getBroker().getBrokerPool().getProcessMonitor();
        ProcessMonitor.JobInfo[] jobs = monitor.runningJobs();
        for (int i = 0; i < jobs.length; i++) {
            Thread process = jobs[i].getThread();
			Date startDate = new Date(jobs[i].getStartTime());
            builder.startElement( new QName( "job", NAMESPACE_URI, PREFIX ), null);
            builder.addAttribute( new QName("id", null, null), process.getName());
            builder.addAttribute( new QName("action", null, null), jobs[i].getAction());
			builder.addAttribute( new QName("start", null, null), new DateTimeValue(startDate).getStringValue());
            builder.addAttribute(new QName("info", null, null), jobs[i].getAddInfo().toString());
            builder.endElement();
        }

        builder.endElement();
        builder.endDocument();

        return (NodeValue)builder.getDocument().getDocumentElement();
    }
}
