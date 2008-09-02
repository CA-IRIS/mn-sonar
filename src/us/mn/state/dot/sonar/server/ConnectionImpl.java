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
package us.mn.state.dot.sonar.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.BufferOverflowException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.net.ssl.SSLException;
import us.mn.state.dot.sonar.Conduit;
import us.mn.state.dot.sonar.Connection;
import us.mn.state.dot.sonar.Message;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;
import us.mn.state.dot.sonar.SSLState;
import us.mn.state.dot.sonar.User;

/**
 * A connection encapsulates the state of one client connection on the server.
 *
 * @author Douglas Lau
 */
public class ConnectionImpl extends Conduit implements Connection {

	/** Define the set of valid messages from a client connection */
	static protected final EnumSet<Message> MESSAGES = EnumSet.of(
		Message.LOGIN, Message.QUIT, Message.ENUMERATE, Message.IGNORE,
		Message.OBJECT, Message.REMOVE, Message.ATTRIBUTE);

	/** Lookup a message from the specified message code */
	static protected Message lookupMessage(char code) throws ProtocolError {
		for(Message m: MESSAGES)
			if(code == m.code)
				return m;
		throw ProtocolError.INVALID_MESSAGE_CODE;
	}

	/** Random number generator for session IDs */
	static protected final Random RAND = new Random();

	/** Create a new session ID */
	static protected long createSessionId() {
		return RAND.nextLong();
	}

	/** Get the SONAR type name */
	public String getTypeName() {
		return SONAR_TYPE;
	}

	/** Client host name and port */
	protected final String hostport;

	/** Get the SONAR object name */
	public String getName() {
		return hostport;
	}

	/** User logged in on the connection */
	protected UserImpl user;

	/** Get the user logged in on the connection */
	public User getUser() {
		return user;
	}

	/** Session ID (random cookie) */
	protected final long sessionId;

	/** Get the SONAR session ID */
	public long getSessionId() {
		return sessionId;
	}

	/** Server for the connection */
	protected final Server server;

	/** SONAR namepsace */
	protected final Namespace namespace;

	/** Selection key for the socket channel */
	protected final SelectionKey key;

	/** Channel to client */
	protected final SocketChannel channel;

	/** SSL state for encrypting network data */
	protected final SSLState state;

	/** Set of names the connection is watching */
	protected final Set<String> watching = new HashSet<String>();

	/** Phantom object for setting attributes before storing a new object
	 * in the database. */
	protected SonarObject phantom;

	/** Create a new connection */
	public ConnectionImpl(Server s, SelectionKey k, SocketChannel c)
		throws SSLException
	{
		server = s;
		namespace = server.getNamespace();
		key = k;
		channel = c;
		state = new SSLState(this, server.createSSLEngine(), true);
		StringBuilder h = new StringBuilder();
		h.append(c.socket().getInetAddress().getHostAddress());
		h.append(':');
		h.append(c.socket().getPort());
		hostport = h.toString();
		user = null;
		connected = true;
		sessionId = createSessionId();
	}

	/** Start watching the specified name */
	public void startWatching(String name) {
		synchronized(watching) {
			watching.add(name);
		}
	}

	/** Stop watching the specified name */
	public boolean stopWatching(String name) {
		synchronized(watching) {
			return watching.remove(name);
		}
	}

	/** Check if the connection is watching a name */
	protected boolean isWatching(String name) {
		synchronized(watching) {
			return watching.contains(name);
		}
	}

	/** Notify the client of a new object being added */
	protected void notifyObject(SonarObject o) {
		try {
			namespace.enumerateObject(state.encoder, o);
			flush();
		}
		catch(SonarException e) {
			System.err.println("SONAR: notify: " + e.getMessage());
			disconnect();
		}
	}

	/** Notify the client of a new object being added */
	public void notifyObject(String[] names, SonarObject o) {
		if(isWatching(names[0]))
			notifyObject(o);
	}

	/** Notify the client of an attribute change */
	public void notifyAttribute(String tname, String oname, String name,
		String[] params)
	{
		if(isWatching(tname) || isWatching(oname)) {
			state.encoder.encode(Message.ATTRIBUTE, name, params);
			flush();
		}
	}

