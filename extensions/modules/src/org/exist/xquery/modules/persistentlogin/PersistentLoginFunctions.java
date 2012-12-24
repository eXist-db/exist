package org.exist.xquery.modules.persistentlogin;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.AuthenticationException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xquery.value.StringValue;

/**
 * Functions to access the persistent login module.
 *
 */
public class PersistentLoginFunctions extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("register", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
            "Try to log in the user and create a one-time login token. The token can be stored to a cookie and used to log in " +
            "(via the login function) as the same user without " +
            "providing credentials. However, for security reasons the token will be valid only for " +
            "the next request to the login function and is deleted afterwards. " +
            "If the user is valid and the token could be generated, the " +
            "supplied callback function is called with 4 arguments: $token as xs:string, $user as xs:string, $password as xs:string, " +
            "$timeToLive as xs:duration.",
            new SequenceType[] {
                new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "user name"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "password"),
                new FunctionParameterSequenceType("timeToLive", Type.DURATION, Cardinality.EXACTLY_ONE, "duration for which the user is remembered"),
                new FunctionParameterSequenceType("onLogin", Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE,
                    "callback function to be called when the login succeeds")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
        ),
        new FunctionSignature(
            new QName("login", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
            "Try to log in the user based on the supplied token. If the login succeeds, the provided callback function " +
            "is called with 4 arguments: $token as xs:string, $user as xs:string, $password as xs:string, $timeToLive as duration. " +
            "$token will be a new token which can be used for the next request. The old token is deleted.",
            new SequenceType[] {
                new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token"),
                new FunctionParameterSequenceType("onLogin", Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE,
                    "callback function to be called when the login succeeds"),
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
        ),
        new FunctionSignature(
            new QName("invalidate", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
            "Invalidate the supplied one-time token, so it can no longer be used to log in.",
            new SequenceType[] {
                new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token")
            },
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EXACTLY_ONE, "empty sequence")
        )
    };

    private AnalyzeContextInfo cachedContextInfo;

    public PersistentLoginFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (isCalledAs("register")) {
            String user = args[0].getStringValue();
            String pass = null;
            if (!args[1].isEmpty())
                pass = args[1].getStringValue();
            DurationValue timeToLive = (DurationValue) args[2].itemAt(0);
            FunctionReference callback = null;
            if (!args[3].isEmpty())
                callback = (FunctionReference) args[3].itemAt(0);
            return register(user, pass, timeToLive, callback);
        } else if (isCalledAs("login")) {
            String token = args[0].getStringValue();
            FunctionReference callback = null;
            if (!args[1].isEmpty())
                callback = (FunctionReference) args[1].itemAt(0);
            return authenticate(token, callback);
        } else {
            PersistentLogin.getInstance().invalidate(args[0].getStringValue());
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    private Sequence register(String user, String pass, DurationValue timeToLive, FunctionReference callback) throws XPathException {
        if (login(user, pass)) {
            PersistentLogin.LoginDetails details = PersistentLogin.getInstance().register(user, pass, timeToLive);
            return callback(callback, null, details);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence authenticate(String token, FunctionReference callback) throws XPathException {
        PersistentLogin.LoginDetails data = PersistentLogin.getInstance().lookup(token);
        if (data == null)
            return Sequence.EMPTY_SEQUENCE;
        if (login(data.getUser(), data.getPassword())) {
            return callback(callback, token, data);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private boolean login(String user, String pass) throws XPathException {
        try {
            org.exist.security.SecurityManager sm = BrokerPool.getInstance().getSecurityManager();
            Subject subject = sm.authenticate(user, pass);
            if (subject == null)
                return false;
            context.getBroker().setSubject(subject);
            return true;
        } catch (AuthenticationException e) {
            return false;
        } catch (EXistException e) {
            return false;
        }
    }

    private Sequence callback(FunctionReference func, String oldToken, PersistentLogin.LoginDetails details) throws XPathException {
        Sequence[] args = new Sequence[4];
        String newToken = details.toString();
        if (oldToken != null && oldToken.equals(newToken))
            args[0] = Sequence.EMPTY_SEQUENCE;
        else
            args[0] = new StringValue(newToken);
        args[1] = new StringValue(details.getUser());
        args[2] = new StringValue(details.getPassword());
        args[3] = details.getTimeToLive();
        func.analyze(cachedContextInfo);
        return func.evalFunction(null, null, args);
    }
}
