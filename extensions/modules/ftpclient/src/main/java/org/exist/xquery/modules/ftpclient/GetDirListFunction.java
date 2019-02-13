package org.exist.xquery.modules.ftpclient;

import java.io.IOException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
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
 * @author ws
 * @author Adam Retter <adam@existsolutions.com>
 */
public class GetDirListFunction extends BasicFunction {

    private static final Logger log = LogManager.getLogger(GetDirListFunction.class);
    
    private static final FunctionParameterSequenceType CONNECTION_HANDLE_PARAM = new FunctionParameterSequenceType("connection-handle", Type.LONG, Cardinality.EXACTLY_ONE, "The connection handle");
    private static final FunctionParameterSequenceType REMOTE_DIRECTORY_PARAM = new FunctionParameterSequenceType("remote-directory", Type.STRING, Cardinality.EXACTLY_ONE, "The remote directory");
    
    public final static FunctionSignature signature = new FunctionSignature(
        new QName("list", FTPClientModule.NAMESPACE_URI, FTPClientModule.PREFIX),
        "Get file list from remote FTP server.",
        new SequenceType[] {
            CONNECTION_HANDLE_PARAM,
            REMOTE_DIRECTORY_PARAM,
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE, "Response from server in XML" )
    );

    public GetDirListFunction( XQueryContext context ){
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        long connectionUID = ((IntegerValue)args[0].itemAt(0)).getLong();
        FTPClient ftp = FTPClientModule.retrieveConnection(context, connectionUID);
        if(ftp != null) {
            String remoteDirectory = args[1].getStringValue();
            
            result = list(ftp, remoteDirectory);
        
        }
        
        return result;
    }

    private Sequence list(FTPClient ftp, String remoteDirectory) {
        Sequence result = Sequence.EMPTY_SEQUENCE;
        try {
            ftp.changeWorkingDirectory(remoteDirectory);

            FTPFile[] ftpFiles = ftp.listFiles();
                
            MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            builder.startElement(new QName("list", FTPClientModule.NAMESPACE_URI), null);
                
            for(FTPFile ftpFile : ftpFiles) {
                if (ftpFile.getType() == FTPFile.FILE_TYPE) {
                    builder.startElement(new QName("file", FTPClientModule.NAMESPACE_URI), null);
                }
                else if(ftpFile.getType() == FTPFile.DIRECTORY_TYPE) {
                    builder.startElement(new QName("directory", FTPClientModule.NAMESPACE_URI), null);
                }
                builder.characters(ftpFile.getName());
                builder.endElement();
            }
                
            builder.endElement();
            builder.endDocument();
            
            result = builder.getDocument();

        } catch(IOException ioe) {
            log.error(ioe.getMessage(), ioe);
        }

        return result;
    }
}