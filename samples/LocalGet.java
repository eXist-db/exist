
package samples;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.w3c.dom.Element;
import org.apache.xml.serialize.*;

/**
 * Retrieve a document from the repository.
 *
 * To run this example enter:
 *
 * bin/run.sh samples.APISearch xpath-query
 *
 * in the root directory of the distribution.
 */
public class LocalGet {

    protected static String driver = "org.exist.xmldb.DatabaseImpl";
    
    protected static String URI = "xmldb:exist:";
    
    protected static void usage() {
	System.out.println("usage: samples.APIGet [ collection ] docName");
	System.exit(0);
    }
    
    public static void main(String args[]) throws Exception {
	if(args.length < 1)
	    usage();
	
	String collection = "/", file;
	
	if(args.length == 2) {
            // if collection does not start with "/" add it
	    collection = (args[0].charAt(0) == '/') ? args[0] : "/" + args[0];
	    file = args[1];
	} else
	    file = args[0];
	
        // initialize database drivers            
	Class cl = Class.forName(driver);			
	Database database = (Database)cl.newInstance();
	DatabaseManager.registerDatabase(database);
	
        // get the collection
	System.out.println("retrieving collection " + URI + collection);
	Collection col = 
	    DatabaseManager.getCollection(URI + collection);
	col.setProperty("pretty", "true");
	col.setProperty("encoding", "ISO-8859-1");
        
	String resources[] = col.listResources();
	for(int i = 0; i < resources.length; i++)
	    System.out.println(resources[i]);

        // get the resource
	XMLResource res = (XMLResource)col.getResource(file);
	
	if(res == null) {
	    System.err.println("could not retrieve document " + file + "!");
	    return;
	}
	System.out.println((String)res.getContent());
    }
}
