package com.threeic.mibview;

import javafx.beans.property.SimpleStringProperty;

public class OidPair {
	private final SimpleStringProperty oidProperty = new SimpleStringProperty();
	private final SimpleStringProperty valueProperty = new SimpleStringProperty();;
	
	public String oid;
	public String value;
	
	public OidPair(String oid, String value) { 
		this.oid = oid;
		this.value = value;
		oidProperty.set(oid);
		valueProperty.set(value);
	}
  
	public String getOid() {
		return oidProperty.get();
	}
  
	public String getValue() {
		return valueProperty.get();
	}
	
	public void setOid(String oid) {
		oidProperty.set(oid);
	}
  
	public void setValue(String value) {
		valueProperty.set(value);
	}
	
	public String toString() {
		return "OID: " + oid + " Value: " + value;
	}
}
