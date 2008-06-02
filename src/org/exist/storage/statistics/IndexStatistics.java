package org.exist.storage.statistics;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Collects statistics on the distribution of elements in the database.
 * This is not really an index, though it sits in the indexing pipeline to
 * gather its statistics.
 *
 * The class maintains a graph structure which describes the frequency
 * and depth of elements in the database (see @link DataGuide). This forms
 * the basis for advanced query optimizations.
 */
public class IndexStatistics extends AbstractIndex {

    public final static String ID = IndexStatistics.class.getName();

    protected final static Logger LOG = Logger.getLogger(IndexStatistics.class);

    private File dataFile;
    private DataGuide dataGuide = new DataGuide();
    
    public IndexStatistics() {
    }

    public String getIndexId() {
        return ID;
    }

    public int getMaxParentDepth(QName qname) {
        return dataGuide.getMaxParentDepth(qname);
    }

    protected void mergeStats(DataGuide other) {
        dataGuide = other.mergeInto(dataGuide);
    }

    protected void updateStats(DataGuide newGuide) {
        dataGuide = newGuide;
    }

    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        String fileName = "stats.dbx";
        if (config.hasAttribute("file"))
            fileName = config.getAttribute("file");
        dataFile = new File(dataDir, fileName);
    }

    public void open() throws DatabaseConfigurationException {
        dataGuide = new DataGuide();
        if (dataFile.exists()) {
            try {
                long start = System.currentTimeMillis();
                FileInputStream is = new FileInputStream(dataFile);
                FileChannel fc = is.getChannel();
                dataGuide.read(fc, getBrokerPool().getSymbols());
                is.close();
                if (LOG.isDebugEnabled())
                    LOG.debug("Reading " + dataFile.getName() + " took " +
                        (System.currentTimeMillis() - start) + "ms. Size of " +
                        "the graph: " + dataGuide.getSize());
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                throw new DatabaseConfigurationException("Error while loading " +
                    dataFile.getAbsolutePath() + ": " + e.getMessage(), e);
            }
        }
    }

    public void close() throws DBException {
    }

    public void sync() throws DBException {
        try {
            FileOutputStream os = new FileOutputStream(dataFile);
            FileChannel fc = os.getChannel();
            dataGuide.write(fc, getBrokerPool().getSymbols());
            os.close();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new DBException("Error while writing " + dataFile.getAbsolutePath() +
                    ": " + e.getMessage());
        }
    }

    public void remove() throws DBException {
        dataFile.delete();
    }

    public IndexWorker getWorker(DBBroker broker) {
        return new IndexStatisticsWorker(this);
    }

    public boolean checkIndex(DBBroker broker) {
        return true;
    }

    public void toSAX(ContentHandler handler) throws SAXException {
        dataGuide.toSAX(handler);
    }

    public String toString() {
        return dataGuide.toString();
    }
}
