package org.exist.xquery.modules.xmlcalabash;

import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.xmlcalabash.core.XProcRuntime;

public class ProcessFunction extends BasicFunction {

    @SuppressWarnings("unused")
	private final static Logger logger = Logger.getLogger(ProcessFunction.class);

    private XProcRuntime runtime = null;

    String outputResult;
    
    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("process", XMLCalabashModule.NAMESPACE_URI, XMLCalabashModule.PREFIX),
			"Function which invokes xmlcalabash XProc processor.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("pipeline", Type.STRING, Cardinality.EXACTLY_ONE, "XProc Pipeline"),
                    new FunctionParameterSequenceType("output", Type.STRING, Cardinality.EXACTLY_ONE, "Output result")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "return type"));

	public ProcessFunction(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {

        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        String pipelineURI = args[0].getStringValue();
        String outputURI = args[1].getStringValue();

       try {

        String[] calabash_args = {"-oresult="+outputURI,pipelineURI};

        PrintStream stdout = System.out;

        com.xmlcalabash.drivers.Main main = new com.xmlcalabash.drivers.Main();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(byteStream, true));
        main.run(calabash_args);
        outputResult = byteStream.toString();
        System.setOut(stdout);

       } catch (Exception e) {
           System.err.println(e);
       }

        StringReader reader = new StringReader(outputResult);
                try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(reader);

            XMLReader xr = null;

            if (xr == null) {
                SAXParser parser = factory.newSAXParser();
                xr = parser.getXMLReader();
            }

            SAXAdapter adapter = new SAXAdapter(context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);

            return (DocumentImpl) adapter.getDocument();
        } catch (ParserConfigurationException e) {
            throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        }

	}
}
