package samples;

import org.jboss.system.ServiceMBean;

/**
 * This are the managed operations for the test service
 *
 * @author Per Nyfelt
 */
public interface XmlDbClientServiceMBean extends ServiceMBean {

    String useXmlDbService();

    String addXMLforResourceName(String xml, String resourceName);

    String fetchXMLforResurceName(String resourceName);
}
