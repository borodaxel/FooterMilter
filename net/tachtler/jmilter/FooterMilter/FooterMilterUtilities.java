/**
 * Copyright (c) 2022 Klaus Tachtler. All Rights Reserved.
 * Klaus Tachtler. <klaus@tachtler.net>
 * http://www.tachtler.net
 */
package net.tachtler.jmilter.FooterMilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import java.util.Base64;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

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
public class FooterMilterUtilities {

	private static Logger log = LogManager.getLogger();

	/**
	 * Constructor.
	 */
	public FooterMilterUtilities() {
		super();
	}

	/**
	 * Write to output stream data including the content from the given entity with added
	 * footer. The given entity should be from "Content-Type" - "text/plain".
	 * 
	 * If the given entity has the "Content-Disposition" - "attachment" do NOT
	 * append any footer String!
	 * 
	 * If the given entity has the "Content-Transfer-Encoding" - "quoted-printable"
	 * convert the given footer String to a "Quoted Printable" String, before
	 * appending the footer String!
	 * 
	 * @param entity
	 * @param bodyContent
	 * @param footer
	 */
	public static void getTextContentWithFooter(Entity entity, ByteArrayOutputStream bodyContent, String footer) throws FooterMilterException, IOException {

		// For encoded content we need to concat original body with footer first and then encode both
		ByteArrayOutputStream textBody = new ByteArrayOutputStream();

		String charset = entity.getCharset();

		log.debug("*entity.getCharset()                    : " + charset);
		log.debug("*entity.getDispositionType()            : " + entity.getDispositionType());
		log.debug("*entity.getContentTransferEncoding()    : " + entity.getContentTransferEncoding());

		// 0 - no transfer encoding
		// 1 - base64
		// 2 - quoted-printable
		int messageType = 0;
		boolean parseMessage = false;

		if (null != entity.getDispositionType()) {
			if (!entity.getDispositionType().equalsIgnoreCase("attachment")) {
				parseMessage = true;
			} else {
				// Pass attachment without modifications
				// However it may have content-transfer-encoding
				FooterMilterUtilities.writeTextBody(entity, bodyContent, true);
			}
		} else {
			parseMessage = true;
		}

		if (parseMessage) {
			FooterMilterUtilities.writeTextBody(entity, textBody);
			textBody.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

			if (null != entity.getContentTransferEncoding()) {
				if (entity.getContentTransferEncoding().equalsIgnoreCase("base64")) {
					messageType = 1;
				} else if (entity.getContentTransferEncoding().equalsIgnoreCase("quoted-printable")) {
					messageType = 2;
				} else {
					// just for reference messageType = 0;
				}
			} else {
				// just for reference messageType = 0;
			}

			switch (messageType) {
			case 0: // Without transfer encoding
				textBody.writeTo(bodyContent);
				bodyContent.write(footer.getBytes(Charset.forName(charset)));
				// Footer trailing EOF was trimmed
				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
				break;
			case 1: // Base64
				textBody.write(footer.getBytes(Charset.forName(charset)));
				bodyContent.write(Base64.getMimeEncoder().encode(textBody.toByteArray()));
				// Close the last line of base64 data
				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
				break;
			case 2: // Quoted-printable
				textBody.write(footer.getBytes(Charset.forName(charset)));
				FooterMilterUtilities.writeQuotedPrintable(textBody.toByteArray(), bodyContent);
				// Close the last line of quoted data
				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
				break;
			}
		}

		// Extra line to split from message end
		bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

//		log.debug("Content-Type: text/plain                : " + stringBuffer.toString());

		return;
	}

