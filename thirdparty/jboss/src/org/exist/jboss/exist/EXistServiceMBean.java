package org.exist.jboss.exist;

import org.jboss.system.ServiceMBean;

/**
 * This are the managed operations and attributes for the EXist service
 *
 * @author Per Nyfelt
 */
public interface EXistServiceMBean extends ServiceMBean {

    public String getStatus();

    public String getEXistHome();

    public void setEXistHome(String existHome);
}