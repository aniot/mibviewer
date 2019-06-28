package com.threeic.mibview;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.ListView;

public class HostListView extends ListView<String> {
	
	Map<String, String> scanlist = new HashMap<>();
	
	public HostListView() {
		//setFixedCellSize(line_height);
	}

	public void add(Map<String, String> map) {
		String name = map.get("name");
		getItems().add(name);
		scanlist.put(name, map.get("addr"));
	}
	
	public void add(String k, String v) {
		getItems().add(k);
		scanlist.put(k, v);
	}

	public void clear() {
		getItems().clear();
	}

	public String getAddress(String name) {
		return scanlist.get(name);
	}
}