//$Id$
package org.exist;

import org.exist.cluster.ClusterReceiver;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *
 *         Date: 27-gen-2005
 *         Time: 14.32.04
 *         Revision $Revision$
 */
public class ClusterStart extends JettyStart {

     ClusterReceiver clusterService = new ClusterReceiver();

    /**
     * Start a Jetty Server with a Cluster Flavour
     * @param args
     */
    public static void main(String[] args){
        System.out.println("Cluster : starting cluster (braulio edition)");

        ClusterStart cluster = new ClusterStart();
        cluster.run( args );
    }

    /**
     * Shutdown system and clustering
     */
    public void shutdown() {
        clusterService.stop();
        super.shutdown();
    }

    /**
     * Start Jetty and the Cluster service
     * @param args
     */
    public void run(String[] args) {
        new Thread(clusterService).start();
        super.run(args);
    }
}
