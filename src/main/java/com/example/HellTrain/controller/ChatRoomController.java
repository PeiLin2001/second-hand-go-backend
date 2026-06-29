package com.example.HellTrain.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.HellTrain.response.ChatRoomRes;
import com.example.HellTrain.response.ChatUnreadRes;
import com.example.HellTrain.service.ChatRoomService;

@RestController
@RequestMapping("/chat")
public class ChatRoomController {
	@Autowired
	private ChatRoomService chatRoomService;

	@PostMapping("/get-or-create")
	public ChatRoomRes getOrCreateRoom(@RequestBody Map<String, Integer> ChatRoomReq) {
		int initiatorId = ChatRoomReq.get("initiatorId");
		int receiverId = ChatRoomReq.get("receiverId");

		return chatRoomService.getOrCreateRoom(initiatorId, receiverId);
	}

	@GetMapping(value = "/get-all-room")
	public ChatRoomRes getAllRoom(@RequestParam("userId") int userId) {
		return chatRoomService.getAllRoom(userId);
	}

	@PostMapping("/read-room")
	public ResponseEntity<?> readAllRoomMessages(@RequestParam("roomId") int roomId,
			@RequestParam("userId") int userId) {
		chatRoomService.readAllRoomMessages(roomId, userId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/update-productId")
	public ChatRoomRes updateProductId(@RequestParam("productId") Integer productId,
			@RequestParam("roomId") int roomId) {
		return chatRoomService.updateProductId(productId, roomId);
	}
	
	@GetMapping(value = "/total-unread-count")
	public ChatUnreadRes getTotalUnreadCount(@RequestParam("userId") int userId) {
		return chatRoomService.getTotalUnreadCount(userId);
	}
}
