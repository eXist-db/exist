package org.exist.jboss.exist;


import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.EXistException;
import org.jboss.system.ServiceMBeanSupport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This service handles the lifecycle of the eXist XML database
 *
 * @author Per Nyfelt
 */
public class EXistService extends ServiceMBeanSupport implements EXistServiceMBean {

    protected String confFile;
    protected Configuration configuration;
    protected String eXistHome;

    private static final String DEFAULT_CONFIG = "<?xml version='1.0'?> " +
        "<exist> " +
            "<db-connection database='native' files='data' " +
	            "buffers='512' words_buffers='8192' " +
	            "elements_buffers='1024' free_mem_min='2000000' " +
	            "grow='32' compress='false'/> " +
            "<indexer batchLoad='true' tmpDir='tmp' " +
	            "stemming='false' controls='ctl' caseSensitive='false' " +
	            "suppress-whitespace='both'> " +
                "<stopwords file='stopword'/> " +
            "</indexer> " +
        "</exist>";

    public String getEXistHome() {
        return eXistHome;
    }

    public void setEXistHome(String existHome) {
        this.eXistHome = existHome;
    }

    protected void startService() throws Exception {
        // get path to the deploy target directory
        String jbossServerDir = System.getProperty("jboss.server.home.dir");
        log.debug("jbossServerDir is " + jbossServerDir + ", eXistHome is " + eXistHome);
        File eXistHomeDir = new File(jbossServerDir, eXistHome);
        log.debug("eXistHomeDir set to " + eXistHomeDir);
        eXistHome = eXistHomeDir.getAbsolutePath();

        log.debug("eXistHome set to " + eXistHome);
        if (!eXistHomeDir.exists()) {
            log.info("exist home directory not found at " + eXistHome + ", creating new directory");
            eXistHomeDir.mkdirs();
        }
        System.setProperty("exist.home", eXistHome);

        File dataDir = new File(eXistHomeDir, "data");
        if (!dataDir.exists()) {
            log.info("creating data dir in eXist home");
            dataDir.mkdir();
        }

        confFile = new File(eXistHome, "conf.xml").getAbsolutePath();
        log.debug("confFile set to " + confFile);
        File f = new File(confFile);
        if (!f.exists()) {
            log.info("Config file does not exist, creating default configuration...");
            createDefaultConfigFile(f);
        }
        if (!f.canRead()) {
            throw new IOException("configuration file " + confFile + " is not readable");
        }
        configuration = new Configuration(confFile, eXistHome);
        if (configuration == null) {
            throw new Exception("Failed to create configuration for database");
        }
        if (!BrokerPool.isConfigured()) {
            log.debug("Configuring database");
            BrokerPool.configure(1, 5, configuration);
        }
    }

    private void createDefaultConfigFile(File file) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write(DEFAULT_CONFIG);
        writer.flush();
        writer.close();
    }

    protected void stopService() throws Exception {
        if (BrokerPool.isConfigured()) {
            BrokerPool.stop();
        }
    }

    /**
     * This is for the JMX HTML adapter to display som status info about the server
     * @return an HTML string containing the db stauts
     */
    public String getStatus() {
        String output = "";
        try {
            if (!BrokerPool.isConfigured())
                output += "<p>Server is not running ...</p>";
            else {
                output += "<p>The database server is running ...</p>";

                BrokerPool pool = BrokerPool.getInstance();
                Configuration conf = pool.getConfiguration();
                output += "<table  width=\"80%\"><tr> <th colspan=\"2\" align=\"left\" bgcolor=\"#0086b2\"><b>Status</b></th></tr>";
                output += "<tr><td>Configuration:</td><td>" + conf.getPath() + "</td></tr>";
                output += "<tr><td>Data directory:</td><td>" + (String) conf.getProperty("db-connection.data-dir") + "</td></tr>";
                output += "<tr><td>Active instances:</td><td>" + pool.active() + "</td></tr>";
                output += "<tr><td>Available instances:</td><td>" + pool.available() + "</td></tr>";
                output += "</table>";
            }
        } catch (EXistException e) {
            output += e.toString();
        }
        return output;
    }
}
