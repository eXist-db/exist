package org.exist.xquery.modules.ftpclient;

import java.io.IOException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */


public class GetConnectionFunction extends BasicFunction {
    
    private static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, "an xs:long representing the connection handle" );
    private static final FunctionParameterSequenceType FTP_PASSWORD_PARAM = new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The FTP server password" );
    private static final FunctionParameterSequenceType FTP_USERNAME_PARAM = new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The FTP server username" );
    private static final FunctionParameterSequenceType FTP_HOST_PARAM = new FunctionParameterSequenceType("host", Type.STRING, Cardinality.EXACTLY_ONE, "The host to connect to" );
    
    private static final Logger log = LogManager.getLogger(GetConnectionFunction.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("get-connection", FTPClientModule.NAMESPACE_URI, FTPClientModule.PREFIX),
            "Opens a connection to a SQL Database",
            new SequenceType[] {
                FTP_HOST_PARAM,
                FTP_USERNAME_PARAM,
                FTP_PASSWORD_PARAM
            },
            RETURN_TYPE
        )
    };

    /**
     * GetConnectionFunction Constructor.
     *
     * @param  context    The Context of the calling XQuery
     * @param  signature  DOCUMENT ME!
     */
    public GetConnectionFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * evaluate the call to the xquery get-connection() function, it is really the main entry point of this class.
     *
     * @param   args             arguments from the get-connection() function call
     * @param   contextSequence  the Context Sequence to operate on (not used here internally!)
     *
     * @return  A xs:long representing a handle to the connection
     *
     * @throws  XPathException  DOCUMENT ME!
     *
     * @see     org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException {
        
        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        // get the ftp connection details
        final String host = args[0].getStringValue();
        final String username = args[1].getStringValue();
        final String password = args[2].getStringValue();

        final FTPClient ftp = new FTPClient();
        try {
            ftp.connect(host);

            log.debug("Connected to: " + host + ". " + ftp.getReplyString());

            // After connection attempt, you should check the reply code to verify
            // success.
            int reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                log.warn("FTP server refused connection.");
            } else {
                if(ftp.login(username, password)) {
                    // store the Connection and return the uid handle of the Connection
                    result = new IntegerValue(FTPClientModule.storeConnection(context, ftp));
                } else {
                    ftp.disconnect();
                    log.warn("Unable to login with username/password to FTP server");
                }
            }
        } catch(final IOException se) {
            if(ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch(final IOException ioe) {
                    log.error(ioe.getMessage(), ioe);
                }
            }
        }
            
        return result;
    }
}