//$Id$
package org.exist.cluster.journal.test;

import java.io.File;
import java.util.ArrayList;

import junit.framework.TestCase;

import org.exist.cluster.ClusterEvent;
import org.exist.cluster.CreateCollectionClusterEvent;
import org.exist.cluster.RemoveClusterEvent;
import org.exist.cluster.StoreClusterEvent;
import org.exist.cluster.journal.JournalIdGenerator;
import org.exist.cluster.journal.JournalManager;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;

/**
 */
public class JournalManagerTest extends TestCase{

    Configuration configuration;

    protected void setUp() {
    	try  {
	        configuration = new Configuration("conf.xml", System.getProperty("exist.home",".") );
                File existDir = new File(System.getProperty("exist.home","."));
	        File temp = new File(existDir,"test");
	        String[] files = temp.list();
                if (files!=null) {
                   for( int i=0; i<files.length; i++)
                   {
                       String fName = files[i];
                       if( fName.indexOf("jbx") > 0 )
                       {
                           File f = new File(temp, fName);
                           f.delete();
                       }
                   }
                }
    	} catch (Exception e) {
           e.printStackTrace();
    		fail(e.getMessage());    		
    	}	        
    }

    public void testWriteEventJournal() {
        saveEvent( new JournalManager( configuration ), 0 );
    }

    public void testWriteRead() {
        JournalManager journal = new JournalManager( configuration );
        saveEvent( journal , 0 );
        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test", ((CreateCollectionClusterEvent)ev).getCollectionName());
    }

    public void testMultiShuffleWriteRead() {
        JournalManager journal = new JournalManager( configuration );
        saveEvent( journal , 0 );
        saveEvent( journal , "test1", 1 );
        saveEvent( journal , "test2", 2 );
        saveEvent( journal , "test3", 3 );
        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 3 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 3, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION +  "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test3", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 2 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 2, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test2", ((CreateCollectionClusterEvent)ev).getCollectionName());
    }

    public void testMultiShuffleWriteReadMultiEvents() {
        JournalManager journal = new JournalManager( configuration );
        saveEvent( journal , 0 );
        saveEvent( journal , "test1", 1 );
        saveEvent( journal , "test2", 2 );
        saveEvent( journal , "test3", 3 );
        ClusterEvent r = new RemoveClusterEvent("doc","collection");
        r.setId(4);
        saveEvent( journal, r);

        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 3 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 3, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION +  "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test3", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 2 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 2, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test2", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 4 );
        assertTrue( "Wrong event class", ev instanceof RemoveClusterEvent);
        assertEquals("Wrong id", 4, ev.getId());
        assertEquals("Wrong docNAme value", "doc", ((RemoveClusterEvent)ev).getDocumentName());
        assertEquals("Wrong collectionName value", "collection", ((RemoveClusterEvent)ev).getCollectionName());
    }

