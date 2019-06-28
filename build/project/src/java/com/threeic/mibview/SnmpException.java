package com.threeic.mibview;

public class SnmpException extends Exception {	
	private static final long serialVersionUID = 1L;
	
	public SnmpException() {
		super("SNMP Exception");
	}
	
	public SnmpException(String msg) {
		super(msg);
	}
	
	public SnmpException(Exception exception) {
		super(exception);
	}
}
