//$Id$
package org.exist.cluster.cocoon;

import java.io.IOException;

import org.apache.cocoon.generation.AbstractGenerator;
import org.exist.cluster.ClusterComunication;
import org.exist.cluster.ClusterException;
import org.exist.cluster.journal.JournalManager;
import org.exist.storage.report.XMLStatistics;
import org.exist.util.Configuration;
import org.jgroups.Address;
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
public class ClusterInfoGenerator extends AbstractGenerator {

    public final static String NAMESPACE = "http://exist.sourceforge.net/generators/cluster";
    public final static String PREFIX = "cluster";
    XMLStatistics stats;

    public ClusterInfoGenerator() {
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

        genInfos();

        this.contentHandler.endElement(NAMESPACE,"page",PREFIX+":page");

        this.contentHandler.endPrefixMapping(PREFIX);
        this.contentHandler.endPrefixMapping("ci");


        this.contentHandler.endDocument();
    }

    private void genInfos() throws SAXException
    {
        ClusterComunication cluster = ClusterComunication.getInstance();

        if (cluster == null)
            return;

        try {

            boolean coordinator = cluster.isCoordinator();
            Address localaddress = cluster.getAddress();

            int[][] headers = cluster.getHeaders();

            AttributesImpl atts = new AttributesImpl();
            AttributesImpl inner = new AttributesImpl();

            atts.addAttribute("", "ismaster", "ismaster", "CDATA", ""+coordinator);
            atts.addAttribute("", "name", "name", "CDATA", ""+localaddress);
            this.contentHandler.startElement(NAMESPACE, "node", PREFIX + ":node", atts);

            atts = new AttributesImpl();
            atts.addAttribute("", "type", "type", "CDATA", "headers");
            this.contentHandler.startElement(NAMESPACE, "info", PREFIX + ":info", atts);

            inner.addAttribute("", "name", "name", "CDATA", "lastId");
            inner.addAttribute("", "value", "value", "CDATA", ""+headers[0][0]);
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "maxId");
            inner.addAttribute("", "value", "value", "CDATA", ""+headers[0][1]);
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "counter");
            inner.addAttribute("", "value", "value", "CDATA", ""+headers[0][2]);
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            this.contentHandler.endElement(NAMESPACE, "info", PREFIX + ":info");

            if(!coordinator){
                atts = new AttributesImpl();
                atts.addAttribute("", "type", "type", "CDATA", "master-headers");
                this.contentHandler.startElement(NAMESPACE, "info", PREFIX + ":info", atts);

                inner = new AttributesImpl();
                inner.addAttribute("", "name", "name", "CDATA", "lastId");
                inner.addAttribute("", "value", "value", "CDATA", ""+headers[1][0]);
                this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
                this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

                inner = new AttributesImpl();
                inner.addAttribute("", "name", "name", "CDATA", "maxId");
                inner.addAttribute("", "value", "value", "CDATA", ""+headers[1][1]);
                this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
                this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

                inner = new AttributesImpl();
                inner.addAttribute("", "name", "name", "CDATA", "counter");
                inner.addAttribute("", "value", "value", "CDATA", ""+headers[1][2]);
                this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
                this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

                this.contentHandler.endElement(NAMESPACE, "info", PREFIX + ":info");
            }

            Configuration conf = cluster.getConfiguration();

            atts = new AttributesImpl();
            atts.addAttribute("", "type", "type", "CDATA", "jgroups");
            this.contentHandler.startElement(NAMESPACE, "info", PREFIX + ":info", atts);

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "protocol");
            String protocol = (String) conf.getProperty(ClusterComunication.PROPERTY_CLUSTER_PROTOCOL);
            if(protocol==null)
                protocol = ClusterComunication.DEFAULT_PROTOCOL_STACK;
            StringBuffer prot = new StringBuffer();
            for(int i=0;i<protocol.length();i+=70){
                prot.append(protocol.substring(i,Math.min(i+70,protocol.length())));
                prot.append(" ");
            }
            inner.addAttribute("", "value", "value", "CDATA", prot.toString());
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            this.contentHandler.endElement(NAMESPACE, "info", PREFIX + ":info");


            atts = new AttributesImpl();
            atts.addAttribute("", "type", "type", "CDATA", "journal");
            this.contentHandler.startElement(NAMESPACE, "info", PREFIX + ":info", atts);

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "journal-dir");
            inner.addAttribute("", "value", "value", "CDATA", ""+conf.getProperty(JournalManager.PROPERTY_JOURNAL_DIR));
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "exclude-dir");
            inner.addAttribute("", "value", "value", "CDATA", ""+conf.getProperty(ClusterComunication.PROPERTY_CLUSTER_EXCLUDED_COLLECTIONS));
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "max-item");
            inner.addAttribute("", "value", "value", "CDATA", ""+conf.getProperty(JournalManager.PROPERTY_CLUSTER_JOURNAL_MAXSTORE));
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            inner = new AttributesImpl();
            inner.addAttribute("", "name", "name", "CDATA", "coordinator-shift");
            inner.addAttribute("", "value", "value", "CDATA", ""+conf.getProperty(JournalManager.PROPERTY_CLUSTER_JOURNAL_SHIFT));
            this.contentHandler.startElement(NAMESPACE, "data", PREFIX + ":data", inner);
            this.contentHandler.endElement(NAMESPACE, "data", PREFIX + ":data");

            this.contentHandler.endElement(NAMESPACE, "info", PREFIX + ":info");


        } catch (ClusterException e) {
            e.printStackTrace();
            throw new SAXException("ERROR CREATING INFOS ", e);
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
