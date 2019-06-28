package com.threeic.mibview;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class Console extends TextArea {
	int max = 100;
	
	public Console(int max, boolean wrap) {
		this.max = max;
		this.setEditable(false);
		this.setWrapText(wrap);
		//getStyleClass().add("console");
	}
/*
	private void count() {
		getText().length();
	}
*/
	public void log(String s) {
		println(s);
	}

	public void println(String s) {
		if (s != null) print(System.lineSeparator() + s);
	}
	
	public void print(String s) {
		if (s != null) runSafe(() -> appendText(s));
	}
	
	public void clear() {
		runSafe( () -> this.setText("") );
	}
	
	public void runSafe(final Runnable runnable) {
        if (runnable == null) return;
        //Objects.requireNonNull(runnable, "runnable");
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}