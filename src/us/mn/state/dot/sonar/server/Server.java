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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.PropertyLoader;
import us.mn.state.dot.sonar.Security;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * The SONAR server processes all data transfers with client connections.
 *
 * @author Douglas Lau
 */
public class Server extends Thread {

	/** SSL unwrap buffer size. This should really be taken from the
	 * session.getApplicationBufferSize(), but since all client sessions
	 * share the same buffer, it has to be defined here. Unfortunately,
	 * this isn't future-proof. */
	static protected final int SSL_UNWRAP_BUFFER_SIZE = 16660;

	/** SSL wrap buffer size. This should really be taken from the
	 * session.getPacketBufferSize(), but since all client sessions
	 * share the same buffer, it has to be defined here. Unfortunately,
	 * this isn't future-proof. */
	static protected final int SSL_WRAP_BUFFER_SIZE = 16665;

	/** SONAR server configuration file */
	static protected final String PROP_FILE = "/etc/sonar/sonar.properties";

	/** SONAR namespace being served */
	protected final Namespace namespace;

	/** Selector for non-blocking I/O */
	protected final Selector selector;

	/** Socket channel to listen for new client connections */
	protected final ServerSocketChannel channel;

	/** SSL context */
	protected final SSLContext context;

	/** SSL unwrap buffer, shared by all clients */
	protected final ByteBuffer ssl_buf;

	/** SSL wrap buffer, shared by all clients */
	protected final ByteBuffer ssl_out;

	/** LDAP authenticator for user credentials */
	protected final LDAPAuthenticator authenticator;

	/** Map of active client connections */
	protected final Map<SelectionKey, ConnectionImpl> clients =
		new HashMap<SelectionKey, ConnectionImpl>();

	/** Task processor thread */
	protected final TaskProcessor processor = new TaskProcessor();

	/** Get the port from a set of properties */
	static protected int getPort(Properties p) throws ConfigurationError {
		try {
			return Integer.parseInt(p.getProperty("sonar.port"));
		}
		catch(NumberFormatException e) {
			throw new ConfigurationError("Invalid sonar.port");
		}
	}

	/** Create a new SONAR server */
	public Server(Namespace n, Properties props) throws IOException,
		ConfigurationError
	{
		namespace = n;
		selector = Selector.open();
		channel = createChannel(getPort(props));
		channel.register(selector, SelectionKey.OP_ACCEPT);
		context = Security.createContext(props);
		String ldap_host = props.getProperty("ldap.host");
		if(ldap_host == null)
			throw new ConfigurationError("LDAP host not specified");
		String ldap_port = props.getProperty("ldap.port");
		if(ldap_port == null)
			throw new ConfigurationError("LDAP port not specified");
		String ldap_ssl = props.getProperty("ldap.ssl");
		if(ldap_ssl == null)
			throw new ConfigurationError("LDAP SSL not specified");
		if(ldap_ssl.equalsIgnoreCase("true"))
			authenticator = new LDAPAuthenticator(context,
				ldap_host, Integer.valueOf(ldap_port));
		else
			authenticator = new LDAPAuthenticator(ldap_host,
				Integer.valueOf(ldap_port));
		setDaemon(true);
		ssl_buf = ByteBuffer.allocate(SSL_UNWRAP_BUFFER_SIZE);
		ssl_out = ByteBuffer.allocate(SSL_WRAP_BUFFER_SIZE);
		start();
	}

	/** Create a new SONAR server */
	public Server(Namespace n) throws IOException, ConfigurationError {
		this(n, PropertyLoader.load(PROP_FILE));
	}

	/** Get the (shared) SSL unwrap buffer */
	public ByteBuffer getSSLUnwrapBuffer() {
		return ssl_buf;
	}

	/** Get the (shared) SSL wrap buffer */
	public ByteBuffer getSSLWrapBuffer() {
		return ssl_out;
	}

	/** Create and configure a server socket channel */
	protected ServerSocketChannel createChannel(int port)
		throws IOException
	{
		ServerSocketChannel c = ServerSocketChannel.open();
		c.configureBlocking(false);
		InetAddress host = InetAddress.getLocalHost();
		InetSocketAddress address = new InetSocketAddress(host, port);
		c.socket().bind(address);
		return c;
	}

	/** Get the user authenticator */
	public LDAPAuthenticator getAuthenticator() {
		return authenticator;
	}

	/** Get the SONAR namespace */
	public Namespace getNamespace() {
		return namespace;
	}

