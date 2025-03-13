/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.process;

import org.exist.dom.QName;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.util.FileUtils;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

    public final static QName RESULT_QNAME = new QName("execution", XMLConstants.NULL_NS_URI);
    public final static QName COMMAND_LINE_QNAME = new QName("commandline", XMLConstants.NULL_NS_URI);
    public final static QName STDOUT_QNAME = new QName("stdout", XMLConstants.NULL_NS_URI);
    public final static QName LINE_QNAME = new QName("line", XMLConstants.NULL_NS_URI);

    public Execute(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole())
            throw new XPathException(this, "process:execute is only available to users with dba role");

        // create list of parameters to pass to shell
        List<String> cmdArgs = new ArrayList<>(args[0].getItemCount());
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            cmdArgs.add(i.nextItem().getStringValue());
        }

        // parse options
        List<String> stdin = null;
        Path workingDir = null;
        Map<String, String> environment = null;
        if (!args[1].isEmpty()) {
            try {
                final NodeValue options = (NodeValue) args[1].itemAt(0);
                final int thisLevel = options.getNodeId().getTreeLevel();
                final XMLStreamReader reader = context.getXMLStreamReader(options);
                reader.next();
                while (reader.hasNext()) {
                    int status = reader.next();
                    if (status == XMLStreamReader.START_ELEMENT) {
                        String name = reader.getLocalName();
                        if ("workingDir".equals(name)) {
                            workingDir = getWorkingDir(reader.getElementText());
                        } else if ("line".equals(name)) {
                            if (stdin == null)
                                stdin = new ArrayList<>(21);
                            stdin.add(reader.getElementText() + "\n");
                        } else if ("env".equals(name)) {
                            if (environment == null)
                                environment = new HashMap<>();
                            String key = reader.getAttributeValue(null, "name");
                            String value = reader.getAttributeValue(null, "value");
                            if (key != null && value != null)
                                environment.put(key, value);
                        }
                    } else if (status == XMLStreamReader.END_ELEMENT) {
                        final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        final int otherLevel = otherId.getTreeLevel();
                        if (otherLevel == thisLevel) {
                            // finished `optRoot` element...
                            break;  // exit-while
                        }
                    }
                }
            } catch (XMLStreamException | IOException e) {
                throw new XPathException(this, "Invalid XML fragment for options: " + e.getMessage(), e);
            }
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Creating process {}", cmdArgs.getFirst());

        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.redirectErrorStream(true);
        if (workingDir != null)
            pb.directory(workingDir.toFile());
        if (environment != null) {
            Map<String, String> env = pb.environment();
            env.putAll(environment);
        }
        try {
            Process process = pb.start();
            if (stdin != null) {
                try(final Writer writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    for (final String line : stdin) {
                        writer.write(line);
                    }
                }
            }
            List<String> output = readOutput(process);
            int exitValue = process.waitFor();
            return createReport(exitValue, output, cmdArgs);
        } catch (IOException e) {
            throw new XPathException(this, "An IO error occurred while executing the process " + cmdArgs.getFirst() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            throw new XPathException(this, "process:execute was interrupted while waiting for process " + cmdArgs.getFirst());
        }
    }

    private Path getWorkingDir(String arg) {
        final Path file = Paths.get(arg);
        if (file.isAbsolute()) {
            return file;
        }
        final Optional<Path> home = context.getBroker().getConfiguration().getExistHome();
        return FileUtils.resolve(home, arg);
    }

    private ElementImpl createReport(int exitValue, List<String> output, List<String> cmdArgs) {
        context.pushDocumentContext();
        try {
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
        } finally {
            context.popDocumentContext();
        }
    }

    private List<String> readOutput(Process process) throws XPathException {
        try(final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> output = new ArrayList<>(31);

            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
            return output;
        } catch (IOException e) {
            throw new XPathException(this, "An IO error occurred while reading output from the process: " + e.getMessage(), e);
        }
    }
}
