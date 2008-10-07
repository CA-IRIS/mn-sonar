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

import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * A namespace is a mapping of names to objects.
 *
 * @author Douglas Lau
 */
abstract public class Namespace {

	/** Name separator */
	static public final String SEP = "/";

	/** Empty namespace path */
	static protected final String[] NULL_PATH = {};

	/** Parse a SONAR path */
	static public String[] parse(String path) {
		if(path.length() == 0)
			return NULL_PATH;
		else
			return path.split(SEP);
	}

	/** Make a SONAR path from an object */
	static public String makePath(SonarObject o) {
		return o.getTypeName() + SEP + o.getName();
	}

	/** Make a SONAR path from an object/attribute pair */
	static public String makePath(SonarObject o, String a) {
		return o.getTypeName() + SEP + o.getName() + SEP + a;
	}

	/** Make a SONAR path from a type/object pair */
	static public String makePath(String tname, String oname) {
		return tname + SEP + oname;
	}

	/** Make a SONAR path from a type/object/attribute tuple */
	static public String makePath(String tnm, String onm, String anm) {
		return tnm + SEP + onm + SEP + anm;
	}

	/** Test if a SONAR path is absolute (versus relative) */
	static public boolean isAbsolute(String path) {
		return path.contains(SEP);
	}

	/** Get the name of a SONAR type */
	static public String typeName(Class t)
		throws NoSuchFieldException, IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(t);
		Field f = (Field)t.getField("SONAR_TYPE");
		return (String)f.get(t);
	}

	/** Make an array of the given class and size */
	static protected Object[] makeArray(Class t, int size) {
		return (Object [])Array.newInstance(t, size);
	}

	/** Marshall a java object into a parameter value string */
	public String marshall(Object v) {
		if(v instanceof SonarObject) {
			SonarObject o = (SonarObject)v;
			return o.getName();
		} else if(v != null)
			return v.toString();
		else
			return "";
	}

	/** Marshall java parameters into a parameter value string */
	public String[] marshall(Class t, Object[] v) {
		if(t.isArray())
			v = (Object [])v[0];
		String[] values = new String[v.length];
		for(int i = 0; i < v.length; i++)
			values[i] = marshall(v[i]);
		return values;
	}

	/** Unmarshall a parameter value string into a java object */
	public Object unmarshall(Class t, String p) throws ProtocolError {
		if(t == String.class)
			return p;
		if("".equals(p))
			return null;
		try {
			if(t == Integer.TYPE || t == Integer.class)
				return Integer.valueOf(p);
			else if(t == Short.TYPE || t == Short.class)
				return Short.valueOf(p);
			else if(t == Boolean.TYPE || t == Boolean.class)
				return Boolean.valueOf(p);
			else if(t == Float.TYPE || t == Float.class)
				return Float.valueOf(p);
			else if(t == Long.TYPE || t == Long.class)
				return Long.valueOf(p);
			else if(t == Double.TYPE || t == Double.class)
				return Double.valueOf(p);
		}
		catch(NumberFormatException e) {
			throw ProtocolError.INVALID_PARAMETER;
		}
		if(SonarObject.class.isAssignableFrom(t)) {
			try {
				return lookupObject(typeName(t), p);
			}
			catch(Exception e) {
				System.err.println("SONAR: unmarshall \"" + p +
					"\": " + e.getMessage());
				throw ProtocolError.INVALID_PARAMETER;
			}
		}
		throw ProtocolError.INVALID_PARAMETER;
	}

	/** Unmarshall parameter strings into one java parameter */
	public Object unmarshall(Class t, String[] v) throws ProtocolError {
		if(t.isArray())
			return unmarshallArray(t.getComponentType(), v);
		else {
			if(v.length != 1)
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			return unmarshall(t, v[0]);
		}
	}

	/** Unmarshall parameter strings into a java array parameter */
	protected Object[] unmarshallArray(Class t, String[] v)
		throws ProtocolError
	{
		Object[] values = makeArray(t, v.length);
		for(int i = 0; i < v.length; i++)
			values[i] = unmarshall(t, v[i]);
		return values;
	}

	/** Unmarshall multiple parameters */
	public Object[] unmarshall(Class[] pt, String[] v) throws ProtocolError
	{
		if(pt.length == 1 && pt[0].isArray()) {
			return new Object[] {
				unmarshall(pt[0], v)
			};
		}
		if(pt.length != v.length)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		Object[] params = new Object[pt.length];
		for(int i = 0; i < params.length; i++)
			params[i] = unmarshall(pt[i], v[i]);
		return params;
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name
	 * @param oname Sonar object name
	 * @return Object from namespace or null if name does not exist */
	abstract public SonarObject lookupObject(String tname, String oname);
}
