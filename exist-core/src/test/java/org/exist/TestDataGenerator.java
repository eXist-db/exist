package org.exist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Helper class to generate test documents from a given XQuery.
 */
public class TestDataGenerator {

    private static final Logger LOG = LogManager.getLogger(TestDataGenerator.class);

    private final static Properties outputProps = new Properties();
    static {
        outputProps.setProperty(OutputKeys.INDENT, "yes");
    }

    private final static String IMPORT =
            "import module namespace pt='http://exist-db.org/xquery/test/performance' " +
            "at 'java:org.exist.performance.xquery.PerfTestModule';\n" +
            "declare variable $filename external;\n" +
            "declare variable $count external;\n";

    private String prefix;
    private int count;
    private Path[] generatedFiles;

    public TestDataGenerator(final String prefix, final int count) {
        this.prefix = prefix;
        this.count = count;
        this.generatedFiles = new Path[count];
    }

    public Path[] generate(final DBBroker broker, final Collection collection, final String xqueryContent) throws SAXException {
        try {
            final DocumentSet docs = collection.allDocs(broker, new DefaultDocumentSet(), true);
            final XQuery service = broker.getBrokerPool().getXQueryService();
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            context.declareVariable("filename", "");
            context.declareVariable("count", "0");
            context.setStaticallyKnownDocuments(docs);

            final String query = IMPORT + xqueryContent;

            final CompiledXQuery compiled = service.compile(broker, context, query);

            for (int i = 0; i < count; i++) {
                generatedFiles[i] = Files.createTempFile(prefix, ".xml");

                context.declareVariable("filename", generatedFiles[i].getFileName().toString());
                context.declareVariable("count", new Integer(i));
                final Sequence results = service.execute(broker, compiled, Sequence.EMPTY_SEQUENCE);

                final Serializer serializer = broker.getSerializer();
                serializer.reset();
                try(final Writer out = Files.newBufferedWriter(generatedFiles[i], StandardCharsets.UTF_8)) {
                    final SAXSerializer sax = new SAXSerializer(out, outputProps);
                    serializer.setSAXHandlers(sax, sax);
                    for (final SequenceIterator iter = results.iterate(); iter.hasNext(); ) {
                        final Item item = iter.nextItem();
                        if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                            continue;
                        }
                        serializer.toSAX((NodeValue) item);
                    }
                }
            }
        } catch (final XPathException | PermissionDeniedException | LockException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new SAXException(e.getMessage(), e);
        }
        return generatedFiles;
    }

    public Path[] generate(final org.xmldb.api.base.Collection collection, final String xqueryContent) throws SAXException {
        final String query = IMPORT + xqueryContent;
        try {
            final XQueryService service = (XQueryService) collection.getService("XQueryService", "1.0");
            service.declareVariable("filename", "");
            service.declareVariable("count", "0");
            final CompiledExpression compiled = service.compile(query);

            for (int i = 0; i < count; i++) {
                generatedFiles[i] = Files.createTempFile(prefix, ".xml");

                service.declareVariable("filename", generatedFiles[i].getFileName().toString());
                service.declareVariable("count", new Integer(i));
                final ResourceSet result = service.execute(compiled);

                try(final Writer out = Files.newBufferedWriter(generatedFiles[i], StandardCharsets.UTF_8)) {
                    final SAXSerializer sax = new SAXSerializer(out, outputProps);
                    for (ResourceIterator iter = result.getIterator(); iter.hasMoreResources(); ) {
                        XMLResource r = (XMLResource) iter.nextResource();
                        r.getContentAsSAX(sax);
                    }
                }
            }
        } catch (final XMLDBException | IOException e) {
            LOG.error(e.getMessage(), e);
            throw new SAXException(e.getMessage(), e);
        }
        return generatedFiles;
    }

    public void releaseAll() {
        for(final Path generatedFile : generatedFiles) {
            FileUtils.deleteQuietly(generatedFile);
        }
    }
}