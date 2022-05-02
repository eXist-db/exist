package org.exist.security.realm.jwt.xquery;

import org.exist.dom.QName;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.security.realm.jwt.JWTRealm;

public class JWTFunctions extends UserSwitchingBasicFunction {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("authorize", JWTModule.NAMESPACE, JWTModule.PREFIX),
                    "Invalidate the supplied one-time token, so it can no longer be used to log in.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a JWT authorization token")
                    },
                    new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EXACTLY_ONE, "empty sequence")
            )
    };


    private AnalyzeContextInfo cachedContextInfo;

    public JWTFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String token = args[0].getStringValue();
        final SecurityManager securityManager = JWTRealm.getInstance().getSecurityManager();
        try {
            final Subject user = securityManager.authenticate(token, token);

            if (user != null) {
            } else {
            }
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
        return Sequence.EMPTY_SEQUENCE;
    }

}