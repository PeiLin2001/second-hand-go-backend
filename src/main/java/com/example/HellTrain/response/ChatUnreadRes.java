package com.example.HellTrain.response;

public class ChatUnreadRes extends BasicResponse {

	private int totalUnread;

	public ChatUnreadRes() {
		super();
	}

	public ChatUnreadRes(int statusCode, String message) {
		super(statusCode, message);
	}

	public ChatUnreadRes(int statusCode, String message, int totalUnread) {
		super(statusCode, message);
		this.totalUnread = totalUnread;
	}

	public int getTotalUnread() {
		return totalUnread;
	}

	public void setTotalUnread(int totalUnread) {
		this.totalUnread = totalUnread;
	}

}
