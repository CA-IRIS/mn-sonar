/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2010  Minnesota Department of Transportation
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

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * A message decoder provides a Java API for decoding messages from the SONAR
 * wire protocol.
 *
 * @author Douglas Lau
 */
public class MessageDecoder {

	/** Everything on the wire is encoded to UTF-8 */
	static protected final Charset UTF8 = Charset.forName("UTF-8");

	/** Byte buffer to store incoming SONAR data */
	protected final ByteBuffer app_in;

	/** Byte buffer input stream */
	protected final ByteBufferInputStream in_buf;

	/** GZIP input stream for decompressing data */
	protected final GZIPInputStream gzip_in;

	/** Char reader input stream */
	protected final InputStreamReader reader;

	/** String builder to build decoded parameters */
	protected final StringBuilder m_buf = new StringBuilder();

	/** List of decoded parameters */
	protected final LinkedList<String> params = new LinkedList<String>();

	/** Create a new SONAR message decoder */
	public MessageDecoder(ByteBuffer in) throws IOException {
		app_in = in;
		in_buf = new ByteBufferInputStream(in);
		gzip_in = new GZIPInputStream(in_buf);
		reader = new InputStreamReader(gzip_in, UTF8);
	}

	/** Complete the current parameter */
	protected void completeParameter() {
		params.add(m_buf.toString());
		m_buf.setLength(0);
	}

	/** Decode messages */
	public List<String> decode() throws IOException {
		try {
			app_in.flip();
			return _decode();
		}
		finally {
			app_in.compact();
		}
	}

	/** Decode messages */
	protected List<String> _decode() throws IOException {
		params.clear();
		while(reader.ready()) {
			int ch = reader.read();
			if(ch < 0)
				break;
			char c = (char)ch;
			if(c == Message.TERMINATOR.code) {
				completeParameter();
				return params;
			} else if(c == Message.DELIMITER.code)
				completeParameter();
			else
				m_buf.append(c);
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
