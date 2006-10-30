//$Id$
package org.exist.cluster;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *         Date: Aug 30, 2004
 *         Time: 3:58:41 PM
 *         Revision $Revision$
 */
public class ClusterException extends Exception {
    public ClusterException() {
    }

    public ClusterException(String s) {
        super(s);
    }

    public ClusterException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ClusterException(Throwable throwable) {
        super(throwable);
    }
}