	/** Notify the client of a name being removed */
	public void notifyRemove(String name, String value) {
		if(isWatching(name) || isWatching(value)) {
			state.encoder.encode(Message.REMOVE, value);
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
			server.processMessages(this);
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

	/** Disconnect the client connection */
	public void disconnect() {
		super.disconnect();
		synchronized(watching) {
			watching.clear();
		}
		server.disconnect(key);
		try {
			channel.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	/** Cancel the connection task */
	public void cancel() {
		disconnect();
	}

	/** Destroy the connection */
	public void destroy() {
		disconnect();
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
			state.encoder.encode(Message.SHOW, e.getMessage());
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

	/** Check that the client is logged in */
	protected void checkLoggedIn() throws SonarException {
		if(user == null)
			throw ProtocolError.AUTHENTICATION_REQUIRED;
	}

	/** Lookup a user by name */
	protected UserImpl lookupUser(String n) throws PermissionDenied {
		try {
			return (UserImpl)namespace.lookupObject(
				User.SONAR_TYPE, n);
		}
		catch(NamespaceError e) {
			throw PermissionDenied.AUTHENTICATION_FAILED;
		}
	}

	/** Get the specified object (either phantom or new object) */
	protected SonarObject getObject(String tname, String oname)
		throws SonarException
	{
		if(phantom != null && tname.equals(phantom.getTypeName()) &&
			oname.equals(phantom.getName()))
		{
			return phantom;
		} else
			return namespace.createObject(tname, oname);
	}

	/** Create a new object in the server namespace */
	protected void createObject(String name, String[] names)
		throws SonarException
	{
		SonarObject o = getObject(names[0], names[1]);
		namespace.storeObject(o);
		server.notifyObject(names, o);
		phantom = null;
	}

	/** Set the value of an attribute */
	protected void setAttribute(String name, String[] names,
		List<String> params) throws SonarException
	{
		String[] p = new String[params.size() - 2];
		for(int i = 0; i < p.length; i++)
			p[i] =  params.get(i + 2);
		phantom = namespace.setAttribute(names[0], names[1], names[2],
			p, phantom);
		if(phantom == null) {
			String oname = Names.makePath(names[0], names[1]);
			server.notifyAttribute(names[0], oname, name, p);
		}
	}

	/** Start writing data to client */
	protected void startWrite() throws IOException {
		if(state.doWrite())
			server.flush(this);
	}

	/** Tell the I/O thread to flush the output buffer */
	public void flush() {
		try {
			if(isConnected())
				startWrite();
		}
		catch(BufferOverflowException e) {
			System.err.println("SONAR: buffer overflow");
			disconnect();
		}
		catch(IOException e) {
			System.err.println("SONAR: error " + e.getMessage());
			disconnect();
		}
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

	/** Respond to a LOGIN message */
	public void doLogin(List<String> params) throws SonarException {
		if(user != null)
			throw ProtocolError.ALREADY_LOGGED_IN;
		if(params.size() != 3)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = params.get(1);
		String password = params.get(2);
		UserImpl u = lookupUser(name);
		server.getAuthenticator().authenticate(u.getDn(),
			password.toCharArray());
		System.err.println("SONAR: Login " + name + " from " +
			getName());
		user = u;
		// The first TYPE message indicates a successful login
		state.encoder.encode(Message.TYPE);
		// Send the connection name to the client first
		state.encoder.encode(Message.SHOW, hostport);
		server.setAttribute(this, "user", new String[] { name });
	}

	/** Respond to a QUIT message */
	public void doQuit(List<String> params) {
		disconnect();
	}

	/** Respond to an ENUMERATE message */
	public void doEnumerate(List<String> params) throws SonarException {
		checkLoggedIn();
		if(params.size() > 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = "";
		if(params.size() > 1)
			name = params.get(1);
		if(!user.canRead(name))
			throw PermissionDenied.INSUFFICIENT_PRIVILEGES;
		if(name.length() > 0)
			startWatching(name);
		namespace.enumerate(name, state.encoder);
	}

	/** Respond to an IGNORE message */
	public void doIgnore(List<String> params) throws SonarException {
		checkLoggedIn();
		if(params.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = params.get(1);
		if(!stopWatching(name))
			throw ProtocolError.NOT_WATCHING;
	}

	/** Respond to an OBJECT message */
	public void doObject(List<String> params) throws SonarException {
		checkLoggedIn();
		if(params.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = params.get(1);
		String[] names = Names.parse(name);
		if(names.length == 2) {
			if(!user.canAdd(name))
				throw PermissionDenied.INSUFFICIENT_PRIVILEGES;
			createObject(name, names);
		} else
			throw NamespaceError.NAME_INVALID;
	}

	/** Respond to a REMOVE message */
	public void doRemove(List<String> params) throws SonarException {
		checkLoggedIn();
		if(params.size() != 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = params.get(1);
		if(!user.canRemove(name))
			throw PermissionDenied.INSUFFICIENT_PRIVILEGES;
		SonarObject o = namespace.removeObject(name);
		server.notifyRemove(o.getTypeName(), name);
	}

	/** Respond to an ATTRIBUTE message */
	public void doAttribute(List<String> params) throws SonarException {
		checkLoggedIn();
		if(params.size() < 2)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		String name = params.get(1);
		String[] names = Names.parse(name);
		if(names.length == 3) {
			if(!user.canUpdate(name))
				throw PermissionDenied.INSUFFICIENT_PRIVILEGES;
			setAttribute(name, names, params);
		} else
			throw NamespaceError.NAME_INVALID;
	}
}
