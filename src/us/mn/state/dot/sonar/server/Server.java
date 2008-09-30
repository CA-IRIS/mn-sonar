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

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sched.Job;
import us.mn.state.dot.sched.Scheduler;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.FlushError;
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

	/** SONAR debug log */
	static protected final DebugLog DEBUG = new DebugLog("sonar");

	/** SONAR task debug log */
	static protected final DebugLog DEBUG_TASK = new DebugLog("sonar_task");

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

	/** LDAP authenticator for user credentials */
	protected final LDAPAuthenticator authenticator;

	/** Map of active client connections */
	protected final Map<SelectionKey, ConnectionImpl> clients =
		new HashMap<SelectionKey, ConnectionImpl>();

	/** File to write session list */
	protected final String session_file;

	/** Task processor thread */
	protected final Scheduler processor = new Scheduler("Task Processor",
		new ExceptionHandler() {
			public boolean handle(Exception e) {
				if(e instanceof CancelledKeyException) {
					System.err.println(
						"SONAR: Key already cancelled");
				} else if(e instanceof SSLException) {
					System.err.println("SONAR: SSL error " +
						e.getMessage());
				} else {
					System.err.println("SONAR: error " +
						e.getMessage());
					e.printStackTrace();
				}
				return true;
			}
		}
	);

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
		LDAPSocketFactory.FACTORY = context.getSocketFactory();
		String ldap_urls = props.getProperty("sonar.ldap.urls");
		if(ldap_urls == null)
			throw new ConfigurationError("LDAP urls not specified");
		authenticator = new LDAPAuthenticator(ldap_urls);
		session_file = props.getProperty("sonar.session.file");
		setDaemon(true);
		start();
	}

	/** Create a new SONAR server */
	public Server(Namespace n) throws IOException, ConfigurationError {
		this(n, PropertyLoader.load(PROP_FILE));
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

	/** Get a list of active connections */
	protected List<ConnectionImpl> getConnectionList() {
		LinkedList<ConnectionImpl> clist =
			new LinkedList<ConnectionImpl>();
		synchronized(clients) {
			clist.addAll(clients.values());
		}
		return clist;
	}

	/** Disconnect the client associated with the selection key */
	public void disconnect(SelectionKey key) {
		key.cancel();
		ConnectionImpl c;
		synchronized(clients) {
			c = clients.remove(key);
		}
		if(c != null) {
			updateSessionList();
			System.err.println("SONAR: Disconnected " +c.getName());
			removeObject(c);
		}
	}

	/** Update list of valid session IDs */
	protected void updateSessionList() {
		if(session_file == null)
			return;
		List<ConnectionImpl> clist = getConnectionList();
		try {
			FileWriter fw = new FileWriter(session_file);
			try {
				for(ConnectionImpl c: clist) {
					fw.write(String.valueOf(
						c.getSessionId()));
					fw.append('\n');
				}
			}
			finally {
				fw.close();
			}
		}
		catch(IOException e) {
			System.err.println("SONAR: Error writing session " +
				"file: " + session_file + " (" +
				e.getMessage() + ")");
		}
	}

	/** Process messages on one connection */
	public void processMessages(final ConnectionImpl c) {
		processor.addJob(new Job() {
			public void perform() {
				DEBUG_TASK.log("Processing messages for " +
					c.getName());
				try {
					c.processMessages();
				}
				catch(SSLException e) {
					c.disconnect("SSL error " +
						e.getMessage());
				}
				catch(FlushError e) {
					c.disconnect("Flush error " +
						e.getMessage());
				}
			}
		});
	}

	/** Flush outgoing data for one connection */
	public void flush(final ConnectionImpl c) {
		processor.addJob(new Job() {
			public void perform() {
				DEBUG_TASK.log("Flushing for " + c.getName());
				c.flush();
			}
		});
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
		updateSessionList();
	}

	/** Check if a new client is connecting */
	protected boolean checkAccept(SelectionKey key) {
		try {
			if(key.isAcceptable()) {
				DEBUG.log("Accepting connection");
				doAccept();
			} else
				return false;
		}
		catch(CancelledKeyException e) {
			disconnect(key);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		return true;
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
		if(c == null) {
			disconnect(key);
			return;
		}
		try {
			if(key.isWritable()) {
				DEBUG.log("Writing data to " + c.getName());
				c.doWrite();
			}
			if(key.isReadable()) {
				DEBUG.log("Reading data from " + c.getName());
				c.doRead();
			}
		}
		catch(CancelledKeyException e) {
			c.disconnect("Key cancelled");
		}
		catch(IOException e) {
			c.disconnect("I/O error " + e.getMessage());
		}
	}

	/** Select and perform I/O on ready channels */
	protected void _doSelect() throws IOException {
		selector.select();
		Set<SelectionKey> readySet = selector.selectedKeys();
		for(SelectionKey key: readySet) {
			if(checkAccept(key))
				continue;
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
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist)
			c.notifyObject(names, o);
	}

	/** Notify all connections watching a name of an attribute change */
	void notifyAttribute(String tname, String oname, String name,
		String[] params)
	{
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist)
			c.notifyAttribute(tname, oname, name, params);
	}

	/** Notify all connections watching a name of an object remove */
	public void notifyRemove(String name, String value) {
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist) {
			c.notifyRemove(name, value);
			c.stopWatching(value);
		}
	}

	/** Server loop to perfrom socket I/O */
	public void run() {
		while(true)
			doSelect();
	}

	/** Add the specified object to the server's namespace */
	public void addObject(final SonarObject o) {
		processor.addJob(new Job() {
			public void perform() throws NamespaceError {
				DEBUG_TASK.log("Adding object " + o.getName());
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

	/** Create (synchronously) an object in the server's namespace */
	public void createObject(SonarObject o) throws SonarException {
		namespace.storeObject(o);
		String[] names = new String[] {
			o.getTypeName(), o.getName()
		};
		notifyObject(names, o);
	}

	/** Remove the specified object from the server's namespace */
	public void removeObject(final SonarObject o) {
		processor.addJob(new Job() {
			public void perform() throws SonarException {
				DEBUG_TASK.log("Removing object " +o.getName());
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
		processor.addJob(new Job() {
			public void perform() throws NamespaceError {
				DEBUG_TASK.log("Setting attribute " + a +
					" on " + o.getName());
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
