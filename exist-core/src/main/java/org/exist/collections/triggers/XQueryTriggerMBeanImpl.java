package org.exist.collections.triggers;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.StringJoiner;

final class XQueryTriggerMBeanImpl extends StandardMBean implements XQueryTriggerMBean {

    private XQueryTriggerMBeanImpl() throws NotCompliantMBeanException {
        super(XQueryTriggerMBean.class);
    }

    static void init() {
        try {
            final ObjectName name = ObjectName.getInstance("org.exist.management.exist", "type", "TriggerStates");
            final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            if (!platformMBeanServer.isRegistered(name)) {
                platformMBeanServer.registerMBean(new XQueryTriggerMBeanImpl(), name);
            }
        } catch (final MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public int getKeys() {
        return TriggerStatePerThread.keys();
    }

    @Override
    public void clear() {
        TriggerStatePerThread.clearAll();
    }

    @Override
    public String dumpTriggerStates() {
        StringJoiner joiner = new StringJoiner("\n");
        TriggerStatePerThread.forEach((k, v) -> joiner.add("%s: %s".formatted(k, v.size())));
        return joiner.toString();
    }

    @Override
    public String listKeys() {
        StringJoiner joiner = new StringJoiner("\n");
        TriggerStatePerThread.forEach((k, v) -> joiner.add("%s".formatted(k)));
        return joiner.toString();
    }
}
