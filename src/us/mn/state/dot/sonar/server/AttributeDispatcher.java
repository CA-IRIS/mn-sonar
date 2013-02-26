/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2013  Minnesota Department of Transportation
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
package us.mn.state.dot.sonar.server;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.TreeSet;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * An attribute dispatcher is an adapter for SonarObjects. It provides
 * a pair of simple methods to set and get attributes of those objects.
 *
 * @author Douglas Lau
 */
public class AttributeDispatcher {

	/** Method name to store an object */
	static protected final String DO_STORE_METHOD = "doStore";

	/** Method name to destroy an object */
	static protected final String DESTROY_METHOD = "destroy";

	/** Alternate method name to destroy an object */
	static protected final String DO_DESTROY_METHOD = "doDestroy";

	/** Empty array of parameters */
	static protected final Object[] NO_PARAMS = new Object[0];

	/** Empty array of strings */
	static protected final String[] EMPTY_STRING = new String[0];

	/** Test if a class is an interface extending SonarObject */
	static protected boolean is_sonar_iface(Class iface) {
		return iface.isInterface() &&
		       SonarObject.class.isAssignableFrom(iface) &&
		       (iface != SonarObject.class);
	}

	/** Check for a valid constructor */
	static protected boolean is_valid_constructor(Constructor c) {
		Class[] paramTypes = c.getParameterTypes();
		return (paramTypes.length == 1) &&
			(paramTypes[0] == String.class);
	}

	/** Lookup the constructor */
	static protected Constructor lookup_constructor(Class c) {
		for(Constructor con: c.getConstructors()) {
			if(is_valid_constructor(con))
				return con;
		}
		return null;
	}

	/** Lookup a method on the specified class */
	static protected Method lookup_method(Class c, String method) {
		for(Method m: c.getMethods()) {
			if(method.equalsIgnoreCase(m.getName())) {
				if(!Modifier.isStatic(m.getModifiers()))
					return m;
			}
		}
		return null;
	}

	/** Lookup a method to store new objects */
	static protected Method lookup_storer(Class c) {
		return lookup_method(c, DO_STORE_METHOD);
	}

	/** Lookup a method to destroy objects */
	static protected Method lookup_destroyer(Class c) {
		Method m = lookup_method(c, DO_DESTROY_METHOD);
		if(m != null)
			return m;
		else
			return lookup_method(c, DESTROY_METHOD);
	}

	/** Lookup all attribute setter or getter methods */
	protected HashMap<String, Method> lookup_methods(String prefix,
		Class c)
	{
		HashMap<String, Method> map = new HashMap<String, Method>();
		for(String a: attributes) {
			Method m = lookup_method(c, prefix + a);
			if(m != null)
				map.put(a, m);
		}
		return map;
	}

	/** SONAR namespace */
	protected final Namespace namespace;

	/** Attributes which can be dispatched */
	protected final TreeSet<String> attributes = new TreeSet<String>();

	/** Constructor to create a new object */
	protected final Constructor constructor;

	/** Method to store an object */
	protected final Method storer;

	/** Method to destroy an object */
	protected final Method destroyer;

	/** Mapping of attribute names to setter methods */
	protected final HashMap<String, Method> setters;

	/** Mapping of attribute names to getter methods */
	protected final HashMap<String, Method> getters;

	/** Get an array of readable attributes */
	public String[] getReadableAttributes() {
		return getters.keySet().toArray(EMPTY_STRING);
	}

	/** Test if an attribute is readable */
	public boolean isReadable(String a) {
		return getters.containsKey(a);
	}

	/** Lookup all the attributes of the specified class */
	protected void lookup_attributes(Class c) {
		while(c != null) {
			for(Class iface: c.getInterfaces()) {
				if(is_sonar_iface(iface)) {
					lookup_iface_attributes(iface);
					lookup_attributes(iface);
				}
			}
			c = c.getSuperclass();
		}
	}

