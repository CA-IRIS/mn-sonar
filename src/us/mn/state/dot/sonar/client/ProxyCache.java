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

import java.util.HashMap;
import us.mn.state.dot.sonar.Names;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * The proxy cache contains all client SonarObject proxies.
 *
 * @author Douglas Lau
 */
class ProxyCache extends Names {

	/** Create a new proxy cache */
	public ProxyCache() {
		_namespace = this;
	}

	/** Map of all types in the cache */
	protected final HashMap<String, TypeCache> types =
		new HashMap<String, TypeCache>();

	/** Add a new SonarObject type */
	public void addType(TypeCache tc) {
		types.put(tc.tname, tc);
	}

	/** Current type */
	protected TypeCache cur_type = null;

	/** Current object */
	protected SonarObject cur_obj = null;

	/** Get the TypeCache for the specified name */
	protected TypeCache getTypeCache() throws NamespaceError {
		if(cur_type != null)
			return cur_type;
		else
			throw NamespaceError.NAME_INVALID;
	}

	/** Get the TypeCache for the specified name */
	protected TypeCache getTypeCache(String[] names) throws NamespaceError {
		if(types.containsKey(names[0])) {
			cur_type = types.get(names[0]);
			return cur_type;
		} else
			throw NamespaceError.NAME_INVALID;
	}

	/** Put a new object in the cache */
	public void putObject(String name) throws NamespaceError {
		if(isAbsolute(name)) {
			String[] names = parse(name);
			if(names.length != 2)
				throw NamespaceError.NAME_INVALID;
			cur_obj = getTypeCache(names).add(names[1]);
		} else
			cur_obj = getTypeCache().add(name);
	}

	/** Remove an object from the cache */
	public void removeObject(String name) throws NamespaceError {
		if(isAbsolute(name)) {
			String[] names = parse(name);
			if(names.length != 2)
				throw NamespaceError.NAME_INVALID;
			getTypeCache(names).remove(names[1]);
		} else
			getTypeCache().remove(name);
	}

	/** Unmarshall an object attribute */
	public void unmarshallAttribute(String name, String[] v)
		throws SonarException
	{
		TypeCache t;
		SonarObject o;
		String a;
		if(isAbsolute(name)) {
			String[] names = parse(name);
			if(names.length != 3)
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			t = getTypeCache(names);
			o = t.getProxy(names[1]);
			cur_obj = o;
			a = names[2];
		} else {
			t = getTypeCache();
			o = cur_obj;
			if(o == null)
				throw NamespaceError.NAME_INVALID;
			a = name;
		}
		t.unmarshallAttribute(o, a, v);
	}

	/** Process a TYPE message from the server */
	public void setCurrentType(String t) throws NamespaceError {
		if(t.equals("") || types.containsKey(t)) {
			if(t.equals("") && cur_type != null)
				cur_type.enumerationComplete();
			TypeCache tc = types.get(t);
			cur_type = tc;
			cur_obj = null;
		} else
			throw NamespaceError.NAME_INVALID;
	}

	/** Lookup an object in the cache */
	protected SonarObject lookupObject(String tname, String oname)
		throws NamespaceError
	{
		TypeCache t = types.get(tname);
		if(t == null)
			throw NamespaceError.NAME_INVALID;
		return t.lookupObject(oname);
	}
}
