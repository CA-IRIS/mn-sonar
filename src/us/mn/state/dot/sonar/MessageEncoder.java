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

	/** Number of tries to flush write buffer */
	static protected final int FLUSH_TRIES = 10;

	/** Threshold of bytes to start flushing write buffer */
	static protected final int FLUSH_THRESHOLD = 1024;

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
		m_buf = CharBuffer.allocate(256);
		app_out = out;
		conduit = c;
	}

	/** Encode one message with the given code, name and parameters */
	public void encode(Message m, String name, String[] params) {
		m_buf.clear();
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
		m_buf.flip();
		if(conduit.isConnected())
			fillBuffer(UTF8.encode(m_buf));
	}

	/** Encode one message with the given code and name */
	public void encode(Message m, String name) {
		encode(m, name, null);
	}

	/** Encode one message with the given code */
	public void encode(Message m) {
		encode(m, null, null);
	}

	/** Check if we must flush the write buffer */
	protected boolean mustFlush(int n_bytes) {
		return app_out.remaining() < n_bytes + FLUSH_THRESHOLD;
	}

	/** Ensure there is capacity in the write buffer */
	protected boolean ensureCapacity(int n_bytes) {
		for(int i = 0; i < FLUSH_TRIES; i++) {
			if(mustFlush(n_bytes)) {
				conduit.setWritePending(true);
				conduit.flush();
			} else
				return true;
		}
		System.err.println("SONAR flush failed: " + conduit.getName());
		conduit.disconnect();
		return false;
	}

	/** Fill the output buffer with encoded message data */
	protected void fillBuffer(ByteBuffer b) {
		if(ensureCapacity(b.remaining())) {
			app_out.put(b);
			conduit.setWritePending(true);
		}
	}
}
