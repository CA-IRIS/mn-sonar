/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2008  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.sonar.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import us.mn.state.dot.sonar.Conduit;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;
import us.mn.state.dot.sonar.SSLState;

/**
 * A client conduit represents a client connection.
 *
 * @author Douglas Lau
 */
class ClientConduit extends Conduit {

	/** Define the set of valid messages from the server */
	static protected final EnumSet<Message> MESSAGES = EnumSet.of(
		Message.QUIT, Message.OBJECT, Message.REMOVE, Message.ATTRIBUTE,
		Message.TYPE, Message.SHOW);

	/** Lookup a message from the specified message code */
	static protected Message lookupMessage(char code) throws ProtocolError {
		for(Message m: MESSAGES)
			if(code == m.code)
				return m;
		throw ProtocolError.INVALID_MESSAGE_CODE;
	}

	/** Create and configure a socket channel */
	static protected SocketChannel createChannel(String host, int port)
		throws IOException
	{
		SocketChannel c = SocketChannel.open();
		c.configureBlocking(false);
		c.connect(new InetSocketAddress(host, port));
		return c;
	}

	/** Create and configure a socket channel */
	static protected SocketChannel createChannel(Properties props)
		throws ConfigurationError, IOException
	{
		int p;
		String h = props.getProperty("sonar.host");
		if(h == null)
			throw new ConfigurationError(
				"Missing sonar.host property");
		try {
			p = Integer.parseInt(props.getProperty("sonar.port"));
		}
		catch(NumberFormatException e) {
			throw new ConfigurationError("Invalid sonar.port");
		}
		return createChannel(h, p);
	}

	/** Get a string host:port representation */
	protected String getHostPort() {
		StringBuilder h = new StringBuilder();
		h.append(channel.socket().getInetAddress().getHostAddress());
		h.append(':');
		h.append(channel.socket().getPort());
		return h.toString();
	}

	/** Get the conduit name */
	public String getName() {
		return getHostPort();
	}

	/** Socket channel to communicate with the server */
	protected final SocketChannel channel;

	/** Client thread */
	protected final Client client;

	/** Key for selecting on the channel */
	protected final SelectionKey key;

	/** SSL connection state information */
	protected final SSLState state;

	/** Cache of all proxy objects */
	protected final ProxyCache cache;

	/** Handler for SHOW messages */
	protected final ShowHandler show_handler;

	/** Flag to determine if login was accepted */
	protected boolean loggedIn = false;

	/** Name of connection */
	protected String connection = null;

	/** Get the connection name */
	public String getConnection() {
		return connection;
	}

	/** Check if the user successfully logged in */
	public boolean isLoggedIn() {
		return loggedIn;
	}

	/** Create a new client conduit */
	public ClientConduit(Properties props, Client c, Selector selector,
		SSLEngine engine, ShowHandler handler)
		throws ConfigurationError, IOException
	{
		channel = createChannel(props);
		client = c;
		key = channel.register(selector, SelectionKey.OP_CONNECT);
		engine.setUseClientMode(true);
		state = new SSLState(this, engine);
		cache = new ProxyCache();
		show_handler = handler;
		connected = false;
	}

	/** Attempt to log in to the SONAR server */
	public void login(String name, String pwd) {
		state.encoder.encode(Message.LOGIN, name, new String[] {pwd});
		flush();
	}

	/** Quit the SONAR session */
	public void quit() {
		state.encoder.encode(Message.QUIT);
		flush();
	}

	/** Query all SONAR objects of the given type */
	public void queryAll(TypeCache tcache) {
		cache.addType(tcache);
		state.encoder.encode(Message.ENUMERATE, tcache.tname);
		flush();
	}

	/** Create the specified object name */
	public void createObject(String name) {
		state.encoder.encode(Message.OBJECT, name);
		flush();
	}

	/** Request an attribute change */
	public void setAttribute(String name, String[] params) {
		state.encoder.encode(Message.ATTRIBUTE, name, params);
		flush();
	}

