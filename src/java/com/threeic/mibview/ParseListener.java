package com.threeic.mibview;

public interface ParseListener {
	void onNew(MibRecord rec);
	void onNotice(String s);
}
