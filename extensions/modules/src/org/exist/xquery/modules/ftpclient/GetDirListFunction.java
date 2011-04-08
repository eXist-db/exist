package org.exist.xquery.modules.ftpclient;

import java.io.IOException;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 *
 * @author ws
 */
public class GetDirListFunction extends BaseFTPClientFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "getDirectoryList", FTPClientModule.NAMESPACE_URI, FTPClientModule.PREFIX ),
			"Get file list from remote FTP server.",
			new SequenceType[] {
                        URI_PARAM,
                        USER_NAME,
                        USER_PASS,
                        HOME_DIRECTORY

       		},
			new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_MORE, "Response from server in XML" )
		);

    public GetDirListFunction( XQueryContext context ){
        super(context, signature);
    }

     @Override
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
         Sequence response = null;
            if (args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            String url = "";
            String user_name = "";
            String user_password = "";
            String home_dir = "";
            for (int i = 0; i < args.length; i++) {
                String str = args[i].getStringValue();
                switch (i) {
                    case 0:
                        url = str;
                        break;
                    case 1:
                        user_name = str;
                        break;
                    case 2:
                        user_password = str;
                        break;
                    case 3:
                        home_dir = str;
                        break;
                }
            }
            FTPClient client = new FTPClient();
            try {
                client.connect(url);
                client.login(user_name, user_password);
                if (!home_dir.isEmpty())
                    client.changeWorkingDirectory(home_dir);

                MemTreeBuilder builder = context.getDocumentBuilder();
                builder.startDocument();
                builder.startElement(new QName("response", null, null), null);

                FTPFile[] ftpFiles = client.listFiles();
                for (FTPFile ftpFile : ftpFiles) {
                    if (ftpFile.getType() == FTPFile.FILE_TYPE) {
                        String resClass = "file";
                        builder.startElement(new QName(resClass, null, null), null);
                        builder.characters(ftpFile.getName());
                        builder.endElement();
                    }
                    if (ftpFile.getType() == FTPFile.DIRECTORY_TYPE) {
                        String resClass = "directory";
                        builder.startElement(new QName(resClass, null, null), null);
                        builder.characters(ftpFile.getName());
                        builder.endElement();
                    }
                }
                client.logout();
                client.disconnect();

                builder.endElement();
                response = (NodeValue) builder.getDocument().getDocumentElement();

            } catch (SocketException ex) {
                Logger.getLogger(GetDirListFunction.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(GetDirListFunction.class.getName()).log(Level.SEVERE, null, ex);
            }

        return response;
    }

}
