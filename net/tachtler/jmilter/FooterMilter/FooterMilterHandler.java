/**
 * Copyright (c) 2022 Klaus Tachtler. All Rights Reserved.
 * Klaus Tachtler. <klaus@tachtler.net>
 * http://www.tachtler.net
 */
package net.tachtler.jmilter.FooterMilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.nightcode.milter.AbstractMilterHandler;
import org.nightcode.milter.MessageModificationService;
import org.nightcode.milter.MilterContext;
import org.nightcode.milter.MilterException;
import org.nightcode.milter.Code;
import org.nightcode.milter.CommandCode;
import org.nightcode.milter.codec.MilterPacket;
import org.nightcode.milter.Actions;
import org.nightcode.milter.ProtocolSteps;

/*******************************************************************************
 * JMilter Handler for handling connections from an MTA to add a footer.
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
public class FooterMilterHandler extends AbstractMilterHandler {

	private static Logger log = LogManager.getLogger();

	private static int timeout = 3;
	private static int ttl = 64;

	private String mailFrom = null;
	private Boolean footerAvailableResult = null;

	private ByteArrayOutputStream parseContent = new ByteArrayOutputStream();

	private MessageBuilder messageBuilder = new DefaultMessageBuilder();

	private Message message = null;
	private Body body = null;
	private ByteArrayOutputStream bodyContent = new ByteArrayOutputStream();
	private ContentTypeField contentTypeField = null;

	private FooterMilterInitBean argsBean = new FooterMilterInitBean(null, 0, null, null);

	private StringBuffer addHeaderContent = new StringBuffer();

	/**
	 * @param milterActions
	 * @param milterProtocolSteps
	 */
	public FooterMilterHandler(Actions milterActions, ProtocolSteps milterProtocolSteps) {
		super(milterActions, milterProtocolSteps);
	}

	/**
	 * @param milterActions
	 * @param milterProtocolSteps
	 * @param messageModificationService
	 */
	public FooterMilterHandler(Actions milterActions, ProtocolSteps milterProtocolSteps,
			MessageModificationService messageModificationService) {
		super(milterActions, milterProtocolSteps, messageModificationService);
	}

	/**
	 * Extended to delivered argsBean (FooterMilterInitBean).
	 * 
	 * @param milterActions
	 * @param milterProtocolSteps
	 * @param argsBean
	 */
	public FooterMilterHandler(Actions milterActions, ProtocolSteps milterProtocolSteps,
			FooterMilterInitBean argsBean) {
		super(milterActions, milterProtocolSteps);
		this.argsBean = argsBean;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.nightcode.milter.AbstractMilterHandler#connect(org.nightcode.milter.
	 * MilterContext, java.lang.String, java.net.InetAddress)
	 */
	@Override
	public void connect(MilterContext context, String hostname, int family, int port, @Nullable SocketAddress address) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug(
				"JMilter - ENTRY: connect                : MilterContext context, String hostname, @Nullable InetAddress address");
		log.debug("----------------------------------------: ");

		log.debug("*hostname                               : " + hostname);
		log.debug("*address                                : " + address);
		log.debug("*family                                 : " + family);
		log.debug("*port                                   : " + port);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: connect                : MilterContext context, String hostname, @Nullable InetAddress address");
		log.debug("----------------------------------------: ");

		/*
		 * Change the SMFIS action possible values are:
		 *
		 * SMFIS_CONTINUE, SMFIS_REJECT, SMFIS_DISCARD, SMFIS_ACCEPT, SMFIS_TEMPFAIL,
		 * SMFIS_SKIP
		 *
		 * context.sendPacket(MilterPacketUtil.SMFIS_CONTINUE);
		 */

		super.connect(context, hostname, family, port, address);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#helo(org.nightcode.milter.
	 * MilterContext, java.lang.String)
	 */
	@Override
	public void helo(MilterContext context, String helohost) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: helo                   : MilterContext context, String helohost");
		log.debug("----------------------------------------: ");

		log.debug("*helohost                               : " + helohost);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: helo                   : MilterContext context, String helohost");
		log.debug("----------------------------------------: ");

		super.helo(context, helohost);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.nightcode.milter.AbstractMilterHandler#envfrom(org.nightcode.milter.
	 * MilterContext, java.util.List)
	 */
	@Override
	public void envfrom(MilterContext context, List<String> from) throws MilterException {

		/*
		 * Detect if the from email address is available inside the mapText or mapHtml.
		 * The variable result will be true or false and the variable mailFrom will be
		 * the mail_from address, "@domain.tld" or null.
		 */
		isFooterAvailable(context);

		log.debug("*isFooterAvailable (envfrom)            : " + footerAvailableResult);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: envfrom                : MilterContext context, List<String> from");
		log.debug("----------------------------------------: ");

		for (int i = 0; i <= from.size() - 1; i++) {
			log.debug("*from.get(i)                            : " + "[" + i + "] " + from.get(i));
		}

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: envfrom                : MilterContext context, List<String> from");
		log.debug("----------------------------------------: ");

		super.envfrom(context, from);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#envrcpt(org.nightcode.milter.
	 * MilterContext, java.util.List)
	 */
	@Override
	public void envrcpt(MilterContext context, List<String> recipients) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: envrcpt                : MilterContext context, List<String> recipients");
		log.debug("----------------------------------------: ");

		for (int i = 0; i <= recipients.size() - 1; i++) {
			log.debug("*recipients.get(i)                      : " + "[" + i + "] " + recipients.get(i));
		}

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: envrcpt                : MilterContext context, List<String> recipients");
		log.debug("----------------------------------------: ");

		super.envrcpt(context, recipients);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.nightcode.milter.AbstractMilterHandler#data(org.nightcode.milter.
	 * MilterContext, byte[])
	 */
	@Override
	public void data(MilterContext context, byte[] payload) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: data                   : MilterContext context, byte[] payload");
		log.debug("----------------------------------------: ");

		byte[] dataPayload = payload;
		String dataPayloadString = null;
		StringBuilder stringBuilder = new StringBuilder();

		if (dataPayload != null && dataPayload.length > 0) {
			for (byte b : dataPayload) {
				stringBuilder.append(String.format("%02x:", b));
			}
			dataPayloadString = stringBuilder.deleteCharAt(stringBuilder.length() - 1).toString();
		}

		log.debug("*payload                                : " + dataPayloadString);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: data                   : MilterContext context, byte[] payload");
		log.debug("----------------------------------------: ");

		super.data(context, payload);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#header(org.nightcode.milter.
	 * MilterContext, java.lang.String, java.lang.String)
	 */
	@Override
	public void header(MilterContext context, String headerName, String headerValue) throws MilterException {

		log.debug("*isFooterAvailable (header)             : " + footerAvailableResult);

		/*
		 * Check if the from email address is available inside the mapText or mapHtml.
		 * If true, continue adding a foot, else do nothing.
		 */
		if (footerAvailableResult) {

			/*
			 * Concatenate every headerName and headerValue to a formated single line.
			 */
			try {
				parseContent.write(headerName.getBytes(StandardCharsets.US_ASCII));
				parseContent.write(": ".getBytes(StandardCharsets.US_ASCII));
				parseContent.write(headerValue.getBytes(StandardCharsets.US_ASCII));
				parseContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
			} catch (IOException eIOException) {
				FooterMilterException.InitException(false);

				log.error("Exception: " + "FooterMilterException");
				log.error("Caused by: " + ExceptionUtils.getStackTrace(eIOException));
			}

//			log.debug("*parseContent.toString()                : " + parseContent.toString());
		}

		log.debug("----------------------------------------: ");
		log.debug(
				"JMilter - ENTRY: header                 : MilterContext context, String headerName, String headerValue");
		log.debug("----------------------------------------: ");

		log.debug("*headerName: headerValue                : " + headerName + ": " + headerValue);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);

		log.debug("----------------------------------------: ");
		log.debug(
				"JMilter - LEAVE: header                 : MilterContext context, String headerName, String headerValue");
		log.debug("----------------------------------------: ");

		super.header(context, headerName, headerValue);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.nightcode.milter.AbstractMilterHandler#eoh(org.nightcode.milter.
	 * MilterContext)
	 */
	@Override
	public void eoh(MilterContext context) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: eoh                    : MilterContext context");
		log.debug("----------------------------------------: ");

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: eoh                    : MilterContext context");
		log.debug("----------------------------------------: ");

		super.eoh(context);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#body(org.nightcode.milter.
	 * MilterContext, java.lang.String)
	 */
	@Override
	public void body(MilterContext context, byte[] bodyChunk) throws MilterException {

		log.debug("*isFooterAvailable (body)               : " + footerAvailableResult);

		/*
		 * Check if the from email address is available inside the mapText or mapHtml.
		 * If true, continue adding a foot, else do nothing.
		 */
		if (footerAvailableResult) {

			/*
			 * Add the bodyChunk to the formated header lines to parseContent.
			 */
			try {
				parseContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
				parseContent.write(bodyChunk);
			} catch (IOException eIOException) {
				FooterMilterException.InitException(false);

				log.error("Exception: " + "FooterMilterException");
				log.error("Caused by: " + ExceptionUtils.getStackTrace(eIOException));
			}

//			log.debug("*parseContent <- (Start at next line) ->: " + System.lineSeparator() + parseContent.toString());

		}

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: body                   : MilterContext context, String bodyChunk");
		log.debug("----------------------------------------: ");

		log.debug("*bodyChunk <-- (Start at next line) --> : " + System.lineSeparator() + bodyChunk);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);
		logContext(context, CommandCode.SMFIC_BODY);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: body                   : MilterContext context, String bodyChunk");
		log.debug("----------------------------------------: ");

		super.body(context, bodyChunk);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#eom(org.nightcode.milter.
	 * MilterContext, java.lang.String)
	 */
	@Override
	public void eom(MilterContext context, @Nullable byte[] bodyChunk) throws MilterException {

		log.debug("*isFooterAvailable (eom)                : " + footerAvailableResult);

		/*
		 * Check if the from email address is available inside the mapText or mapHtml.
		 * If true, continue adding a foot, else do nothing.
		 */
		if (footerAvailableResult) {

			/*
			 * Generate the modified Body with the necessary footer added.
			 */
			try {
				generateModifiedBody();
			} catch (FooterMilterException eFooterMilterException) {
				FooterMilterException.InitException(false);

				log.error("Exception: " + "FooterMilterException");
				log.error("Caused by: " + ExceptionUtils.getStackTrace(eFooterMilterException));
			}

			log.debug("*bodyContent.size()                     : " + bodyContent.size());

			/*
			 * Check footerAvailableResult again, because if inside the message a signature
			 * was detected, the footerAvailableResult will be false, to prevent changing
			 * the content, because this will break the signature!
			 */
			if (footerAvailableResult) {

				/*
				 * Replace the original body with the modified bodyContent byte array.
				 */
				messageModificationService.replaceBody(context, bodyContent.toByteArray());

				/*
				 * Add the header tag for mail body modifying (using footer) - CR/LF
				 * {daemon_name}.
				 */
				addHeaderContent.append("Mail body modified (using footer)");
				addHeaderContent.append(System.lineSeparator());
				addHeaderContent.append("by ");
				addHeaderContent
						.append(context.getMacros(CommandCode.SMFIC_CONNECT.code()).get("{daemon_name}").toString());
				addHeaderContent.append(System.lineSeparator());
				addHeaderContent.append("for <");
				addHeaderContent.append(mailFrom);
				addHeaderContent.append(">");

				messageModificationService.addHeader(context, "X-FooterMilter-Modified", addHeaderContent.toString());

				log.debug("messageModificationService.addHeader    : " + "X-FooterMilter-Modified: "
						+ addHeaderContent.toString());

			}

		}

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: eom                    : MilterContext context, @Nullable String bodyChunk");
		log.debug("----------------------------------------: ");

		log.debug("*bodyChunk <-- (Start at next line) --> : " + System.lineSeparator() + bodyChunk);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);
		logContext(context, CommandCode.SMFIC_BODY);
		logContext(context, CommandCode.SMFIC_EOB);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: eom                    : MilterContext context, @Nullable String bodyChunk");
		log.debug("----------------------------------------: ");

		super.eom(context, bodyChunk);

		/*
		 * Initialize global variables after every email delivery.
		 */
		initGlobalVariables();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#abort(org.nightcode.milter.
	 * MilterContext, org.nightcode.milter.codec.MilterPacket)
	 */
	@Override
	public void abort(MilterContext context, MilterPacket packet) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: abort                  : MilterContext context, MilterPacket packet");
		log.debug("----------------------------------------: ");

		log.debug("*packet                                 : " + packet);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);
		logContext(context, CommandCode.SMFIC_BODY);
		logContext(context, CommandCode.SMFIC_EOB);
		logContext(context, CommandCode.SMFIC_ABORT);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: abort                  : MilterContext context, MilterPacket packet");
		log.debug("----------------------------------------: ");

		/*
		 * !IMPORTANT
		 * 
		 * Disabled, because it will break the delivery after the email will come BACK
		 * from smtp_proxy_filter or content_filter.
		 * 
		 * Many thanks to dmitry@nightcode.org for the support.
		 */
		// super.abort(context, packet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.nightcode.milter.AbstractMilterHandler#optneg(org.nightcode.milter.
	 * MilterContext, int, org.nightcode.milter.util.Actions,
	 * org.nightcode.milter.util.ProtocolSteps)
	 */
	@Override
	public void optneg(MilterContext context, int mtaProtocolVersion, Actions mtaActions,
			ProtocolSteps mtaProtocolSteps) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug(
				"JMilter - ENTRY: negotiate              : MilterContext context, int mtaProtocolVersion, Actions mtaActions, ProtocolSteps mtaProtocolSteps");
		log.debug("----------------------------------------: ");

		log.debug("*mtaProtocolVersion                     : " + mtaProtocolVersion);
		log.debug("*mtaActions                             : " + mtaActions);
		log.debug("*mtaProtocolSteps                       : " + mtaProtocolSteps);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);
		logContext(context, CommandCode.SMFIC_BODY);
		logContext(context, CommandCode.SMFIC_EOB);
		logContext(context, CommandCode.SMFIC_ABORT);
		logContext(context, CommandCode.SMFIC_OPTNEG);

		log.debug("----------------------------------------: ");
		log.debug(
				"JMilter - LEAVE: negotiate              : MilterContext context, int mtaProtocolVersion, Actions mtaActions, ProtocolSteps mtaProtocolSteps");
		log.debug("----------------------------------------: ");

		super.optneg(context, mtaProtocolVersion, mtaActions, mtaProtocolSteps);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.nightcode.milter.AbstractMilterHandler#unknown(org.nightcode.milter.
	 * MilterContext, byte[])
	 */
	@Override
	public void unknown(MilterContext context, byte[] payload) throws MilterException {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: unknown                : MilterContext context, byte[] payload");
		log.debug("----------------------------------------: ");

		byte[] dataPayload = payload;
		String dataPayloadString = null;
		StringBuilder stringBuilder = new StringBuilder();

		if (dataPayload != null && dataPayload.length > 0) {
			for (byte b : dataPayload) {
				stringBuilder.append(String.format("%02x:", b));
			}
			dataPayloadString = stringBuilder.deleteCharAt(stringBuilder.length() - 1).toString();
		}

		log.debug("*payload                                : " + dataPayloadString);

		logContext(context);
		logContext(context, CommandCode.SMFIC_CONNECT);
		logContext(context, CommandCode.SMFIC_HELO);
		logContext(context, CommandCode.SMFIC_MAIL);
		logContext(context, CommandCode.SMFIC_RCPT);
		logContext(context, CommandCode.SMFIC_DATA);
		logContext(context, CommandCode.SMFIC_HEADER);
		logContext(context, CommandCode.SMFIC_EOH);
		logContext(context, CommandCode.SMFIC_BODY);
		logContext(context, CommandCode.SMFIC_EOB);
		logContext(context, CommandCode.SMFIC_ABORT);
		logContext(context, CommandCode.SMFIC_OPTNEG);
		logContext(context, CommandCode.SMFIC_UNKNOWN);

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: unknown                : MilterContext context, byte[] payload");
		log.debug("----------------------------------------: ");

		super.unknown(context, payload);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.nightcode.milter.MilterHandler#close(org.nightcode.milter.MilterContext)
	 */
	@Override
	public void quit(MilterContext arg0) {

		log.debug("----------------------------------------: ");
		log.debug("JMilter - ENTRY: close                  : MilterContext arg0");
		log.debug("----------------------------------------: ");

		log.debug("----------------------------------------: ");
		log.debug("JMilter - LEAVE: close                  : MilterContext arg0");
		log.debug("----------------------------------------: ");
	}

	/**
	 * There are three possibilities to determine, if a given mail_addr from envfrom
	 * MILTER step was inside the footermilter.ini configuration file and with that
	 * a footer could be set.
	 * 
	 * First possibility, the given complete (localpart@domain.tld) mail_addr from
	 * envfrom MILTER step was direct inside the specified Map (mapText or mapHtml).
	 * If so, mailFrom was already set.
	 * 
	 * Second possibility, ONLY the domain part (@domain.tld) including the @-Char
	 * from the mail_addr from envfrom MILTER step was found in the specified map
	 * (mapText or mapHtml). If so, mailFrom was set to @domain.tld from mail_addr.
	 * 
	 * Third possibility, a iteration over the whole specified map (mapText or
	 * mapHtml) and comparing ONLY the (domain.tld) excluding the @-Char from the
	 * mail_addr from envfrom MILTER step was found in the specified map (mapText or
	 * mapHtml). If so, mailFrom was set to @domain.tld from mail_addr ignoring any
	 * sub-domain parts of the given mail_addr too.
	 * 
	 * @param context
	 * @return Boolean
	 */
	private void isFooterAvailable(MilterContext context) {

		/*
		 * Initialize the mailFrom with the mail_addr from envfrom MILTER step and set
		 * the footerAvailiableResult with false as "standard" values.
		 */
		mailFrom = context.getMacros(CommandCode.SMFIC_MAIL.code()).get("{mail_addr}").toString();
		footerAvailableResult = false;

		log.debug("*mailFrom                        (init) : " + mailFrom);
		log.debug("*footerAvailableResult           (init) : " + footerAvailableResult);

		if (argsBean.getMapText().containsKey(mailFrom) || argsBean.getMapHtml().containsKey(mailFrom)) {
			footerAvailableResult = true;
		} else {

			/*
			 * To avoid java.lang.IndexOutOfBoundsException, check if the @ char exists
			 * inside the mail_addr.
			 */
			if (mailFrom.indexOf("@") >= 0) {
				if (argsBean.getMapText().containsKey(mailFrom.substring(mailFrom.indexOf("@")))
						|| argsBean.getMapHtml().containsKey(mailFrom.substring(mailFrom.indexOf("@")))) {
					mailFrom = mailFrom.substring(mailFrom.indexOf("@"));
					footerAvailableResult = true;
				} else {

					/*
					 * To avoid java.lang.IndexOutOfBoundsException, check if the @ char exists
					 * inside the mail_addr.
					 */
					if (mailFrom.indexOf("@") >= 0) {

						/*
						 * Iterate over the mapText, and if a from address entry will be a part of the
						 * mail_addr, then take that match as mailFrom.
						 */
						Iterator<Entry<String, String>> iteratorMapText = argsBean.getMapText().entrySet().iterator();
						while (iteratorMapText.hasNext()) {
							Map.Entry<String, String> pair = (Map.Entry<String, String>) iteratorMapText.next();
							if (mailFrom.substring(mailFrom.indexOf("@") + 1)
									.contains(pair.getKey().substring(pair.getKey().indexOf("@") + 1))) {
								mailFrom = pair.getKey();
								footerAvailableResult = true;
								break;
							}
						}

						/*
						 * Iterate over the mapHtml, and if a from address entry will be a part of the
						 * mail_addr, then take that match as mailFrom.
						 */
						Iterator<Entry<String, String>> iteratorMapHtml = argsBean.getMapHtml().entrySet().iterator();
						while (iteratorMapHtml.hasNext()) {
							Map.Entry<String, String> pair = (Map.Entry<String, String>) iteratorMapHtml.next();
							if (mailFrom.substring(mailFrom.indexOf("@") + 1)
									.contains(pair.getKey().substring(pair.getKey().indexOf("@") + 1))) {
								mailFrom = pair.getKey();
								footerAvailableResult = true;
								break;
							}
						}

					}
				}

			}
		}

		log.debug("*mailFrom                        (done) : " + mailFrom);
		log.debug("*footerAvailableResult           (done) : " + footerAvailableResult);

	}

	/**
	 * Generate the modified Body from multipart or single message with the
	 * different part types like text/plain, text/html and binary parts.
	 */
	private void generateModifiedBody() throws FooterMilterException {

		/*
		 * Generate the message parsing the parseContent with the messageBuilder.
		 */
		try {
			message = messageBuilder
					.parseMessage(new ByteArrayInputStream(parseContent.toByteArray()));
		} catch (MimeException eMimeException) {
			throw new FooterMilterException(false, eMimeException);
		} catch (IOException eIOException) {
			throw new FooterMilterException(false, eIOException);
		}

		/*
		 * Start creating the modified body while iterating over all parts of the
		 * message.
		 */
		try {
			createModifiedBody(message);
		} catch (IOException eIOException) {
			throw new FooterMilterException(false, eIOException);
		}

	}

	/**
	 * Creates the body parts from a given MIME entity (either a Message or a Part)
	 * and write them to the bodyContent output stream. To iterate over the whole message,
	 * if necessary, the code is calling itself.
	 */
	private void createModifiedBody(Entity entity) throws FooterMilterException, IOException {

		body = entity.getBody();

		if (body instanceof Multipart) {
			createModifiedMultipartBody((Multipart) body);
		} else if (body instanceof MessageImpl) {
			createModifiedBody((MessageImpl) body);
		} else if (body instanceof TextBody) {
			if (entity.getMimeType().equalsIgnoreCase("text/plain")) {
				FooterMilterUtilities.getTextContentWithFooter(entity, bodyContent, argsBean.getMapText().get(mailFrom));
			} else if (entity.getMimeType().equalsIgnoreCase("text/html")) {
				FooterMilterUtilities.getHtmlContentWithFooter(entity, bodyContent, argsBean.getMapHtml().get(mailFrom));
			}
		} else if (body instanceof BinaryBody) {
			FooterMilterUtilities.writeBinaryContent(entity, bodyContent);
		}

	}

	/**
	 * Create a body part form a given multipart body. Add the "preamble", all body
	 * parts the with their "boundary" and the "epilogue" to the bodyContent
	 * String.
	 * 
	 * @param multipart
	 */
	private void createModifiedMultipartBody(Multipart multipart) throws FooterMilterException, IOException {

		/*
		 * If available, add "preamble" before the multipart messages.
		 */
		if (null != multipart.getPreamble() && !(multipart.getParent().getBody() instanceof MessageImpl)) {
			bodyContent.write(multipart.getPreamble().getBytes(StandardCharsets.US_ASCII));
			bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
		}

		/*
		 * Iterate over the parts of the multipart message.
		 */
		for (Entity part : multipart.getBodyParts()) {

			/*
			 * Determine the "boundary" from the multipart parent using the
			 * contentTypeField.
			 */
			contentTypeField = (ContentTypeField) multipart.getParent().getHeader().getField(FieldName.CONTENT_TYPE);

			/*
			 * If a signed or encrypted part was found, STOP changing the content by setting
			 * the footerAvailableResult to false, because this will break any signatures!
			 */
			if (contentTypeField.getMimeType().toLowerCase().contains("signed")
					|| contentTypeField.getMimeType().toLowerCase().contains("encrypted")) {
				footerAvailableResult = false;

				log.debug("*contentTypeField.getMimeType()         : " + contentTypeField.getMimeType());
			}

			/*
			 * In front of every boundary the '--' must be specified.
			 * https://tools.ietf.org/html/rfc2046
			 */
			bodyContent.write("--".getBytes(StandardCharsets.US_ASCII));
			bodyContent.write(contentTypeField.getBoundary().getBytes(StandardCharsets.US_ASCII));
			bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

			/*
			 * Add unmodified body part header.
			 */
			bodyContent.write(part.getHeader().toString().getBytes(StandardCharsets.US_ASCII));
			bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

			/*
			 * Add the recommended part from multipart to the bodyContent.
			 */
			createModifiedBody(part);
		}

		/*
		 * In front of every boundary the '--' must be specified.
		 * https://tools.ietf.org/html/rfc2046
		 */
		bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
		bodyContent.write("--".getBytes(StandardCharsets.US_ASCII));
		bodyContent.write(contentTypeField.getBoundary().getBytes(StandardCharsets.US_ASCII));

		/*
		 * At the end of the last boundary the '--' must be specified.
		 * https://tools.ietf.org/html/rfc2046
		 */
		bodyContent.write("--".getBytes(StandardCharsets.US_ASCII));
		bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
		bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

		/*
		 * If available, add "epilogue" after the multipart messages.
		 */
		if (null != multipart.getEpilogue() && !(multipart.getParent().getBody() instanceof MessageImpl)) {
			bodyContent.write(multipart.getEpilogue().getBytes(StandardCharsets.US_ASCII));
			bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
		}
	}

	/**
	 * Initialize global defined and used variables.
	 */
	private void initGlobalVariables() {

		mailFrom = null;
		footerAvailableResult = null;

		parseContent.reset();

		message = null;
		body = null;
		contentTypeField = null;
		bodyContent.reset();

		try {
			argsBean.init();
		} catch (FooterMilterException eFooterMilterException) {
			FooterMilterException.InitException(false);

			log.error("Exception: " + "FooterMilterException");
			log.error("Caused by: " + ExceptionUtils.getStackTrace(eFooterMilterException));
		}

		addHeaderContent.delete(0, addHeaderContent.length());
	}

	/**
	 * Log MilterContext (context) default (0) part.
	 * 
	 * @param context
	 */
	private void logContext(MilterContext context) {
		logContext(context, null);
	}

	/**
	 * Log MilterContext (context) SMFIC or default (0) part.
	 * 
	 * @param context
	 * @param smfic
	 */
	private void logContext(MilterContext context, @Nullable Code smfic) {

		if (smfic != null) {
		if (smfic.code() == CommandCode.SMFIC_CONNECT.code()) {
			if (context.getMacros(CommandCode.SMFIC_CONNECT.code()) != null) {
				log.debug("*context.getMacros(SMIFC_CONNECT)       : "
						+ context.getMacros(CommandCode.SMFIC_CONNECT.code()));

				if (context.getMacros(CommandCode.SMFIC_CONNECT.code()).containsKey("v")) {
					log.debug("*context.getMacros(SMIFC_CONNECT)|(\"v\") : "
							+ context.getMacros(CommandCode.SMFIC_CONNECT.code()).get("v").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_CONNECT.code()).containsKey("{daemon_name}")) {
					log.debug("*context.getMacros(SMIFC_CONNECT)|(\"{...: "
							+ context.getMacros(CommandCode.SMFIC_CONNECT.code()).get("{daemon_name}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_CONNECT.code()).containsKey("j")) {
					log.debug("*context.getMacros(SMIFC_CONNECT)|(\"j\") : "
							+ context.getMacros(CommandCode.SMFIC_CONNECT.code()).get("j").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_HELO.code()) {
			if (context.getMacros(CommandCode.SMFIC_HELO.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_HELO)          : " + context.getMacros(CommandCode.SMFIC_HELO.code()));
			}

		} else if (smfic.code() == CommandCode.SMFIC_MAIL.code()) {
			if (context.getMacros(CommandCode.SMFIC_MAIL.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_MAIL)          : " + context.getMacros(CommandCode.SMFIC_MAIL.code()));

				if (context.getMacros(CommandCode.SMFIC_MAIL.code()).containsKey("{mail_host}")) {
					log.debug("*context.getMacros(SMIFC_MAIL)|(\"{mai...: "
							+ context.getMacros(CommandCode.SMFIC_MAIL.code()).get("{mail_host}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_MAIL.code()).containsKey("{mail_mailer}")) {
					log.debug("*context.getMacros(SMIFC_MAIL)|(\"{mai...: "
							+ context.getMacros(CommandCode.SMFIC_MAIL.code()).get("{mail_mailer}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_MAIL.code()).containsKey("{mail_addr}")) {
					log.debug("*context.getMacros(SMIFC_MAIL)|(\"{mai...: "
							+ context.getMacros(CommandCode.SMFIC_MAIL.code()).get("{mail_addr}").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_RCPT.code()) {
			if (context.getMacros(CommandCode.SMFIC_RCPT.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_RCPT)          : " + context.getMacros(CommandCode.SMFIC_RCPT.code()));

				if (context.getMacros(CommandCode.SMFIC_RCPT.code()).containsKey("{rcpt_mailer}")) {
					log.debug("*context.getMacros(SMIFC_RCPT)|(\"{rcp...: "
							+ context.getMacros(CommandCode.SMFIC_RCPT.code()).get("{rcpt_mailer}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_RCPT.code()).containsKey("{rcpt_addr}")) {
					log.debug("*context.getMacros(SMIFC_RCPT)|(\"{rcp...: "
							+ context.getMacros(CommandCode.SMFIC_RCPT.code()).get("{rcpt_addr}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_RCPT.code()).containsKey("{rcpt_host}")) {
					log.debug("*context.getMacros(SMIFC_RCPT)|(\"{rcp...: "
							+ context.getMacros(CommandCode.SMFIC_RCPT.code()).get("{rcpt_host}").toString());
				}
				if (context.getMacros(CommandCode.SMFIC_RCPT.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_RCPT)|(\"i\")    : "
							+ context.getMacros(CommandCode.SMFIC_RCPT.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_DATA.code()) {
			if (context.getMacros(CommandCode.SMFIC_DATA.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_DATA)          : " + context.getMacros(CommandCode.SMFIC_DATA.code()));

				if (context.getMacros(CommandCode.SMFIC_DATA.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_DATA)|(\"i\")    : "
							+ context.getMacros(CommandCode.SMFIC_DATA.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_HEADER.code()) {
			if (context.getMacros(CommandCode.SMFIC_HEADER.code()) != null) {
				log.debug("*context.getMacros(SMFIC_HEADER)        : "
						+ context.getMacros(CommandCode.SMFIC_HEADER.code()));

				if (context.getMacros(CommandCode.SMFIC_HEADER.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_HEADER)|(\"i\")  : "
							+ context.getMacros(CommandCode.SMFIC_HEADER.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_EOH.code()) {
			if (context.getMacros(CommandCode.SMFIC_EOH.code()) != null) {
				log.debug("*context.getMacros(SMFIC_EOH)           : " + context.getMacros(CommandCode.SMFIC_EOH.code()));

				if (context.getMacros(CommandCode.SMFIC_EOH.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_EOH)|(\"i\")     : "
							+ context.getMacros(CommandCode.SMFIC_EOH.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_BODY.code()) {
			if (context.getMacros(CommandCode.SMFIC_BODY.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_BODY)          : " + context.getMacros(CommandCode.SMFIC_BODY.code()));

				if (context.getMacros(CommandCode.SMFIC_BODY.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_BODY)|(\"i\")    : "
							+ context.getMacros(CommandCode.SMFIC_BODY.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_EOB.code()) {
			if (context.getMacros(CommandCode.SMFIC_EOB.code()) != null) {
				log.debug("*context.getMacros(SMIFC_EOB)           : "
						+ context.getMacros(CommandCode.SMFIC_EOB.code()));

				if (context.getMacros(CommandCode.SMFIC_EOB.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_EOB)|(\"i\")     : "
							+ context.getMacros(CommandCode.SMFIC_EOB.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_ABORT.code()) {
			if (context.getMacros(CommandCode.SMFIC_ABORT.code()) != null) {
				log.debug(
						"*context.getMacros(SMIFC_ABORT)         : " + context.getMacros(CommandCode.SMFIC_ABORT.code()));

				if (context.getMacros(CommandCode.SMFIC_ABORT.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMIFC_ABORT)|(\"i\")   : "
							+ context.getMacros(CommandCode.SMFIC_ABORT.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_OPTNEG.code()) {
			if (context.getMacros(CommandCode.SMFIC_OPTNEG.code()) != null) {
				log.debug("*context.getMacros(SMFIC_OPTNEG)        : "
						+ context.getMacros(CommandCode.SMFIC_OPTNEG.code()));

				if (context.getMacros(CommandCode.SMFIC_OPTNEG.code()).containsKey("i")) {
					log.debug("*context.getMacros(SMFIC_OPTNEG)|(\"i\")  : "
							+ context.getMacros(CommandCode.SMFIC_OPTNEG.code()).get("i").toString());
				}
			}

		} else if (smfic.code() == CommandCode.SMFIC_UNKNOWN.code()) {
			if (context.getMacros(CommandCode.SMFIC_UNKNOWN.code()) != null) {
				log.debug("*context.getMacros(SMFIC_UNKNOWN)       : "
						+ context.getMacros(CommandCode.SMFIC_UNKNOWN.code()));
			}

		}
		} else {
			if (context != null) {
				log.debug("*context.getMtaProtocolVersion()        : " + context.getMtaProtocolVersion());
				log.debug("*context.getSessionProtocolVersion()    : " + context.getSessionProtocolVersion());
				log.debug("*ontextt.milterProtocolVersion()        : " + context.milterProtocolVersion());
				log.debug("*context.PROTOCOL_VERSION               : " + MilterContext.PROTOCOL_VERSION);
				log.debug("*context.getMacros(ttl)                 : " + context.getMacros(ttl));
				log.debug("*context.getMacros(timeout)             : " + context.getMacros(timeout));
				log.debug("*context.getMtaActions()                : " + context.getMtaActions());
				log.debug("*context.getMtaProtocolSteps()          : " + context.getMtaProtocolSteps());
				log.debug("*context.getSessionProtocolSteps()      : " + context.getSessionProtocolSteps());
				log.debug("*context.getSessionStep()               : " + context.getSessionStep().code());
				log.debug("*context.id()                           : " + context.id());
				log.debug("*context.milterActions()                : " + context.milterActions());
				log.debug("*context.milterProtocolSteps()          : " + context.milterProtocolSteps());
			}

		}

	}

}