    public void testMultiShuffleWriteReadStoreDocument() {
        JournalManager journal = new JournalManager( configuration );
        String content = getExternalXML();
        StoreClusterEvent s = new StoreClusterEvent( content, "name", "docu");
        s.setId( 1 );
        saveEvent( journal , s );
        saveEvent( journal , "test4", 4 );
        saveEvent( journal , "test2", 2 );
        saveEvent( journal , "test3", 0 );

        s = new StoreClusterEvent( content, "name2", "docu2");
        s.setId(3);
        saveEvent( journal, s);

        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test3", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 3 );
        assertTrue( "Wrong event class " + ev.getClass().getName(), ev instanceof StoreClusterEvent);
        assertEquals("Wrong id", 3, ev.getId());
        assertEquals("Wrong parent value", "docu2", ((StoreClusterEvent)ev).getDocumentName());
        assertEquals("Wrong collectionName value", "name2", ((StoreClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 2 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 2, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test2", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 4 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 4, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test4", ((CreateCollectionClusterEvent)ev).getCollectionName());
    }


    public void testMultiShuffleWriteReadWithQueue() {
        JournalManager journal = new JournalManager( configuration );
        saveEvent( journal , 0 );
        saveEvent( journal , "test1", 1 );
        saveEvent( journal , "test3", 3 );
        saveEvent( journal , "test2", 2 );
        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 3 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 3, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test3", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 2 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 2, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test2", ((CreateCollectionClusterEvent)ev).getCollectionName());
    }


    public void testMultiShuffleWriteReadWithRotation() {
        JournalManager journal = new JournalManager( configuration );
        String content = getExternalXML();
        StoreClusterEvent s = new StoreClusterEvent( content, "name", "docu");
        s.setId( 1 );
        saveEvent( journal , s );
        saveEvent( journal , "test4", 4 );
        saveEvent( journal , "test2", 2 );
        saveEvent( journal , "test3", 0 );

        s = new StoreClusterEvent( content, "name2", "docu2");
        s.setId(3);
        saveEvent( journal, s);

        s = new StoreClusterEvent( content, "name3", "docu3");
        s.setId(JournalIdGenerator.MAX_STORED_INDEX-100);
        saveEvent( journal, s);

        s = new StoreClusterEvent( content, "name4", "docu4");
        s.setId(2);
        s.setCounter(2);
        saveEvent( journal, s);

        ClusterEvent ev = readEvent( journal, 0 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 0, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test3", ((CreateCollectionClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 3 );
        assertTrue( "Wrong event class " + ev.getClass().getName(), ev instanceof StoreClusterEvent);
        assertEquals("Wrong id", 3, ev.getId());
        assertEquals("Wrong parent value", "docu2", ((StoreClusterEvent)ev).getDocumentName());
        assertEquals("Wrong collectionName value", "name2", ((StoreClusterEvent)ev).getCollectionName());

        ev = readEvent( journal, 2 );
        assertTrue( "Wrong event class", ev instanceof StoreClusterEvent);
        assertEquals("Wrong id", 2, ev.getId());
        assertEquals("Wrong parent value", "docu4", ((StoreClusterEvent)ev).getDocumentName());
        assertEquals("Wrong collectionName value", "name4", ((StoreClusterEvent)ev).getCollectionName());
        assertEquals("Wrong counter",2,ev.getCounter());

        ev = readEvent( journal, 4 );
        assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
        assertEquals("Wrong id", 4, ev.getId());
        assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
        assertEquals("Wrong collectionName value", "test4", ((CreateCollectionClusterEvent)ev).getCollectionName());

        assertEquals("Wrong lastIdSaved",2,journal.getLastIdSaved());
        assertEquals("Wrong maxIdSaved",2,journal.getMaxIdSaved());
        assertEquals("Wrong counter",2,journal.getCounter());
    }

    public void testRetrieveEvents() {
       JournalManager journal = new JournalManager( configuration );
       saveEvent( journal , 0 );
       saveEvent( journal , "test1", 1 );
       saveEvent( journal , "test2", 2 );
       saveEvent( journal , "test3", 3 );

       ArrayList events = journal.getNextEvents( new int[]{0,0,1}, new int[]{3,3,1}, new Integer(-1));
       assertNotNull( "Wrong null events", events );
       assertEquals("Wrong event size", 1, events.size());

       int idR = ((ClusterEvent)events.get(0)).getId();
       events = journal.getNextEvents( new int[]{0,0,1}, new int[]{3,3,1}, new Integer(idR));

       assertNotNull( "Wrong null events", events );
       assertEquals("Wrong event size", 4, events.size());

       ClusterEvent ev = (ClusterEvent) events.get(2);
       assertTrue( "Wrong event class", ev instanceof CreateCollectionClusterEvent);
       assertEquals("Wrong id", 2 , ev.getId());
       assertEquals("Wrong parent value", DBBroker.ROOT_COLLECTION + "/test", ((CreateCollectionClusterEvent)ev).getParent());
       assertEquals("Wrong collectionName value", "test2", ((CreateCollectionClusterEvent)ev).getCollectionName());

        
    }

    public void testRotation() {
      //TODO: aggiungere test sulla rotazione....... opportuno creare una classe a parte di test.
    }

    private void saveEvent(JournalManager journal,  int idTest) {
        saveEvent(journal, "test", idTest);
    }

    private void saveEvent(JournalManager journal, String collectionName, int idTest) {
        CreateCollectionClusterEvent ev = new CreateCollectionClusterEvent( DBBroker.ROOT_COLLECTION + "/test", collectionName);
        ev.setId(idTest);
        saveEvent(journal, ev);
    }

    private void saveEvent(JournalManager journal, ClusterEvent ev) {
    	try {
	        journal.enqueEvent( ev );
	        journal.squeueEvent();
    	} catch (Exception e) {
    		fail(e.getMessage());    		
    	}		        
    }


    private ClusterEvent readEvent(JournalManager journal, int i)
    {
        return journal.read( i );
    }

    private String getExternalXML()
    {
        return "<?xml version=\"1.0\"?>\n" +
                "\n" +
                "<!--    This build file sets up the example XML files provided in\n" +
                "        the distribution.\n" +
                "\n" +
                "        Call it with \n" +
                "        \n" +
                "        build.sh -f example-setup.xml\n" +
                "\n" +
                "        or\n" +
                "\n" +
                "        build.bat -f example-setup.xml\n" +
                "-->\n" +
                "<project basedir=\".\" default=\"store\" name=\"exist-ant-tasks\">\n" +
                "\n" +
                "\t<path id=\"classpath.core\">\n" +
                "\t\t<fileset dir=\"lib/core\">\n" +
                "\t\t\t<include name=\"*.jar\"/>\n" +
                "\t\t</fileset>\n" +
                "        <pathelement path=\"exist.jar\"/>\n" +
                "        <pathelement path=\"exist-optional.jar\"/>\n" +
                "\t</path>\n" +
                "\n" +
                "\t<typedef resource=\"org/exist/ant/antlib.xml\"\n" +
                "\t\turi=\"http://exist-db.org/ant\">\n" +
                "\t\t<classpath refid=\"classpath.core\"/>\n" +
                "\t</typedef>\n" +
                "\t\n" +
                "\t<target name=\"store\" xmlns:xmldb=\"http://exist-db.org/ant\">\n" +
                "        <xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc" + DBBroker.ROOT_COLLECTION + "/shakespeare/plays\"\n" +
                "\t\t\tcreatecollection=\"true\">\n" +
                "            <fileset dir=\"samples/shakespeare\"> \n" +
                "                <include name=\"*.xml\"/>\n" +
                "                <include name=\"*.xsl\"/>\n" +
                "            </fileset>\n" +
                "\t\t</xmldb:store>\n" +
                "\n" +
                "        <xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc/" + DBBroker.ROOT_COLLECTION + "/shakespeare/plays\"\n" +
                "\t\t\ttype=\"binary\">\n" +
                "\t\t\t<fileset dir=\"samples/shakespeare\">\n" +
                "\t\t\t\t<include name=\"*.css\"/>\n" +
                "\t\t\t</fileset>\n" +
                "\t\t</xmldb:store>\n" +
                "\t\t\n" +
                "        <xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc/" + DBBroker.ROOT_COLLECTION + "/library\"\n" +
                "\t\t\tcreatecollection=\"true\">\n" +
                "\t\t\t<fileset dir=\"samples\" includes=\"biblio.rdf\"/>\n" +
                "\t\t</xmldb:store>\n" +
                "\n" +
                "        <xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc"+ DBBroker.ROOT_COLLECTION + "/xinclude\"\n" +
                "\t\t\tcreatecollection=\"true\">\n" +
                "\t\t\t<fileset dir=\"samples/xinclude\" includes=\"**.xml\"/>\n" +
                "        </xmldb:store>\n" +
                "\n" +
                "        <xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc" + DBBroker.ROOT_COLLECTION + "\">\n" +
                "            <fileset dir=\"samples\" includes=\"examples.xml\"/>\n" +
                "        </xmldb:store>\n" +
                "\n" +
                "\t\t<xmldb:store uri=\"xmldb:exist://localhost:8080/exist/xmlrpc" + DBBroker.ROOT_COLLECTION + "/mods\">\n" +
                "            <fileset dir=\"mods\" includes=\"**.xml\"/>\n" +
                "        </xmldb:store>\n" +
                "\t</target>\n" +
                "</project>";
    }

}
