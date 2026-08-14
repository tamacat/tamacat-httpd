/*
 * Copyright (c) 2019 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.filter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.tamacat.httpd.config.ServiceUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tamacat.httpd.core.util.StringUtils;

/**
 * RequestFilter for Request Header convert.
 * <li>headerNames: Header1,Header2,Header3,...
 * <li>convertValues: BeforeValue=AfterValue
 * 
 * ex) conf/components.xml
 * ----------------------------------
 * <bean id="RequestHeaderFilter" class="org.tamacat.httpd.filter.RequestHeaderConvertFilter">
 *   <property name="headerNames">
 *     <value>Destination</value>
 *   </property>
 *   <property name="convertValues">
 *     <value>https://test.example.com/=http://localhost:8080/</value>
 *   </property>
 * </bean>
 * 
 * Before: Destination: https://test.example.com/test.html
 * After : Destination: http://localhost:8080/test.html
 */
public class RequestHeaderConvertFilter implements RequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RequestHeaderConvertFilter.class);
		
	protected ServiceUrl serviceUrl;
	protected Set<String> headerNames = new LinkedHashSet<>();
	
	protected Map<String, String> convert = new LinkedHashMap<>();
	
	/**
	 * Set the Target Header Names.
	 * @param names Comma separated Header Names.
	 */
	public void setHeaderNames(String names) {
		String[] values = StringUtils.split(names, ",");
		if (values.length > 0) {
			this.headerNames.addAll(Arrays.asList(values));
		}
	}
	
	/**
	 * Set the convert values.
	 * @param values beforeValue=afterValue
	 */
	public void setConvertValues(String values) {
		String[] conv = StringUtils.split(values, "=");
		if (conv.length == 2) { 
			this.convert.put(conv[0], conv[1]);
		}
	}
	
	@Override
	public void init(ServiceUrl serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	@Override
	public void doFilter(ClassicHttpRequest req, ClassicHttpResponse resp, HttpContext context) {
		for (String headerName : headerNames) {
			Header[] headers = req.getHeaders(headerName);
			for (Header header : headers) {
				String value = header.getValue();
				if (StringUtils.isNotEmpty(value)) {
					String convertedValue = convertHeaderValue(value);
					LOG.trace("[Filter] "+value +" => "+convertedValue);
					req.removeHeader(header);
					req.addHeader(new BasicHeader(headerName, convertedValue));
				}
			}
		}
	}

	protected String convertHeaderValue(String value) {
		Set<String> beforeValues = convert.keySet();
		for (String before : beforeValues) {
			if (value.indexOf(before) >= 0) {
				return value.replace(before, convert.get(before));
			}
		}
		return value;
	}
}
