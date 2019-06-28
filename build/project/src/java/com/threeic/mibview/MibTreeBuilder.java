package com.threeic.mibview;

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class MibTreeBuilder implements ParseListener, Runnable {
	private TreeItem<MibRecord> rootNode;
	
	private TreeItem<MibRecord> treeRootNode;
	private TreeItem<MibRecord> rootOrphan;
	private TreeItem<MibRecord> rootVariable;
	private TreeItem<MibRecord> rootVariableTable;

	private Console console;

	private Map<String, TreeItem<MibRecord>> treeMap = new HashMap<>();
	private Map<String, TreeItem<MibRecord>> orphanMap = new HashMap<>();
	private Map<String, MibRecord> variableMap = new HashMap<>();
	private Vector<String> fileVect = new Vector<String>();
	
	public OidSupport oidSupport = new OidSupport();

	public MibTreeBuilder() { }

	public TreeItem<MibRecord> getRootNode() {
		return rootNode;
	}

	public boolean addFile(String path) {
		if (path == null) return false;
		
		File mibFile = new File(path);
		if (!mibFile.exists()) return false;
		
		fileVect.add(path);
		return true;
	}

	public boolean addDirectory(String path) {
		log("Adding directory : " + path);
		File dir = new File(path);
		if (dir.isDirectory() != true) return false;
		
		File files[] = dir.listFiles();
		if (files == null) return false;
		
		for(int i = 0; i < files.length; i++) {
			fileVect.add(files[i].getPath());
		}
		return true;
	}

	public String[] getFiles() {
		try {
			Enumeration<String> enu = fileVect.elements();
			String returnStr[] = new String[fileVect.size()];

			int i = 0;
			while (enu.hasMoreElements()) {
				returnStr[i++] = enu.nextElement();
			}
			return returnStr;
		} catch (Exception e) {
			log("Error in getting filenames..\n" + e.toString());
			return null;
		}
	}

	public void buildTree(TreeView<MibRecord> tree) {
		String projectdir = System.getProperty("ProjectDir");
		if (projectdir == null) projectdir = ".";

		if (addDirectory(projectdir + "/mibs/") == false) {
			log("Directory " + projectdir + "/mibs/ not found, or it is an empty directory!");
		}
		
		if(fileVect.size() == 0) {			// Check if files have been added to list
			log("Error : Please add files first");
			return;
		}
		
		MibRecord treeRootRec = new MibRecord();
		treeRootRec.name = "MIB";
		treeRootRec.parent = "MIB";
		treeRootRec.number = 0;
		treeRootNode = new TreeItem<>(treeRootRec);
		
		tree.setRoot(treeRootNode);
		treeRootNode.setExpanded(true);

		MibRecord rootRec = new MibRecord();
		rootRec.name = "root";
		rootRec.parent = "MIB";
		rootRec.number = 1;
		rootNode = new TreeItem<>(rootRec);
		treeRootNode.getChildren().add(rootNode);

		MibRecord rootOrphanRec = new MibRecord();
		rootOrphanRec.name = "Orphans";
		rootOrphanRec.parent = "MIB";
		rootOrphanRec.description = "This subtree contains MIB Records whose parent cannot be traced";
		rootOrphanRec.number = 10;
		rootOrphan = new TreeItem<>(rootOrphanRec);
		treeRootNode.getChildren().add(rootOrphan);
		
		MibRecord rootVariableRec = new MibRecord();
		rootVariableRec.name = "Variables/Textual Conventions";
		rootVariableRec.parent = "MIB";
		rootVariableRec.description = "This subtree contains all the variables which map to the standard variables.";
		rootVariableRec.number = 11;
		rootVariable = new TreeItem<>(rootVariableRec);
		treeRootNode.getChildren().add(rootVariable);

		treeMap.put(rootRec.name, rootNode);

		MibRecord rootVariableTableRec=new MibRecord();
		rootVariableTableRec.name = "Table Entries";
		rootVariableTableRec.parent = "Variables/Textual Conventions";
		rootVariableTableRec.description = "This branch contains a list of sequences for all the tables ";
		rootVariableTableRec.number = 12;
		rootVariableTable = new TreeItem<>(rootVariableTableRec);
		rootVariable.getChildren().add(rootVariableTable);

		Thread treeThread = new Thread(this);
		treeThread.setPriority(Thread.MAX_PRIORITY - 1);
		treeThread.start();
	}

	public void run() {
		Enumeration<String> en = fileVect.elements();
		String fileName = "";
		while(en.hasMoreElements()) {
			fileName = en.nextElement();
			loadFile(fileName);
		}
		updateOrphans();
		log("*****COMPLETED******");
	}

	private void loadFile(String name) {
		console.log("Adding file " + name);
		if (parseFile(name) < 0) print(".. Error");
		else print("..Done");
	}

	public boolean loadNewFile(String name) {
		if (name == null) return false;
		
		File mibFile = new File(name);
		if(mibFile.exists() != true) return false;
		
		if(fileVect.indexOf(name) == -1) {
			fileVect.add(name);
			loadFile(name);
			updateOrphans();
			return true;
		}
		return false;
	}

	/**
	 * Check if orphan's parents have arrived. if yes, remove them from orphan list
	 */
	private void  updateOrphans() {
		log("Updating orphans");
		MibRecord orphanRec = null;
		boolean contFlag = true;

		//TODO check the strange contflag
		while(contFlag == true) {
			contFlag = false;
			Iterator<Entry<String, TreeItem<MibRecord>>> it = orphanMap.entrySet().iterator();
		    while (it.hasNext()) {
		        TreeItem<MibRecord> orphanNode = it.next().getValue();
		        if (addNode(orphanNode) == true) {
					contFlag = true;
					it.remove();
					continue;
				}
		    }
		    print(".");
		}
		print("Done");
		
		log("\nBuilding OID Name resolution table...");
		oidSupport.buildOidToNameResolutionTable(rootNode);

		//Add remaining orphans to treeroot.orphans
		for (TreeItem<MibRecord> orphanNode : orphanMap.values()) {
			orphanRec = orphanNode.getValue();
			if (orphanRec.recordType == MibRecord.recVariable) continue;
			
			if (orphanRec.recordType == MibRecord.recTable) {
				rootVariable.getChildren().add(orphanNode);
			} else if (treeMap.containsKey(orphanRec.name) != true) {
				rootOrphan.getChildren().add(orphanNode);
			}
		}

		//Add variables to varroot
		log("Updating variables table..");
		for (MibRecord var : variableMap.values()) {
			rootVariable.getChildren().add(new TreeItem<MibRecord>(var));
		}
		print("Done");
	}

	private int parseFile(String path) {
		MibParser parser = new MibParser(path, this);
		return parser.parseMibFile();
	}

	private boolean addRecord(MibRecord childRec) {
		if(childRec == null) return false;

		TreeItem<MibRecord> newNode = new TreeItem<MibRecord>(childRec);
		if(addNode(newNode) == false) {
			orphanMap.put(childRec.name, newNode);
			return false;
		}
		return true;

/*
		// See if parent exists. if no parent, add it to orphans
		if (treeHash.containsKey(childRec.parent) == false) {
		//	outputText("Orphan : " + childRec.name + "  Parent : " + childRec.parent );
			DefaultMutableTreeNode orphanNode=new DefaultMutableTreeNode(childRec,true);
			treeHash.put(childRec.name,orphanNode);
			orphanVect.add(orphanNode);
			return false;
		}
		// Get the parent node (current node will be added to it)
		DefaultMutableTreeNode parentNode =(DefaultMutableTreeNode) treeHash.get(childRec.parent);

		// Check if parent node contains a child of same name as new node
		// If  child exists, return true
		if (isChildPresent(childRec)!=null) return true;

		// Check if parent is a Table, and set the node tableEntry accordingly
		MibRecord parentRec=(MibRecord)parentNode.getUserObject();
		if(parentRec.recordType > 0) childRec.recordType =parentRec.recordType+1;
		//outputText("Added Child : " + childRec.name  + "  Parent : " + childRec.parent );
		DefaultMutableTreeNode childNode=new DefaultMutableTreeNode (childRec,true);
		// Add  node to  its parent
		parentNode.add(childNode);
		childNode.setParent(parentNode);
		treeHash.put(childRec.name,childNode);
		return true;
*/
	}

	private boolean addNode(TreeItem<MibRecord> newNode) {
		MibRecord newRec = newNode.getValue();

		TreeItem<MibRecord> parentNode = treeMap.get(newRec.parent);
		if (parentNode == null) return false;

		// Check if parent is a Table, and set the node tableEntry accordingly
		MibRecord parentMIB = parentNode.getValue();
		if (parentMIB.recordType > 0) newRec.recordType = parentMIB.recordType+1;

		TreeItem<MibRecord> dupNode = isChildPresent(newNode);
		if (dupNode == null) {
			parentNode.getChildren().add(newNode);		//Add  node to  its parent
			// See if parent is not an orphan
			treeMap.put(newRec.name, newNode);
		} else {      // Node already present. add all its children to the existing node
			for (TreeItem<MibRecord> child: newNode.getChildren()) {
				if (isChildPresent(child) == null) dupNode.getChildren().add(child);
			}
		}
		return true;
	}

	TreeItem<MibRecord> isChildPresent(TreeItem<MibRecord> child) {
		MibRecord mib = child.getValue();
		return isChildPresent(mib);
	}

	TreeItem<MibRecord> isChildPresent(MibRecord mib) {
		TreeItem<MibRecord> parentNode = treeMap.get(mib.parent);
		if(parentNode == null) parentNode = orphanMap.get(mib.parent);
		if(parentNode == null) return null;
		
		for (TreeItem<MibRecord> child: parentNode.getChildren()) {
			if (child.getValue().equals(mib)) {
				return child;
			}
		}
		return null;
	}

	public void setOutput(Console console) {
		this.console = console;
	}

	private void log(String s) {
		if (console != null) console.log(s);
	}
	
	private void print(String s) {
		if (console != null) console.print(s);
	}

	public void onNew(MibRecord rec) {
		addRecord(rec);
	}

	@Override
	public void onNotice(String s) {
		log(s);
	}

}