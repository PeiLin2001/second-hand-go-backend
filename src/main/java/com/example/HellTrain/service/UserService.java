package com.example.HellTrain.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.HellTrain.component.VerificationCodeStore;
import com.example.HellTrain.config.CloudinaryService;
import com.example.HellTrain.constant.ReplyMessage;
import com.example.HellTrain.constant.UserStatus;
import com.example.HellTrain.dao.ManagerDao;
import com.example.HellTrain.dao.UserDao;
import com.example.HellTrain.entity.Manager;
import com.example.HellTrain.entity.User;
import com.example.HellTrain.request.UserReq;
import com.example.HellTrain.response.BasicResponse;
import com.example.HellTrain.response.LogInRes;
import com.example.HellTrain.response.UserRes;
import com.example.HellTrain.vo.SetInfoVo;
import com.example.HellTrain.vo.UserVo;
import com.example.HellTrain.vo.VerifyVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

//@EnableScheduling(正式專案要放在main中)
@EnableScheduling // 讓此類別下的scheduled(排成)方法生效
@Service
public class UserService {

	private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	@Value("${default.avatar}")
	private String defaultAvatar;

	private final String pwdPattern = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*\\-_]{8,}$";// 密碼至少8碼
	private final String phonepattern = "09-\\d{8}";

	private ObjectMapper mapper = new ObjectMapper();
	
	private List<String> parseJsonList(String json) {
	    if (json == null || json.isBlank()) return new ArrayList<>();
	    try {
	        return mapper.readValue(json, new TypeReference<List<String>>() {});
	    } catch (Exception e) {
	        return new ArrayList<>();
	    }
	}

	private UserVo toVo(User user) {//
	    UserVo vo = new UserVo();//
	    vo.setUserId(user.getUserId());//
	    vo.setUserEmail(user.getUserEmail());//
	    vo.setUserName(user.getUserName());//
	    vo.setPhone(user.getPhone());//
	    vo.setLocation(parseJsonList(user.getLocation()));  // 轉 List
	    vo.setSchool(user.getSchool());//
	    vo.setDepartment(user.getDepartment());//
	    vo.setStatus(user.getStatus());//
	    vo.setVerified(user.getVerified());//
	    vo.setGoodLevel(user.getGoodLevel());//
	    vo.setMsg(user.getMsg());//
	    vo.setImgPath(user.getImgPath());//
	    vo.setNote(user.getNote());//
	    vo.setCreateDate(user.getCreateDate());//
	    return vo;//
	}

	private BasicResponse checkAccount(String email, String password) {
		if (!email.endsWith(".edu.tw")) {
			return new BasicResponse(ReplyMessage.EMAIL_ISNOT_SCHOOL.getCode(), //
					ReplyMessage.EMAIL_ISNOT_SCHOOL.getMessage());
		}

		if (!password.matches(pwdPattern)) {
			return new BasicResponse(ReplyMessage.PARAM_PASSWORD_ERROR.getCode(), //
					ReplyMessage.PARAM_PASSWORD_ERROR.getMessage());
		}
		return null;
	}

	private BasicResponse checkphone(String phone) {
		if (!phone.matches(phonepattern)) {
			return new BasicResponse(ReplyMessage.PARAM_PHONE_ERROR.getCode(), //
					ReplyMessage.PARAM_PHONE_ERROR.getMessage());
		}
		return null;
	}

	@Autowired
	private UserDao userdao;
	//注入驗證碼相關文件
	@Autowired
	private VerificationCodeStore codeStore;
	//注入上傳至雲端(cloudinaryService相關文件)
	@Autowired
	private CloudinaryService cloudinaryService;
	@Autowired
	private JavaMailSender mailSender;
	@Autowired
	private ManagerDao managerdao;

	/* insert */
	public BasicResponse addUser(UserReq req) {

	    /* 資料檢查 */
	    if (!StringUtils.hasText(req.getSchool())) {
	        return new BasicResponse(ReplyMessage.SCHOOL_IS_NULL.getCode(),
	                ReplyMessage.SCHOOL_IS_NULL.getMessage());
	    }

	    // 檢查地區
	    if (req.getLocation() == null || !StringUtils.hasText(req.getLocation())) {
	        return new BasicResponse(ReplyMessage.LOCATION_IS_NULL.getCode(),
	        		ReplyMessage.LOCATION_IS_NULL.getMessage());
	    }
	    // 檢查姓名格式
	    if (req.getName().length()>20) {
	        return new BasicResponse(ReplyMessage.PARAM_NAME_ERROR.getCode(),
	                ReplyMessage.PARAM_NAME_ERROR.getMessage());
	    }

	    // 檢查email及password
	    if (checkAccount(req.getEmail(), req.getPassword()) != null) {
	        return checkAccount(req.getEmail(), req.getPassword());
	    }

	    // 檢查電話號碼格式
	    if (req.getPhone() != null && checkphone(req.getPhone()) != null) {
	        return checkphone(req.getPhone());
	    }

	    // 確認email是否被註冊過
	    if (userdao.selectCount(req.getEmail()) == 1) {
	        return new BasicResponse(ReplyMessage.EMAIL_HAS_FOUND.getCode(),
	                ReplyMessage.EMAIL_HAS_FOUND.getMessage());
	    }

	    LocalDateTime createDate = LocalDateTime.now();

	    /* 建立帳號並傳入加密後的密碼 */
	    List<String> locationList = new ArrayList<>();
	    locationList.add(req.getLocation());
//	    locationJson = mapper.writeValueAsString(locationList);  // 變成 ["桃園縣"]
	    String locationJson;
	    try {
	        locationJson = mapper.writeValueAsString(locationList);  // List<String> 直接序列化
	        userdao.addUser(req.getEmail(), req.getName(), encoder.encode(req.getPassword()), req.getPhone(),
	                locationJson, req.getSchool(), UserStatus.Unverified.getMessage(), createDate);
	    } catch (JsonProcessingException e) {
	        return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),
	                ReplyMessage.PLEASE_TRY_LATE.getMessage());
	    }

	    /* 產生驗證碼並寄信 */
	    String code = codeStore.generate(req.getEmail());
	    sendVerificationEmail(req.getEmail(), code);

	    return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	}
	
	/* 寄送驗證信 */
	private void sendVerificationEmail(String email, String code) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			helper.setTo(email);
			helper.setSubject("【HellTrain】Email 驗證碼");

			// 當有正式網站時localhost:4200會是網址
			String verifyUrl = "http://localhost:4200/login?mode=register&step=2&email=" + email;

			String content = """
					<div style="font-family: Arial, sans-serif; padding: 20px;">
					    <h2>二手GO 帳號驗證</h2>
					    <p>您的驗證碼為：<strong>%s</strong></p>
					    <p>15分鐘內有效，請勿告知他人。</p>
					    <a href="%s" style="background-color: #FB831D; color: white;
					       padding: 10px 20px; text-decoration: none; border-radius: 5px;">
					        前往驗證
					    </a>
					</div>
					""".formatted(code, verifyUrl);

			helper.setText(content, true); // true = HTML 格式
			mailSender.send(message);

		} catch (MessagingException e) {
			// 寄信失敗不影響主流程
			System.out.println("寄信失敗：" + e.getMessage());
		}
	}

	public BasicResponse verifyAndRegister(VerifyVO vo) {
		// 比對驗證碼
		boolean valid = codeStore.verify(vo.getEmail(), vo.getCode());
		if (!valid) {
			return new BasicResponse(ReplyMessage.INVALID_VERIFICATION_CODE.getCode(), //
					ReplyMessage.INVALID_VERIFICATION_CODE.getMessage());
		}

		// 驗證成功 → 更新 status
		userdao.updateverified(vo.getEmail(), LocalDate.now(), UserStatus.Normal.getMessage());

		// 銷毀驗證碼
		codeStore.remove(vo.getEmail());

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	public BasicResponse resendCode(String email) {
		// 確認這個 email 有註冊過
		if (userdao.selectCount(email) == 0) {
			return new BasicResponse(ReplyMessage.EMAIL_NOT_FOUND.getCode(), //
					ReplyMessage.EMAIL_NOT_FOUND.getMessage());
		}

		// 確認目前狀態是未驗證（已驗證的不需要重寄）
		User user = userdao.getAccount(email);
		if (user.getVerified() != null) {
			return new BasicResponse(ReplyMessage.ACCOUNT_IS_VERIFICATION.getCode(), //
					ReplyMessage.ACCOUNT_IS_VERIFICATION.getMessage());
		}

		// 產生新驗證碼並寄信（舊的會被覆蓋）
		String code = codeStore.generate(email);
		sendVerificationEmail(email, code);
		return new BasicResponse(ReplyMessage.VERIFICATION_CODE_IS_SEND.getCode(), //
				ReplyMessage.VERIFICATION_CODE_IS_SEND.getMessage());
	}

	// 排程提醒驗證之信件內容
	private void sendReminderEmail(String email, String code) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			helper.setTo(email);
			helper.setSubject("【HellTrain】帳號驗證提醒");

			// 當有正式網站時localhost:4200會是網址
			String verifyUrl = "http://localhost:4200/login?mode=register&step=2&email=" + email;

			String content = """
					<div style="font-family: Arial, sans-serif; padding: 20px;">
					    <h2>二手GO 帳號驗證提醒！！！</h2>
					    <p>您的帳號尚未完成驗證，將在 <strong>24小時後</strong> 被刪除。</p>
					    <p>您的驗證碼為：<strong>%s</strong></p>
					    <p>15分鐘內有效，請勿告知他人。</p>
					    <a href="%s" style="background-color: #FB831D; color: white;
					       padding: 10px 20px; text-decoration: none; border-radius: 5px;">
					        前往驗證
					    </a>
					</div>
					""".formatted(code, verifyUrl);

			helper.setText(content, true);
			mailSender.send(message);

		} catch (MessagingException e) {
			System.out.println("寄信失敗：" + e.getMessage());
		}
	}

	// 排程自動發送，0:00送出通知
	@Scheduled(cron = "0 0 0 * * *")
	public void sendReminderEmail() {
		LocalDateTime deadline = LocalDateTime.now().minusDays(6);
		List<User> users = userdao.findUnverifiedBefore(deadline);

		for (User user : users) {
			String code = codeStore.generate(user.getUserEmail());
			sendReminderEmail(user.getUserEmail(), code); // ← 用新的方法
		}
	}

	// 逾期未驗證刪除，1:00執行刪除
	@Scheduled(cron = "0 0 1 * * *") // 凌晨 1 點（提醒信寄完後才刪）
	public void cleanUnverifiedAccounts() {
		LocalDateTime deadline = LocalDateTime.now().minusDays(7);
		userdao.deleteUnverified(deadline);
	}

	/* 登入，默認大部分為一般使用者，故先以使用者資料庫進行比對 */
	public LogInRes login(String email, String password) {
		BasicResponse check = checkAccount(email, password);
		String role = "";// 帳號身分

		/* 查詢帳號格式 */
		if (check != null) {
			return new LogInRes(check.getStatusCode(), //
					check.getMessage());
		}

		// 比對使用者資料庫，找出email相符的資料
		User user = userdao.getAccount(email);
		
		if (user != null) {
			

			// 3. 密碼錯
			if (!encoder.matches(password, user.getPassword())) {
				return new LogInRes(ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getCode(), //
						ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getMessage());
			}

			role = "user";

			// 4. 停權
			if (user.getStatus().equals(UserStatus.Suspension.getMessage())) {
				return new LogInRes(ReplyMessage.ACCOUNT_IS_BANNED.getCode(), //
						ReplyMessage.ACCOUNT_IS_BANNED.getMessage());
			}

			// 5. 未驗證
			if (user.getVerified() == null) {
				String code = codeStore.generate(user.getUserEmail());
				sendVerificationEmail(user.getUserEmail(), code);
				return new LogInRes(ReplyMessage.PLEASE_VERIFY.getCode(), //
						ReplyMessage.PLEASE_VERIFY.getMessage());
			}

			// 6. 驗證過期
			if (user.getVerified().plusYears(1).isBefore(LocalDate.now())) {
				String code = codeStore.generate(user.getUserEmail());
				sendVerificationEmail(user.getUserEmail(), code);
				return new LogInRes(ReplyMessage.VERIFICATION_IS_INVALID.getCode(), //
						ReplyMessage.VERIFICATION_IS_INVALID.getMessage());
			}

			return new LogInRes(ReplyMessage.SUCCESS.getCode(), //
					ReplyMessage.SUCCESS.getMessage(), role, toVo(user));
		}

		// 比對管理員資料庫
		Manager manager = managerdao.getByEmail(email);
		if (manager != null && encoder.matches(password, manager.getPassword())) {
			role = "manager";
			return new LogInRes(ReplyMessage.SUCCESS.getCode(), //
					ReplyMessage.SUCCESS.getMessage(), role, manager);
		}
		
		if(userdao.getAccount(email)==null && managerdao.getByEmail(email)==null)
		{
			return new LogInRes(ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getCode(), //
					ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getMessage());
		}

		return new LogInRes(ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getCode(), //
				ReplyMessage.EMAIL_OR_PASSWORD_ERROR.getMessage());
	}

	/* 資料修改 */
	public BasicResponse setInfo(int id, SetInfoVo vo) {
		
		User user = userdao.getById(id);
		if (user == null) {
			return new BasicResponse(ReplyMessage.NO_DATA_FOUND.getCode(), //
					ReplyMessage.NO_DATA_FOUND.getMessage());
		}
		// 檢查姓名格式
		if (vo.getName().length()>20) {
			return new BasicResponse(ReplyMessage.PARAM_NAME_ERROR.getCode(), //
					ReplyMessage.PARAM_NAME_ERROR.getMessage());
		}

		// 檢查地區
		if (vo.getLocation() == null || vo.getLocation().isEmpty()) {
			return new BasicResponse(ReplyMessage.LOCATION_IS_NULL.getCode(), //
					ReplyMessage.LOCATION_IS_NULL.getMessage());
		}

		if (!StringUtils.hasText(vo.getSchool())) {
			return new BasicResponse(ReplyMessage.SCHOOL_IS_NULL.getCode(), //
					ReplyMessage.SCHOOL_IS_NULL.getMessage());
		}

		if (!vo.getPhone().isEmpty() && checkphone(vo.getPhone()) != null) {
			return checkphone(vo.getPhone());
		}

		if (vo.getMsg() != null && vo.getMsg().length() > 200) {
			return new BasicResponse(ReplyMessage.MESSAGE_TOO_LONG.getCode(), //
					ReplyMessage.MESSAGE_TOO_LONG.getMessage());
		}

		String imgPath = null;

		if (vo.isDeleteImg()) {
			imgPath = defaultAvatar;

		} else if (StringUtils.hasText(vo.getImg())) {
			// 有傳 base64 → 上傳到 Cloudinary
			try {
				imgPath = cloudinaryService.uploadBase64(vo.getImg());
			} catch (Exception e) {
				return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),
						ReplyMessage.PLEASE_TRY_LATE.getMessage());
			}

		} else {
			// 沒傳圖片 → 保留原本的
			imgPath = user.getImgPath();
		}

		// 存入前轉換，只有單一選項所以不需要Map
		String locationStr;
		try {
			locationStr = mapper.writeValueAsString(vo.getLocation());
			userdao.setInfo(user.getUserEmail(), vo.getName(), imgPath, //
					locationStr, vo.getSchool(), vo.getDepartment(), vo.getPhone(), //
					vo.getMsg());
		} catch (JsonProcessingException e) {
			return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),//
					ReplyMessage.PLEASE_TRY_LATE.getMessage());
		}

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	// 更改密碼
	public BasicResponse changePassword(int id, String nowPwd, String newPwd) {

		User user = userdao.getById(id);
		if (user == null) {
			return new BasicResponse(ReplyMessage.NO_DATA_FOUND.getCode(), //
					ReplyMessage.NO_DATA_FOUND.getMessage());
		}
		
		if(nowPwd.isBlank() || newPwd.isBlank())
		{
			return new BasicResponse(ReplyMessage.PARAM_PASSWORD_ERROR.getCode(), //
					ReplyMessage.PARAM_PASSWORD_ERROR.getMessage());
		}

		if (!nowPwd.matches(pwdPattern) || !encoder.matches(nowPwd, user.getPassword())) {
			return new BasicResponse(ReplyMessage.PARAM_PASSWORD_ERROR.getCode(), //
					ReplyMessage.PARAM_PASSWORD_ERROR.getMessage());
		}

		if (!newPwd.matches(pwdPattern)) {
			return new BasicResponse(ReplyMessage.PARAM_PASSWORD_ERROR.getCode(), //
					ReplyMessage.PARAM_PASSWORD_ERROR.getMessage());
		}

		if (newPwd.equals(nowPwd)) {
			return new BasicResponse(ReplyMessage.PARAM_PASSWORD_ERROR.getCode(), //
					ReplyMessage.PARAM_PASSWORD_ERROR.getMessage());
		}

		// 寫寫dao
		userdao.updatePad(user.getUserEmail(), encoder.encode(newPwd));

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	// 抓取所有使用者
	public UserRes getAllUser() {

		List<User> userList = userdao.getAllUser();
	    List<UserVo> voList = userList.stream().map(this::toVo)
	            .collect(Collectors.toList());
		return new UserRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), voList);
	}

	// 取得個別帳號詳情
	public UserRes getUserData(int userId) {
		User user = userdao.getById(userId);
		if (user == null) {
			return new UserRes(ReplyMessage.USER_ID_ERR.getCode(), //
					ReplyMessage.USER_ID_ERR.getMessage());
		}

		return new UserRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), toVo(user));
	}
	
	// 取得各校成員
	public UserRes getUserDataBySchool(String school) {
		List<User> users = userdao.getBySchool(school);
		if (users == null || users.isEmpty()) {
			return new UserRes(ReplyMessage.CLASSMATE_NO_FOUND.getCode(), //
					ReplyMessage.CLASSMATE_NO_FOUND.getMessage());
		}
		
		List<UserVo> voList = users.stream()
	            .map(this::toVo)
	            .collect(Collectors.toList());
		
		return new UserRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), voList);
	}
	
	// 取得地區成員
	public UserRes getUserDataByLocation(String location) {
		List<User> users = userdao.getByLocation(location);
		if (users == null || users.isEmpty()) {
			return new UserRes(ReplyMessage.CLASSMATE_NO_FOUND.getCode(), //
					ReplyMessage.CLASSMATE_NO_FOUND.getMessage());
		}
		
		List<UserVo> voList = users.stream()
	            .map(this::toVo)
	            .collect(Collectors.toList());
		
		return new UserRes(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage(), voList);
	}

	// 帳號狀態變更(手動)
	public BasicResponse changeStatus(int usesrId) {

		String status;
		User user = userdao.getById(usesrId);
		if (user == null) {
			return new BasicResponse(ReplyMessage.NO_DATA_FOUND.getCode(), //
					ReplyMessage.NO_DATA_FOUND.getMessage());
		}

		if (user.getStatus().equals(UserStatus.Normal.getMessage())) {
//			status = UserStatus.Suspension.getMessage();
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), //
					ReplyMessage.NO_PERMISSIONS.getMessage());
		} else {
			status = UserStatus.Normal.getMessage();
			userdao.changeStatus(usesrId, status);
			return new BasicResponse(ReplyMessage.SUCCESS.getCode(), //
					ReplyMessage.SUCCESS.getMessage());
		}
	}

}