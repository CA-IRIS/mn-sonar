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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarObject;

/**
 * This is a helper class to marshall and unmarshall parameter values.
 *
 * @author Douglas Lau
 */
public class Marshaller {

	/** Marshall a java object into a parameter value string */
	static public String marshall(Object v) {
		if(v instanceof SonarObject) {
			SonarObject o = (SonarObject)v;
			return o.getName();
		} else if(v != null)
			return v.toString();
		else
			return "";
	}

	/** Marshall java parameters into a parameter value string */
	static public String[] marshall(Class t, Object[] v) {
		if(t.isArray())
			v = (Object [])v[0];
		String[] values = new String[v.length];
		for(int i = 0; i < v.length; i++)
			values[i] = marshall(v[i]);
		return values;
	}

	/** Unmarshall a parameter value string into a java object */
	static public Object unmarshall(Class t, String p)
		throws ProtocolError
	{
		if(t == String.class)
			return p;
		try {
			if(t == Integer.TYPE || t == Integer.class)
				return Integer.valueOf(p);
			else if(t == Short.TYPE || t == Short.class)
				return Short.valueOf(p);
			else if(t == Boolean.TYPE || t == Boolean.class)
				return Boolean.valueOf(p);
			else if(t == Float.TYPE || t == Float.class)
				return Float.valueOf(p);
			else if(t == Double.TYPE || t == Double.class)
				return Double.valueOf(p);
		}
		catch(NumberFormatException e) {
			throw ProtocolError.INVALID_PARAMETER;
		}
		if("".equals(p))
			return null;
		if(SonarObject.class.isAssignableFrom(t)) {
			try {
				return lookup_reference(t, p);
			}
			catch(Exception e) {
				System.err.println("SONAR: unmarshall \"" + p +
					"\": " + e.getMessage());
				throw ProtocolError.INVALID_PARAMETER;
			}
		}
		throw ProtocolError.INVALID_PARAMETER;
	}

	/** Make an array of the given class and size */
	static protected Object[] makeArray(Class t, int size) {
		return (Object [])Array.newInstance(t, size);
	}

	/** Unmarshall parameter strings into java array parameters */
	static protected Object[] unmarshallArray(Class t, String[] v)
		throws ProtocolError
	{
		Object[] values = makeArray(t, v.length);
		for(int i = 0; i < v.length; i++)
			values[i] = unmarshall(t, v[i]);
		return values;
	}

	/** Unmarshall parameter strings into java parameters */
	static public Object unmarshall(Class t, String[] v)
		throws ProtocolError
	{
		if(t.isArray())
			return unmarshallArray(t.getComponentType(), v);
		else {
			if(v.length != 1)
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			return unmarshall(t, v[0]);
		}
	}

	/** Get the name of a SONAR type */
	static public String typeName(Class t)
		throws NoSuchFieldException, IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(t);
		Field f = (Field)t.getField("SONAR_TYPE");
		return (String)f.get(t);
	}

	/** Lookup a reference to another object in the namespace */
	static protected Object lookup_reference(Class t, String p)
		throws NoSuchFieldException, IllegalAccessException,
		NamespaceError
	{
		return Names.lookup(typeName(t), p);
	}
}
