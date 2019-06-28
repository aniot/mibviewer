package com.threeic.mibview;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.control.TableView;

public class MibBrowser {
	private Console console = null;
	private OidSupport oidSupport = null;

	private String agentIP = "localhost";
	private int agentPort = 161;
	private String setCommunity = "public";
	private String getCommunity = "public";
	
	private HostListView listview;
	private TableView<OidPair> table;
	private SnmpManager manager;
	
	private ResponseHandler responseHandler = new ResponseHandler() {
		public void onReceived(OidPair p) {
			outputRecord(p);
		}
		
		public void onReceived(Map<String,String> map) {
			listview.add(map);
			console.log(map.get("addr") + " - " + map.get("name") + " - " + map.get("time"));
		}

		public void onStats(int totalRequests, int totalResponses, long timeInMillis) {
			if (totalResponses == 0) totalResponses = 1;
			outputText("Received " + (totalResponses - 1) + " record(s) in " + timeInMillis + " milliseconds.\n");
		}
	};

	public MibBrowser() { }

	public void setOutput(Console console) {
		this.console = console;
	}
	
	public void setScanContainer(HostListView view) {
		listview = view;
	}

	public void setOidSupport(OidSupport oidSupport) {
		this.oidSupport = oidSupport;
	}

	public void setHost(String s) {
		manager = null;
		this.agentIP = s;
	}

	public void setPort(int p) {
		manager = null;
		this.agentPort = p;
	}

	public void setCommunity(String get, String set) {
		manager = null;
		this.getCommunity = get;
		this.setCommunity = set;
	}

	public String getHost() {
		return agentIP;
	}

	public int getPort() {
		return agentPort;
	}

	public String getReadCommunity() {
		return getCommunity;
	}

	public String getWriteCommunity() {
		return setCommunity;
	}

	void destroySession() {
		getManager().destroySession();
	}

	public void outputRecord(OidPair pair) {
		if (oidSupport != null) {
			pair.setOid(oidSupport.resolveOidName(pair.oid));
		}
		Platform.runLater(() -> table.getItems().add(pair) );
	}

	void outputText(String s) {
		Platform.runLater(() -> console.log(s) );
	}
	
	public String getLocalAddress() {
		try {
			InetAddress addr = InetAddress.getLocalHost();
			return addr.getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}


	public void snmpRequestGet(String strVar) {
		Platform.runLater(() -> table.getItems().clear() );
		try {
			getManager().snmpWalk(strVar);
		} catch (Exception e) {
			outputText("\nError in executing GET request : \n" + e.toString());
			e.printStackTrace();
		}
	}

	String processSetRequest(MibRecord setRec, String oid, String setVal) {
		try {
			getManager().snmpSetValue(oid, setRec.getSyntaxID(), setVal);
		} catch (Exception e) {
			outputText("\nError in executing SET request : \n" + e.toString());
			e.printStackTrace();
		}
		return "";
	}
	
	public void scan() {
		listview.clear();
		listview.add("localhost", getHost());
		outputText("\nScanning ...");
		try {
			getManager().scan();
		} catch (Exception e) {
			outputText("\nError in executing SCAN request : \n" + e.toString());
			e.printStackTrace();
		}
	}

	private SnmpManager getManager() {
		if(manager == null) {
			manager = new SnmpManager();
			manager.setHost(getHost());
			manager.setPort(getPort());
			manager.setCommunity(getReadCommunity(), getWriteCommunity());
			manager.setResponseHandler(responseHandler);
		}
		return manager;
	}

	public void setContentView(TableView<OidPair> table) {
		this.table = table;
	}

}
