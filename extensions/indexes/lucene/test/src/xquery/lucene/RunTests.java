package xquery.lucene;


import static org.junit.Assert.fail;

import java.io.File;

import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import xquery.TestRunner;

public class RunTests extends TestRunner {

    @Override
    protected String getDirectory() {
        return "extensions/indexes/lucene/test/src/xquery/lucene";
    }
}
