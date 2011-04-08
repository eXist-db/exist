package org.exist.xquery.modules.ftpclient;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.StringValue;
/**
 *
 * @author WStarcev
 */
public class GetFileFunction extends BaseFTPClientFunction {

     public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "getFile", FTPClientModule.NAMESPACE_URI, FTPClientModule.PREFIX ),
			"Get data from FTP to Base64Binary variable.",
			new SequenceType[] {
                        URI_PARAM,
                        USER_NAME,
                        USER_PASS,
                        HOME_DIRECTORY,
                        FILE_NAME

       		},
			new FunctionReturnSequenceType( Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE, "Response from server in XML" )
		);

     public GetFileFunction( XQueryContext context ){
         super(context, signature);
     }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence res = null;
        if (args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
        }
        MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("response", null, null), null);
        try {
            FTPClient client = new FTPClient();
            client.connect(args[0].getStringValue());
            client.login(args[1].getStringValue(), args[2].getStringValue());
            if (!args[3].getStringValue().isEmpty())
                client.changeWorkingDirectory(args[3].getStringValue());
            client.setFileType(FTP.BINARY_FILE_TYPE);
            BinaryValueFromInputStream bvis = BinaryValueFromInputStream.getInstance(context,
                                                                                    new Base64BinaryValueType(),
                                                                                    client.retrieveFileStream(args[4].getStringValue())
                                                                                    );
            if (client.isConnected()){
                client.disconnect();
            }

            return bvis;

        } catch (IOException ex) {
            Logger.getLogger(SendFileFunction.class.getName()).log(Level.SEVERE, null, ex);
            builder.characters(ex.getLocalizedMessage());
        }

        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

}

