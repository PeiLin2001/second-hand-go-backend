package com.example.HellTrain.ChatConfig;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.HellTrain.dao.ChatMessageDao;
import com.example.HellTrain.dao.ChatRoomDao;
import com.example.HellTrain.dao.UserDao;

import com.example.HellTrain.entity.ChatMessage;
import com.example.HellTrain.entity.ChatRoom;
import com.example.HellTrain.entity.User;
import com.example.HellTrain.vo.ChatMessageVo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {
	@Autowired
	private SocketIOServer server;
	@Autowired
	private UserDao userDao;
	@Autowired
	private ChatMessageDao chatMessageDao;
	@Autowired
	private ChatRoomDao chatRoomDao;

	// 紀錄 userId 對應到哪個 socket 連線
	private final Map<Integer, com.corundumstudio.socketio.SocketIOClient> userSocketMap = new ConcurrentHashMap<>();

	// 當 Spring Boot 啟動完成後，自動啟動 Socket 伺服器
	@PostConstruct
	public void startServer() {

		// 使用者上線時註冊自己的 userId
		server.addEventListener("register_user", Map.class, (client, data, ackSender) -> {
			Integer userId = ((Number) data.get("userId")).intValue();
			userSocketMap.put(userId, client);
			client.set("userId", userId);
			System.out.println("📍 使用者 [" + userId + "] 已註冊連線，可接收即時通知");
		});

		// 監聽前端發來的進房間請求
		server.addEventListener("join_room", Map.class, (client, data, ackSender) -> {
			// 1. 從前端傳來的資料中，拿到資料庫的房間流水號 (例如 1)
			String roomId = String.valueOf(data.get("roomId"));
			String userName = (String) data.get("userName");

			// 2. 讓這個連線（Client）加入這個流水號標籤的房間
			// 如果這個房間本來不存在，Netty-Socketio 會自動幫你建立！
			client.joinRoom(roomId);

			System.out.println("🏠 使用者 [" + userName + "] 順利進入了房間流水號: " + roomId);
		});

		// 監聽前端送過來的 'chatevent' 事件
		server.addEventListener("chatevent", Map.class, (client, data, ackSender) -> {
			System.out.println("收到前端訊息: " + data);

			try {
				String roomId = String.valueOf(data.get("roomId")); // 確保前端發訊息時有帶房間號
				Integer senderId = ((Number) data.get("senderId")).intValue(); // 預防前端傳JSON解析成Double報錯
				String content = (String) data.get("messageContent");

				// 先存訊息進資料庫，拿到含有流水號的 savedEntity
				ChatMessage messageEntity = new ChatMessage();
				messageEntity.setRoomId(Integer.parseInt(roomId));
				messageEntity.setSenderId(senderId);
				messageEntity.setMessageContent(content);
				// 如果房間人數=2，預設已讀
				boolean bothOnline = getRoomUserCount(Integer.parseInt(roomId)) == 2;
                messageEntity.setRead(bothOnline);

				// 存檔並收回完整的實體物件
				ChatMessage savedEntity = chatMessageDao.save(messageEntity);

				// 去 user 表查出名字與大頭貼網址
				User user = userDao.findById(senderId).orElse(null);
				String senderName = (user != null) ? user.getUserName() : "admin";
				String senderImg = (user != null) ? user.getImgPath()
						: "https://res.cloudinary.com/df8kviidh/image/upload/v1780243053/default_avatar_lvgh1a.png";

				ChatMessageVo vo = new ChatMessageVo();
				vo.setMessageId(savedEntity.getMessageId()); // 訊息流水號
				vo.setRoomId(savedEntity.getRoomId()); // 房間流水號

				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

				if (savedEntity.getCreatedAt() != null) {
					vo.setCreatedAt(savedEntity.getCreatedAt().format(formatter));
				} else {
					vo.setCreatedAt(java.time.LocalDateTime.now().format(formatter));
				}

				vo.setMessageContent(savedEntity.getMessageContent()); // 訊息內容
				vo.setRead(savedEntity.isRead()); // 是否已讀
				vo.setSenderId(savedEntity.getSenderId()); // 發送者 ID
				vo.setSenderName(senderName); // 發送者名字
				vo.setSenderImg(senderImg); // 發送者大頭貼

				System.out.println("💬 順利裝箱！房間 [" + roomId + "] -> " + senderName + ": " + content);

				// 廣播
				server.getRoomOperations(roomId).sendEvent("chatevent", vo);
				
				  // 如果對方不在房間裡（未讀），點對點通知他更新未讀數
                if (!bothOnline) {
                    notifyUnreadUpdate(Integer.parseInt(roomId), senderId);
                }

			} catch (Exception e) {
				System.err.println("❌ Socket 收發訊息發生慘劇: " + e.getMessage());
				e.printStackTrace();
			}
		});

		// 監聽使用者離開聊天室
		server.addEventListener("leave_room", Map.class, (client, data, ackSender) -> {
			if (data != null && data.containsKey("roomId")) {
				String roomId = String.valueOf(data.get("roomId"));
				String userName = String.valueOf(data.get("userName"));

				client.leaveRoom(roomId);
				System.out.println("【退房通知】使用者 [" + userName + "] 已主動離開房間: " + roomId);
			}
		});

		// 監聽斷線事件
		server.addDisconnectListener(client -> {
			  Object userIdObj = client.get("userId");
	            if (userIdObj != null) {
	                userSocketMap.remove((Integer) userIdObj);
	                System.out.println("使用者已斷線並移除註冊: " + userIdObj);
	            } else {
	                System.out.println("使用者已斷線: " + client.getSessionId());
	            }
		});

		server.start();
		System.out.println("==== Socket.io 伺服器已在 Port 9092 啟動 ====");
	}

	// 當 Spring Boot 關閉時，關閉 Socket 伺服器
	@PreDestroy
	public void stopServer() {
		if (server != null) {
			server.stop();
		}
	}

	private int getRoomUserCount(int roomId) {
		// 因為 rooms 在 socket 裡面通常是字串，我們把它轉成 String
		String roomName = String.valueOf(roomId);

		if (server.getRoomOperations(roomName) != null) {
			return server.getRoomOperations(roomName).getClients().size();
		}
		return 0;
	}
	
	 // 新增：找出房間另一方的 userId，並推送「未讀數變動」事件給他
    private void notifyUnreadUpdate(int roomId, int senderId) {
        ChatRoom room = chatRoomDao.findById(roomId).orElse(null);
        if (room == null) return;

        int targetUserId = (room.getInitiatorId() == senderId)
                ? room.getReceiverId()
                : room.getInitiatorId();

        com.corundumstudio.socketio.SocketIOClient targetClient = userSocketMap.get(targetUserId);
        if (targetClient != null) {
            int unreadCount = chatMessageDao.countUnreadMsg(roomId, targetUserId);
            targetClient.sendEvent("unreadCountUpdate", Map.of(
                "roomId", roomId,
                "unreadCount", unreadCount
            ));
        }
    }
}
