package org.exist.irc;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

public class IRCSession extends PircBot {

	private static final String EV_MESSAGE = "message";
	private static final String EV_NOTICE = "notice";
	private static final String EV_JOIN = "join";
	private static final String EV_PART = "part";
	private static final String EV_USERS_LIST = "users";
	
	// server and channel settings
	private String channel;
	
	private String sessionId;
	
	private PrintWriter writer;
	private StringWriter tempWriter = new StringWriter();
	
	private boolean disconnect = false;
	
	public IRCSession(String server, String channel, String nick) throws IOException, NickAlreadyInUseException, IrcException {
		super();
		this.channel = channel;
		this.sessionId = Integer.toString(hashCode());
		this.writer = new PrintWriter(tempWriter);
		
		this.setVersion("XIRCProxy 0.1");
		
		this.setName(nick);
		this.setVerbose(true);
		
		log("Connecting to " + server);

		connect(server);
		
		log("Join channel: " + channel);
		joinChannel(channel);
	}
	
	public String getSessionId() {
		return sessionId;
	}
	
	public synchronized void run(HttpServletResponse response) {
		response.setContentType("text/html");
		
		response.setBufferSize(64);
		try {
			ServletOutputStream os = response.getOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), false);
			writer.println("<html><head><title>IRCProxy</title>");
			writer.println("</head><body>");
			writer.println(tempWriter.toString());
			tempWriter = null;
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		log("Listening to chat events ...");
		while (!disconnect) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}
	
	public void quit() {
		disconnect();
	}
	
	public synchronized void send(String message) {
		sendMessage(channel, message);
		writeMessage(getNick(), message);
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
			writer.println("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.println(jsCall("dispatchEvent", new String[] {
					EV_JOIN, sender,
					sender + " [" + hostname + "] has joined " + channel
			}));
			writer.println("</script>\n\n");
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	protected void onPart(String channel, String sender, String login, String hostname) {
		try {
			writer.println("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.println(jsCall("dispatchEvent", new String[] {
					EV_PART, sender,
					sender + " [" + hostname + "] has left " + channel
			}));
			writer.println("</script>\n\n");
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
	}
	
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		try {
			writer.println("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.println(jsCall("dispatchEvent", new String[] {
					EV_PART, sourceNick,
					sourceNick + " [" + sourceLogin + '@' + sourceHostname + "] has quit: \"" + reason + '"'
			}));
			writer.println("</script>\n\n");
			writer.flush();
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
			writer.println("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.println(jsCall("dispatchEvent", args));
			writer.println("</script>\n\n");
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			closeConnection(e.getMessage());
		}
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
			writer.println("<script language=\"JavaScript\" type=\"text/javascript\">");
			writer.println(data);
			writer.println("</script>\n\n");
			writer.flush();
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
	
	private void closeConnection(String message) {
		if (writer != null) {
			writer.println("<h1>Error</h1>");
			writer.println("<p>Error found: " + message + "</p>");
			writer.println("<p>Closing connection.</p>");
			writer.flush();
		}
		partChannel(channel);
		quitServer();
	}
	
	private String escape(String in) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < in.length(); i++) {
			char ch = in.charAt(i);
			switch (ch) {
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
}