	/**
	 * Write to output stream data including the content from the given entity with added
	 * footer. The given entity should be from "Content-Type" - "text/html".
	 * 
	 * Determine if the given "Content-Type" - "text/html" is correct formatted
	 * HTML-Code. If so, add the footer before the closing HTML-Body tag (</body>).
	 * If NOT, add the footer at the end of the given entity body.
	 * 
	 * If the given entity has the "Content-Disposition" - "attachment" do NOT
	 * append any footer String!
	 * 
	 * If the given entity has the "Content-Transfer-Encoding" - "quoted-printable"
	 * convert the given footer String to a "Quoted Printable" String, before
	 * appending the footer String!
	 * 
	 * @param entity
	 * @param bodyContent
	 * @param footer
	 */
	public static void getHtmlContentWithFooter(Entity entity, ByteArrayOutputStream bodyContent, String footer) throws FooterMilterException, IOException {

		String charset = entity.getCharset();
		// Pre buffer with body, which may be encoded with base64 or quoted-printable
		// In case of correct HTML we need another buffer to split the HTML document before closing body tag
		ByteArrayOutputStream entityTextBody = new ByteArrayOutputStream();

		log.debug("*entity.getCharset()                    : " + charset);
		log.debug("*entity.getDispositionType()            : " + entity.getDispositionType());
		log.debug("*entity.getContentTransferEncoding()    : " + entity.getContentTransferEncoding());

		// 0 - no tranfer encoding
		// 1 - base64
		// 2 - quoted-printable
		int messageType = 0;
		boolean parseMessage = false;

		if (null != entity.getDispositionType()) {
			if (!entity.getDispositionType().equalsIgnoreCase("attachment")) {
				parseMessage = true;
			} else {
				// Pass attachment without modifications
				// However it may have content-transfer-encoding
				FooterMilterUtilities.writeTextBody(entity, bodyContent, true);
			}
		} else {
			parseMessage = true;
		}

		if (parseMessage) {
			// Skip any processing if not true
			if (null != entity.getContentTransferEncoding()) {
				if (entity.getContentTransferEncoding().equalsIgnoreCase("base64")) {
					messageType = 1;
				} else if (entity.getContentTransferEncoding().equalsIgnoreCase("quoted-printable")) {
					messageType = 2;
				} else {
					// just for reference messageType = 0;
				}
			} else {
				// just for reference messageType = 0;
			}

			/*
			 * Check if a well formed HTML content will be found. If it's true, customize
			 * the well formed HTML content. If it's false, add the HTML content at the end
			 * of the multipart part.
			 */
			// This should be unencrypted! But writeBody() func already encodes the body!
			FooterMilterUtilities.writeTextBody(entity, entityTextBody);
			// String to detect closing body tag
			String origTextBody = entityTextBody.toString(charset);

			if (origTextBody.indexOf("</body>") != -1) {
				// HTML document with closing body tag
				// Base64 and quoted-printable are totally different, we can't mix unencoded data with encoded
				ByteArrayOutputStream htmlBody = new ByteArrayOutputStream();

				String[] splitString = origTextBody.split("</body>");
				htmlBody.write(splitString[0].getBytes(Charset.forName(charset)));
				// Start new line before footer
				htmlBody.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

				switch (messageType) {
				case 0: // Without transfer encoding
					// Dump HTML message before the </body> tag
					htmlBody.writeTo(bodyContent);
					// Write footer, for HTML we don't care about extra new line
					bodyContent.write(footer.getBytes(Charset.forName(charset)));
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					// Close body tag
					bodyContent.write("</body>".getBytes(StandardCharsets.US_ASCII));
					// Append the rest of HTML
					bodyContent.write(splitString[1].getBytes(Charset.forName(charset)));
					break;
				case 1: // Base64
					// First part of HTML is already in output stream
					// Append the footer
					htmlBody.write(footer.getBytes(Charset.forName(charset)));
					// Close body tag
					htmlBody.write("</body>".getBytes(StandardCharsets.US_ASCII));
					// Append the rest of HTML
					htmlBody.write(splitString[1].getBytes(Charset.forName(charset)));
					// Encode and write to the message
					bodyContent.write(Base64.getMimeEncoder().encode(htmlBody.toByteArray()));
					// Close the last line of base64 data
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					break;
				case 2: // Quoted-printable
					// The same as for base64
					htmlBody.write(footer.getBytes(Charset.forName(charset)));
					htmlBody.write("</body>".getBytes(StandardCharsets.US_ASCII));
					htmlBody.write(splitString[1].getBytes(Charset.forName(charset)));
					FooterMilterUtilities.writeQuotedPrintable(htmlBody.toByteArray(), bodyContent);
					// Add line to split message parts, if any.
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					break;
				}

				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
			} else {
				// HTML document with missing closing body tag
				switch (messageType) {
				case 0: // Without transfer encoding
					entityTextBody.writeTo(bodyContent);
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					bodyContent.write(footer.getBytes(Charset.forName(charset)));
					break;
				case 1: // Base64
					// Original body is already there
					// Append the footer
					entityTextBody.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					entityTextBody.write(footer.getBytes(Charset.forName(charset)));
					// Encode and write to the message
					bodyContent.write(Base64.getMimeEncoder().encode(entityTextBody.toByteArray()));
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					break;
				case 2: // Quoted-printable
					// The same as for base64
					entityTextBody.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					entityTextBody.write(footer.getBytes(Charset.forName(charset)));
					//
					FooterMilterUtilities.writeQuotedPrintable(entityTextBody.toByteArray(), bodyContent);
					// Add line to split message parts, if any.
					bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
					break;
				}
			}

			bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
		}

//		log.debug("Content-Type: text/html                 : " + stringBuffer.toString());

		return;
	}

