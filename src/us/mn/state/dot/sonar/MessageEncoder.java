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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * A message encoder provides a Java API for encoding messages to the SONAR
 * wire protocol.
 *
 * @author Douglas Lau
 */
public class MessageEncoder {

	/** Maximum size (characters) of a single message */
	static protected final int MAX_MESSAGE_SIZE = 512;

	/** Threshold of bytes to start flushing write buffer */
	static protected final int FLUSH_THRESHOLD = 1024;

	/** Number of tries to flush write buffer */
	static protected final int FLUSH_TRIES = 40;

	/** Time to allow I/O thread to flush output buffer */
	static protected final int FLUSH_WAIT_MS = 100;

	/** Sleep to allow the network thread to do some work */
	protected void sleepBriefly() {
		try {
			Thread.sleep(FLUSH_WAIT_MS);
		}
		catch(InterruptedException e) {
			// Shouldn't happen, and who cares?
		}
	}

	/** Everything on the wire is encoded to UTF-8 */
	static protected final Charset UTF8 = Charset.forName("UTF-8");

	/** Character buffer used to build messages */
	protected final CharBuffer m_buf;

	/** Byte buffer to write encoded data */
	protected final ByteBuffer app_out;

	/** Client conduit */
	protected final Conduit conduit;

	/** Create a new SONAR message encoder */
	public MessageEncoder(ByteBuffer out, Conduit c) {
		m_buf = CharBuffer.allocate(MAX_MESSAGE_SIZE);
		app_out = out;
		conduit = c;
	}

	/** Encode one message with the given code */
	public void encode(Message m) throws FlushError {
		encode(m, null, null);
	}

	/** Encode one message with the given code and name */
	public void encode(Message m, String name) throws FlushError {
		encode(m, name, null);
	}

	/** Encode one message with the given code, name and parameters */
	public void encode(Message m, String name, String[] params)
		throws FlushError
	{
		try {
			m_buf.clear();
			_encode(m, name, params);
			m_buf.flip();
		}
		catch(BufferOverflowException e) {
			throw new FlushError();
		}
		fillBuffer(UTF8.encode(m_buf));
	}

	/** Encode one message with the given code, name and parameters */
	protected void _encode(Message m, String name, String[] params)
		throws FlushError
	{
		m_buf.put(m.code);
		if(name != null) {
			m_buf.put(Message.DELIMITER.code);
			m_buf.put(name);
			if(params != null) {
				for(String p: params) {
					m_buf.put(Message.DELIMITER.code);
					m_buf.put(p);
				}
			}
		}
		m_buf.put(Message.TERMINATOR.code);
	}

	/** Check if we must flush the write buffer */
	protected boolean mustFlush(int n_bytes) {
		return app_out.remaining() < n_bytes + FLUSH_THRESHOLD;
	}

	/** Ensure there is capacity in the write buffer */
	protected void ensureCapacity(int n_bytes) throws FlushError {
		for(int i = 0; i < FLUSH_TRIES; i++) {
			if(mustFlush(n_bytes)) {
				conduit.flush();
				sleepBriefly();
			} else
				return;
		}
		throw new FlushError();
	}

	/** Fill the output buffer with encoded message data */
	protected void fillBuffer(ByteBuffer b) throws FlushError {
		ensureCapacity(b.remaining());
		try {
			app_out.put(b);
		}
		catch(BufferOverflowException e) {
			throw new FlushError();
		}
	}
}
