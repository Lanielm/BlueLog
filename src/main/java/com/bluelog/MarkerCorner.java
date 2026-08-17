package com.bluelog;

public enum MarkerCorner {
	TOP_LEFT("Top left"),
	TOP_RIGHT("Top right"),
	BOTTOM_LEFT("Bottom left"),
	BOTTOM_RIGHT("Bottom right");

	private final String name;

	MarkerCorner(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
