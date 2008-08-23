/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006  Minnesota Department of Transportation
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
import java.util.LinkedList;
import java.util.List;

/**
 * A message decoder provides a Java API for decoding messages from the SONAR
 * wire protocol.
 *
 * @author Douglas Lau
 */
public class MessageDecoder {

	/** Everything on the wire is encoded to UTF-8 */
	static protected final Charset UTF8 = Charset.forName("UTF-8");

	/** Byte buffer where encoded message data is stored */
	protected final ByteBuffer r_buf;

	/** Character buffer to hold decoded message data */
	protected CharBuffer c_buf;

	/** Character buffer used to build messages */
	protected final CharBuffer m_buf;

	/** List of decoded parameters */
	protected final LinkedList<String> params = new LinkedList<String>();

	/** Create a new SONAR message decoder */
	public MessageDecoder(ByteBuffer in) {
		r_buf = in;
		m_buf = CharBuffer.allocate(256);
		m_buf.clear();
		c_buf = CharBuffer.allocate(256);
		c_buf.flip();
	}

	/** Complete the current parameter */
	protected void completeParameter() {
		m_buf.flip();
		String p = m_buf.toString();
		params.add(p);
		m_buf.clear();
	}

	/** Decode messages */
	public List<String> decode() {
		params.clear();
		if(!c_buf.hasRemaining()) {
			synchronized(r_buf) {
				r_buf.flip();
				c_buf = UTF8.decode(r_buf);
				r_buf.compact();
			}
		}
		while(c_buf.hasRemaining()) {
			char c = c_buf.get();
			if(c == Message.TERMINATOR.code) {
				completeParameter();
				return params;
			} else if(c == Message.DELIMITER.code)
				completeParameter();
			else
				m_buf.put(c);
		}
		return null;
	}

	/** Debug the SONAR parameters */
	public void debugParameters() {
		StringBuilder b = new StringBuilder();
		for(String s: params) {
			b.append(s);
			b.append(' ');
		}
		System.err.println(b.toString());
	}
}
