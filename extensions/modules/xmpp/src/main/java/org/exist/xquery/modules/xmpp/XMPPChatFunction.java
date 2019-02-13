/*
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.exist.xquery.modules.xmpp;


import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class XMPPChatFunction extends BasicFunction
{
    protected static final Logger logger = LogManager.getLogger(XMPPChatFunction.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "create-chat", XMPPModule.NAMESPACE_URI, XMPPModule.PREFIX ),
			"create a new XMPP chat.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "connection", Type.LONG, Cardinality.EXACTLY_ONE, 
					"The connection handle for chat."),
				new FunctionParameterSequenceType( "JID", Type.STRING, Cardinality.EXACTLY_ONE, 
					"The user JID this chat with is."),
				new FunctionParameterSequenceType( "listener", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, 
					"Listener is the function takes 3 parameters e.g. " +
            		"user:chat-listener($chat as xs:integer, $message as xs:item?, $param as item()*) as empty(). " +
            		"$message is incomming message from the listened $chat." +
            		"$param is an any additional parameters sequence."),
				new FunctionParameterSequenceType( "param", Type.ITEM, Cardinality.ZERO_OR_MORE, 
					"The sequense of any additional parameters for listener.")
            		
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, 
					"an xs:long representing the chat handle." )
			)
		};

	public XMPPChatFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		long connectionHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
		XMPPConnection connection = XMPPModule.retrieveConnection(connectionHandle);
		
		String jid = args[1].itemAt(0).getStringValue();
		
        if(!(args[2].itemAt(0) instanceof FunctionReference))
            throw new XPathException("No chat listener function provided.");
        
        FunctionReference chatListenerFunctionRef = (FunctionReference)args[2].itemAt(0);
        
        FunctionSignature chatListenerFunctionSig = chatListenerFunctionRef.getSignature();
        if(chatListenerFunctionSig.getArgumentCount() < 3)
            throw new XPathException("Chat listener function must take at least 3 arguments.");
        chatListenerFunctionRef.setContext(context.copyContext());
        
        Sequence listenerParam = args[3];
        
        long chatHandle = XMPPModule.getHandle();
		
        Listener listener = new Listener(chatHandle, contextSequence, chatListenerFunctionRef, listenerParam);
        
		Chat chat = connection.getChatManager().createChat(jid, listener);
		
		// store the chat and return the handle of the chat
			
		IntegerValue integerValue = new IntegerValue( XMPPModule.storeChat( chat, chatHandle ) );
		return integerValue;
	}
	
	private class Listener implements MessageListener{
		
		private Sequence contextSequence;
		private long chatHandle;
		
		private FunctionReference chatListenerFunction;
	    private Sequence listenerParam;

		public Listener(long chatHandle, Sequence contextSequence, FunctionReference chatListenerFunction, Sequence listenerParam){
			this.chatHandle = chatHandle;
			this.contextSequence = contextSequence;
			this.chatListenerFunction = chatListenerFunction;
			this.listenerParam = listenerParam;
		}
		
		public void processMessage(Chat caht, Message message) {
			
	        try {
	        	
		        StringReader reader = new StringReader(message.toXML());
	            SAXParserFactory factory = SAXParserFactory.newInstance();
	            factory.setNamespaceAware(true);
	            InputSource src = new InputSource(reader);

                SAXParser parser = factory.newSAXParser();
                XMLReader xr = parser.getXMLReader();

	            SAXAdapter adapter = new SAXAdapter(context);
	            xr.setContentHandler(adapter);
	            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
	            xr.parse(src);

		        Sequence listenerParams[] = new Sequence[3];
		        
		        listenerParams[0] = new IntegerValue(chatHandle);
	            listenerParams[1] = (DocumentImpl) adapter.getDocument();	       
	            listenerParams[2] = listenerParam;
	            
				chatListenerFunction.evalFunction(contextSequence, null, listenerParams);

	        } catch (ParserConfigurationException e) {
	        	LOG.error("Error while constructing XML parser: " + e.getMessage());
	        } catch (SAXException e) {
	        	LOG.error("Error while parsing XML parser: " + e.getMessage());
	        } catch (IOException e) {
	        	LOG.error("Error while parsing XML parser: " + e.getMessage());
			} catch (XPathException e) {
	        	LOG.error("Chat listener function runtime error: " + e.getMessage());
			}
			
		}
		
	}
}
