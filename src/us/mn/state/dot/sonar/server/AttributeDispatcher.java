/*
 * SONAR -- Simple Object Notification And Replication
 * Copyright (C) 2006-2007  Minnesota Department of Transportation
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.TreeSet;
import us.mn.state.dot.sonar.Marshaller;
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

	/** Method name (static) to create a new object */
	static protected final String CREATE_METHOD = "create";

	/** Alternate method name (static) to create a new object */
	static protected final String DO_CREATE_METHOD = "doCreate";

	/** Method name to destroy an object */
	static protected final String DESTROY_METHOD = "destroy";

	/** Alternate method name to destroy an object */
	static protected final String DO_DESTROY_METHOD = "doDestroy";

	/** Empty array of parameters */
	static protected final Object[] NO_PARAMS = new Object[0];

	/** Empty array of strings */
	static protected final String[] EMPTY_STRING = new String[0];

	/** Lookup all the attributes of the specified interface */
	static protected String[] _lookup_attributes(Class iface) {
		TreeSet<String> attributes = new TreeSet<String>();
		for(Method m: iface.getDeclaredMethods()) {
			String n = m.getName();
			if(n.startsWith("get") || n.startsWith("set")) {
				String a = n.substring(3, 4).toLowerCase() +
					n.substring(4);
				attributes.add(a);
			}
		}
		return (String [])attributes.toArray(EMPTY_STRING);
	}

	/** Lookup all the attributes of the specified class */
	static protected String[] lookup_attributes(Class c) {
		while(c != null) {
			Class[] ifaces = c.getInterfaces();
			for(Class i: ifaces) {
				if(SonarObject.class.isAssignableFrom(i))
					return _lookup_attributes(i);
			}
			c = c.getSuperclass();
		}
		return EMPTY_STRING;
	}

	/** Lookup a method on the specified class */
	static protected Method lookup_method(Class c, String method) {
		for(Method m: c.getMethods()) {
			if(method.equalsIgnoreCase(m.getName()))
				return m;
		}
		return null;
	}

	/** Check for a valid factory method */
	static protected boolean valid_factory(Method m) {
		return Modifier.isStatic(m.getModifiers()) &&
			(m.getParameterTypes().length == 1) &&
			(SonarObject.class.isAssignableFrom(m.getReturnType()));
	}

	/** Lookup a factory method to create new objects */
	static protected Method lookup_creator(SonarObject o) {
		Class c = o.getClass();
		Method m = lookup_method(c, DO_CREATE_METHOD);
		if(m != null && valid_factory(m))
			return m;
		else {
			m = lookup_method(c, CREATE_METHOD);
			if(m != null && valid_factory(m))
				return m;
			else
				return null;
		}
	}

	/** Lookup a method to destroy objects */
	static protected Method lookup_destroyer(SonarObject o) {
		Class c = o.getClass();
		Method m = lookup_method(c, DO_DESTROY_METHOD);
		if(m != null)
			return m;
		else
			return lookup_method(c, DESTROY_METHOD);
	}

	/** Make an array of the given class and size */
	static protected Object[] makeArray(Class a_type, int size) {
		return (Object [])Array.newInstance(a_type, size);
	}

	/** Lookup all attribute setter or getter methods */
	protected HashMap<String, Method> lookup_methods(String prefix,
		SonarObject o)
	{
		HashMap<String, Method> map = new HashMap<String, Method>();
		Class c = o.getClass();
		for(String a: attributes) {
			Method m = lookup_method(c, prefix + a);
			if(m != null)
				map.put(a, m);
		}
		return map;
	}

	/** Attributes which can be dispatched */
	public final String[] attributes;

	/** Factory method to create a new object */
	protected final Method creator;

	/** Method to destroy an object */
	protected final Method destroyer;

	/** Mapping of attribute names to setter methods */
	protected final HashMap<String, Method> setters;

	/** Mapping of attribute names to getter methods */
	protected final HashMap<String, Method> getters;

	/** Create a new attribute dispatcher for the given object's type */
	public AttributeDispatcher(SonarObject o) {
		attributes = lookup_attributes(o.getClass());
		creator = lookup_creator(o);
		destroyer = lookup_destroyer(o);
		setters = lookup_methods("set", o);
		getters = lookup_methods("get", o);
		// Accessor methods with a "do" prefix are required for
		// methods which can throw exceptions not declared
		// in the interface specification.
		setters.putAll(lookup_methods("doSet", o));
		getters.putAll(lookup_methods("doGet", o));
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

	/** Invoke an array method on the given SONAR object */
	protected Object invokeArray(SonarObject o, Method method, Class p_type,
		String[] v) throws SonarException
	{
		Object[] params = makeArray(p_type, v.length);
		for(int i = 0; i < params.length; i++)
			params[i] = Marshaller.unmarshall(p_type, v[i]);
		return _invoke(o, method, new Object[] { params });
	}

	/** Invoke a method on the given SONAR object */
	protected Object invoke(SonarObject o, Method method, String[] v)
		throws SonarException
	{
		Class[] p_type = method.getParameterTypes();
		if(p_type.length == 1 && p_type[0].isArray()) {
			return invokeArray(o, method,
				p_type[0].getComponentType(), v);
		}
		if(p_type.length != v.length)
			throw ProtocolError.WRONG_PARAMETER_COUNT;
		Object[] params = new Object[p_type.length];
		for(int i = 0; i < params.length; i++)
			params[i] = Marshaller.unmarshall(p_type[i], v[i]);
		return _invoke(o, method, params);
	}

	/** Create a new object with the given name */
	public SonarObject createObject(String name) throws SonarException {
		if(creator == null)
			throw PermissionDenied.CANNOT_ADD;
		String[] params = { name };
		Object o = invoke(null, creator, params);
		return (SonarObject)o;
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
				res[i] = Marshaller.marshall(r[i]);
			return res;
		} else
			return new String[] { Marshaller.marshall(result) };
	}
}
