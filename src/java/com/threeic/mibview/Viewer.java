package com.threeic.mibview;
	
import java.io.File;
import java.util.Optional;

import com.threeic.fx.control.AwesomeIcon;
import com.threeic.fx.control.FontButton;
import com.threeic.fx.control.FontText;
import com.threeic.fx.control.TitledToolBar;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Viewer extends Application {
	private static final String aboutTitle = "About MIB View";
	private static final String aboutContent =
		"MIB Viewer version 1.0 beta Copyright (C) 2016 J. Shin\n\n" +
		"This program comes with ABSOLUTELY NO WARRANTY. This is free software, and you are " +
		"welcome to redistribute it under certain conditions. See License.txt for details.\n\n" +
		"This software uses snmp4j from http://www.snmp4j.org.\n\n" +
		"Please email your suggestions to june@3ic.co.kr\n\n";
	
	private Stage stage;
	private Console console = new Console(1000, true);
	private MibBrowser browser = new MibBrowser();
	private MibTreeBuilder treeBuilder;
	
	private TreeView<MibRecord> treeView;
	private TableView<OidPair> table;
	private HostListView listview = new HostListView();

	private File selectedFile;
	private TreeItem<MibRecord> selectedNode;
	private StringProperty hostProperty = new SimpleStringProperty();
	private StringProperty oidProperty = new SimpleStringProperty();
	
	private boolean nameEmpty = true;
	private boolean addrEmpty = true;
	
	private void log(String s) {
		if (console != null) console.log(s);
	}
	
	private void alert(AlertType type, String title, String header, String content) {
		Alert alert = new Alert(type);
		alert.setTitle(title);
		alert.setHeaderText(header);
		alert.setContentText(content);
		alert.showAndWait();
	}
	
	private void scanSnmp() {
		browser.setOutput(console);
		browser.setScanContainer(listview);
		browser.setOidSupport(treeBuilder.oidSupport);
		browser.setContentView(table);
		
		String addr = browser.getLocalAddress();
		if (addr != null) {
			hostProperty.setValue(addr);
			browser.setHost(addr);
		}
		Thread t = new Thread(new Runnable() {
			public void run() {
				browser.scan();
			}
		});
		t.start();
	}
	
	private void buildTree() {
		log("Building tree..");
		treeBuilder = new MibTreeBuilder();
		treeBuilder.setOutput(console);
		treeBuilder.buildTree(treeView);
	}

	private void getRequest(String oid) {
		if (oid == null || oid.isEmpty()) {
			alert(AlertType.WARNING, "SNMP GET", "MIB Object is not selected", null);
			return;
		}
		
		String strReq = oid;
		if (strReq.endsWith("0")) {
			strReq = strReq.substring(0, strReq.lastIndexOf("."));
			log("Request : Get  " + strReq);
		} else if (strReq.endsWith("*")) {
			strReq = strReq.substring(0, strReq.lastIndexOf("*") - 1);
			log("Request : Walk " + strReq);
		} else if (strReq.endsWith(")")) {
			strReq = strReq.substring(0, strReq.indexOf("(") - 1);
			log("Request : Walk " + strReq);
		} else {
			log("Error in request. Please check the OID.");
		}

		final String strReqFin = strReq;
		Thread t = new Thread(new Runnable() {
			public void run() {
				browser.snmpRequestGet(strReqFin);
			}
		});
		t.start();
	}

	private void setRequest() {
		String title = "Set MIB";
		String oid = oidProperty.getValue();
		String oidText = treeBuilder.oidSupport.resolveOidName(oid);
		
		MibRecord node= selectedNode.getValue();
		if(!node.isWritable()) {
			alert(AlertType.ERROR, title, null, 
					String.format("The selected node(%s) is not writable.", oidText));
			return;
		}

		String setValue = "";
		
		String header="Enter new value for " + oidText;
		if (node.getSyntaxID() != MibRecord.VALUE_TYPE_NONE) {
			header = header + "\nValue Type: " + node.syntax.trim() + " [" + node.getSyntaxIDString() + "]";
		} else {
			header = header + "\nValue type " + node.syntax.trim() + " unknown, will use STRING.";
		}

		TextInputDialog dialog = new TextInputDialog();
		dialog.setTitle(title);
		dialog.setHeaderText(header);
		dialog.setContentText(oidText.substring(0, oidText.indexOf(".")));
		Optional<String> result = dialog.showAndWait();
		
		
		if (result.isPresent()){ 
			setValue = result.get();
		}
		//result.ifPresent(value -> setValue = value );		// The Java 8 way to get the response value

		if (setValue != null && node.checkValidValue(setValue)) {
			log("Request : Set  " + oid + "  Value : " + setValue);
			if (browser.processSetRequest(node, oid, setValue) == null) {
				log("Error in processing variable data/set request");
				return;
			}
			log("Set command executed...");
			log("Getting new value of " + oid);
			getRequest(oid);
        }
	}

	private void configHost() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(10));

		TextField hostField = new TextField(hostProperty.getValue());
		TextField portField = new TextField(String.valueOf(browser.getPort()));
		TextField commField = new TextField(browser.getReadCommunity());
		
		grid.add(new Label("HOST"), 0, 0);
		grid.add(hostField, 1, 0);
		grid.add(new Label("PORT"), 0, 1);
		grid.add(portField, 1, 1);
		grid.add(new Label("COMMUNITY"), 0, 2);
		grid.add(commField, 1, 2);

		Dialog<String[]> dialog = new Dialog<>();
		dialog.setTitle("Select Host");
		dialog.initStyle(StageStyle.UTILITY);

		dialog.getDialogPane().setContent(grid);
		Platform.runLater(() -> hostField.requestFocus());
		
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
		Node doneButton = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		doneButton.setDisable(true);

		hostField.textProperty().addListener((observable, oldValue, newValue) -> {
			doneButton.setDisable(newValue.trim().isEmpty());
		});

		// Convert the result to Array
		dialog.setResultConverter(button -> {
		    if (button == ButtonType.APPLY) {
		        return new String[] {hostField.getText(), portField.getText(), commField.getText()};
		    }
		    return null;
		});
		
		Optional<String[]> result = dialog.showAndWait();
		result.ifPresent(array -> {
		    log("Host=" + array[0] + ", PORT=" + array[1] + ", Community=" + array[2]);
		    hostProperty.setValue(array[0]);
		    browser.setHost(hostProperty.getValue());
		    browser.setPort(Integer.parseInt(array[1]));
		    browser.setCommunity(array[2], array[2]);
		});
	}

	private TitledToolBar getToolbar() {
		Button btnHost = new Button();
		btnHost.setGraphic(new FontText(AwesomeIcon.desktop, Color.DARKRED, 12));
		btnHost.textProperty().bind(hostProperty);
		btnHost.setOnAction((ActionEvent e) -> configHost() );

		Button btnGet = new Button("Get");
		btnGet.setOnAction((ActionEvent e) -> {
			getRequest(oidProperty.getValue());
        });
		Button btnSet = new Button("Set");
		btnSet.setOnAction((ActionEvent e) -> {
			setRequest();
        });
		Button btnStop = new Button("Stop");
		btnStop.setOnAction((ActionEvent e) -> {
			browser.destroySession();
			log(" ******** Cancelled *********");
        });
		Button btnClear = new Button("Clear");
		btnClear.setOnAction((ActionEvent e) -> {
			console.clear();
        });
		
		FontButton btnAbout = new FontButton(AwesomeIcon.info_circle, Color.DARKRED, 13);
		btnAbout.setOnAction((ActionEvent e) -> {
			alert(AlertType.INFORMATION, aboutTitle, null, aboutContent);
        });

		TitledToolBar toolbar = new TitledToolBar();
    	toolbar.addLeftItems(btnHost);
    	toolbar.addRightItems(btnGet, btnSet, btnStop, btnClear, btnAbout);
    	
    	return toolbar;
	}

	private VBox getContentPanel() {
		TitledToolBar toobalr = getToolbar();
		table();
		VBox.setVgrow(table, Priority.ALWAYS);
		return new VBox(toobalr, table);
	}
	
	@SuppressWarnings("unchecked")
	private void table() {
		table = new TableView<>();
		
		TableColumn<OidPair, String> oidCol = new TableColumn<>("OID");
		oidCol.setCellValueFactory(new PropertyValueFactory<>("oid"));
		oidCol.prefWidthProperty().bind(table.widthProperty().multiply(0.2));

        TableColumn<OidPair, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.prefWidthProperty().bind(table.widthProperty().multiply(0.8));

        table.getColumns().addAll(oidCol, valueCol);
	}
	
	private void addHostItem() {
		
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(10));

		TextField nameField = new TextField();
		TextField addrField = new TextField();
		
		grid.add(new Label("NAME"), 0, 0);
		grid.add(nameField, 1, 0);
		grid.add(new Label("HOST"), 0, 1);
		grid.add(addrField, 1, 1);

		Dialog<String[]> dialog = new Dialog<>();
		dialog.setTitle("Add Host");
		dialog.initStyle(StageStyle.UTILITY);

		dialog.getDialogPane().setContent(grid);
		Platform.runLater(() -> nameField.requestFocus());
		
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
		Node doneButton = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		doneButton.setDisable(true);

		nameField.textProperty().addListener((observable, oldValue, newValue) -> {
			nameEmpty = newValue.trim().isEmpty();
			doneButton.setDisable(nameEmpty || addrEmpty);
		});
		
		addrField.textProperty().addListener((observable, oldValue, newValue) -> {
			addrEmpty = newValue.trim().isEmpty();
			doneButton.setDisable(nameEmpty || addrEmpty);
		});

		// Convert the result to Array
		dialog.setResultConverter(button -> {
		    if (button == ButtonType.APPLY) {
		        return new String[] {nameField.getText(), addrField.getText()};
		    }
		    return null;
		});
		
		Optional<String[]> result = dialog.showAndWait();
		result.ifPresent(array -> {
		    log("NMAE=" + array[0] + ", HOST=" + array[1]);
		    listview.add(array[0], array[1]);
		});
	}
	
	private ListCell<String> setHostList() {
		ListCell<String> cell = new ListCell<>();

		ContextMenu contextMenu = new ContextMenu();
		MenuItem editItem = new MenuItem();
		editItem.textProperty().bind(Bindings.format("Edit", cell.itemProperty()));
		editItem.setOnAction(event -> {
			//String item = cell.getItem();
			// code to edit item...
		});
		MenuItem addItem = new MenuItem();
		addItem.textProperty().bind(Bindings.format("Add", cell.itemProperty()));
		addItem.setOnAction(event -> {
			//String item = cell.getItem();
			addHostItem();
		});
		MenuItem deleteItem = new MenuItem();
		deleteItem.textProperty().bind(Bindings.format("Delete \"%s\"", cell.itemProperty()));
		deleteItem.setOnAction(event -> listview.getItems().remove(cell.getItem()));
		contextMenu.getItems().addAll(editItem, addItem, deleteItem);

		cell.textProperty().bind(cell.itemProperty());
		cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
			if (isNowEmpty) {
				cell.setContextMenu(null);
			} else {
				cell.setContextMenu(contextMenu);
			}
		});
		return cell ;
	}
	
	private SplitPane getTreePanel() {
		Label oidLabel = new Label();
		oidLabel.setMaxWidth(Double.MAX_VALUE);
		oidLabel.textProperty().bind(oidProperty);
		
		FontButton btnOid = new FontButton(AwesomeIcon.question_circle, Color.DARKRED, 12);
		btnOid.setDisable(true);
		btnOid.setOnAction((ActionEvent e) -> {
			TreeItem<MibRecord> node = treeView.getSelectionModel().getSelectedItem();
			if(node == null) return;
			alert(AlertType.INFORMATION, oidLabel.getText(), null, node.getValue().getCompleteString());
		});
		
		Button btnLoad = new FontButton(AwesomeIcon.plus_square, Color.DARKRED, 12);
		btnLoad.setOnAction( (ActionEvent e) -> loadMibFile() );
		
		HBox oidBar = new HBox(btnLoad, new Separator(Orientation.VERTICAL), oidLabel, btnOid);
		HBox.setHgrow(oidLabel, Priority.ALWAYS);
		oidBar.setAlignment(Pos.CENTER_LEFT);
		oidBar.setSpacing(6);
		oidBar.setPadding(new Insets(6));
		
		treeView = new TreeView<>();
		treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		treeView.getSelectionModel().selectedItemProperty()
		.addListener((ObservableValue<? extends TreeItem<MibRecord>> observable, TreeItem<MibRecord> oldValue, TreeItem<MibRecord> newValue) -> {
			if (newValue != null) {
				selectedNode = newValue;
				btnOid.setDisable(false);
				oidProperty.setValue(treeBuilder.oidSupport.getNodeOid(newValue));
			}
		});
		
		VBox oidBox = new VBox(oidBar, treeView);
		VBox.setVgrow(treeView, Priority.ALWAYS);
		VBox.setMargin(oidBox, new Insets(6,10,6,10));
		
		listview.getSelectionModel().selectedItemProperty().addListener(
			(ObservableValue<? extends String> ob, String ov, String nv) -> {
			System.out.println("selected > "+nv);
			String host = listview.getAddress(nv);
			hostProperty.setValue(host);
			browser.setHost(host);
		});
		listview.setCellFactory(lv -> setHostList() );

		SplitPane sp = new SplitPane();
		sp.setOrientation(Orientation.VERTICAL);
		sp.setDividerPosition(0, 0.7);
		
		sp.getItems().addAll(oidBox, listview);

		return sp;
	}
	
	private void loadMibFile() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open MIB File");
		if (selectedFile != null) fileChooser.setInitialDirectory(selectedFile.getParentFile());
		fileChooser.getExtensionFilters().addAll(
			new FileChooser.ExtensionFilter("MIB File", "*.mib"),
			new FileChooser.ExtensionFilter("Text File", "*.txt"),
			new FileChooser.ExtensionFilter("All Files", "*.*")
		);
		
		selectedFile = fileChooser.showOpenDialog(stage);
		if (selectedFile != null) {
			log(selectedFile.getName()+" selected");
			treeBuilder.loadNewFile(selectedFile.getAbsolutePath());
		}
	}
	
	@Override
	public void start(Stage stage) {
		this.stage = stage;
		
		SplitPane root = new SplitPane();
		root.setOrientation(Orientation.VERTICAL);
		root.setDividerPosition(0, 0.9);

		SplitPane content = new SplitPane();
		content.setOrientation(Orientation.HORIZONTAL);
		content.setDividerPosition(0, 0.3);

		try {
			Scene scene = new Scene(root, 1000, 600);
			scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
			stage.getIcons().add(new Image("/images/MIBViewer.png"));
			stage.setTitle("MIB Viewer");
			
			stage.setScene(scene);
			stage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		content.getItems().addAll(getTreePanel(), getContentPanel());
		root.getItems().addAll(content, console);
		
		buildTree();
		scanSnmp();
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
