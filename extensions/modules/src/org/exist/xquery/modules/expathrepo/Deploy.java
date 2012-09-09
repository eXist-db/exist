package org.exist.xquery.modules.expathrepo;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.repo.Deployment;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.PackageException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;

public class Deploy extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(Deploy.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
			"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
			"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
			"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
			new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
			new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
					"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
		new FunctionSignature(
				new QName("deploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
				"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
				"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
				"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
				new SequenceType[] { 
					new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name"),
					new FunctionParameterSequenceType("targetCollection", Type.STRING, Cardinality.EXACTLY_ONE, "the target " +
							"collection into which the package will be stored")
				},
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise.")),
		new FunctionSignature(
				new QName("undeploy", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
				"Deploy an application package. Installs package contents to the specified target collection, using the permissions " +
				"defined by the &lt;permissions&gt; element in repo.xml. Pre- and post-install XQuery scripts can be specified " +
				"via the &lt;prepare&gt; and &lt;finish&gt; elements.",
				new SequenceType[] { new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name")},
				new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, 
						"<status result=\"ok\"/> if deployment was ok. Throws an error otherwise."))
	};

	private static final QName STATUS_ELEMENT = new QName("status", ExpathPackageModule.NAMESPACE_URI);
	
	public Deploy(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (!context.getSubject().hasDbaRole())
			throw new XPathException(this, EXPathErrorCode.EXPDY003, "Permission denied. You need to be a member " +
					"of the dba group to use repo:deploy/undeploy");
		
		String pkgName = args[0].getStringValue();
        String userTarget = null;
        if (getArgumentCount() == 2)
            userTarget = args[1].getStringValue();
        try {
            Deployment deployment = new Deployment(context.getBroker());
            String target;
            if (isCalledAs("deploy"))
                target = deployment.deploy(pkgName, context.getRepository(), userTarget);
            else
                target = deployment.undeploy(pkgName, context.getRepository());
            return statusReport(target);
        } catch (PackageException e) {
            throw new XPathException(this, EXPathErrorCode.EXPDY001, e.getMessage());
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Caught IO error while deploying expath archive");
        }
    }

	private Sequence statusReport(String target) {
		context.pushDocumentContext();
		try {
			MemTreeBuilder builder = context.getDocumentBuilder();
			AttributesImpl attrs = new AttributesImpl();
			attrs.addAttribute("", "result", "result", "CDATA", "ok");
			if (target != null)
				attrs.addAttribute("", "target", "target", "CDATA", target);
			builder.startElement(STATUS_ELEMENT, attrs);
			builder.endElement();
			
			return builder.getDocument().getNode(1);
		} finally {
			context.popDocumentContext();
		}
		
	}

	@Override
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
	}
}
