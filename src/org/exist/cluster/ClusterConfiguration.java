//$Id$
package org.exist.cluster;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;

/**
 * This class rapresent a cluster configuration for the cluster
 *
 * @author Francesco Mondora aka Makkina
 *         MIchele Danieli aka mdanieli
 *         Date: Sep 1, 2004
 *         Time: 1:52:08 PM
 *         Revision $Revision$
 */
public class ClusterConfiguration {
    private static Logger log = Logger.getLogger( ClusterConfiguration.class );


    private static String protocolStack = null;
    private static String dbauser = null;
    private static String dbapwd = "";


    static {
        init("conf.xml", System.getProperty("exist.home", System.getProperty("user.dir")));
    }



    private static void init(String file, String dbHome) {
        log.info("Cluster Configuration init started");

        try {
            File f = new File(dbHome, file);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource src = new InputSource(new FileReader(f));
            Document doc = builder.parse(src);

            NodeList clusters = doc.getElementsByTagName("cluster");
            Element cluster;
            if (clusters.getLength() > 0) {
                cluster = (Element) clusters.item(0);
                protocolStack = cluster.getAttribute("protocol");
                dbauser = cluster.getAttribute("dbaUser");
                dbapwd = cluster.getAttribute("dbaPassword");

                log.debug("Cluster initialization:  protocostack="+protocolStack);
                log.debug("Cluster initialization:  dbauser="+dbauser);
                log.debug("Cluster initialization:  dappwd="+dbapwd);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("Cluster Configuration init completed");
    }


    public static String getDBName() {
        return dbauser;
    }

    public static String getDbPassword() {
        return dbapwd;
    }

    public static String getProtocolStack() {
        return protocolStack;
    }
}