	/**
	 * Write to output stream a data including the content from the given entity WITHOUT added
	 * footer. The given entity could be from ANY "Content-Type", but should NOT be
	 * from "Content-Type" - "text/plain" or "text/html".
	 * 
	 * @param entity
	 * @param bodyContent
	 */
	public static void writeBinaryContent(Entity entity, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {

		FooterMilterUtilities.writeBinaryBody(entity, bodyContent);
		bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));

//		log.debug("Content-Type: \"binary-content\"          : " + stringBuffer.toString());

		return;
	}

	/**
	 * Write text body bytes from given (TextBody) entity to output stream.
	 * 
	 * @param entity
	 * @param bodyContent
	 */
	public static void writeTextBody(Entity entity, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {
		writeTextBody(entity, bodyContent, false);
	}

	/**
	 * Write text body bytes from given (TextBody) entity to output stream.
	 * 
	 * @param entity
	 * @param bodyContent
	 * @param encode
	 */
	public static void writeTextBody(Entity entity, ByteArrayOutputStream bodyContent, boolean encode) throws FooterMilterException, IOException {
		TextBody textBody = (TextBody) entity.getBody();
		if (encode) {
			writeEncodedBody(entity, textBody, bodyContent);
		} else {
			writeBody(entity, textBody, bodyContent);
		}
		return;
	}

	/**
	 * Write the binary body content to output stream.
	 * 
	 * @param entity
	 * @param bodyContent
	 */
	public static void writeBinaryBody(Entity entity, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {
		BinaryBody binaryBody = (BinaryBody) entity.getBody();
		writeEncodedBody(entity, binaryBody, bodyContent);
		return;
	}

	/**
	 * Write encoded data from given entity and body to output stream. Depending on the
	 * "Content-Transfer-Encoding" create the right body encoding.
	 * 
	 * If the "Content-Transfer-Encoding" is "base64" or "quoted-printable", not
	 * only a simple text body was given. The bodyString String must be encoded as
	 * "base64" or "quoted-printable", while using the "Base64" or the "EncoderUtil"
	 * from MIME4J.
	 * 
	 * @param entity
	 * @param body
	 * @param bodyContent
	 */
	private static void writeEncodedBody(Entity entity, Body body, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {
		try {
			InputStream inputStream = ((SingleBody) body).getInputStream();
			// This is raw content with some charset!
			byte[] bytes = IOUtils.toByteArray(inputStream);

			if (entity.getContentTransferEncoding().equalsIgnoreCase("base64")) {
				bodyContent.write(Base64.getMimeEncoder().encode(bytes));
				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
			} else if (entity.getContentTransferEncoding().equalsIgnoreCase("quoted-printable")) {
				writeQuotedPrintable(bytes, bodyContent);
				// Add line to split message parts, if any.
				bodyContent.write(System.lineSeparator().getBytes(StandardCharsets.US_ASCII));
			} else {
				bodyContent.write(bytes);
			}
		} catch (IOException eIOException) {
			throw new FooterMilterException(false, eIOException);
		}

//		log.debug("*bodyString   <- (Start at next line) -> : " + System.lineSeparator() + bodyString);

		return;
	}


	/**
	 * Write data from given entity and body to output stream as plain text.
	 * Encoding is required in case of writeBinaryContent only.
	 * The resulting encodind will be done in footer generation functions.
	 * 
	 * @param entity
	 * @param body
	 * @param bodyContent
	 */
	private static void writeBody(Entity entity, Body body, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {
		try {
			InputStream inputStream = ((SingleBody) body).getInputStream();
			// This is raw content with some charset!
			IOUtils.copy(inputStream, bodyContent);
		} catch (IOException eIOException) {
			throw new FooterMilterException(false, eIOException);
		}

//		log.debug("*bodyString   <- (Start at next line) -> : " + System.lineSeparator() + bodyString);

		return;
	}

	/**
	 * Write a "Quoted Printable" data from a byte array to output stream, using the Apache QuotedPrintableCodec.
	 * 
	 * @param string
	 * @return String
	 */
	private static void writeQuotedPrintable(byte[] content, ByteArrayOutputStream bodyContent) throws FooterMilterException, IOException {
		QuotedPrintableCodec quotedPrintableCodec = new QuotedPrintableCodec(true);

		bodyContent.write(quotedPrintableCodec.encode(content));

//		log.debug(
//				"*quotedStringBuffer (Start at next line) : " + System.lineSeparator() + quotedStringBuffer.toString());

		return;
	}

}
