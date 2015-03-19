package org.exist.xquery.modules.ftpclient;

import java.io.IOException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.IntegerValue;
/**
 *
 * @author WStarcev
 * @author Adam Retter <adam@existsolutions.com>
 */
public class GetFileFunction extends BasicFunction {

    private static final FunctionParameterSequenceType CONNECTION_HANDLE_PARAM = new FunctionParameterSequenceType("connection-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The connection handle");
    private static final FunctionParameterSequenceType REMOTE_DIRECTORY_PARAM = new FunctionParameterSequenceType("remote-directory", Type.STRING, Cardinality.EXACTLY_ONE, "The remote directory");
    private static final FunctionParameterSequenceType FILE_NAME_PARAM = new FunctionParameterSequenceType("file-name", Type.STRING, Cardinality.EXACTLY_ONE, "File name");
    
    private static final Logger log = LogManager.getLogger(GetFileFunction.class);
    
    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-binary-file", FTPClientModule.NAMESPACE_URI, FTPClientModule.PREFIX),
        "Get binary file from the FTP Server.",
        new SequenceType[] {
            CONNECTION_HANDLE_PARAM,
            REMOTE_DIRECTORY_PARAM,
            FILE_NAME_PARAM
        },
        new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "File retrieved from the server.")
    );

    public GetFileFunction(XQueryContext context){
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        long connectionUID = ((IntegerValue)args[0].itemAt(0)).getLong();
        FTPClient ftp = FTPClientModule.retrieveConnection(context, connectionUID);
        if(ftp != null) {
            String remoteDirectory = args[1].getStringValue();
            String fileName = args[2].getStringValue();
            
            result = getBinaryFile(ftp, remoteDirectory, fileName);
        
        }
        
        return result;
    }

    private Sequence getBinaryFile(FTPClient ftp, String remoteDirectory, String fileName) throws XPathException {
        
        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        try {
            ftp.changeWorkingDirectory(remoteDirectory);
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            
            result = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), ftp.retrieveFileStream(fileName));

        } catch(IOException ioe) {
            log.error(ioe.getMessage(), ioe);
        }

        return result;
    }
}