package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;

/**
 *
 * @author aretter
 */
public interface TriggerProxy<T extends Trigger> {
    
    public void setParameters(Map<String, List<? extends Object>> parameters);
}
