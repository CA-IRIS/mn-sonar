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
import java.nio.channels.WritableByteChannel;
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
	protected final ByteBuffer out_buf;

	/** Byte buffer to store incoming encrypted network data */
	protected final ByteBuffer in_buf;

	/** Byte buffer to store outgoing SONAR data */
	protected final ByteBuffer w_buf;

	/** Byte buffer to store incoming SONAR data */
	protected final ByteBuffer r_buf;

	/** Byte buffer to wrap outgoing SSL data */
	protected final ByteBuffer ssl_out;

	/** Byte buffer to unwrap incoming SSL data */
	protected final ByteBuffer ssl_buf;

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
		out_buf = ByteBuffer.allocate(p_size);
		in_buf = ByteBuffer.allocate(p_size);
		w_buf = ByteBuffer.allocate(a_size);
		r_buf = ByteBuffer.allocate(a_size);
		ssl_buf = ByteBuffer.allocate(a_size);
		ssl_out = ByteBuffer.allocate(p_size);
		decoder = new MessageDecoder(r_buf);
		encoder = new MessageEncoder(w_buf, conduit);
		engine.beginHandshake();
	}

	/** Write encoded data to a channel */
	public void doWrite(WritableByteChannel c) throws IOException {
		synchronized(out_buf) {
			out_buf.flip();
			c.write(out_buf);
			if(!out_buf.hasRemaining())
				conduit.disableWrite();
			out_buf.compact();
		}
		doWrapHandshake();
	}

	/** Read available data from the specified channel */
	public void doRead(ReadableByteChannel c) throws IOException {
		int nbytes = c.read(in_buf);
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
		if(w_buf.position() > out_buf.remaining())
			return false;
		SSLEngineResult result;
		synchronized(ssl_out) {
			ssl_out.clear();
			synchronized(w_buf) {
				w_buf.flip();
				try {
					result = engine.wrap(w_buf, ssl_out);
				}
				finally {
					w_buf.compact();
				}
			}
			ssl_out.flip();
			synchronized(out_buf) {
				out_buf.put(ssl_out);
			}
		}
		conduit.enableWrite();
		return checkStatus(result);
	}

	/** Unwrap SSL data into appcliation buffer */
	protected boolean doUnwrap() throws SSLException {
		SSLEngineResult result;
		in_buf.flip();
		try {
			if(!in_buf.hasRemaining())
				return false;
			ssl_buf.clear();
			result = engine.unwrap(in_buf, ssl_buf);
			ssl_buf.flip();
			synchronized(r_buf) {
				r_buf.put(ssl_buf);
			}
		}
		finally {
			in_buf.compact();
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
