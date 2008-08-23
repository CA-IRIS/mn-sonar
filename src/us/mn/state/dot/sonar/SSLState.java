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
import java.nio.channels.ReadableByteChannel;
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
		net_out = ByteBuffer.allocate(p_size);
		net_in = ByteBuffer.allocate(p_size);
		app_out = ByteBuffer.allocate(a_size);
		app_in = ByteBuffer.allocate(a_size);
		ssl_in = ByteBuffer.allocate(a_size);
		ssl_out = ByteBuffer.allocate(p_size);
		decoder = new MessageDecoder(app_in);
		encoder = new MessageEncoder(app_out, conduit);
		engine.beginHandshake();
	}

	/** Get the network out buffer */
	public ByteBuffer getNetOutBuffer() {
		return net_out;
	}

	/** Write encoded data to a channel */
	public void doWrite() throws IOException {
		doWrapHandshake();
	}

	/** Read available data from the specified channel */
	public void doRead(ReadableByteChannel c) throws IOException {
		int nbytes = c.read(net_in);
		if(nbytes > 0) {
			doHandshake();
			while(doUnwrap());
		} else if(nbytes < 0)
			throw new IOException("EOF");
	}

	/** Do any pending handshake stuff */
	public void doHandshake() throws SSLException {
		while(_doHandshake());
	}

	/** Do something to progress handshaking */
	protected boolean _doHandshake() throws SSLException {
		hs = engine.getHandshakeStatus();
		switch(hs) {
			case NOT_HANDSHAKING:
			case FINISHED:
				return false;
			case NEED_TASK:
				doTask();
				return true;
			case NEED_WRAP:
				doWrap();
				return false;
			case NEED_UNWRAP:
				return doUnwrap();
		}
		return false;
	}

	/** Perform a delegated SSL engine task */
	protected void doTask() {
		Runnable task = engine.getDelegatedTask();
		if(task != null)
			task.run();
	}

	/** Do handshake wrap if needed */
	protected void doWrapHandshake() throws SSLException {
		hs = engine.getHandshakeStatus();
		if(hs == SSLEngineResult.HandshakeStatus.NEED_WRAP)
			doWrap();
	}

	/** Wrap application data into SSL buffer */
	public boolean doWrap() throws SSLException {
		if(app_out.position() > net_out.remaining())
			return false;
		SSLEngineResult result;
		synchronized(ssl_out) {
			ssl_out.clear();
			synchronized(app_out) {
				app_out.flip();
				try {
					result = engine.wrap(app_out, ssl_out);
				}
				finally {
					app_out.compact();
				}
			}
			ssl_out.flip();
			synchronized(net_out) {
				net_out.put(ssl_out);
			}
		}
		conduit.enableWrite();
		return checkStatus(result);
	}

	/** Unwrap SSL data into appcliation buffer */
	protected boolean doUnwrap() throws SSLException {
		SSLEngineResult result;
		net_in.flip();
		try {
			if(!net_in.hasRemaining())
				return false;
			ssl_in.clear();
			result = engine.unwrap(net_in, ssl_in);
			ssl_in.flip();
			synchronized(app_in) {
				app_in.put(ssl_in);
			}
		}
		finally {
			net_in.compact();
		}
		return checkStatus(result);
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

	/** Check if the handshake is still in progress */
	public boolean isHandshaking() {
		return hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
	}
}
