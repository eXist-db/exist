package samples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;

/**
 *  Description of the Class
 *
 * @author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 * @created    August 1, 2002
 */
public class Search {

    private static String encoding = "ISO-8859-1";


    /**
     *  Description of the Method
     *
     * @param  args           Description of the Parameter
     * @exception  Exception  Description of the Exception
     */
    public static void main(String args[]) throws Exception {
        XmlRpcClient xmlrpc = new XmlRpcClient("http://localhost:8080/exist/xmlrpc");
        xmlrpc.setBasicAuthentication( "admin", "" );
        // execute query and retrieve an id for the generated result set
        Vector params = new Vector();
        params.addElement("document(*)//SPEECH[SPEAKER='HAMLET']");
        Integer resultId = (Integer) xmlrpc.execute("executeQuery", params);

        // get the number of hits
        params.clear();
        params.addElement(resultId);
        Integer hits = (Integer) xmlrpc.execute("getHits", params);
        System.out.println("found " + hits + " hits.");
        System.out.println("retrieving hits 1 to 5 ...");

        // retrieve some results
        params.clear();
        params.addElement(resultId);
        params.addElement(null);
        params.addElement(new Integer(1));
        params.addElement(encoding);
        for (int i = 0; i < 5 && i < hits.intValue(); i++) {
            params.setElementAt(new Integer(i), 1);
            byte[] data = (byte[]) xmlrpc.execute("retrieve", params);
            System.out.println(new String(data, encoding));
        }
    }
}

