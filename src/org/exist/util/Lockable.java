package org.exist.util;

import org.exist.storage.lock.Lock;

/**
 * @author wolf
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public interface Lockable {

    public Lock getLock();
    
}
