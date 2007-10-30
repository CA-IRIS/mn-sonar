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
package us.mn.state.dot.sonar.client;

import java.lang.reflect.Proxy;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import us.mn.state.dot.sonar.Marshaller;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * A type cache represents the first-level nodes in the SONAR namespace. It
 * contains all proxy objects of the given type.
 *
 * @author Douglas Lau
 */
public class TypeCache<T extends SonarObject> {

	/** Class loader needed to create proxy objects */
	static protected final ClassLoader LOADER =
		TypeCache.class.getClassLoader();

	/** Get the hash code of a proxy object */
	static protected int hashCode(SonarObject o) {
		return System.identityHashCode(o);
	}

	/** Conduit (to send attribute update messages) */
	protected ClientConduit conduit;

	/** Type name */
	public final String tname;

	/** Interfaces which proxies of this type implement */
	protected final Class[] ifaces;

	/** Sonar object proxy method invoker */
	protected final SonarInvoker invoker;

	/** All SONAR objects of this type are put here.
	 * All access must be synchronized on the "children" lock. */
	private final HashMap<String, T> children = new HashMap<String, T>();

	/** Mapping from object identity to attribute hash.
	 * All access must be synchronized on the "children" lock. */
	private final HashMap<Integer, HashMap<String, Attribute>> attributes =
		new HashMap<Integer, HashMap<String, Attribute>>();

	/** Proxy listener list */
	private final LinkedList<ProxyListener<T>> listeners =
		new LinkedList<ProxyListener<T>>();

	/** Create a type cache. NOTE: because of limitations with generics
	 * and reflection, the interface must be passed to the constructor in
	 * addition to the &lt;T&gt; qualifier. These types must be identical!
	 * For example, to create a cache of Users, do this:
	 * <code>new TypeCache&lt;User&gt;(User.class)</code> */
	public TypeCache(Class iface) throws NoSuchFieldException,
		IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(iface);
		tname = Marshaller.typeName(iface);
		ifaces = new Class[] { iface };
		invoker = new SonarInvoker(this, iface);
	}

	/** Set the conduit to send messages */
	void setConduit(ClientConduit c) {
		conduit = c;
	}

	/** Notify proxy listeners that a proxy has been added */
	protected void notifyProxyAdded(T proxy) {
		for(ProxyListener<T> l: listeners)
			l.proxyAdded(proxy);
	}

	/** Notify proxy listeners that a proxy has been removed */
	protected void notifyProxyRemoved(T proxy) {
		for(ProxyListener<T> l: listeners)
			l.proxyRemoved(proxy);
	}

	/** Notify proxy listeners that a proxy has been changed */
	protected void notifyProxyChanged(T proxy, String a) {
		for(ProxyListener<T> l: listeners)
			l.proxyChanged(proxy, a);
	}

	/** Add a proxy to the type cache */
	T add(String name) throws NamespaceError {
		T o = (T)Proxy.newProxyInstance(LOADER, ifaces, invoker);
		HashMap<String, Attribute> amap = invoker.createAttributes();
		amap.put("typeName", new Attribute(tname));
		amap.put("name", new Attribute(name));
		synchronized(children) {
			if(children.containsKey(name))
				throw NamespaceError.NAME_EXISTS;
			children.put(name, o);
			attributes.put(hashCode(o), amap);
			notifyProxyAdded(o);
		}
		return o;
	}

	/** Remove a proxy from the type cache */
	T remove(String name) throws NamespaceError {
		synchronized(children) {
			if(!children.containsKey(name))
				throw NamespaceError.NAME_UNKNOWN;
			T proxy = children.remove(name);
			notifyProxyRemoved(proxy);
			attributes.remove(hashCode(proxy));
			return proxy;
		}
	}

	/** Lookup a proxy from the given name */
	public T lookupObject(String n) throws NamespaceError {
		synchronized(children) {
			T o = children.get(n);
			if(o == null)
				throw NamespaceError.NAME_UNKNOWN;
			else
				return o;
		}
	}

	/** Lookup the attribute map for the given object id */
	protected HashMap<String, Attribute> lookupAttributeMap(int i) {
		synchronized(children) {
			return attributes.get(i);
		}
	}

	/** Lookup an attribute of the given proxy */
	protected Attribute lookupAttribute(T o, String a)
		throws NamespaceError
	{
		int i = System.identityHashCode(o);
		HashMap<String, Attribute> amap = lookupAttributeMap(i);
		if(amap == null)
			throw NamespaceError.NAME_UNKNOWN;
		Attribute attr = amap.get(a);
		if(attr == null)
			throw NamespaceError.NAME_UNKNOWN;
		else
			return attr;
	}

	/** Get the value of an attribute from the given proxy */
	Object getAttribute(T o, String a) throws NamespaceError {
		Attribute attr = lookupAttribute(o, a);
		return attr.getValue();
	}

	/** Get the value of an attribute from the named proxy */
	Object getAttribute(String n, String a) throws NamespaceError {
		return getAttribute(lookupObject(n), a);
	}

	/** Set the value of an attribute on the given proxy */
	void setAttribute(T o, String a, Object[] args) throws SonarException {
		Attribute attr = lookupAttribute(o, a);
		String[] values = attr.marshall(args);
		String name = Names.makePath(o, a);
		conduit.setAttribute(name, values);
	}

	/** Unmarshall an attribute value into the given proxy */
	void unmarshallAttribute(T o, String a, String[] v)
		throws SonarException
	{
		synchronized(children) {
			Attribute attr = lookupAttribute(o, a);
			attr.unmarshall(v);
			notifyProxyChanged(o, a);
		}
	}

	/** Remove the specified object */
	void removeObject(T o) {
		String name = Names.makePath(o);
		conduit.removeObject(name);
	}

	/** Create the specified object name */
	public void createObject(String oname) {
		String name = Names.makePath(tname, oname);
		conduit.createObject(name);
	}

	/** Get the map of names to SonarObjects of the specified type. All
	 * access to this map must be synchronized. Any attempt to write to
	 * the Map will invalidate the cache. */
	public Map<String, T> getAll() {
		return children;
	}

	/** Add a ProxyListener */
	public void addProxyListener(ProxyListener<T> l) {
		synchronized(children) {
			listeners.add(l);
			for(T proxy: children.values())
				l.proxyAdded(proxy);
		}
	}

	/** Remove a ProxyListener */
	public void removeProxyListener(ProxyListener<T> l) {
		synchronized(children) {
			listeners.remove(l);
		}
	}
}
