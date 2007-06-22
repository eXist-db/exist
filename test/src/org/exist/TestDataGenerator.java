package org.exist;

import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Helper class to generate test documents from a given XQuery.
 */
public class TestDataGenerator {

    private final static String IMPORT =
            "import module namespace pt='http://exist-db.org/xquery/test/performance' " +
            "at 'java:org.exist.performance.xquery.PerfTestModule';\n" +
            "declare variable $filename external;\n" +
            "declare variable $count external;\n";

    private String prefix;
    private int count;
    private File[] generatedFiles;

    public TestDataGenerator(String prefix, int count) {
        this.prefix = prefix;
        this.count = count;
        this.generatedFiles = new File[count];
    }

    public File[] generate(DBBroker broker, Collection collection, String xqueryContent) throws SAXException {
        try {
            DocumentSet docs = collection.allDocs(broker, new DocumentSet(), true, false);
            XQuery service = broker.getXQueryService();
            XQueryContext context = service.newContext(AccessContext.TEST);
            context.declareVariable("filename", "");
            context.declareVariable("count", "0");
            context.setStaticallyKnownDocuments(docs);

            Properties outputProps = new Properties();
            outputProps.setProperty(OutputKeys.INDENT, "yes");

            String query = IMPORT + xqueryContent;
            System.out.println("query: " + query);

            CompiledXQuery compiled = service.compile(context, query);

            for (int i = 0; i < count; i++) {
                generatedFiles[i] = File.createTempFile(prefix, ".xml");

                context.declareVariable("filename", generatedFiles[i].getName());
                context.declareVariable("count", new IntegerValue(i));
                Sequence results = service.execute(compiled, Sequence.EMPTY_SEQUENCE);

                Serializer serializer = broker.getSerializer();
                serializer.reset();
                Writer out = new OutputStreamWriter(new FileOutputStream(generatedFiles[i]), "UTF-8");
                SAXSerializer sax = new SAXSerializer(out, outputProps);
                serializer.setSAXHandlers(sax, sax);
                for (SequenceIterator iter = results.iterate(); iter.hasNext(); ) {
                    Item item = iter.nextItem();
                    if (!Type.subTypeOf(item.getType(), Type.NODE))
                        continue;
                    serializer.toSAX((NodeValue) item);
                }
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SAXException(e.getMessage(), e);
        }
        return generatedFiles;
    }

    public void releaseAll() {
        for (int i = 0; i < generatedFiles.length; i++) {
            File file = generatedFiles[i];
            file.delete();
            generatedFiles[i] = null;
        }
    }
}