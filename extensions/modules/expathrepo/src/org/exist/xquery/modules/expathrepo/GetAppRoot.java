package org.exist.xquery.modules.expathrepo;

import org.exist.dom.QName;
import org.exist.repo.Deployment;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class GetAppRoot extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("get-root", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Returns the root collection into which applications are installed. Corresponds to the " +
            "collection path defined in conf.xml (<repository root=\"...\"/>) or /db if not configured.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                "The application root collection"));

    public GetAppRoot(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String configured = (String) context.getBroker().getConfiguration().getProperty(Deployment.PROPERTY_APP_ROOT);
        if (configured != null) {
            return new StringValue(configured);
        } else {
            return new StringValue(XmldbURI.ROOT_COLLECTION);
        }
    }
}
