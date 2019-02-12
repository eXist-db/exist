package org.exist.security.realm;

import java.util.List;

/**
 * @author aretter
 */
public interface TransformationContext {
    List<String> getAdditionalGroups();
}
