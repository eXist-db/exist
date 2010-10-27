package org.exist.security.realm;

import java.util.List;

/**
 *
 * @author aretter
 */
public interface TransformationContext {

    public List<String> getAdditionalGroups();
}
