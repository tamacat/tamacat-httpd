/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.di.define;

/**
 * Bean of parameter for constrctor.
 */
public class BeanConstructorParam implements Cloneable {

	private String refId;
	private String type;
	private String value;

	/**
	 * Return an id of reference.
	 * 
	 * @return
	 */
	public String getRefId() {
		return refId;
	}

	/**
	 * Set a id of reference.
	 * 
	 * @param refId
	 */
	public void setRefId(String refId) {
		this.refId = refId;
	}

	/**
	 * Return whether this bean use the reference.
	 */
	public boolean isRef() {
		return refId != null;
	}

	/**
	 * Return a String of class name.
	 * 
	 * @return
	 */
	public String getType() {
		return type;
	}

	/**
	 * Set the String of class name.
	 * 
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Return a value.
	 * 
	 * @return
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Set the value.
	 * 
	 * @param value
	 */
	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public BeanConstructorParam clone() {
		try {
			return (BeanConstructorParam) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.getMessage());
		}
	}
}
