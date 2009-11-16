/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * \$Id\$
 */

package org.exist.debugger.client;

import jline.ConsoleReader;
import jline.Terminal;
import org.exist.debuggee.CommandContinuation;
import org.exist.debugger.*;

import java.io.EOFException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DebugClient implements ResponseListener {

    private final static Pattern REGEX_COMMAND = Pattern.compile("^([^\\s]+)\\s*(.*)$");

    private final static String COMMAND_RUN = "run";
    private final static String COMMAND_QUIT = "quit";
    private final static String COMMAND_STEP = "step";
    private final static String COMMAND_CONT = "cont";

    private ConsoleReader console;
    private Debugger debugger;
    private DebuggingSource source = null;

    public DebugClient() throws IOException {
        Terminal.setupTerminal();
        console = new ConsoleReader();
        debugger = new DebuggerImpl();
    }

    public void readlineInputLoop() {
        String prompt = "offline";
        boolean cont = true;
        while (cont) {
            try {
                String line = console.readLine(prompt + "> ");
                cont = parseInput(line);
            } catch (EOFException e) {
                break;
            } catch (IOException e) {
                System.err.println("Exception caught: " + e.getMessage());
            }
        }
        if (source != null)
            source.stop(this);
    }

    private boolean parseInput(String line) {
        Matcher commandMatcher = REGEX_COMMAND.matcher(line);
        if (commandMatcher.matches()) {
            String command = commandMatcher.group(1);
            String arguments = commandMatcher.group(2);
            if (COMMAND_RUN.equals(command))
                run(arguments);
            else if (COMMAND_STEP.equals(command))
                source.stepInto(this);
            else if (COMMAND_CONT.equals(command))
                source.run(this);
            else if (COMMAND_QUIT.equals(command)) {
                return false;
            } else
                System.out.println("Unknown command: " + command);
        } else
            System.out.println("Unknown command: " + line);
        return true;
    }

    private void run(String arguments) {
        String target = "http://127.0.0.1:8080/exist/" + arguments;
        System.out.println("Connecting to " + target);
        try {
            source = debugger.init(target);
            if (source == null)
                System.err.println("Failed to initialize session. Connection timed out.");
        } catch (IOException e) {
            System.err.println("Error while initializing session: " + e.getMessage());
        } catch (ExceptionTimeout e) {
            System.err.println("Timeout while initializing session: " + e.getMessage());
        }
    }

    public void responseEvent(CommandContinuation command, Response response) {
        System.out.println(command.getStatus() + ": " + response.getText());
    }

    public static void main(String[] args) throws IOException {
        DebugClient client = new DebugClient();
        client.readlineInputLoop();
    }
}
