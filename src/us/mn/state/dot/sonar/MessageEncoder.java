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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

/**
 * A message encoder provides a Java API for encoding messages to the SONAR
 * wire protocol.
 *
 * @author Douglas Lau
 */
public class MessageEncoder {

	/** Everything on the wire is encoded to UTF-8 */
	static protected final Charset UTF8 = Charset.forName("UTF-8");

	/** Byte buffer output stream */
	protected final ByteBufferOutputStream out_buf;

	/** GZIP output stream for compressing data */
	protected final GZIPOutputStream gzip_out;

	/** Char writer output stream */
	protected final OutputStreamWriter writer;

	/** Create a new SONAR message encoder */
	public MessageEncoder(int n_bytes) throws IOException {
		out_buf = new ByteBufferOutputStream(n_bytes);
		gzip_out = new GZIPOutputStream(out_buf);
		writer = new OutputStreamWriter(gzip_out, UTF8);
	}

	/** Encode one message with the given code.
	 * This may only be called on the Task Processor thread. */
	public void encode(Message m) throws IOException {
		encode(m, null, null);
	}

	/** Encode one message with the given code and name.
	 * This may only be called on the Task Processor thread. */
	public void encode(Message m, String name) throws IOException {
		encode(m, name, null);
	}

	/** Encode one message with the given code, name and parameters.
	 * This may only be called on the Task Processor thread. */
	public void encode(Message m, String name, String[] params)
		throws IOException
	{
		writer.write(m.code);
		if(name != null) {
			writer.write(Message.DELIMITER.code);
			writer.write(name);
			if(params != null) {
				for(String p: params) {
					writer.write(Message.DELIMITER.code);
					writer.write(p);
				}
			}
		}
		writer.write(Message.TERMINATOR.code);
	}

	/** Flush the encoded data */
	public void flush() throws IOException {
		writer.flush();
	}

	/** Get the current output buffer */
	public ByteBuffer getBuffer() {
		return out_buf.getBuffer();
	}

	/** Check if there is any encoded data */
	public boolean hasData() {
		return getBuffer().position() > 0;
	}
}
