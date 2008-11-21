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
import us.mn.state.dot.sonar.Checker;
import us.mn.state.dot.sonar.Name;
import us.mn.state.dot.sonar.Namespace;
import us.mn.state.dot.sonar.NamespaceError;
import us.mn.state.dot.sonar.ProtocolError;
import us.mn.state.dot.sonar.SonarException;
import us.mn.state.dot.sonar.SonarObject;

/**
 * The client namespace is a cache which contains SonarObject proxies.
 *
 * @author Douglas Lau
 */
public class ClientNamespace extends Namespace {

	/** Test if a SONAR path is absolute (versus relative) */
	static protected boolean isAbsolute(String path) {
		return path.contains(Name.SEP);
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
	protected TypeCache getTypeCache(Name name) throws NamespaceError {
		String tname = name.getTypePart();
		if(types.containsKey(tname)) {
			cur_type = types.get(tname);
			return cur_type;
		} else
			throw NamespaceError.NAME_INVALID;
	}

	/** Put a new object in the cache */
	void putObject(String n) throws NamespaceError {
		if(isAbsolute(n)) {
			Name name = new Name(n);
			if(!name.isObject())
				throw NamespaceError.NAME_INVALID;
			cur_obj = getTypeCache(name).add(name.getObjectPart());
		} else
			cur_obj = getTypeCache().add(n);
	}

	/** Remove an object from the cache */
	void removeObject(String n) throws NamespaceError {
		if(isAbsolute(n)) {
			Name name = new Name(n);
			if(!name.isObject())
				throw NamespaceError.NAME_INVALID;
			getTypeCache(name).remove(name.getObjectPart());
		} else
			getTypeCache().remove(n);
	}

	/** Unmarshall an object attribute */
	void unmarshallAttribute(String n, String[] v) throws SonarException{
		TypeCache t;
		SonarObject o;
		String a;
		if(isAbsolute(n)) {
			Name name = new Name(n);
			if(!name.isAttribute())
				throw ProtocolError.WRONG_PARAMETER_COUNT;
			t = getTypeCache(name);
			o = t.getProxy(name.getObjectPart());
			cur_obj = o;
			a = name.getAttributePart();
		} else {
			t = getTypeCache();
			o = cur_obj;
			if(o == null)
				throw NamespaceError.NAME_INVALID;
			a = n;
		}
		t.unmarshallAttribute(o, a, v);
	}

	/** Process a TYPE message from the server */
	void setCurrentType(String t) throws NamespaceError {
		if(t.equals("") || types.containsKey(t)) {
			if(t.equals("") && cur_type != null)
				cur_type.enumerationComplete();
			TypeCache tc = types.get(t);
			cur_type = tc;
			cur_obj = null;
		} else
			throw NamespaceError.NAME_INVALID;
	}

	/** Lookup an object in the SONAR namespace.
	 * @param tname Sonar type name
	 * @param oname Sonar object name
	 * @return Object from namespace or null if name does not exist */
	public SonarObject lookupObject(String tname, String oname) {
		TypeCache t = types.get(tname);
		if(t != null)
			return t.lookupObject(oname);
		else
			return null;
	}

	/** Find an object by calling a checker for each object of a type.
	 * @param tname Sonar type name
	 * @param c Checker callback
	 * @return First object which Checker returns true */
	public SonarObject findObject(String tname, Checker c) {
		TypeCache t = types.get(tname);
		if(t != null)
			return t.findObject(c);
		else
			return null;
	}
}
