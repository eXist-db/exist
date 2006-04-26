//$Id$
package org.exist.cluster.cocoon;

import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.cocoon.generation.AbstractGenerator;
import org.exist.cluster.ClusterComunication;
import org.exist.storage.report.XMLStatistics;
import org.jgroups.Address;
import org.jgroups.stack.IpAddress;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A Cocoon Generator which generates status information about running database instances,
 * buffer usage and the like.
 * Created by Nicola Breda.
 *
 * @author Nicola Breda aka maiale
 * @author David Frontini aka spider
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class ClusterGenerator extends AbstractGenerator {

    public final static String NAMESPACE = "http://exist.sourceforge.net/generators/cluster";
    public final static String PREFIX = "cluster";
    XMLStatistics stats;

    public ClusterGenerator() {
        super();
    }

    /**
     * @see org.apache.cocoon.generation.Generator#generate()
     */
    public void generate() throws IOException, SAXException {
        this.contentHandler.startDocument();
        this.contentHandler.startPrefixMapping(PREFIX, NAMESPACE);
        this.contentHandler.startPrefixMapping("ci", "http://apache.org/cocoon/include/1.0");

        this.contentHandler.startElement(NAMESPACE,"page",PREFIX+":page",new AttributesImpl());

        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute("", "src", "src", "CDATA", "sidebar.xml");
        this.contentHandler.startElement("http://apache.org/cocoon/include/1.0","include","ci:include",attr);
        this.contentHandler.endElement("http://apache.org/cocoon/include/1.0","include","ci:include");

        this.contentHandler.startElement(NAMESPACE, "cluster", PREFIX + ":cluster", new AttributesImpl());
        genNodes();
        this.contentHandler.endElement(NAMESPACE, "cluster", PREFIX + ":cluster");
        this.contentHandler.endPrefixMapping(PREFIX);

        this.contentHandler.endElement(NAMESPACE,"page",PREFIX+":page");

        this.contentHandler.endDocument();
    }

    private void genNodes() throws SAXException
    {
        ClusterComunication cluster = ClusterComunication.getInstance();

        if (cluster == null)
            return;

        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "address", "address", "CDATA", cluster.getAddress().toString());
        atts.addAttribute("", "type", "type", "CDATA", cluster.isCoordinator() ? "local-master" : "local-node");
        atts.addAttribute("", "console-address", "console-address", "CDATA", "");
        this.contentHandler.startElement(NAMESPACE, "node", PREFIX + ":node", atts);
        this.contentHandler.endElement(NAMESPACE, "node", PREFIX + ":node");

        if (!cluster.isCoordinator()) {
            String consoleAddr = "no-ip-found";
            Address addr = cluster.getCoordinator();
            if ( addr instanceof IpAddress )
            {
                 IpAddress ipA = (IpAddress)addr;
                 consoleAddr = ipA.getIpAddress().getHostAddress() ;
            }

            Vector v = new Vector();
            v.add(addr);
            ConsoleInfo info = (ConsoleInfo) cluster.getConsoleInfos(v).get(addr.toString());

            atts = new AttributesImpl();
            atts.addAttribute("", "address", "address", "CDATA", addr.toString());
            atts.addAttribute("", "type", "type", "CDATA",  "remote-master");
            if(info!=null)
                atts.addAttribute("", "console-address", "console-address", "CDATA", "http://"+ consoleAddr + ":" + info.getProperty("port") +"/exist/cluster");
            this.contentHandler.startElement(NAMESPACE, "node", PREFIX + ":node", atts);
            this.contentHandler.endElement(NAMESPACE, "node", PREFIX + ":node");
        }

        Vector members = cluster.getMembersNoCoordinator();
        HashMap infos = cluster.getConsoleInfos(members);
        for(int i=0;i<members.size();i++){
            String consoleAddr = "no-ip-found";
            ConsoleInfo info = (ConsoleInfo) infos.get(members.get(i).toString());
            if ( members.get(i) instanceof IpAddress )
            {
                 IpAddress ipA = (IpAddress)members.get(i);
                 consoleAddr = ipA.getIpAddress().getHostAddress() ;
            }
            atts = new AttributesImpl();
            atts.addAttribute("", "address", "address", "CDATA", members.get(i).toString());
            atts.addAttribute("", "type", "type", "CDATA",  "remote-node");
            if(info!=null)
                atts.addAttribute("", "console-address", "console-address", "CDATA", "http://"+ consoleAddr + ":" + info.getProperty("port") +"/exist/cluster");
            this.contentHandler.startElement(NAMESPACE, "node", PREFIX + ":node", atts);
            this.contentHandler.endElement(NAMESPACE, "node", PREFIX + ":node");
        }



    }

    /**
     * @param elem
     * @param value
     * @throws org.xml.sax.SAXException
     */
    private void addValue(String elem, String value) throws SAXException {
        stats.addValue(elem, value);
    }
}
