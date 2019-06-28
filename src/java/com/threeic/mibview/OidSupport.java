package com.threeic.mibview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.TreeItem;

public class OidSupport {

	Map<String, String> oidMap = new HashMap<>();

	public String getNodeOid(TreeItem<MibRecord> node) {
		MibRecord  mib = node.getValue();
		if (mib.recordType == MibRecord.recVariable) return(mib.name + "-" + mib.syntax);
		
		String strPath = getNodeOidActual(node);
		if (strPath.equals(".")) strPath = "";
		
		// Node OID Obtained, now check if it is in a table For Table Element
		if (mib.recordType  == 3) {
			MibRecord parentMib = node.getParent().getValue();
			if (parentMib.tableEntry == -1) strPath = strPath.concat(".("+1 + " - " + "n"+")");
			else strPath = strPath.concat(".(" + 1 + " - " + String.valueOf(parentMib.tableEntry)+")");
		} else if (mib.recordType  == 2) {
			if (mib.tableEntry == -1) strPath = strPath.concat(".(1-"+node.getChildren().size()+")"+ ".(1-" + "n)");
			else strPath = strPath.concat(".(1-"+node.getChildren().size()+")"+ ".(1-" + String.valueOf(mib.tableEntry)+")");
		} else if (node.isLeaf()) {
			strPath = strPath.concat(".0");
		} else {
			strPath = strPath.concat(".*");
		}
		//System.out.println(strPath);
		return strPath;
	}

	/**
	 *  RETURNS OID VALUES SUCH THAT THEY CAN BE STRAIGHTAWAY USED FOR QUERIES
	 */
	public String getNodeOidQuery(TreeItem<MibRecord> node) {
		MibRecord mib = node.getValue();
		String strPath = getNodeOidActual(node);
		if (strPath.equals(".")) strPath = "";
		
		// Node OID Obtained, now check if it is in a table // For Table Element
		if (mib.recordType == 3) {
			MibRecord parentMib = node.getParent().getValue();
			if (parentMib.tableEntry == -1) strPath = strPath.concat(".65535");
			else strPath = strPath.concat("." + String.valueOf(parentMib.tableEntry));
		} else if (mib.recordType  == 2) {
			if (mib.tableEntry == -1) strPath = strPath.concat(".1.1");
			else strPath = strPath.concat(".1." + String.valueOf(mib.tableEntry));
		} else if (node.isLeaf() == true) {
			strPath = strPath.concat(".0");
		} else {
			strPath = strPath.concat(".0");
		}
		return strPath;
	}

	/**
	 * RETURNS THE ACTUAL OID, WITHOUT	APPENDING ANYTHING. 
	 * MAINLY USED FOR OID TO NAME RESOLVING.
	 */
	private String getNodeOidActual(TreeItem<MibRecord> node) {
		String str = "";
		TreeItem<MibRecord> pNode = node;
		while (true) {
			MibRecord mib = pNode.getValue();
			str = ("." + String.valueOf (mib.number)).concat(str);
			TreeItem<MibRecord> parent = pNode.getParent();
			if (parent == null) {
				break;
			} else {
				pNode = parent;
			}
		}

		String[] ids = str.split("\\.");
		if (ids.length < 3) return ".";		//2 nodes from root are not oid
			
		String[] array = Arrays.copyOfRange(ids, 3, ids.length);
		return "." + String.join(".", array);
	}

	void buildOidToNameResolutionTable(TreeItem<MibRecord> root){
        for (TreeItem<MibRecord> child: root.getChildren()) {
        	MibRecord mib = child.getValue();
			String sRec = getNodeOidActual(child);
			oidMap.put(sRec, mib.name);
			
            if (!child.getChildren().isEmpty()) {
            	buildOidToNameResolutionTable(child);
            }
        }
    }
 
	public String resolveOidName(String oid) {
		String objName = null;
		String oidCopy;

		if (oid.startsWith(".")) oidCopy = oid.toString();
		else oidCopy = "." + oid.toString();

		try {
			oidCopy = oidCopy.substring(0, oidCopy.lastIndexOf('.'));

			while (objName == null && oidCopy.length() > 2) {
				objName = oidMap.get(oidCopy);
				oidCopy = oidCopy.substring(0, oidCopy.lastIndexOf('.'));
			}
			if (objName == null) return("***");
		} catch (Exception e) {
			System.out.println("Error in Resolving OID Name :\n " + e.toString());
		}
		return objName + oid.substring(oid.indexOf(".", oidCopy.length() + 1));
	}

}