	/** Disconnect the client associated with the selection key */
	public void disconnect(SelectionKey key) {
		key.cancel();
		ConnectionImpl c;
		synchronized(clients) {
			c = clients.remove(key);
		}
		if(c != null) {
			System.err.println("SONAR: Disconnected " +c.getName());
			removeObject(c);
		}
	}

	/** Process data on one connection */
	public void processConnection(ConnectionImpl c) {
		processor.add(c);
	}

	/** Accept a new client connection */
	protected void doAccept() throws IOException {
		SocketChannel c = channel.accept();
		c.configureBlocking(false);
		SelectionKey key = c.register(selector, SelectionKey.OP_READ);
		ConnectionImpl con = new ConnectionImpl(this, key, c);
		System.err.println("SONAR: Connected " + con.getName());
		addObject(con);
		synchronized(clients) {
			clients.put(key, con);
		}
	}

	/** Create an SSL engine in the server context */
	public SSLEngine createSSLEngine() {
		SSLEngine engine = context.createSSLEngine();
		engine.setUseClientMode(false);
		return engine;
	}

	/** Do any pending read/write on a client connection */
	protected void serviceClient(SelectionKey key) {
		ConnectionImpl c;
		synchronized(clients) {
			c = clients.get(key);
		}
		try {
			if(key.isWritable())
				c.doWrite();
			if(key.isReadable())
				c.doRead();
		}
		catch(IOException e) {
			c.disconnect();
		}
	}

	/** Select and perform I/O on ready channels */
	protected void _doSelect() throws IOException {
		selector.select();
		Set<SelectionKey> readySet = selector.selectedKeys();
		for(SelectionKey key: readySet) {
			if(key.isAcceptable()) {
				doAccept();
				continue;
			}
			serviceClient(key);
		}
		readySet.clear();
	}

	/** Select and perform I/O on ready channels */
	protected void doSelect() {
		try {
			_doSelect();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	/** Notify all connections watching a name of an object add */
	public void notifyObject(String[] names, SonarObject o) {
		synchronized(clients) {
			for(ConnectionImpl c: clients.values())
				c.notifyObject(names, o);
		}
	}

	/** Notify all connections watching a name of an attribute change */
	public void notifyAttribute(String tname, String oname, String name,
		String[] params)
	{
		synchronized(clients) {
			for(ConnectionImpl c: clients.values())
				c.notifyAttribute(tname, oname, name, params);
		}
	}

	/** Notify all connections watching a name of an object remove */
	public void notifyRemove(String name, String value) {
		synchronized(clients) {
			for(ConnectionImpl c: clients.values()) {
				c.notifyRemove(name, value);
				c.stopWatching(value);
			}
		}
	}

	/** Server loop to perfrom socket I/O */
	public void run() {
		while(true)
			doSelect();
	}

	/** Add the specified object to the server's namespace */
	public void addObject(final SonarObject o) {
		processor.add(new Task() {
			public String getName() {
				return "ObjectAdder";
			}
			public void perform() throws NamespaceError {
				doAddObject(o);
			}
		});
	}

	/** Perform an add object task */
	protected void doAddObject(SonarObject o) throws NamespaceError {
		namespace.add(o);
		String[] names = new String[] {
			o.getTypeName(), o.getName()
		};
		notifyObject(names, o);
	}

	/** Remove the specified object from the server's namespace */
	public void removeObject(final SonarObject o) {
		processor.add(new Task() {
			public String getName() {
				return "ObjectRemover";
			}
			public void perform() throws SonarException {
				doRemoveObject(o);
			}
		});
	}

	/** Perform a remove object task */
	protected void doRemoveObject(SonarObject o) throws SonarException {
		notifyRemove(o.getTypeName(), Names.makePath(o));
		namespace.removeObject(o);
	}

	/** Set the specified attribute in the server's namespace */
	public void setAttribute(final SonarObject o, final String a,
		final String[] v)
	{
		processor.add(new Task() {
			public String getName() {
				return "AttributeSetter";
			}
			public void perform() throws NamespaceError {
				doSetAttribute(o, a, v);
			}
		});
	}

	/** Perform a "set attribute" task */
	protected void doSetAttribute(SonarObject o, String a, String[] v) {
		String oname = Names.makePath(o);
		String aname = Names.makePath(o, a);
		notifyAttribute(o.getTypeName(), oname, aname, v);
	}
}
