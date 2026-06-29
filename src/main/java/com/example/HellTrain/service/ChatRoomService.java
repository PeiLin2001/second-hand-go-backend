package com.example.HellTrain.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.HellTrain.constant.ReplyMessage;
import com.example.HellTrain.dao.ChatMessageDao;
import com.example.HellTrain.dao.ChatRoomDao;
import com.example.HellTrain.dao.UserDao;
import com.example.HellTrain.entity.ChatRoom;
import com.example.HellTrain.response.ChatRoomRes;
import com.example.HellTrain.response.ChatUnreadRes;
import com.example.HellTrain.vo.ChatRoomVo;

import jakarta.transaction.Transactional;

@Service
public class ChatRoomService {

	@Autowired
	private ChatRoomDao chatRoomDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private ChatMessageDao chatMessageDao;

	// 尋找某一房間或建立
	public ChatRoomRes getOrCreateRoom(int initiatorId, int receiverId) {
		ChatRoom room = chatRoomDao.findChatRoom(initiatorId, receiverId);

		if (room == null) {
			ChatRoom newRoom = new ChatRoom();
			newRoom.setInitiatorId(initiatorId);
			newRoom.setReceiverId(receiverId);

			room = chatRoomDao.save(newRoom);
		}

		ChatRoomRes res = new ChatRoomRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());

		res.setRoomId(room.getRoomId());
		res.setProductId(room.getProductId());

		return res;
	}

	// 取得單一使用者的所有房間
	public ChatRoomRes getAllRoom(int userId) {
		List<ChatRoom> roomList = chatRoomDao.getAllRoom(userId);
		List<ChatRoomVo> chatRoomVo = roomList.stream() //
				.map((ChatRoom room) -> this.convertToVo(room, userId)).toList();

		ChatRoomRes res = new ChatRoomRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
		res.setChatRoomVo(chatRoomVo);
		return res;
	}

	private ChatRoomVo convertToVo(ChatRoom room, int userId) {
		ChatRoomVo vo = new ChatRoomVo();
		vo.setRoomId(room.getRoomId());
		vo.setProductId(room.getProductId());

		// 判斷哪一個是對方ID
		int targetUserId;
		if (room.getInitiatorId() == userId) {
			targetUserId = room.getReceiverId();
		} else {
			targetUserId = room.getInitiatorId();
		}
		vo.setTargetUserId(targetUserId);

		// 取得對方姓名及頭貼
		// 先判斷對方是不是管理員（ID = 0）
		if (targetUserId == 0) {
			vo.setTargetUserName("admin");
			vo.setTargetUserImg(
					"https://res.cloudinary.com/df8kviidh/image/upload/v1780243053/default_avatar_lvgh1a.png");
		} else {
			userDao.findById(targetUserId).ifPresentOrElse(user -> {
				vo.setTargetUserName(user.getUserName());
				vo.setTargetUserImg(user.getImgPath());
			}, () -> {
				// 找不到使用者時的防呆（帳號可能已刪除）
				vo.setTargetUserName("已刪除的使用者");
				vo.setTargetUserImg(
						"https://res.cloudinary.com/df8kviidh/image/upload/v1780243053/default_avatar_lvgh1a.png");
			});
		}

		// 取得最後一則訊息
		chatMessageDao.lastMessage(room.getRoomId()).ifPresent(msg -> {
			vo.setLastMessage(msg.getMessageContent());
		});

		// 取得未讀數量
		int unread = chatMessageDao.countUnreadMsg(room.getRoomId(), targetUserId);
		vo.setUnreadCount(unread);

		return vo;
	}

	// 已讀所有訊息
	@Transactional
	public void readAllRoomMessages(int roomId, int userId) {
		chatRoomDao.findById(roomId).ifPresent(room -> {
			int targetUserId;
			if (room.getInitiatorId() == userId) {
				targetUserId = room.getReceiverId();
			} else {
				targetUserId = room.getInitiatorId();
			}

			chatMessageDao.readAllMessages(roomId, targetUserId);
		});
	}

	// 更新帶入的商品ID
	@Transactional
	public ChatRoomRes updateProductId(Integer productId, int roomId) {
		Optional<ChatRoom> roomOpt = chatRoomDao.findById(roomId);

		if (roomOpt.isPresent()) {
			chatRoomDao.updateProductId(productId, roomId);
			return new ChatRoomRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
		} else {
			return new ChatRoomRes(ReplyMessage.ROOM_IS_NOTFOUND.getCode(), //
					ReplyMessage.ROOM_IS_NOTFOUND.getMessage());
		}
	}
	
	// 取得單一使用者的未讀訊息總數
	public ChatUnreadRes getTotalUnreadCount(int userId) {
	    List<ChatRoom> roomList = chatRoomDao.getAllRoom(userId);

	    int totalUnread = roomList.stream()
	            .mapToInt(room -> {
	                int targetUserId = (room.getInitiatorId() == userId)
	                        ? room.getReceiverId()
	                        : room.getInitiatorId();
	                return chatMessageDao.countUnreadMsg(room.getRoomId(), targetUserId);
	            })
	            .sum();

	    ChatUnreadRes res = new ChatUnreadRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	    res.setTotalUnread(totalUnread);
	    return res;
	}
	
}
