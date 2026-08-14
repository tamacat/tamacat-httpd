/*
 * Copyright (c) 2013, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.core.BasicHttpStatus;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.exception.HttpException;
import org.tamacat.httpd.exception.NotFoundException;
import org.tamacat.httpd.util.HeaderUtils;
import org.tamacat.httpd.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.IOUtils;
import org.tamacat.httpd.core.util.StringUtils;
/**
 * Support Range Retrieval Requests.
 */
public class LocalFileStreamingHttpHandler extends LocalFileHttpHandler {
	private static final Logger LOG = LoggerFactory.getLogger(LocalFileStreamingHttpHandler.class);

	protected int bufferSize = 5 * 1024 * 1024; //5MB
	protected boolean acceptRanges = true;
	
	public void setAcceptRanges(boolean acceptRanges) {
		this.acceptRanges = acceptRanges;
	}
	
	/**
	 * Allocates a buffer size.
	 * (for Range Retrieval Requests) 
	 * @param bufferSize
	 */
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}
	
	@Override
	public void doRequest(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
			throws HttpException, IOException {
		
		String path = RequestUtils.getPath(request);
		if (StringUtils.isEmpty(path) || path.contains("..")) {
			throw new NotFoundException();
		}
		if (path.endsWith("/") && useDirectoryListings() == false) {
			path = path + welcomeFile;
		}
		File file = new File(docsRoot, getDecodeUri(path.replace(serviceUrl.getPath(), "/")));
		///// 404 NOT FOUND /////
		if (!file.exists()) {
			LOG.debug("File " + path + " not found");
			throw new NotFoundException();
		}
		///// 403 FORBIDDEN /////
		else if (!file.canRead() || file.isDirectory()) {
			if (file.isDirectory() && useDirectoryListings()) {
				String html = listingPage.getListingsPage(
						request, response, file);
				response.setCode(HttpStatus.SC_OK);
				if (!"HEAD".equals(request.getMethod())) {
					response.setEntity(getEntity(html));
				}
			} else {
				LOG.trace("Cannot read file " + file.getPath());
				throw new ForbiddenException();
			}
		}
		///// 200 OK /////
		else {
			if (acceptRanges) {
				response.setHeader("Accept-Ranges", "bytes");
			}
			String range = HeaderUtils.getHeader(request, "Range");
			//if (StringUtils.isEmpty(range)) range = HeaderUtils.getHeader(request, "If-Range");
			long offset = 0;
			int limit = bufferSize;
			boolean chunked = false;
			try {
				if ("GET".equals(request.getMethod()) 
					&& StringUtils.isNotEmpty(range) && range.indexOf("bytes=")>=0
					&& range.indexOf("-")>=0) {
					LOG.debug("Range: " + range);

					range = range.replace("bytes=", "");
					String[] offsetLimit = range.split("-");
					offset = StringUtils.parse(offsetLimit[0], -1L);
					if (offset >= 0) {
						chunked = true;
						if (offsetLimit.length >= 2) {
							int getLimit = StringUtils.parse(offsetLimit[1], limit);
							if (getLimit < limit) {
								limit = getLimit;
							}
						}
					}
				}
			} catch (Exception e) {
				LOG.warn(e.getMessage());
				throw new HttpException(BasicHttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			}
			if (!"HEAD".equals(request.getMethod())) {
				if (chunked) {
					partialContent(response, file, offset, limit);
				} else {
					FileInputStream in = new FileInputStream(file);
					InputStreamEntity entity = new InputStreamEntity(in, file.length(),
						getStreamingContentType(file));
						response.setEntity(entity);
				}
			}
		}
	}
	
	/**
	 * <p>Returns the {@link ContentType} used for the streamed body.
	 * <p>core5 requires the content type at entity construction time, while 4.4 let
	 * {@code InputStreamEntity} / {@code ByteArrayEntity} be built without one. The
	 * type is resolved from the file extension, falling back to
	 * {@code application/octet-stream}, which is what an entity without a content type
	 * effectively meant before.
	 * @since 2.0
	 */
	protected ContentType getStreamingContentType(File file) {
		try {
			String type = getContentType(file);
			if (StringUtils.isNotEmpty(type)) {
				return ContentType.create(ContentType.parse(type).getMimeType(),
					ContentType.parse(type).getCharset());
			}
		} catch (Exception e) {
			LOG.debug("Can not resolve the content type of " + file + " - " + e.getMessage());
		}
		return ContentType.DEFAULT_BINARY;
	}

	protected void partialContent(ClassicHttpResponse response, File file, long offset, int limit) {
		long length = file.length();
		if (offset<0 || limit<0 || length<offset) {
			throw new HttpException(BasicHttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
		}
		RandomAccessFile rf = null;
		FileChannel fc = null;
		try {
			rf = new RandomAccessFile(file, "r");
			fc = rf.getChannel();
			ByteBuffer buffer = ByteBuffer.allocate(limit);
			int readed = fc.read(buffer,(int)offset);
			byte[] array = buffer.array();
			buffer.clear();

			//core5 entities are immutable: content type, content encoding and the
			//chunked flag are all constructor arguments now.
			ByteArrayEntity entity = new ByteArrayEntity(
				array, getStreamingContentType(file), "chunked", true);
			if (fc.position() < file.length()) {
				response.setCode(HttpStatus.SC_PARTIAL_CONTENT);
			}
			String value = "bytes "+offset+"-"+(offset+readed-1)+"/"+length;
			LOG.debug("Content-Range: " + value);
			response.setHeader("Content-Range", value);
			response.setEntity(entity);
		} catch (IOException e) {
			LOG.warn(e.getMessage());
			LOG.debug(e.toString());
			throw new HttpException(BasicHttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
		} finally {
			IOUtils.close(fc);
			IOUtils.close(rf);
		}
	}
}
