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

import java.lang.reflect.Method;
import java.util.TreeSet;
import us.mn.state.dot.sonar.Marshaller;
import us.mn.state.dot.sonar.ProtocolError;

/**
 * Attributes are named values attached to SONAR proxy objects.
 *
 * @author Douglas Lau
 */
class Attribute {

	/** The type of the attribute */
	protected final Class type;

	/** The value of the attribute */
	protected Object value;

	/** Create a new attribute of the specified class */
	public Attribute(Class t) {
		type = t;
		value = null;
	}

	/** Create a new attribute with the specified value */
	public Attribute(Object v) {
		type = v.getClass();
		value = v;
	}

	/** Get the value of the attribute */
	public Object getValue() {
		return value;
	}

	/** Unmarshall a value and store it in the attribute */
	public void unmarshall(String[] v) throws ProtocolError {
		value = Marshaller.unmarshall(type, v);
	}

	/** Marshall the given attribute value */
	public String[] marshall(Object[] args) {
		return Marshaller.marshall(type, args);
	}

	/** Check if the attribute value equals the given value */
	public boolean valueEquals(Object[] v) {
		if(value == null && v[0] == null)
			return true;
		else if(value != null && value.equals(v[0]))
			return true;
		else
			return false;
	}
}
