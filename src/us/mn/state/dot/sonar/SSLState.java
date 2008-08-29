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
package us.mn.state.dot.sonar;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * The SSL state manages buffers and handshaking for one SSL connection.
 *
 * @author Douglas Lau
 */
public class SSLState {

	/** Size (in bytes) of network buffers */
	static protected final int NETWORK_BUFFER_SIZE = 1 << 20;

	/** Conduit */
	protected final Conduit conduit;

	/** SSL engine */
	protected final SSLEngine engine;

	/** SSL engine handshake status */
	protected SSLEngineResult.HandshakeStatus hs;

	/** Byte buffer to store outgoing encrypted network data */
	protected final ByteBuffer net_out;

	/** Byte buffer to store incoming encrypted network data */
	protected final ByteBuffer net_in;

	/** Byte buffer to store outgoing SONAR data */
	protected final ByteBuffer app_out;

	/** Byte buffer to store incoming SONAR data */
	protected final ByteBuffer app_in;

	/** Byte buffer to wrap outgoing SSL data */
	protected final ByteBuffer ssl_out;

	/** Byte buffer to unwrap incoming SSL data */
	protected final ByteBuffer ssl_in;

	/** Decoder for messages received */
	public final MessageDecoder decoder;

	/** Encoder for messages to send */
	public final MessageEncoder encoder;

	/** Create a new SONAR SSL state */
	public SSLState(Conduit c, SSLEngine e) throws SSLException {
		conduit = c;
		engine = e;
		SSLSession session = engine.getSession();
		int p_size = session.getPacketBufferSize();
		int a_size = session.getApplicationBufferSize();
		net_out = ByteBuffer.allocate(NETWORK_BUFFER_SIZE);
		net_in = ByteBuffer.allocate(NETWORK_BUFFER_SIZE);
		app_out = ByteBuffer.allocate(a_size);
		app_in = ByteBuffer.allocate(a_size);
		ssl_out = ByteBuffer.allocate(p_size);
		ssl_in = ByteBuffer.allocate(a_size);
		decoder = new MessageDecoder(app_in);
		encoder = new MessageEncoder(app_out, conduit);
		engine.beginHandshake();
	}

	/** Get the network out buffer */
	public ByteBuffer getNetOutBuffer() {
		return net_out;
	}

	/** Ge the network in buffer */
	public ByteBuffer getNetInBuffer() {
		return net_in;
	}

	/** Read available data from network input buffer */
	public boolean doRead() throws IOException {
		doUnwrap();
		while(doHandshake());
		return app_in.position() > 0;
	}

	/** Do something to progress handshaking */
	protected boolean doHandshake() throws SSLException {
		hs = engine.getHandshakeStatus();
		switch(hs) {
			case NEED_TASK:
				doTask();
				return true;
			case NEED_WRAP:
				doWrap();
				return true;
			case NEED_UNWRAP:
				return doUnwrap();
			default:
				return false;
		}
	}

	/** Write data to the network output buffer */
	public boolean doWrite() throws IOException {
		doWrap();
		return app_out.position() > 0;
	}

	/** Perform a delegated SSL engine task */
	protected void doTask() {
		Runnable task = engine.getDelegatedTask();
		if(task != null)
			task.run();
	}

	/** Wrap application data into SSL buffer */
	protected void doWrap() throws SSLException {
		ssl_out.clear();
		app_out.flip();
		try {
			engine.wrap(app_out, ssl_out);
		}
		finally {
			app_out.compact();
		}
		ssl_out.flip();
		int n_bytes;
		synchronized(net_out) {
			net_out.put(ssl_out);
			n_bytes = net_out.position();
		}
		if(n_bytes > 0)
			conduit.enableWrite();
	}

	/** Unwrap SSL data into appcliation buffer */
	protected boolean doUnwrap() throws SSLException {
		synchronized(net_in) {
			net_in.flip();
			try {
				if(!net_in.hasRemaining())
					return false;
				ssl_in.clear();
				engine.unwrap(net_in, ssl_in);
				ssl_in.flip();
				app_in.put(ssl_in);
			}
			finally {
				net_in.compact();
			}
		}
		return true;
	}

	/** Check the status of the SSL engine */
	static protected boolean checkStatus(SSLEngineResult result) {
		switch(result.getStatus()) {
			case BUFFER_OVERFLOW:
				System.err.println("SSL: buffer OVERFLOW");
				break;
			case BUFFER_UNDERFLOW:
				break;
			case CLOSED:
				System.err.println("SSL: CLOSED");
				break;
		}
		return result.getStatus() == SSLEngineResult.Status.OK;
	}
}
