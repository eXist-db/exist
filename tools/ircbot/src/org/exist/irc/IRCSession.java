package org.exist.irc;

import java.io.*;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.jibble.pircbot.*;

public class IRCSession extends PircBot {

	private static final int PING_PERIOD = 20000;
	private static final int TIMEOUT = 240000;

    private static final String EV_MESSAGE = "message";
	private static final String EV_NOTICE = "notice";
	private static final String EV_JOIN = "join";
	private static final String EV_PART = "part";
	private static final String EV_USERS_LIST = "users";
	private static final String EV_PING = "ping";
	
	private static char[] CHUNK = new char[8192];
	static {
		Arrays.fill(CHUNK, ' ');
	}
	
	// server and channel settings
	private String server;
	private String channel;
	
	private String sessionId;

    private HttpServletResponse response = null;
    private Writer responseWriter = null;

    private StringWriter writer = new StringWriter();

    private boolean fillChunks = false;
	
	private boolean disconnect = false;
	
	private long lastPing;

    private boolean isRunning = false;
    
    private Pattern cmdRegex = Pattern.compile("^/\\s*(\\w+)\\s+(.*)$");
	private Matcher matcher = cmdRegex.matcher("");
	
	public IRCSession(String server, String channel, String nick, boolean modProxyHack) throws IOException, NickAlreadyInUseException, IrcException {
		super();
		
		this.server = server;
		this.channel = channel;
		this.sessionId = Integer.toString(hashCode());
		this.fillChunks = modProxyHack;
		
		this.setVersion("XIRCProxy 0.1");
		this.setLogin("XIRCProxy");
		
		this.setName(nick);
		this.setVerbose(true);
		this.setEncoding("UTF-8");
		connect();
	}

	protected void connect() throws IOException, IrcException, NickAlreadyInUseException {
		log("Connecting to " + server);

		connect(server);
		
		log("Join channel: " + channel);
		joinChannel(channel);
    }
	
	public String getSessionId() {
		return sessionId;
	}

    public boolean started() {
        return isRunning;
    }
    
    public void run(HttpServletResponse servletResponse) {
        setResponseObject(servletResponse);

        log("Listening to chat events ...");
		disconnect = false;
		lastPing = System.currentTimeMillis();
        isRunning = true;
        while (!disconnect) {
            synchronized (this) {
                try {
                    wait(PING_PERIOD);
                } catch (InterruptedException e) {
                }
            }
            if (!disconnect) {
                long now = System.currentTimeMillis();
                if (now - lastPing > TIMEOUT) {
					log("No response from client, disconnecting user " + getNick() + " from channel " + channel + " ...");
					partChannel(channel, "Connection Timed Out");
					quitServer();
				} else {
					pingClient();
                }
            }
		}
        isRunning = false;
        flush();
        log("Exiting ...");
        synchronized(this) {
            notifyAll();
        }
    }

    public synchronized void closeTunnel() {
        disconnect = true;
        notifyAll();
        try {
            wait();
        } catch (InterruptedException e) {
        }
    }

