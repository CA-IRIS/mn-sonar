/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2014  Minnesota Department of Transportation
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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import us.mn.state.dot.sched.ExceptionHandler;
import us.mn.state.dot.sched.Job;
import us.mn.state.dot.sched.Scheduler;
import us.mn.state.dot.sonar.Conduit;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.Security;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * The SONAR client processes all data transfers with the server.
 *
 * @author Douglas Lau
 */
public class Client extends Thread {

	/** Exception handler */
	protected final ExceptionHandler handler;

	/** Selector for non-blocking I/O */
	protected final Selector selector;

	/** SSL context */
	protected final SSLContext context;

	/** Client conduit */
	protected final ClientConduit conduit;

	/** Task processor thread */
	protected final Scheduler processor = new Scheduler("sonar_proc");

	/** Message processor task */
	protected final MessageProcessor m_proc = new MessageProcessor();

	/** Flag to indicate the client is quitting */
	protected boolean quitting = false;

	/** Get the connection name */
	public String getConnection() {
		return conduit.getConnection();
	}

	/** Get the namespace */
	public Namespace getNamespace() {
		return conduit.getNamespace();
	}

	/** Create a new SONAR client */
	public Client(Properties props, ExceptionHandler h) throws IOException,
		ConfigurationError
	{
		super("SONAR Client");
		handler = h;
		selector = Selector.open();
		context = Security.createContext(props);
		conduit = new ClientConduit(props, this, selector,
			createSSLEngine(), handler);
		setDaemon(true);
		setPriority(Thread.MAX_PRIORITY);
		start();
	}

	/** Create an SSL engine in the client context */
	protected SSLEngine createSSLEngine() {
		SSLEngine engine = context.createSSLEngine();
		engine.setUseClientMode(true);
		return engine;
	}

	/** Client loop to perfrom socket I/O */
	public void run() {
		while(selector.isOpen()) {
			try {
				doSelect();
			}
			catch(IOException e) {
				if(!quitting)
					handler.handle(e);
				break;
			}
		}
		processor.dispose();
	}

	/** Select and perform I/O on ready channels */
	protected void doSelect() throws IOException {
		selector.select();
		Set<SelectionKey> ready = selector.selectedKeys();
		for(SelectionKey key: ready) {
			if(key.isConnectable())
				conduit.doConnect();
			if(key.isWritable())
				conduit.doWrite();
			if(key.isReadable())
				conduit.doRead();
		}
		ready.clear();
	}

	/** Populate the specified type cache */
	public void populate(final TypeCache tc) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.queryAll(tc);
			}
		});
	}

	/** Populate the specified type cache */
	@SuppressWarnings("unchecked")
	public void populate(TypeCache tc, boolean wait) {
		if(wait) {
			EnumerationWaiter<SonarObject> ew =
				new EnumerationWaiter<SonarObject>();
			tc.addProxyListener(ew);
			populate(tc);
			while(!ew.complete) {
				try {
					Thread.sleep(100);
				}
				catch(InterruptedException e) {
					// Ignore
				}
			}
			tc.removeProxyListener(ew);
		} else
			populate(tc);
	}

	/** Simple class to wait for enumeration of a type to complete */
	private class EnumerationWaiter<T extends SonarObject>
		implements ProxyListener<T>
	{
		protected boolean complete = false;
		public void proxyAdded(T proxy) { }
		public void enumerationComplete() {
			complete = true;
		}
		public void proxyRemoved(T proxy) { }
		public void proxyChanged(T proxy, String a) { }
	}

	/** Login to the SONAR server.
	 * @param user Name of user.
	 * @param password Password of user.
	 * @throws SonarException Thrown on error (needed by subclasses). */
	public void login(final String user, final String password)
		throws SonarException
	{
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.login(user, password);
			}
		});
	}

	/** Check if the client is logged in */
	public boolean isLoggedIn() {
		return conduit.isLoggedIn();
	}

	/** Send a password change request */
	public void changePassword(final String pwd_current,
		final String pwd_new)
	{
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.changePassword(pwd_current, pwd_new);
			}
		});
	}

	/** Process messages on the conduit */
	void processMessages() {
		processor.addJob(m_proc);
	}

	/** Message processor for handling incoming messages */
	protected class MessageProcessor extends Job {
		public void perform() throws IOException {
			try {
				conduit.processMessages();
			}
			catch(IOException e) {
				conduit.disconnect("I/O error: " +
					e.getMessage());
				throw e;
			}
		}
	}

	/** Message processor for handling incoming messages */
	void flush() {
		// NOTE: this was added as a workaround for a login problem
		//       on Windows using a slow link.  The connection would
		//       get "stuck" during SSL handshaking, and the login
		//       request would never get sent to the server.
		processor.addJob(new Job(500) {
			public void perform() {
				conduit.flush();
			}
		});
	}

	/** Quit the client connection */
	public void quit() {
		quitting = true;
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.quit();
			}
		});
	}

	/** Request an attribute change */
	void setAttribute(final Name name, final String[] params) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.setAttribute(name, params);
			}
		});
	}

	/** Create the specified object name */
	void createObject(final Name name) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.createObject(name);
			}
		});
	}

	/** Remove the specified object name */
	void removeObject(final Name name) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.removeObject(name);
			}
		});
	}

	/** Enumerate the specified name */
	void enumerateName(final Name name) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.enumerateName(name);
			}
		});
	}

	/** Ignore the specified name */
	void ignoreName(final Name name) {
		processor.addJob(new Job() {
			public void perform() throws IOException {
				conduit.ignoreName(name);
			}
		});
	}
}
