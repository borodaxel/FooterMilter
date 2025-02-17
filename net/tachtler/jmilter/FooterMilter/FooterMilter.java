/**
 * Copyright (c) 2022 Klaus Tachtler. All Rights Reserved.
 * Klaus Tachtler. <klaus@tachtler.net>
 * http://www.tachtler.net
 */
package net.tachtler.jmilter.FooterMilter;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.nightcode.milter.net.ServerFactory;

import org.apache.commons.cli.ParseException;
import org.nightcode.common.service.ServiceManager;
import org.nightcode.milter.MilterHandler;
import org.nightcode.milter.net.MilterGatewayManager;
import org.nightcode.milter.Actions;
import org.nightcode.milter.ProtocolSteps;

/*******************************************************************************
 * JMilter Server for connections from an MTA.
 * 
 * JMilter is an Open Source implementation of the Sendmail milter protocol, for
 * implementing milters in Java that can interface with the Sendmail or Postfix
 * MTA.
 * 
 * Java implementation of the Sendmail Milter protocol based on the project of
 * org.nightcode.jmilter from dmitry@nightcode.org.
 * 
 * @author Klaus Tachtler. <klaus@tachtler.net>
 * 
 *         Homepage : http://www.tachtler.net
 * 
 *         Licensed under the Apache License, Version 2.0 (the "License"); you
 *         may not use this file except in compliance with the License. You may
 *         obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *         implied. See the License for the specific language governing
 *         permissions and limitations under the License..
 * 
 *         Copyright (c) 2022 by Klaus Tachtler.
 ******************************************************************************/
public class FooterMilter {
	
	/**
	 * Constructor.
	 */
	public FooterMilter() {
		super();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws FooterMilterException {

		/*
		 * Read the arguments from command line into the dwuaFileBean.
		 */
		FooterMilterInitBean argsBean = new FooterMilterInitBean(null, 0, null, null);

		try {
			argsBean = FooterMilterCLIArgsParser.readArgs(argsBean, args);
		} catch (ParseException eParseException) {
			throw new FooterMilterException(true, eParseException);
		}

		/*
		 * Start JMilter only, if all required arguments are set.
		 */
		if (argsBean.getInetAddress() != null && argsBean.getPort() != 0) {
			
			StringBuffer addressStr = new StringBuffer();
			
			// Build listen address:port variable.
			addressStr.append(argsBean.getInetAddress().getHostAddress());
			addressStr.append(":");
			addressStr.append(argsBean.getPort());

			String envAddress = System.getProperty("jmilter.address", addressStr.toString());
			String[] addrParts = envAddress.split(":");
			
			// Generate configuration string.
			InetSocketAddress address = new InetSocketAddress(addrParts[0], Integer.parseInt(addrParts[1]));
			ServerFactory<InetSocketAddress> serverFactory = ServerFactory.tcpIpFactory(address);

			// Indicates what changes will be made with the messages.
			Actions milterActions = Actions.builder().replaceBody().addHeader().build();

			// Indicates which steps will be skipped.
			ProtocolSteps milterProtocolSteps = ProtocolSteps.builder().build();

			// Create the JMilter handler.
			MilterHandler milterHandler = new FooterMilterHandler(milterActions, milterProtocolSteps, argsBean);

			MilterGatewayManager<InetSocketAddress> gatewayManager;
			gatewayManager = new MilterGatewayManager<>(serverFactory, milterHandler);
			gatewayManager.bind();
		}

	}

}