    public synchronized boolean setResponseObject(HttpServletResponse servletResponse) {
        this.response = servletResponse;
        response.setContentType("text/html");
        response.setBufferSize(64);
        try {
            ServletOutputStream os = response.getOutputStream();
            responseWriter = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), false);
            responseWriter.write("<html><head>");
            responseWriter.write("<title>IRCProxy</title>");
            responseWriter.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
            responseWriter.write("</head><body>");
            writeEvent("Connection to proxy opened.", EV_NOTICE);
            flush();
        } catch (IOException e) {
            log("Exception while opening push stream: " + e.getMessage());
            return true;
        }
        return false;
    }

    public void quit() {
		quit("Client Quit");
	}
	
	private void quit(String partMessage) {
		partChannel(channel, partMessage);
		quitServer();
	}
	
	public synchronized void send(String message) {
		matcher.reset(message);
		if (matcher.find()) {
			String cmd = matcher.group(1);
			message = matcher.group(2);
			processCommand(cmd, message);
		} else {
			sendMessage(channel, message);
			writeMessage(getNick(), message);
		}
	}
	
	public synchronized void attemptReconnect() throws IOException, IrcException {
		log("Reconnecting ...");
		disconnect = true;
		lastPing = System.currentTimeMillis();
		writer = new StringWriter();
		notifyAll();
		if (!isConnected()) {
			connect();
		}
	}

    protected void onConnect() {
        writeEvent("Connected to server " + this.server + ".", EV_NOTICE);
        flush();
    }

    protected synchronized void onDisconnect() {
		disconnect = true;
		notifyAll();
	}
	
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		log("Message from " + sender);
		writeMessage(sender, message);
	}
	
	protected void onJoin(String channel, String sender, String login, String hostname) {
		try {
			writer.write("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.write(jsCall("dispatchEvent", new String[] {
					EV_JOIN, sender,
					sender + " [" + hostname + "] has joined " + channel
			}));
			writer.write("</script>\n\n");
			flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	protected void onPart(String channel, String sender, String login, String hostname) {
		try {
			writer.write("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.write(jsCall("dispatchEvent", new String[] {
					EV_PART, sender,
					sender + " [" + hostname + "] has left " + channel
			}));
			writer.write("</script>\n\n");
			flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		try {
			writer.write("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.write(jsCall("dispatchEvent", new String[] {
					EV_PART, sourceNick,
					sourceNick + " [" + sourceLogin + '@' + sourceHostname + "] has quit: \"" + reason + '"'
			}));
			writer.write("</script>\n\n");
			flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) {
		writeEvent("Notice from " + sourceNick + " [" + sourceHostname + "] to " + target + ": " + notice, EV_NOTICE);
	}
	
	protected void onUserList(String channel, User[] users) {
		String args[] = new String[users.length + 1];
		args[0] = EV_USERS_LIST;
		for (int i = 0; i < users.length; i++) {
			args[i + 1] = users[i].getNick();
		}
		try {
			writer.write("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.write(jsCall("dispatchEvent", args));
			writer.write("</script>\n\n");
			flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}


    protected void onServerResponse(int i, String string) {
        if (i == ReplyConstants.RPL_MOTDSTART || i == ReplyConstants.RPL_MOTD ||
                i == ReplyConstants.RPL_ENDOFMOTD) {
            writeEvent(string, EV_NOTICE);
        }
    }

    public void pingResponse() {
		log("Received ping reponse ...");
		lastPing = System.currentTimeMillis();
	}
	
	private void pingClient() {
		String js = jsCall("dispatchEvent", new String[] { EV_PING });
		writeLine(js);
	}
	
	private void writeMessage(String sender, String message) {
		String js = jsCall("dispatchEvent", new String[] { EV_MESSAGE, sender, message });
		writeLine(js);
	}
	
	private void writeEvent(String message, String cls) {
		String js = jsCall("dispatchEvent", new String[] { cls, message });
		writeLine(js);
	}
	
	private void writeLine(String data) {
		try {
			writer.write("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.write(data);
			writer.write("</script>\n\n");
			flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	private String jsCall(String func, String[] params) {
		StringBuffer buf = new StringBuffer();
		buf.append("top.").append(func).append("('");
		buf.append(sessionId);
		buf.append('\'');
		for (int i = 0; i < params.length; i++) {
			buf.append(", '");
			buf.append(escape(params[i]));
			buf.append('\'');
		}
		buf.append(");");
		return buf.toString();
	}
	
	private void flush() {
        if (response == null)
            return;
        String data = writer.toString();
        writer = new StringWriter();
        try {
            if (data.length() > 0) {
                responseWriter.write(data);
                if (fillChunks)
                    responseWriter.write(CHUNK);
            }
            responseWriter.flush();
        } catch (IOException e) {
            log("Exception while flushing servlet output: " + e.getMessage());
            outputStreamClosed();
        }
    }

    private void outputStreamClosed() {
        log("HttpServletResponse closed.");
        response = null;
        responseWriter = null;
    }

    private void closeConnection(String message) {
		if (writer != null) {
			writer.write("<h1>Error</h1>");
			writer.write("<p>Error found: " + message + "</p>");
			writer.write("<p>Closing connection.</p>");
			flush();
		}
		partChannel(channel);
		quitServer();
	}
	
	private String escape(String in) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < in.length(); i++) {
			char ch = in.charAt(i);
			switch (ch) {
            case '\\' :
                break;    
            case '"' :
            case '\'' :
				buf.append("&quot;");
				break;
			case '<' :
				buf.append("&lt;");
				break;
			case '>' :
				buf.append("&gt;");
				break;
			case '&' :
				buf.append("&amp;");
				break;
			default:
				buf.append(ch);
				break;
			}
		}
		return buf.toString();
	}
	
	private void processCommand(String command, String message) {
		if ("MSG".equalsIgnoreCase(command)) {
			String target = new StringTokenizer(message).nextToken();
			sendMessage(target, message.substring(target.length() + 1));
		} else if ("QUIT".equalsIgnoreCase(command)) {
			writeMessage(getNick(), "QUIT: " + message);
			quit(message);
		} else if ("NICK".equalsIgnoreCase(command)) {
			writeMessage(getNick(), "Trying to change nick to " + message);
			changeNick(message);
            send("/list");
        }
	}
	
	public static void main(String[] args) {
		Pattern cmdRegex = Pattern.compile("^/\\s*(\\w+)\\s+(.*)$");
		Matcher matcher = cmdRegex.matcher("");
		matcher.reset("/quit abcdefg");
		if (matcher.find()) {
			System.out.println("Command: " + matcher.group(1) + " - " + matcher.group(2));
		} else {
			System.out.println("Nothing");
		}
	}
}