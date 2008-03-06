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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Helper functions to create an SSL security context
 *
 * @author Douglas Lau
 */
public class Security {

	/** Load a KeyStore from an InputStream in the jks format */
	static protected KeyStore _loadKeyStore(InputStream is)
		throws IOException, GeneralSecurityException
	{
		try {
			KeyStore ks = KeyStore.getInstance("jks");
			ks.load(is, null);
			return ks;
		}
		finally {
			is.close();
		}
	}

	/** Load a KeyStore from a file in the jks format */
	static protected KeyStore _loadKeyStore(String keystore)
		throws IOException, GeneralSecurityException
	{
		return _loadKeyStore(new FileInputStream(keystore));
	}

	/** Load a KeyStore from a resource in the jks format */
	static protected KeyStore _loadKeyStoreResource(String keystore)
		throws IOException, GeneralSecurityException
	{
		InputStream is = Security.class.getResourceAsStream(keystore);
		if(is != null)
			return _loadKeyStore(is);
		else
			throw new IOException();
	}

	/** Load a KeyStore in the jks format */
	static protected KeyStore loadKeyStore(String keystore)
		throws GeneralSecurityException, ConfigurationError
	{
		try {
			return _loadKeyStore(keystore);
		}
		catch(IOException e) {
			try {
				return _loadKeyStoreResource(keystore);
			}
			catch(IOException ee) {
				throw new ConfigurationError("Cannot read " +
					keystore);
			}
		}
	}

	/** Create and configure an SSL context */
	static protected SSLContext _createContext(String keystore, String pwd)
		throws GeneralSecurityException, ConfigurationError
	{
		SSLContext context = SSLContext.getInstance("TLS");
		KeyStore ks = loadKeyStore(keystore);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(
			"SunX509");
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(
			"SunX509");
		kmf.init(ks, pwd.toCharArray());
		tmf.init(ks);
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(),
			null);
		return context;
	}

	/** Create and configure an SSL context */
	static protected SSLContext _createContext(Properties props)
		throws GeneralSecurityException, ConfigurationError
	{
		String keystore = props.getProperty("keystore.file");
		if(keystore == null)
			throw new ConfigurationError("Keystore not specified");
		String pwd = props.getProperty("keystore.password");
		if(pwd == null)
			throw new ConfigurationError("Password not specified");
		return _createContext(keystore, pwd);
	}

	/** Create and configure an SSL context */
	static public SSLContext createContext(Properties props)
		throws ConfigurationError
	{
		try {
			return _createContext(props);
		}
		catch(GeneralSecurityException e) {
			throw new ConfigurationError("Keystore error: " +
				e.getMessage());
		}
	}
}
