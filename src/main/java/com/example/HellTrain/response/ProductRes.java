package com.example.HellTrain.response;

public class ProductRes extends BasicResponse {
	private int productId;

	public ProductRes() {
		super();
	}

	public ProductRes(int statusCode, String message) {
		super(statusCode, message);
	}

	public ProductRes(int statusCode, String message, int productId) {
		super(statusCode, message);
		this.productId = productId;
	}

	public int getProductId() {
		return productId;
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	
}