	/** Remove the specified object name */
	public void removeObject(String name) {
		state.encoder.encode(Message.REMOVE, name);
		flush();
	}

	/** Complete the connection on the socket channel */
	public void doConnect() throws IOException {
		if(channel.finishConnect()) {
			connected = true;
			disableWrite();
			flush();
		}
	}

	/** Read messages from the socket channel */
	public void doRead() throws IOException {
		int nbytes;
		ByteBuffer net_in = state.getNetInBuffer();
		synchronized(net_in) {
			nbytes = channel.read(net_in);
		}
		if(nbytes > 0)
			client.processMessages();
		else if(nbytes < 0)
			throw new IOException("EOF");
	}

	/** Write pending data to the socket channel */
	public void doWrite() throws IOException {
		ByteBuffer net_out = state.getNetOutBuffer();
		synchronized(net_out) {
			net_out.flip();
			channel.write(net_out);
			if(!net_out.hasRemaining())
				disableWrite();
			net_out.compact();
		}
	}

	/** Start writing data to client */
	protected void startWrite() throws IOException {
		if(state.doWrite())
			client.flush();
	}

	/** Flush out all outgoing data in the conduit */
	public void flush() {
		try {
			if(isConnected())
				startWrite();
		}
		catch(IOException e) {
			disconnect();
		}
	}

	/** Disconnect the conduit */
	public void disconnect() {
		super.disconnect();
		loggedIn = false;
		show_handler.display("Disconnected from server");
	}

	/** Process any incoming messages */
	public void processMessages() throws IOException {
		if(!isConnected())
			return;
		while(state.doRead()) {
			List<String> params = state.decoder.decode();
			while(params != null) {
				processMessage(params);
				params = state.decoder.decode();
			}
		}
		flush();
	}

	/** Process one message from the client */
	protected void processMessage(List<String> params) {
		try {
			if(params.size() > 0)
				_processMessage(params);
		}
		catch(SonarException e) {
			e.printStackTrace();
			disconnect();
		}
	}

	/** Process one message from the client */
	protected void _processMessage(List<String> params)
		throws SonarException
	{
		String c = params.get(0);
		if(c.length() != 1)
			throw ProtocolError.INVALID_MESSAGE_CODE;
		Message m = lookupMessage(c.charAt(0));
		m.handle(this, params);
	}

	/** Enable writing data back to the client */
	public void enableWrite() {
		key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		key.selector().wakeup();
	}

	/** Disable writing data back to the client */
	public void disableWrite() {
		key.interestOps(SelectionKey.OP_READ);
	}

	/** Process a QUIT message from the server */
	public void doQuit(List<String> p) throws SonarException {
		if(p.size() != 1)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		disconnect();
	}

	/** Process an OBJECT message from the server */
	public void doObject(List<String> p) throws SonarException {
		if(p.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		cache.putObject(p.get(1));
	}

	/** Process a REMOVE message from the server */
	public void doRemove(List<String> p) throws SonarException {
		if(p.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		cache.removeObject(p.get(1));
	}

	/** Process an ATTRIBUTE message from the server */
	public void doAttribute(List<String> p) throws SonarException {
		if(p.size() < 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		p.remove(0);
		String name = p.remove(0);
		cache.unmarshallAttribute(name, p.toArray(new String[0]));
	}

	/** Process a TYPE message from the server */
	public void doType(List<String> p) throws SonarException {
		if(p.size() > 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		if(p.size() > 1)
			cache.setCurrentType(p.get(1));
		else
			cache.setCurrentType("");
		loggedIn = true;
	}

	/** Process a SHOW message from the server */
	public void doShow(List<String> p) throws SonarException {
		if(p.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String m = p.get(1);
		// First SHOW message after login is the connection name
		if(loggedIn && connection == null)
			connection = m;
		else
			show_handler.display(m);
	}
}
