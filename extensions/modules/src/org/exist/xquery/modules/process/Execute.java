package org.exist.xquery.modules.process;

import org.exist.dom.QName;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Execute extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("execute", ProcessModule.NAMESPACE, ProcessModule.PREFIX),
            "",
            new SequenceType[] {
                new FunctionParameterSequenceType("args", Type.STRING, Cardinality.ONE_OR_MORE,
                    "a list of strings which signifies the external program file to be invoked and its arguments, if any"),
                new FunctionParameterSequenceType("options", Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                    "an XML fragment defining optional parameters like working directory or the lines to send to " +
                    "the process via stdin. Format: <options><workingDir>workingDir</workingDir>" +
                    "<environment><env name=\"name\" value=\"value\"/></environment><stdin><line>line</line></stdin></options>")
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, "the sequence of code points"));

    public final static QName RESULT_QNAME = new QName("execution");
    public final static QName COMMAND_LINE_QNAME = new QName("commandline");
    public final static QName STDOUT_QNAME = new QName("stdout");
    public final static QName LINE_QNAME = new QName("line");

    public Execute(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole())
            throw new XPathException(this, "process:execute is only available to users with dba role");

        // create list of parameters to pass to shell
        List<String> cmdArgs = new ArrayList<String>(args[0].getItemCount());
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            cmdArgs.add(i.nextItem().getStringValue());
        }

        // parse options
        List<String> stdin = null;
        File workingDir = null;
        Map<String, String> environment = null;
        if (!args[1].isEmpty()) {
            try {
                XMLStreamReader reader = context.getXMLStreamReader((NodeValue) args[1].itemAt(0));
                reader.next();
                while (reader.hasNext()) {
                    int status = reader.next();
                    if (status == XMLStreamReader.START_ELEMENT) {
                        String name = reader.getLocalName();
                        if (name.equals("workingDir")) {
                            workingDir = getWorkingDir(reader.getElementText());
                        } else if (name.equals("line")) {
                            if (stdin == null)
                                stdin = new ArrayList<String>(21);
                            stdin.add(reader.getElementText() + "\n");
                        } else if (name.equals("env")) {
                            if (environment == null)
                                environment = new HashMap<String, String>();
                            String key = reader.getAttributeValue(null, "name");
                            String value = reader.getAttributeValue(null, "value");
                            if (key != null && value != null)
                                environment.put(key, value);
                        }
                    }
                }
            } catch (XMLStreamException e) {
                throw new XPathException(this, "Invalid XML fragment for options: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new XPathException(this, "Invalid XML fragment for options: " + e.getMessage(), e);
            }
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Creating process " + cmdArgs.get(0));

        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.redirectErrorStream(true);
        if (workingDir != null)
            pb.directory(workingDir);
        if (environment != null) {
            Map<String, String> env = pb.environment();
            env.putAll(environment);
        }
        try {
            Process process = pb.start();
            if (stdin != null) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
                for (String line : stdin) {
                    writer.write(line);
                }
                writer.close();
            }
            List<String> output = readOutput(process);
            int exitValue = process.waitFor();
            return createReport(exitValue, output, cmdArgs);
        } catch (IOException e) {
            throw new XPathException(this, "An IO error occurred while executing the process " + cmdArgs.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            throw new XPathException(this, "process:execute was interrupted while waiting for process " + cmdArgs.get(0));
        }
    }

    private File getWorkingDir(String arg) {
        File file = new File(arg);
        if (file.isAbsolute())
            return file;
        File home = context.getBroker().getConfiguration().getExistHome();
        return new File(home, arg);
    }

    private ElementImpl createReport(int exitValue, List<String> output, List<String> cmdArgs) {
        MemTreeBuilder builder = context.getDocumentBuilder();
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "exitCode", "exitCode", "CDATA", Integer.toString(exitValue));
        builder.startDocument();
        int nodeNr = builder.startElement(RESULT_QNAME, attribs);

        // print command line
        StringBuilder cmdLine = new StringBuilder();
        for (String param : cmdArgs) {
            cmdLine.append(param).append(' ');
        }
        builder.startElement(COMMAND_LINE_QNAME, null);
        builder.characters(cmdLine.toString());
        builder.endElement();

        // print received output to <stdout>
        builder.startElement(STDOUT_QNAME, null);
        for (String line : output) {
            builder.startElement(LINE_QNAME, null);
            builder.characters(line);
            builder.endElement();
        }
        builder.endElement();

        builder.endElement();

        return (ElementImpl) builder.getDocument().getNode(nodeNr);
    }

    private List<String> readOutput(Process process) throws XPathException {
        BufferedReader reader = null;
        try {
            List<String> output = new ArrayList<String>(31);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
            return output;
        } catch (IOException e) {
            throw new XPathException(this, "An IO error occurred while reading output from the process: " + e.getMessage(), e);
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                }
        }
    }
}