	/** Lookup all the attributes of the specified interface */
	protected void lookup_iface_attributes(Class iface) {
		for(Method m: iface.getDeclaredMethods()) {
			String n = m.getName();
			if(n.startsWith("get") || n.startsWith("set")) {
				String a = n.substring(3, 4).toLowerCase() +
					n.substring(4);
				attributes.add(a);
			}
		}
	}

	/** Create a new attribute dispatcher for the given object's type */
	public AttributeDispatcher(Class c, Namespace ns) {
		namespace = ns;
		lookup_attributes(c);
		constructor = lookup_constructor(c);
		storer = lookup_storer(c);
		destroyer = lookup_destroyer(c);
		setters = lookup_methods("set", c);
		getters = lookup_methods("get", c);
		// Accessor methods with a "do" prefix are required for
		// methods which can throw exceptions not declared
		// in the interface specification.
		setters.putAll(lookup_methods("doSet", c));
		getters.putAll(lookup_methods("doGet", c));
	}

	/** Create a new object with the given name */
	public SonarObject createObject(String name) throws SonarException {
		if(constructor == null)
			throw PermissionDenied.CANNOT_ADD;
		Object[] params = { name };
		try {
			return (SonarObject)constructor.newInstance(params);
		}
		catch(Exception e) {
			throw new SonarException(e);
		}
	}

	/** Invoke a method on the given SONAR object */
	protected Object _invoke(SonarObject o, Method method, Object[] params)
		throws SonarException
	{
		try {
			return method.invoke(o, params);
		}
		catch(Exception e) {
			throw new SonarException(e);
		}
	}

	/** Invoke a method on the given SONAR object */
	protected Object invoke(SonarObject o, Method method, String[] v)
		throws SonarException
	{
		Class[] p_types = method.getParameterTypes();
		Object[] params = namespace.unmarshall(p_types, v);
		return _invoke(o, method, params);
	}

	/** Store the given object */
	public void storeObject(SonarObject o) throws SonarException {
		if(storer == null)
			throw PermissionDenied.CANNOT_ADD;
		invoke(o, storer, EMPTY_STRING);
	}

	/** Destroy the given object */
	public void destroyObject(SonarObject o) throws SonarException {
		if(destroyer == null)
			throw PermissionDenied.CANNOT_REMOVE;
		invoke(o, destroyer, EMPTY_STRING);
	}

	/** Set the value of the named attribute */
	public void setValue(SonarObject o, String a, String[] v)
		throws SonarException
	{
		Method m = (Method)setters.get(a);
		if(m == null)
			throw PermissionDenied.CANNOT_WRITE;
		invoke(o, m, v);
	}

	/** Lookup the named field from the given class */
	static protected Field lookupField(Class c, String a)
		throws SonarException
	{
		try {
			Field f = c.getDeclaredField(a);
			f.setAccessible(true);
			return f;
		}
		catch(NoSuchFieldException e) {
			c = c.getSuperclass();
			if(c != null)
				return lookupField(c, a);
			else
				throw new SonarException("No such field: " + a);
		}
		catch(Exception e) {
			throw new SonarException(e);
		}
	}

	/** Set a field directly (through reflection) */
	public void setField(SonarObject o, String a, String[] v)
		throws SonarException
	{
		Field f = lookupField(o.getClass(), a);
		Object param = namespace.unmarshall(f.getType(), v);
		try {
			f.set(o, param);
		}
		catch(Exception e) {
			throw new SonarException(e);
		}
	}

	/** Get the value of the named attribute */
	public String[] getValue(SonarObject o, String a)
		throws SonarException
	{
		Method m = (Method)getters.get(a);
		if(m == null)
			throw PermissionDenied.CANNOT_READ;
		Object result = _invoke(o, m, NO_PARAMS);
		if(result instanceof Object[]) {
			Object[] r = (Object [])result;
			String[] res = new String[r.length];
			for(int i = 0; i < r.length; i++)
				res[i] = namespace.marshall(r[i]);
			return res;
		} else
			return new String[] { namespace.marshall(result) };
	}
}
