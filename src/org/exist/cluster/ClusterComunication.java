//$Id$
package org.exist.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.exist.cluster.cocoon.ConsoleInfo;
import org.exist.cluster.journal.JournalIdGenerator;
import org.exist.cluster.journal.JournalManager;
import org.exist.util.Configuration;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.SuspectedException;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;

/**
 * Manage the Cluster communication via RPC JGroups
 * Created by Nicola Breda.
 *
 * @author Nicola Breda aka maiale
 * @author David Frontini aka spider
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class ClusterComunication implements MembershipListener {

	public static final String CONFIGURATION_ELEMENT_NAME = "cluster";
	public static final String CLUSTER_PROTOCOL_ATTRIBUTE = "protocol";
	public static final String CLUSTER_USER_ATTRIBUTE = "dbaUser";	
	public static final String CLUSTER_PWD_ATTRIBUTE = "dbaPassword";
	public static final String CLUSTER_EXCLUDED_COLLECTIONS_ATTRIBUTE = "exclude"; 
	
	public static final String PROPERTY_CLUSTER_PROTOCOL = "cluster.protocol";
	public static final String PROPERTY_CLUSTER_USER = "cluster.user";
	public static final String PROPERTY_CLUSTER_PWD = "cluster.pwd";
	public static final String PROPERTY_CLUSTER_EXCLUDED_COLLECTIONS = "cluster.exclude";
	
	private static Logger log = Logger.getLogger(ClusterComunication.class);

    private static JChannel channel;
    private static RpcDispatcher disp;

    private static final String banner =
            " #####  #       #     #  #####  ####### ####### ######\n" +
            "#     # #       #     # #     #    #    #       #     #\n" +
            "#       #       #     # #          #    #       #     #\n" +
            "#       #       #     #  #####     #    #####   ######\n" +
            "#       #       #     #       #    #    #       #   #\n" +
            "#     # #       #     # #     #    #    #       #    #\n" +
            " #####  #######  #####   #####     #    ####### #     #\n" +
            "\n" +
            "\n" +
            " ######  #    #     #     ####    #####\n" +
            " #        #  #      #    #          #\n" +
            " #####     ##       #     ####      #\n" +
            " #         ##       #         #     #\n" +
            " #        #  #      #    #    #     #\n" +
            " ######  #    #     #     ####      #";


    public static final String DEFAULT_PROTOCOL_STACK =
            "UDP(mcast_addr=228.1.2.3;mcast_port=45566;ip_ttl=32;loopback=true):" +
            "PING(timeout=3000;num_initial_members=6):" +
            "FD(timeout=3000):" +
            "VERIFY_SUSPECT(timeout=1500):" +
            "pbcast.NAKACK(gc_lag=10;retransmit_timeout=600,1200,2400,4800):" +
            "UNICAST(timeout=600,1200,2400,4800):" +
            "pbcast.STABLE(desired_avg_gossip=10000):" +
            "FRAG:" +
            "pbcast.GMS(join_timeout=5000;join_retry_timeout=2000;" +
            "shun=true;print_local_addr=true)";

    private static ClusterComunication instance;

    private Vector membersNoSender = new Vector();
    private Address localAddress;
    private Address coordinatorAddress;

    private static String dbaUser;
    private static String dbaPwd;
    private static ArrayList excludedCollection;

    private JournalManager journalManager;
    private JournalIdGenerator journalIdGenerator;
    private boolean coordinator = false;

    private boolean isRealign = true;
    private ArrayList realignQueue = new ArrayList();
    private boolean viewConfigured = false;
    private int shift;

    private Configuration configuration;

    public static String getDbaUser() {
        return dbaUser;
    }

    public static String getDbaPwd() {
        return dbaPwd;
    }

    private static void createInstance(Configuration conf) throws ClusterException {
        ClusterComunication c = new ClusterComunication();

        System.out.println(banner);

        try {
            String protocol = (String) conf.getProperty(PROPERTY_CLUSTER_PROTOCOL);
            dbaUser = (String) conf.getProperty(PROPERTY_CLUSTER_USER);
            dbaPwd = (String) conf.getProperty(PROPERTY_CLUSTER_PWD);
            excludedCollection = (ArrayList) conf.getProperty(PROPERTY_CLUSTER_EXCLUDED_COLLECTIONS);

            if (protocol == null)
                protocol = DEFAULT_PROTOCOL_STACK;

            System.out.println("PROTOCOL \n" + protocol);

            channel = new JChannel(protocol);

            disp = new RpcDispatcher(channel, null, c, c);
            disp.setDeadlockDetection(true);

            c.configuration = conf;

            c.journalManager = new JournalManager(conf);

            c.journalIdGenerator = new JournalIdGenerator(c.journalManager, ((Integer)conf.getProperty(JournalManager.PROPERTY_CLUSTER_JOURNAL_MAXSTORE)).intValue());

            c.shift = ((Integer)conf.getProperty(JournalManager.PROPERTY_CLUSTER_JOURNAL_SHIFT)).intValue();

            instance = c;

            channel.connect("eXist-cluster");
            c.localAddress = channel.getLocalAddress();

            while(!c.viewConfigured){
                log.info("SLEEPING - WAITING TO CONFIGURE THE CLUSTER");
                Thread.sleep(2000);
            }

            if(c.isRealign){
                log.info("TRY TO REALIGNING " + Thread.currentThread().getName());
                c.realign();
                c.isRealign = false;
            }
            log.info("REALIGNED ... "+ Thread.currentThread().getName());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error during cluster JGroups environment configuration " + e);
            throw new ClusterException("ERROR CREATING CLUSTER ...",e);
        }
    }

    private ClusterComunication() {
    }

    /**
     * ----------------   MEMBERSHIP LISTENER METHODS   ------------------------------   ****
     */

    public void viewAccepted(View view) {
        this.coordinatorAddress = view.getCreator(); // The master address of the cluster
        boolean coordinator = coordinatorAddress.equals(localAddress);
        log.info("COordinator : " + coordinator + " localAddress : " + localAddress);
        if(coordinator)
            log.info("***************** I'M MASTER!!!!!!!!!");
        //Per evitare problematiche di sincronizzazione in caso di failure - il nuovo master sposta in avanti i suoi indici
        //in modo da compensare possibili disallineamenti.
        if (coordinator && !this.coordinator && journalIdGenerator!=null) {
            journalIdGenerator.shiftId(shift);
        }
        this.coordinator = coordinatorAddress.equals(localAddress); //check if this node is a master

        Vector members = (Vector) view.getMembers().clone();
        members.removeElement(channel.getLocalAddress());

        this.membersNoSender = members; //all members into the cluster

        viewConfigured = true;


    }

    public void suspect(Address address) {
        if(coordinatorAddress.equals(address)){
            log.info("MASTER IS DEAD");
        }
    }

    public void block() {
    }

    /**
     * ********** ---------------------------------------------------------  **********
     */

    public static ClusterComunication getInstance() {
        return instance;
    }


    /**
     * **************  --------- CONSOLE METHODS ---------- *******************************
     */
    public boolean isCoordinator(){
        return coordinator;
    }

    public Address getCoordinator(){
        return coordinatorAddress;
    }

    public Address getAddress(){
        return localAddress;
    }

    public Vector getMembersNoCoordinator(){
        Vector members =  (Vector) membersNoSender.clone();
        members.remove(coordinatorAddress);
        return members;
    }

    public HashMap getConsoleInfos(Vector address){

        HashMap response = new HashMap();

        RspList list = disp.callRemoteMethods(address, "getConsoleProperties", new Object[]{}, new Class[]{}, GroupRequest.GET_ALL, 0);

        for(int i=0;i<address.size();i++){
            Address addr = (Address) address.get(i);
            response.put(addr.toString(),list.get(addr));
        }

        return response;

    }

    public int[][] getHeaders() throws ClusterException {
        int[][] data = new int[2][];

        data[0] = new int[]{journalManager.getLastIdSaved(),journalManager.getMaxIdSaved(),journalManager.getCounter()};
        try{
        if(!coordinator)
            data[1] = (int[]) disp.callRemoteMethod(coordinatorAddress, "getRemoteHeader", new Object[]{}, new Class[]{}, GroupRequest.GET_FIRST, 0);

        }catch(Throwable e) {
            e.printStackTrace();
            throw new ClusterException("Error retrieving ...",e );
        }

        return data;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * **************  ---------------------------------------- *******************************
     */


    /**
     * Configure the cluster communication
     *
     * @param c
     */
    public static void configure(Configuration c) throws ClusterException {
        createInstance(c);
    }

    public void synch() throws ClusterException {
        journalManager.squeueEvent();
    }


    public void removeDocument(String collection, String documentName) throws ClusterException {
        if (excludedCollection.contains(collection))
            return;
        remoteInvocation(new RemoveClusterEvent(documentName, collection));
    }

    public void storeDocument(String collection, String documentName, String content) throws ClusterException {
        if (excludedCollection.contains(collection))
            return;
        remoteInvocation(new StoreClusterEvent(content, collection, documentName));
    }

    public void addCollection(String parent, String collectionName) throws ClusterException {
        if (excludedCollection.contains(parent) || excludedCollection.contains(parent + "/" + collectionName))
            return;

        remoteInvocation(new CreateCollectionClusterEvent(parent, collectionName));
    }

    public void update(String resource, String name, String xupdate) throws ClusterException {
        if (excludedCollection.contains(resource))
            return; //avoid to propagate the internal collection for example temp.
        remoteInvocation(new UpdateClusterEvent(resource, name, xupdate));
    }

    public void removeCollection(String parent, String collection) throws ClusterException {
        if (excludedCollection.contains(collection)|| excludedCollection.contains(parent + "/" + collection))
            return; //avoid to propagate the internal collection for example temp.
        remoteInvocation(new RemoveCollectionClusterEvent(parent, collection));
    }

    private void remoteInvocation(ClusterEvent event) throws ClusterException {
        String code = "" + event.hashCode();

        if (!ClusterChannel.hasToBePublished(code)) {
            ClusterChannel.removeEvent(code);
            return;
        }
        int[] data = getId(true);
        event.setId(data[0]);
        event.setCounter(data[1]);

        journalManager.enqueEvent(event); //add event to the journal queue
        disp.callRemoteMethods(membersNoSender, "invoke", new Object[]{event}, new Class[]{ClusterEvent.class}, GroupRequest.GET_NONE, 0);

        if (!coordinator)
            journalIdGenerator.increaseId(event.getId(), event.getCounter());

    }

    /**
     * Retrieve the id for the journal
     *
     * @return the unique id
     * @throws ClusterException
     * @param firstRequest
     */
    private int[] getId(boolean firstRequest) throws ClusterException {
        try {
            int[] id;
            if (coordinator) { //if I'am a master - create next id
                log.info("GENERATING LOCAL ID...");
                id = journalIdGenerator.getNextData(localAddress.toString());
            } else { // ask to the master the next id --> rpc to getNextDataRemote
                log.info("RETRIEVING ID FROM " + coordinatorAddress);
                Object idObj = disp.callRemoteMethod(coordinatorAddress, "getNextDataRemote", new Object[]{localAddress.toString()}, new Class[]{String.class}, GroupRequest.GET_FIRST, 0);
                id = ((int[]) idObj);
            }
            return id;
        }catch (SuspectedException se){
            if(!firstRequest)
                throw new ClusterException("unable to retrieve the journal id... master down ... no more retry ", se);
            log.info("SUSPECTED MASTER SHUTDOWN .... RETRY...");
            try {
                log.info("WAITING FOR NEW MASTER");
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            return getId(false);
        }catch (Exception e) {
            throw new ClusterException("unable to retrieve the journal id ", e);
        }
    }

    private void realign() throws ClusterException {
        if(coordinator)
            return; //TODO: per ora assumiamo che il master (o chi diventa master) sia allineato.
        int last = ClusterEvent.NO_EVENT;
        try{
            ArrayList events = null;
            int[] header = new int[]{journalManager.getLastIdSaved(),journalManager.getMaxIdSaved(),journalManager.getCounter()};
            int[] remoteHeader = (int[]) disp.callRemoteMethod(coordinatorAddress, "getRemoteHeader", new Object[]{}, new Class[]{}, GroupRequest.GET_FIRST, 0);

            int counterDiff =Math.abs(header[2]-remoteHeader[2]);
            if(counterDiff>1)
                killNoRealign();

            if(counterDiff==1 && remoteHeader[1]>header[1])
                killNoRealign();

            if(counterDiff==0 && header[1]>remoteHeader[1])
                killClusterMasterDisaligned();


            while(true) {
                log.info("Call remote method getNextEvents: " + Thread.currentThread().getName());
                Object idObj = disp.callRemoteMethod(coordinatorAddress, "getNextEvents", new Object[] {header, remoteHeader, new Integer(last)}, new Class[] {int[].class, int[].class, Integer.class }, GroupRequest.GET_FIRST, 0);
                events = ((ArrayList) idObj);

                if( events==null || events.size() == 0 )
                    break;

                last = manageEvents(events);
                log.info("Last id managed : " + last);
            }

            synchronized(realignQueue){
                while(realignQueue.size()>0) { //execute the queue ....
                    ClusterEvent event = (ClusterEvent) realignQueue.remove(0);
                    log.info("Execute the event " + event.getId() );
                    ClusterChannel.accountEvent(""+event.hashCode());
                    if(journalManager.isProcessed(event) ) {
                        log.info("Event  processed ..........");
                        continue;
                    }
                    manageEvent(event);
                }
            }
            isRealign = false;
        }catch(Throwable e) {
            e.printStackTrace();
            log.error("No align done successfully ...");
            throw new ClusterException("No align done successfully ...",e );
        }
    }

    private void killClusterMasterDisaligned() {
        log.fatal("MASTER DISALIGNED... CLUSTER DATA MAY BE CORRUPTED");
        log.fatal("PLEASE STOP CLUSTER AND FIX COLLECTION AND JOURNAL DATA");
        //TODO ... to be implemented... MUSTER DISALIGNED
    }

    private void killNoRealign() throws ClusterException {
        log.fatal("NODE DISALIGNED... no hot realignement available.... please fix node collection and journal data");
        throw new ClusterException("NODE DISALIGNED");
    }

    private int manageEvents(ArrayList events) throws ClusterException {
        for(int i = 0; i < events.size() ; i++) {
            ClusterEvent event = (ClusterEvent) events.get(i);
            log.info("Manage event id " + event.getId());
            if(journalManager.isProcessed(event))
            {
                log.info("event already processed .........");
                continue;
            }
            ClusterChannel.accountEvent("" + event.hashCode());
            manageEvent(event);
        }
        return ((ClusterEvent)events.get(events.size() - 1)).getId();
    }

    private void manageEvent(ClusterEvent event) throws ClusterException {
        event.execute();

        journalManager.enqueEvent(event);

        if (coordinator)
            journalIdGenerator.releaseId(event.getId());
        else
            journalIdGenerator.increaseId(event.getId(), event.getCounter());
    }



    /* -------------- REMOTE METHODS --------------------- */

    public ArrayList getNextEvents(int[] header, int[] myHeader, Integer start){
        return journalManager.getNextEvents(header,myHeader,start);
    }


    public int[] getNextDataRemote(String address) {
        return journalIdGenerator.getNextData(address);
    }


    public void invoke(ClusterEvent event) throws ClusterException {
         String code = "" + event.hashCode();
         ClusterChannel.accountEvent(code); //reentrant fix

             synchronized(realignQueue){
                 if(isRealign){
                     realignQueue.add(event);
                     return;
                 }
             }

        manageEvent(event);

    }

    public int[] getRemoteHeader() throws ClusterException {
        return new int[]{journalManager.getLastIdSaved(),journalManager.getMaxIdSaved(),journalManager.getCounter()};
    }

    public ConsoleInfo getConsoleProperties() throws ClusterException{
        String port = System.getProperty("jetty.port");

        if(port==null)
            port = "8080";   //TODO ... verify how to retrieve default port

        ConsoleInfo info = new ConsoleInfo();
        info.setProperty("port",port);
        return info;
    }


    public void stop() {
        disp.stop();
        channel.disconnect();
        instance = null;
    }

}
