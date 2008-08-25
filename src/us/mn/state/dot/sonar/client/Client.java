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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import javax.naming.AuthenticationException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import us.mn.state.dot.sonar.Conduit;
import us.mn.state.dot.sonar.ConfigurationError;
import us.mn.state.dot.sonar.Security;
import us.mn.state.dot.sonar.SonarObject;
import us.mn.state.dot.sonar.Task;
import us.mn.state.dot.sonar.TaskProcessor;

/**
 * The SONAR client processes all data transfers with the server.
 *
 * @author Douglas Lau
 */
public class Client extends Thread {

	/** Selector for non-blocking I/O */
	protected final Selector selector;

	/** SSL context */
	protected final SSLContext context;

	/** Client conduit */
	protected final ClientConduit conduit;

	/** Task processor thread */
	protected final TaskProcessor processor = new TaskProcessor();

	/** Message processor task */
	protected final MessageProcessor m_proc = new MessageProcessor();

	/** Get the connection name */
	public String getConnection() {
		return conduit.getConnection();
	}

	/** Create a new SONAR client */
	public Client(Properties props, ShowHandler handler) throws IOException,
		ConfigurationError
	{
		super("SONAR Client");
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
		while(conduit.isConnected()) {
			try {
				doSelect();
			}
			catch(Exception e) {
				e.printStackTrace();
				break;
			}
		}
	}

	/** Quit the client connection */
	public void quit() {
		conduit.quit();
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
	public void populate(TypeCache tc) {
		tc.setConduit(conduit);
		conduit.queryAll(tc);
	}

	/** Login to the SONAR server */
	public void login(final String user, final String password)
		throws AuthenticationException
	{
		processor.add(new Task() {
			public String getName() {
				return "LoginTask";
			}
			public void perform() {
				conduit.login(user, password);
			}
		});
		for(int i = 0; i < 100; i++) {
			if(conduit.isLoggedIn())
				return;
			try {
				Thread.sleep(50);
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		throw new AuthenticationException("Login failed");
	}

	/** Process messages on the conduit */
	public void processMessages() {
		processor.add(m_proc);
	}

	/** Message processor for handling incoming messages */
	protected class MessageProcessor implements Task {
		public String getName() {
			return "MessageProcessor";
		}
		public void perform() throws IOException {
			try {
				conduit.processMessages();
			}
			catch(IOException e) {
				conduit.disconnect();
				throw e;
			}
		}
	}

	/** Message processor for handling incoming messages */
	public void flush() {
		processor.add(new Task() {
			public String getName() {
				return "Flusher";
			}
			public void perform() {
				conduit.flush();
			}
		});
	}
}
