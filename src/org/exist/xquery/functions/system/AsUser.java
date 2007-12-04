package org.exist.xquery.functions.system;

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 */
public class AsUser extends Function {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("as-user", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"A pseudo-function to execute a limited block of code as a different " +
            "user. The first argument is the name of the user, the second is the " +
            "password. If the user can be authenticated, the function will execute the " +
            "code block given in the third argument with the permissions of that user and" +
            "returns the result of the execution. Before the function completes, it switches " +
            "the current user back to the old user.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

    public AsUser(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        Sequence userSeq = getArgument(0).eval(contextSequence, contextItem);
        Sequence passwdSeq = getArgument(1).eval(contextSequence, contextItem);
        if (userSeq.isEmpty())
            throw new XPathException(getASTNode(), "No user specified");
        String userName = userSeq.getStringValue();
        String passwd = passwdSeq.getStringValue();
        org.exist.security.SecurityManager security = context.getBroker().getBrokerPool().getSecurityManager();
        User user = security.getUser(userName);
        if (user == null)
            throw new XPathException(getASTNode(), "Authentication failed");
        if (user.validate(passwd)) {
            User oldUser = context.getBroker().getUser();
            try {
                context.getBroker().setUser(user);
                return getArgument(2).eval(contextSequence, contextItem);
            } finally {
                context.getBroker().setUser(oldUser);
            }
        } else
            throw new XPathException(getASTNode(), "Authentication failed");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        return getArgument(2).getDependencies();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#returnsType()
     */
    public int returnsType() {
        return getArgument(2).returnsType();
    }
}
