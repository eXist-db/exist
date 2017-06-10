package org.exist.util.serializer;

import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.json.JSONWriter;

import javax.xml.transform.OutputKeys;
import java.io.Writer;
import java.util.Properties;

/**
 * Common base for {@link org.exist.util.serializer.SAXSerializer} and {@link org.exist.util.serializer.DOMSerializer}.
 */
public abstract class AbstractSerializer {

    protected final static int XML_WRITER = 0;
    protected final static int XHTML_WRITER = 1;
    protected final static int TEXT_WRITER = 2;
    protected final static int JSON_WRITER = 3;
    protected final static int XHTML5_WRITER = 4;
    protected final static int MICRO_XML_WRITER = 5;
    protected final static int HTML5_WRITER = 6;

    protected XMLWriter writers[] = {
        new IndentingXMLWriter(),
        new XHTMLWriter(),
        new TEXTWriter(),
        new JSONWriter(),
        new XHTML5Writer(),
        new MicroXmlWriter(),
        new HTML5Writer()
    };

    protected final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.INDENT, "false");
    }

    protected Properties outputProperties;
    protected XMLWriter receiver;

    public AbstractSerializer() {
        super();
        receiver = getDefaultWriter();
    }

    protected XMLWriter getDefaultWriter() {
        return writers[XML_WRITER];
    }

    public void setOutput(Writer writer, Properties properties) {
        if (properties == null) {
            outputProperties = new Properties(defaultProperties);
        } else {
            outputProperties = properties;
        }
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        final String htmlVersionProp = outputProperties.getProperty(EXistOutputKeys.HTML_VERSION, "1.0");

        double htmlVersion;
        try {
            htmlVersion = Double.parseDouble(htmlVersionProp);
        } catch (NumberFormatException e) {
            htmlVersion = 1.0;
        }

        if ("xhtml".equalsIgnoreCase(method)) {
            if (htmlVersion < 5.0) {
                receiver = writers[XHTML_WRITER];
            } else {
                receiver = writers[XHTML5_WRITER];
            }
        } else if ("html".equals(method)) {
            if (htmlVersion < 5.0) {
                receiver = writers[XHTML_WRITER];
            } else {
                receiver = writers[HTML5_WRITER];
            }
        } else if("text".equalsIgnoreCase(method)) {
            receiver = writers[TEXT_WRITER];
        } else if ("json".equalsIgnoreCase(method)) {
            receiver = writers[JSON_WRITER];
        } else if ("xhtml5".equalsIgnoreCase(method)) {
            receiver = writers[XHTML5_WRITER];
        } else if ("html5".equalsIgnoreCase(method)) {
            receiver = writers[HTML5_WRITER];
        } else if("microxml".equalsIgnoreCase(method)) {
            receiver = writers[MICRO_XML_WRITER];
        } else {
            receiver = writers[XML_WRITER];
        }

        receiver.setWriter(writer);
        receiver.setOutputProperties(outputProperties);
    }

    public void reset() {
        for (XMLWriter writer : writers) {
            writer.reset();
        }
    }
}