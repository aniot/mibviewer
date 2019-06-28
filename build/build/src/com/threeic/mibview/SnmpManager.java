package com.threeic.mibview;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpManager {

	private String host;
	private int port;
	private String readCommunity;
	private String writeCommunity;
	private ResponseHandler responseHandler;
	private boolean sessionDestroyed;
	private CommunityTarget target;

	public SnmpManager() {
		target = null;
	}

	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public String getReadCommunity() {
		return readCommunity;
	}
	
	public String getWriteCommunity() {
		return writeCommunity;
	}
	
	public void setCommunity(String readCommunity, String writeCommunity) {
		this.readCommunity = readCommunity;
		this.writeCommunity = writeCommunity;
	}
	
	public void setHost(String host) {
		target = null;
		this.host = host;
	}
	
	public void setPort(int port) {
		target = null;
		this.port = port;
	}
	
	public void setResponseHandler(ResponseHandler r) {
		responseHandler = r;
	}
	
	public ResponseHandler getResponseHandler() {
		return responseHandler;
	}
	
	public void destroySession() {
		sessionDestroyed = true;
	}
	
	private Target getTarget(String host, String strCommunity) {
		if (target == null) {
			Address addr = GenericAddress.parse("udp:" + host + "/" + getPort());
			target = new CommunityTarget();
			target.setCommunity(new OctetString(strCommunity));
			target.setAddress(addr);
			target.setVersion(SnmpConstants.version1);
			target.setRetries(3);
		}
		return target;
	}

	public void snmpWalk(String oidFrom, String oidTo) throws SnmpException {
		snmpWalk(oidFrom);
	}

	public void snmpWalk(String oidFrom) throws SnmpException {
		System.out.println("snmpwalk: " + oidFrom);
		PDU request = new PDU();
		request.setType(PDU.GETNEXT);
		request.add(new VariableBinding(new OID(oidFrom)));
		request.setNonRepeaters(0);
		OID rootOID = request.get(0).getOid();
		PDU response = null;

		int objects = 0;
		int requests = 0;
		long startTime = System.currentTimeMillis();

		try {
			Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
			snmp.listen();
			sessionDestroyed = false;
			
			do {
				requests++;
				ResponseEvent responseEvent = snmp.send(request, getTarget(getHost(), getReadCommunity()));
				response = responseEvent.getResponse();
				if (response != null) {
					objects += response.size();
				}
			} while (!processWalk(response, request, rootOID) && !sessionDestroyed);

		} catch (SnmpException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new SnmpException(e.getMessage());
		}
		
		if(responseHandler != null) {
			responseHandler.onStats(requests, objects, System.currentTimeMillis() - startTime);
		}
	}

	private boolean processWalk(PDU response, PDU request, OID rootOID) throws SnmpException {
		if ((response == null) || (response.getErrorStatus() != 0) ||
			(response.getType() == PDU.REPORT)) {
			return true;
		}

		boolean finished = false;
		OID lastOID = request.get(0).getOid();

		for (int i = 0; (!finished) && (i < response.size()); i++) {
			VariableBinding vb = response.get(i);
			if ((vb.getOid() == null) ||
				(vb.getOid().size() < rootOID.size()) ||
				(rootOID.leftMostCompare(rootOID.size(), vb.getOid()) != 0)) {
				finished = true;
			} else if (Null.isExceptionSyntax(vb.getVariable().getSyntax())) {
				outputResponse(vb);
				finished = true;
			} else if (vb.getOid().compareTo(lastOID) <= 0) {
				throw new SnmpException("Variable received is not the successor of requested one:" + vb.toString() + " <= " + lastOID);
			} else {
				outputResponse(vb);
				lastOID = vb.getOid();
			}
		}

		if (response.size() == 0) {
			finished = true;
		}

		if (!finished) {
			VariableBinding next = response.get(response.size() - 1);
			next.setVariable(new Null());
			request.set(0, next);
			request.setRequestID(new Integer32(0));
		}
		return finished;
	}

	private OidPair outputResponse(VariableBinding vb) {
		OidPair pair = new OidPair(vb.getOid().toString(), vb.getVariable().toString());
		//pair.oid = vb.getOid().toString();
		//pair.value = vb.getVariable().toString();
		if(responseHandler != null) {
			responseHandler.onReceived(pair);
		}
		return pair;
	}

	void snmpSetValue(String oid, int syntax, String value) throws SnmpException {
		VariableBinding varbind = getVarBindForSetRequest(oid, syntax, value);

		PDU request = new PDU();
		request.setType(PDU.SET);
		request.add(varbind);
		PDU response = null;

		try {
			Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
			snmp.listen();
			ResponseEvent responseEvent = snmp.send(request, getTarget(getHost(), getWriteCommunity()));
			response = responseEvent.getResponse();
			System.out.println(response);
		} catch (Exception e) {
			e.printStackTrace();
			throw new SnmpException(e.getMessage());
		}
	}

	VariableBinding getVarBindForSetRequest(String oid, int type, String value) {
		VariableBinding vb = new VariableBinding(new OID(oid));

		if (value != null) {
			Variable variable;
			switch (type) {
			case MibRecord.VALUE_TYPE_INTEGER32:
				variable = new Integer32(Integer.parseInt(value));
				break;
			case MibRecord.VALUE_TYPE_UNSIGNED_INTEGER32:
				variable = new UnsignedInteger32(Long.parseLong(value));
				break;
			case MibRecord.VALUE_TYPE_OCTET_STRING:
				variable = new OctetString(value);
				break;
			case MibRecord.VALUE_TYPE_NULL:
				variable = new Null();
				break;
			case MibRecord.VALUE_TYPE_OID:
				variable = new OID(value);
				break;
			case MibRecord.VALUE_TYPE_TIMETICKS:
				variable = new TimeTicks(Long.parseLong(value));
				break;
			case MibRecord.VALUE_TYPE_IP_ADDRESS:
				variable = new IpAddress(value);
				break;
			default:
				throw new IllegalArgumentException("Variable type " + type + " not supported");
			}
			vb.setVariable(variable);
		}
		return vb;
	}
	
	final static OID oid_sysdesc = new OID(new int[] {1,3,6,1,2,1,1,1});
	final static OID oid_object = new OID(new int[] {1,3,6,1,2,1,1,2});
	final static OID oid_uptime = new OID(new int[] {1,3,6,1,2,1,1,3});
	final static OID oid_ip = new OID(new int[] {1,3,6,1,2,1,4,20,1,1});
	SnmpListener listener = new SnmpListener();

	class SnmpListener implements ResponseListener {
		boolean finished = false;

		public void onResponse(ResponseEvent event) {
			PDU response = event.getResponse();
			System.out.println("Received response PDU is: " + response);
			if (response == null) {
				finished = true;
				synchronized (listener) {
					listener.notify();
				}
			} else {
				Map<String, String> map = new HashMap<>();
				for (VariableBinding vb: response.getVariableBindings()) {
					OID oid = vb.getOid();
					String str = vb.getVariable().toString();
					if (oid.leftMostCompare(8, oid_sysdesc) == 0) {
						map.put("name", str.substring(0, str.indexOf(",")));
					} else if (oid.leftMostCompare(8, oid_ip) == 0) {
						map.put("addr", str);
					} else if (oid.leftMostCompare(8, oid_uptime) == 0) {
						map.put("time", str);
					}
				}
				if (responseHandler != null && map.containsKey("addr")) {
					if (!map.get("addr").equals("127.0.0.1")) responseHandler.onReceived(map);
				}
			}
		}

		public boolean isFinished() {
			return finished;
		}
	}
	
	public void scan() {
		PDU request = new PDU();
		request.setType(PDU.GETNEXT);
		request.setNonRepeaters(0);
		request.add(new VariableBinding(oid_sysdesc));
		request.add(new VariableBinding(oid_ip));
		request.add(new VariableBinding(oid_uptime));

		try {
			Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
			snmp.listen();
			sessionDestroyed = false;
			
			//Create a broadcast target
			Target target = getTarget("255.255.255.255", "public");
			snmp.send(request, target, null, listener);
			
			while (!listener.isFinished()) {
				synchronized (listener) {
					listener.wait(target.getTimeout() * 2);
				}
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
