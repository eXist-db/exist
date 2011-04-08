package org.exist.xquery.modules.ftpclient;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import org.exist.xquery.value.Type;

import org.apache.log4j.Logger;

/**
 *
 * @author WStarcev
 */
public class BaseFTPClientFunction extends BasicFunction {

    protected static final Logger                        logger                         = Logger.getLogger( BaseFTPClientFunction.class );

    protected static final FunctionParameterSequenceType URI_PARAM                      = new FunctionParameterSequenceType( "url", Type.STRING, Cardinality.EXACTLY_ONE, "The FTP server, [ftp|sftp|ftps]://[server_name]:[port_number]" );
    protected static final FunctionParameterSequenceType USER_NAME                      = new FunctionParameterSequenceType("user_name", Type.STRING, Cardinality.EXACTLY_ONE, "User name");
    protected static final FunctionParameterSequenceType USER_PASS                      = new FunctionParameterSequenceType("user_pass", Type.STRING, Cardinality.EXACTLY_ONE, "User password");
    protected static final FunctionParameterSequenceType HOME_DIRECTORY                 = new FunctionParameterSequenceType("home_directory", Type.STRING, Cardinality.EXACTLY_ONE, "Home directory");
    protected static final FunctionParameterSequenceType FILE_NAME                      = new FunctionParameterSequenceType("file_name", Type.STRING, Cardinality.EXACTLY_ONE, "File name");
    protected static final FunctionParameterSequenceType FTP_FILE_CONTENT               = new FunctionParameterSequenceType( "content", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "Base64Binary data" );
    
    protected static final FunctionReturnSequenceType    XML_BODY_RETURN                = new FunctionReturnSequenceType( Type.NODE, Cardinality.EXACTLY_ONE, "Response from server in XML" );

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    public BaseFTPClientFunction( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );
    }


}
