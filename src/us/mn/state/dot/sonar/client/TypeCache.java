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
package us.mn.state.dot.sonar.client;

import java.lang.reflect.Proxy;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import us.mn.state.dot.sonar.Checker;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
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

	/** Type name */
	public final String tname;

	/** Interfaces which proxies of this type implement */
	protected final Class[] ifaces;

	/** Sonar object proxy method invoker */
	protected final SonarInvoker invoker;

	/** Client (to send attribute update messages) */
	protected final Client client;

	/** SONAR namespace */
	protected final Namespace namespace;

	/** All SONAR objects of this type are put here.
	 * All access must be synchronized on the "children" lock. */
	private final HashMap<String, T> children = new HashMap<String, T>();

	/** Flag to indicate enumeration from server is complete */
	private boolean enumerated = false;

	/** A phantom is a new object which has had attributes set, but not
	 * been declared with Message.OBJECT ("o") */
	private T phantom;

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
	public TypeCache(Class iface, Client c) throws NoSuchFieldException,
		IllegalAccessException
	{
		assert SonarObject.class.isAssignableFrom(iface);
		tname = Namespace.typeName(iface);
		ifaces = new Class[] { iface };
		invoker = new SonarInvoker(this, iface);
		client = c;
		namespace = client.getNamespace();
	}

	/** Notify proxy listeners that a proxy has been added */
	protected void notifyProxyAdded(T proxy) {
		for(ProxyListener<T> l: listeners)
			l.proxyAdded(proxy);
	}

	/** Notify proxy listeners that enumeration has completed */
	protected void notifyEnumerationComplete() {
		for(ProxyListener<T> l: listeners)
			l.enumerationComplete();
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

	/** Create a proxy in the type cache */
	T createProxy(String name) {
		T o = (T)Proxy.newProxyInstance(LOADER, ifaces, invoker);
		HashMap<String,Attribute> amap = invoker.createAttributes(name);
		// NOTE: after this point, the amap is considered immutable.
		//       If only there were a way to enforce this...
		synchronized(children) {
			children.put(name, o);
			attributes.put(hashCode(o), amap);
			phantom = o;
		}
		return o;
	}

	/** Get (or create) a proxy from the type cache */
	T getProxy(String name) {
		synchronized(children) {
			if(children.containsKey(name))
				return children.get(name);
			else
				return createProxy(name);
		}
	}

	/** Add a proxy to the type cache */
	T add(String name) {
		T o = getProxy(name);
		synchronized(children) {
			notifyProxyAdded(o);
		}
		phantom = null;
		return o;
	}

	/** Enumeration of proxy type is complete */
	public void enumerationComplete() {
		synchronized(children) {
			notifyEnumerationComplete();
			enumerated = true;
		}
	}

	/** Remove a proxy from the type cache */
	T remove(String name) throws NamespaceError {
		synchronized(children) {
			if(!children.containsKey(name))
				throw NamespaceError.nameUnknown(name);
			T proxy = children.remove(name);
			notifyProxyRemoved(proxy);
			attributes.remove(hashCode(proxy));
			return proxy;
		}
	}

	/** Lookup a proxy from the given name */
	public T lookupObject(String n) {
		synchronized(children) {
			return children.get(n);
		}
	}

	/** Get the size of the cache */
	public int size() {
		synchronized(children) {
			return children.size();
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
		if(amap == null) {
			// This happens if a proxy has been removed, but
			// references still exist in other data structures.
			return new Attribute(Object.class);
		}
		Attribute attr = amap.get(a);
		if(attr == null)
			throw NamespaceError.nameUnknown(a);
		else
			return attr;
	}

	/** Get the value of an attribute from the named proxy */
	Object getAttribute(String n, String a) throws NamespaceError {
		T obj = lookupObject(n);
		if(obj == null)
			throw NamespaceError.nameUnknown(n);
		else
			return getAttribute(obj, a);
	}

	/** Get the value of an attribute from the given proxy */
	Object getAttribute(T o, String a) throws NamespaceError {
		Attribute attr = lookupAttribute(o, a);
		return attr.getValue();
	}

	/** Set the value of an attribute on the given proxy.
	 * @param o Proxy object
	 * @param a Attribute name
	 * @param args New attribute value
	 * @param check Flag to check cache before sending message to server */
	void setAttribute(T o, String a, Object[] args, boolean check)
		throws SonarException
	{
		Attribute attr = lookupAttribute(o, a);
		if(check && attr.valueEquals(args))
			return;
		String[] values = namespace.marshall(attr.type, args);
		client.setAttribute(new Name(o, a), values);
	}

	/** Update an attribute value into the given proxy */
	void updateAttribute(T o, String a, String[] v)
		throws SonarException
	{
		Attribute attr = lookupAttribute(o, a);
		attr.setValue(namespace.unmarshall(attr.type, v));
		synchronized(children) {
			if(o != phantom)
				notifyProxyChanged(o, a);
		}
	}

	/** Remove the specified object */
	void removeObject(T o) {
		client.removeObject(new Name(o));
	}

	/** Create the specified object name */
	public void createObject(String oname) {
		client.createObject(new Name(tname, oname));
	}

	/** Create an object with the specified attributes */
	public void createObject(String oname, Map<String, Object> amap) {
		for(Map.Entry<String, Object> entry: amap.entrySet()) {
			Object v = entry.getValue();
			String[] values = namespace.marshall(
				v.getClass(), new Object[] { v });
			Name name = new Name(tname, oname, entry.getKey());
			client.setAttribute(name, values);
		}
		// FIXME: there is a race between the setAttribute calls and
		// the createObject call. Another thread could get in between
		// and mess up the "phantom" object creation.
		client.createObject(new Name(tname, oname));
	}

	/** Add a ProxyListener */
	public void addProxyListener(ProxyListener<T> l) {
		synchronized(children) {
			listeners.add(l);
			for(T proxy: children.values())
				l.proxyAdded(proxy);
			if(enumerated)
				l.enumerationComplete();
		}
	}

	/** Remove a ProxyListener */
	public void removeProxyListener(ProxyListener<T> l) {
		synchronized(children) {
			listeners.remove(l);
		}
	}

	/** Ignore updates to the specified attribute for all objects */
	public void ignoreAttribute(String a) {
		// To ignore an attribute for all objects in the cache,
		// the name must equal getAttributeName() for any object.
		client.ignoreName(new Name(tname, "", a));
	}

	/** Watch for all attributes of the specified object */
	public void watchObject(T proxy) {
		client.enumerateName(new Name(tname, proxy.getName()));
	}

	/** Ignore attributes of the specified object.  This just removes an
	 * object watch -- it does not prevent the type watch from causing
	 * the object to be watched.  */
	public void ignoreObject(T proxy) {
		client.ignoreName(new Name(tname, proxy.getName()));
	}

	/** Find an object using the supplied checker callback */
	public T findObject(Checker<T> c) {
		synchronized(children) {
			for(T proxy: children.values()) {
				if(c.check(proxy))
					return proxy;
			}
		}
		return null;
	}
}
