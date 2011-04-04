/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2011  Minnesota Department of Transportation
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import us.mn.state.dot.sched.DebugLog;
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sched.Job;
import us.mn.state.dot.sched.Scheduler;
import us.mn.state.dot.sched.TimeSteward;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.Security;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * The SONAR server processes all data transfers with client connections.
 *
 * @author Douglas Lau
 */
public class Server extends Thread {

	/** Directory to store IRIS log files FIXME */
	static protected final String LOG_FILE_DIR = "/var/log/iris/";

	/** SONAR debug log */
	static protected final DebugLog DEBUG = new DebugLog(LOG_FILE_DIR +
		"sonar");

	/** SONAR task debug log */
	static protected final DebugLog DEBUG_TASK = new DebugLog(LOG_FILE_DIR +
		"sonar_task");

	/** SONAR namespace being served */
	protected final ServerNamespace namespace;

	/** Selector for non-blocking I/O */
	protected final Selector selector;

	/** Socket channel to listen for new client connections */
	protected final ServerSocketChannel channel;

	/** SSL context */
	protected final SSLContext context;

	/** LDAP authenticator for user credentials */
	protected final LDAPAuthenticator authenticator;

	/** Authentication thread */
	protected final Scheduler auth_sched = new Scheduler("LDAP Auth",
		new ExceptionHandler()
	{
		public boolean handle(Exception e) {
			System.err.println("SONAR: auth error " +
				e.getMessage());
			e.printStackTrace();
			return true;
		}
	});

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
	public Server(ServerNamespace n, Properties props) throws IOException,
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

	/** Create and configure a server socket channel */
	protected ServerSocketChannel createChannel(int port)
		throws IOException
	{
		ServerSocketChannel c = ServerSocketChannel.open();
		c.configureBlocking(false);
		InetAddress host = InetAddress.getByAddress(new byte[4]);
		InetSocketAddress address = new InetSocketAddress(host, port);
		c.socket().bind(address);
		return c;
	}

	/** Get the SONAR namespace */
	ServerNamespace getNamespace() {
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
	void disconnect(SelectionKey key) {
		key.cancel();
		ConnectionImpl c;
		synchronized(clients) {
			c = clients.remove(key);
		}
		if(c != null) {
			updateSessionList();
			System.err.println("SONAR: Disconnected " + 
				c.getName() + ", on " + 
				TimeSteward.getDateInstance() + ".");
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
	void processMessages(final ConnectionImpl c) {
		processor.addJob(new Job() {
			public void perform() {
				DEBUG_TASK.log("Processing messages for " +
					c.getName());
				c.processMessages();
			}
		});
	}

	/** Flush outgoing data for one connection */
	void flush(final ConnectionImpl c) {
		processor.addJob(new Job() {
			public void perform() {
				DEBUG_TASK.log("Flushing for " + c.getName());
				c.flush();
			}
		});
	}

	/** Authenticate a user connection */
	void authenticate(final ConnectionImpl c, final UserImpl u,
		final String password)
	{
		auth_sched.addJob(new Job() {
			public void perform() throws IOException {
				try {
					authenticator.authenticate(u.getDn(),
						password.toCharArray());
					finishLogin(c, u);
				}
				catch(PermissionDenied e) {
					c.failLogin(u);
					c.flush();
				}
			}
		});
	}

	/** Finish a LOGIN */
	protected void finishLogin(final ConnectionImpl c, final UserImpl u) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				DEBUG_TASK.log("Finishing LOGIN for " +
					u.getName());
				c.finishLogin(u);
				setAttribute(c, "user");
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
	SSLEngine createSSLEngine() {
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

	/** Notify all connections watching a name of an object add.
	 * This may only be called on the Task Processor thread. */
	void notifyObject(SonarObject o) {
		Name name = new Name(o);
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist)
			c.notifyObject(name, o);
	}

	/** Notify all connections watching a name of an attribute change.
	 * This may only be called on the Task Processor thread. */
	void notifyAttribute(Name name, String[] params) {
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist)
			c.notifyAttribute(name, params);
	}

	/** Notify all connections watching a name of an object remove.
	 * This may only be called on the Task Processor thread. */
	void notifyRemove(Name name) {
		List<ConnectionImpl> clist = getConnectionList();
		for(ConnectionImpl c: clist)
			c.notifyRemove(name);
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

	/** Perform an add object task.
	 * This may only be called on the Task Processor thread. */
	protected void doAddObject(SonarObject o) throws NamespaceError {
		namespace.addObject(o);
		notifyObject(o);
	}

	/** Create (synchronously) an object in the server's namespace */
	public void createObject(final SonarObject o) throws SonarException {
		// FIXME: should be renamed to storeObject
		if(processor.isCurrentThread()) {
			doCreateObject(o);
			return;
		}
		Job job = new Job() {
			public void perform() throws SonarException {
				doCreateObject(o);
			}
		};
		processor.addJob(job);
		job.waitForCompletion();
	}

	/** Create (synchronously) an object in the server's namespace.
	 * This may only be called on the Task Processor thread. */
	protected void doCreateObject(SonarObject o) throws SonarException {
		DEBUG_TASK.log("Storing object " + o.getName());
		namespace.storeObject(o);
		notifyObject(o);
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

	/** Perform a remove object task.
	 * This may only be called on the Task Processor thread. */
	protected void doRemoveObject(SonarObject o) throws SonarException {
		notifyRemove(new Name(o));
		namespace.removeObject(o);
	}

	/** Set the specified attribute in the server's namespace */
	public void setAttribute(final SonarObject o, final String a) {
		processor.addJob(new Job() {
			public void perform() throws SonarException {
				DEBUG_TASK.log("Setting attribute " + a +
					" on " + o.getName());
				doSetAttribute(o, a);
			}
		});
	}

	/** Perform a "set attribute" task.
	 * This may only be called on the Task Processor thread. */
	protected void doSetAttribute(SonarObject o, String aname)
		throws SonarException
	{
		Name name = new Name(o, aname);
		String[] v = namespace.getAttribute(name);
		notifyAttribute(name, v);
	}
}
