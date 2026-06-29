package com.example.HellTrain.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.example.HellTrain.config.CloudinaryService;
import com.example.HellTrain.constant.ProductStatus;
import com.example.HellTrain.constant.ReplyMessage;
import com.example.HellTrain.constant.UserStatus;
import com.example.HellTrain.dao.ProductDao;
import com.example.HellTrain.dao.UserDao;
import com.example.HellTrain.entity.Product;
import com.example.HellTrain.entity.User;
import com.example.HellTrain.request.ProductReq;
import com.example.HellTrain.request.SearchProductReq;
import com.example.HellTrain.response.BasicResponse;
import com.example.HellTrain.response.GetProductDataRes;
import com.example.HellTrain.response.ProductRes;
import com.example.HellTrain.vo.ProductVo;
import com.example.HellTrain.vo.SimpleUserVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProductService {

	@Autowired
	private CloudinaryService cloudinaryService;
	@Autowired
	private ProductDao productDao;
	@Autowired
	private UserDao userDao;

	private ObjectMapper mapper = new ObjectMapper();

	// ── 私有工具方法 ──────────────────────────────────────────

	// JSON 字串轉 List<String>，null 或空字串回傳空 List
	private List<String> parseJsonList(String json) {
		if (json == null || json.isBlank())
			return new ArrayList<>();
		try {
			return mapper.readValue(json, new TypeReference<List<String>>() {
			});
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	// 判斷 JSON 字串欄位是否包含 targets 其中任一個值
	// 用於 type（因為 DB 存的是 JSON 字串，無法直接用 SQL WHERE 篩多選）
	private boolean jsonContainsAny(String json, List<String> targets) {
		if (json == null || json.isBlank() || CollectionUtils.isEmpty(targets))
			return false;
		List<String> values = parseJsonList(json);
		return values.stream().anyMatch(targets::contains);
	}

	// Product 轉 ProductVo，給 convertToFrontEndFormat 用
	// 6/6 新增: SellerVo
	// 改成接收已查好的 sellerVo，不在這裡查 DB
	private ProductVo toVo(Product item, SimpleUserVo sellerVo) throws Exception {
		return new ProductVo(item.getProductId(), item.getUserId(), item.getProductName(), item.getDescription(),
				item.getPrice(), parseJsonList(item.getImgPath()), parseJsonList(item.getType()), item.getShelfDate(),
				item.getProductCondition(), item.getStatus(), parseJsonList(item.getGrade()),
				parseJsonList(item.getLocation()), parseJsonList(item.getDeptGroup()), sellerVo);
	}

	// List<Product> 轉成前端格式
	private GetProductDataRes convertToFrontEndFormat(List<Product> productList) {
		if (CollectionUtils.isEmpty(productList)) {
			return new GetProductDataRes(ReplyMessage.NO_DATA_FOUND.getCode(), ReplyMessage.NO_DATA_FOUND.getMessage());
		}

		// 批次查詢所有賣家，避免 N+1
		List<Integer> userIds = productList.stream().map(Product::getUserId).distinct().collect(Collectors.toList());

		Map<Integer, SimpleUserVo> sellerMap = userDao.findByUserIdIn(userIds).stream()
				.collect(Collectors.toMap(User::getUserId, u -> new SimpleUserVo(u.getUserId(), u.getUserName(),
						u.getSchool(), u.getImgPath(), u.getDepartment(), u.getGoodLevel())));

		List<ProductVo> voList = new ArrayList<>();
		for (Product item : productList) {
			try {
				voList.add(toVo(item, sellerMap.get(item.getUserId())));
			} catch (Exception e) {
				return new GetProductDataRes(ReplyMessage.PRODUCT_PARSE_ERROR.getCode(),
						ReplyMessage.PRODUCT_PARSE_ERROR.getMessage());
			}
		}

		return new GetProductDataRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), voList);
	}

	// 1. 直接去資料庫撈取全部的商品資料
	public GetProductDataRes getAllProductsInformation() {
		List<Product> productList = productDao.getAllOnSale();
		return convertToFrontEndFormat(productList);
	} // GetAllData結束

	// 2. 以價位區間搜尋 (無用 可刪)
//	public GetProductDataRes searchByPrice(int minPrice, int maxPrice) {
//		List<Product> productList = productDao.findByPriceBetween(minPrice, maxPrice);
//		return convertToFrontEndFormat(productList);
//	}

	// 3. 以賣家 ID 搜尋
	public GetProductDataRes searchBySellerId(int userId) {
		if (userId < 1) {
			return new GetProductDataRes(ReplyMessage.USER_ID_ERR.getCode(), ReplyMessage.USER_ID_ERR.getMessage());
		}
		List<Product> productList = productDao.findByUserId(userId);
		return convertToFrontEndFormat(productList);
	}

	// 4. 以商品 ID 搜尋
	public GetProductDataRes searchByProductId(int productId) {
		if (productId < 1) {
			return new GetProductDataRes(ReplyMessage.USER_ID_ERR.getCode(), ReplyMessage.USER_ID_ERR.getMessage());
		}

		Product product = productDao.findByProductId(productId);

		if (product == null) {
			return new GetProductDataRes(ReplyMessage.NO_DATA_FOUND.getCode(), ReplyMessage.NO_DATA_FOUND.getMessage());
		}

		return convertToFrontEndFormat(List.of(product)); // 包裝成 List
	}

	// 5. 以類型搜尋
	public GetProductDataRes searchByType(List<String> types) {
		if (CollectionUtils.isEmpty(types)) {
			return new GetProductDataRes(ReplyMessage.INVALID_PARAM.getCode(), ReplyMessage.INVALID_PARAM.getMessage());
		}
		List<Product> filtered = productDao.getAllOnSale().stream().filter(p -> jsonContainsAny(p.getType(), types))
				.collect(Collectors.toList());
		return convertToFrontEndFormat(filtered);
	}

	// 6. 以年級搜尋 (無用 可刪)
//	public GetProductDataRes searchByGrade(String grade) {
//		if (grade == null || grade.isBlank()) {
//			return new GetProductDataRes(ReplyMessage.INVALID_PARAM.getCode(), ReplyMessage.INVALID_PARAM.getMessage());
//		}
//		return convertToFrontEndFormat(productDao.findByGrade(grade));
//	}

	// 7. 以學校搜尋
	public GetProductDataRes getByUniversity(String school) {
		if (school == null || school.isBlank()) {
			return new GetProductDataRes(ReplyMessage.INVALID_PARAM.getCode(), ReplyMessage.INVALID_PARAM.getMessage());
		}
		return convertToFrontEndFormat(productDao.findBySchool(school));
	}

	// ── 8. 複合搜尋（前端主要呼叫這個）──────────────────────
	public GetProductDataRes searchByConditions(SearchProductReq req) {
		if (req == null) {
			return getAllProductsInformation();
		}

		// Step 1：DB 層處理 keyword + price（效率高的先在 DB 過濾）
		String keyword = (req.getKeyword() != null && !req.getKeyword().isBlank()) ? req.getKeyword().trim() : null;

		List<Product> result = productDao.findByMultiConditions(keyword, req.getMinPrice(), req.getMaxPrice());

		// Step 2：Java 層過濾 grades（DB 欄位是普通字串，可以直接比對）
		if (!CollectionUtils.isEmpty(req.getGrades())) {
			result = result.stream().filter(p -> jsonContainsAny(p.getGrade(), req.getGrades()))
					.collect(Collectors.toList());
		}

		// Step 3：Java 層過濾 conditions（productCondition 是普通字串）
		if (!CollectionUtils.isEmpty(req.getConditions())) {
			result = result.stream().filter(p -> req.getConditions().contains(p.getProductCondition()))
					.collect(Collectors.toList());
		}

		// Step 4：Java 層過濾 types（DB 存 JSON 字串，必須在 Java 解析後比對）
		if (!CollectionUtils.isEmpty(req.getTypes())) {
			result = result.stream().filter(p -> jsonContainsAny(p.getType(), req.getTypes()))
					.collect(Collectors.toList());
		}

		// Step 5：Java 層過濾 deptGroups（DB 存 JSON 字串，同 type 處理方式）
		if (!CollectionUtils.isEmpty(req.getDeptGroups())) {
			result = result.stream().filter(p -> jsonContainsAny(p.getDeptGroup(), req.getDeptGroups()))
					.collect(Collectors.toList());
		}

		return convertToFrontEndFormat(result);
	}

//	public BasicResponse addProduct(int id, ProductReq req) {
//
//		/* 檢查 */
//		// 帳號存在且狀態為正常
//		User user = userDao.getById(id);
//		if (user == null || !user.getStatus().equals(UserStatus.Normal.getMessage())) {
//			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(),//
//			 ReplyMessage.NO_PERMISSIONS.getMessage());
//		}
//
//		// 檢查商品稱是否有輸入
//		if (!StringUtils.hasText(req.getProductName())) {
//			return new BasicResponse(ReplyMessage.PRODUCT_NAME_NULL.getCode(),//
//					ReplyMessage.PRODUCT_NAME_NULL.getMessage());
//		}
//
//		// 檢查描述
//		if (!StringUtils.hasText(req.getDescription())) {
//			return new BasicResponse(ReplyMessage.DESCRIPTION_IS_NULL.getCode(),//
//					ReplyMessage.DESCRIPTION_IS_NULL.getMessage());
//		}
//
//		// 檢查價格
//		if (req.getPrice() <= 0) {
//			return new BasicResponse(ReplyMessage.PRICE_ERROR.getCode(),//
//					ReplyMessage.PRICE_ERROR.getMessage());
//		}
//
//		// 檢查是否有圖片（至少一張，最多7張）
//		if (req.getImgList() == null || req.getImgList().isEmpty()) {
//			return new BasicResponse(ReplyMessage.PLEASE_SET_FILE.getCode(),//
//					ReplyMessage.PLEASE_SET_FILE.getMessage());
//		}
//		
//		if (req.getImgList().size() >= 7) {
//			return new BasicResponse(ReplyMessage.FILE_TOO_MANY.getCode(),//
//					ReplyMessage.FILE_TOO_MANY.getMessage());
//		}
//		
//		// 檢查類型
//		if (req.getType() == null || req.getType().isEmpty()) {
//			return new BasicResponse(ReplyMessage.TYPE_IS_NULL.getCode(),//
//					ReplyMessage.TYPE_IS_NULL.getMessage());
//		}
//
//		// 檢查商品狀況(condition)
//		if (!StringUtils.hasText(req.getProductCondition())) {
//			return new BasicResponse(ReplyMessage.CONDITION_IS_NULL.getCode(),//
//					ReplyMessage.CONDITION_IS_NULL.getMessage());
//		}
//
//		// 檢查商品的可交易地點(location)
//		if (req.getLocation() == null || req.getLocation().isEmpty()) {
//			return new BasicResponse(ReplyMessage.NO_DESIGNTED_LOCATION.getCode(),//
//					ReplyMessage.NO_DESIGNTED_LOCATION.getMessage());
//		}
//
//		// 檢查科系群deptGroup(list)
//		if (req.getDeptGroup() == null || req.getDeptGroup().isEmpty()) {
//			return new BasicResponse(ReplyMessage.NO_DEPTGROUP.getCode(),//
//					ReplyMessage.NO_DEPTGROUP.getMessage());
//		}
//		
//		//檢查年級是否為null
//		if (req.getGrade() == null || req.getGrade().isEmpty()) {
//		    return new BasicResponse(ReplyMessage.GRADE_IS_NULL.getCode(),//
//		    		ReplyMessage.GRADE_IS_NULL.getMessage());
//		}
//
//
//		// 上傳圖片到 Cloudinary
//		List<String> imgUrls = new ArrayList<>();
//		for (String base64Image : req.getImgList()) {
//			if (base64Image == null || base64Image.isBlank())
//				continue;
//			try {
//				String imageUrl = cloudinaryService.uploadBase64(base64Image);
//				imgUrls.add(imageUrl);
//			} catch (Exception e) {
//				return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),//
//						ReplyMessage.PLEASE_TRY_LATE.getMessage());
//			}
//		}
//
//		// 設定非賣家指定參數
//		// 設定上架時間，目前預設新增日
//		LocalDate shelfDate = LocalDate.now();
//		// 設定商品狀態，目前預設販售中
//		String status = ProductStatus.OnSale.getMassage();
//
//		// 轉成 JSON 字串存入 DB
//		try {
//			String imgPathJson = mapper.writeValueAsString(imgUrls);
//			String typeJson = mapper.writeValueAsString(req.getType());
//			String locationJson = mapper.writeValueAsString(req.getLocation());
//			String gradeJson = mapper.writeValueAsString(req.getGrade());
//			String deptGroupJson = mapper.writeValueAsString(req.getDeptGroup());
//
//			productDao.insert(id, req.getProductName(), req.getDescription(),//
//					req.getPrice(), imgPathJson, typeJson, shelfDate,//
//					req.getProductCondition(), status, gradeJson, locationJson, deptGroupJson);
//
//		} catch (Exception e) {
//			return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),//
//					ReplyMessage.PLEASE_TRY_LATE.getMessage());
//		}
//
//		return new BasicResponse(ReplyMessage.SUCCESS.getCode(),//
//				ReplyMessage.SUCCESS.getMessage());
//	}

	private BasicResponse checkparam(Product product, int userId) {
		if (product == null) {
			return new BasicResponse(ReplyMessage.PRODUCT_IS_NOTFOUND.getCode(),
					ReplyMessage.PRODUCT_IS_NOTFOUND.getMessage());
		}

		if (product.getUserId() != userId) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), ReplyMessage.NO_PERMISSIONS.getMessage());
		}

		// 只有草稿或已下架才能發布
		if (!product.getStatus().equals(ProductStatus.NotSale.getMassage())
				&& !product.getStatus().equals(ProductStatus.Removed.getMassage())) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), ReplyMessage.NO_PERMISSIONS.getMessage());
		}

		// 完整欄位檢查
		if (!StringUtils.hasText(product.getProductName())) {
			return new BasicResponse(ReplyMessage.PRODUCT_NAME_NULL.getCode(),
					ReplyMessage.PRODUCT_NAME_NULL.getMessage());
		}
		if (!StringUtils.hasText(product.getDescription())) {
			return new BasicResponse(ReplyMessage.DESCRIPTION_IS_NULL.getCode(),
					ReplyMessage.DESCRIPTION_IS_NULL.getMessage());
		}
		if (product.getPrice() <= 0) {
			return new BasicResponse(ReplyMessage.PRICE_ERROR.getCode(), ReplyMessage.PRICE_ERROR.getMessage());
		}

		List<String> imgList = parseJsonList(product.getImgPath());
		if (imgList.isEmpty()) {
			return new BasicResponse(ReplyMessage.PLEASE_SET_FILE.getCode(), ReplyMessage.PLEASE_SET_FILE.getMessage());
		}

		if (imgList.size() >= 7) {
			return new BasicResponse(ReplyMessage.FILE_TOO_MANY.getCode(), ReplyMessage.FILE_TOO_MANY.getMessage());
		}

		if (parseJsonList(product.getType()).isEmpty()) {
			return new BasicResponse(ReplyMessage.TYPE_IS_NULL.getCode(), ReplyMessage.TYPE_IS_NULL.getMessage());
		}
		if (!StringUtils.hasText(product.getProductCondition())) {
			return new BasicResponse(ReplyMessage.CONDITION_IS_NULL.getCode(),
					ReplyMessage.CONDITION_IS_NULL.getMessage());
		}
		if (parseJsonList(product.getLocation()).isEmpty()) {
			return new BasicResponse(ReplyMessage.NO_DESIGNTED_LOCATION.getCode(),
					ReplyMessage.NO_DESIGNTED_LOCATION.getMessage());
		}
		if (parseJsonList(product.getGrade()).isEmpty()) {
			return new BasicResponse(ReplyMessage.GRADE_IS_NULL.getCode(), ReplyMessage.GRADE_IS_NULL.getMessage());
		}
		if (parseJsonList(product.getDeptGroup()).isEmpty()) {
			return new BasicResponse(ReplyMessage.NO_DEPTGROUP.getCode(), ReplyMessage.NO_DEPTGROUP.getMessage());
		}

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	}

	// 新增商品（永遠是草稿，不檢查欄位）
	public ProductRes addProduct(int id, ProductReq req) {

		User user = userDao.getById(id);
		if (user == null || !user.getStatus().equals(UserStatus.Normal.getMessage())) {
			return new ProductRes(ReplyMessage.NO_PERMISSIONS.getCode(), ReplyMessage.NO_PERMISSIONS.getMessage());
		}

		List<String> imgUrls = new ArrayList<>();

		if (req.getImgList() != null) {
			for (String img : req.getImgList()) {
				if (img == null || img.isBlank())
					continue;

				// 新圖片（base64），上傳 Cloudinary
				try {
					imgUrls.add(cloudinaryService.uploadBase64(img));
				} catch (Exception e) {
					return new ProductRes(ReplyMessage.PLEASE_TRY_LATE.getCode(),
							ReplyMessage.PLEASE_TRY_LATE.getMessage());
				}

			}
		}
		// 先判斷 imgUrls 不為空
		String imgPathJson;
		if (!imgUrls.isEmpty()) {
			try {
				imgPathJson = mapper.writeValueAsString(imgUrls);
			} catch (Exception e) {
				return new ProductRes(ReplyMessage.PLEASE_TRY_LATE.getCode(),
						ReplyMessage.PLEASE_TRY_LATE.getMessage());
			}
		} else {
			imgPathJson = "[]";
		}

		int newProductId = 0; // 黑魔法參數

		try {
//			String imgPathJson = mapper.writeValueAsString(req.getImgList() != null ? req.getImgList() : List.of());
			String typeJson = mapper.writeValueAsString(req.getType() != null ? req.getType() : List.of());
			String locationJson = mapper.writeValueAsString(req.getLocation() != null ? req.getLocation() : List.of());
			String gradeJson = mapper.writeValueAsString(req.getGrade() != null ? req.getGrade() : List.of());
			String deptGroupJson = mapper
					.writeValueAsString(req.getDeptGroup() != null ? req.getDeptGroup() : List.of());

			productDao.insert(id, req.getProductName() != null ? req.getProductName() : "",
					req.getDescription() != null ? req.getDescription() : "", req.getPrice(), imgPathJson, typeJson,
					LocalDate.now(), req.getProductCondition() != null ? req.getProductCondition() : "",
					ProductStatus.NotSale.getMassage(), // 寫死未上架
					gradeJson, locationJson, deptGroupJson);

			newProductId = productDao.findProductIdDesc(id).getProductId(); // 回傳productId用的黑魔法(不想也不敢大改了)

		} catch (Exception e) {
			return new ProductRes(ReplyMessage.PLEASE_TRY_LATE.getCode(), ReplyMessage.PLEASE_TRY_LATE.getMessage());
		}

		return new ProductRes(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage(), newProductId);
	}

	// 發布商品（做完整檢查，通過才把 status 改成 OnSale）
	public BasicResponse publishProduct(int userId, int productId) {

		Product product = productDao.findByProductId(productId);

		BasicResponse checkResult = checkparam(product, userId);
		if (checkResult.getStatusCode() != 200) {
			return checkResult;
		}

		// 全部通過，更新 status
		productDao.updateStatus(productId, ProductStatus.OnSale.getMassage());

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	}

	public BasicResponse updateProduct(int id, ProductReq req) {

		Product product = productDao.findByProductId(req.getProductId());
		/* 檢查 */
		// 商品==null
		if (product == null) {
			return new BasicResponse(ReplyMessage.PRODUCT_IS_NOTFOUND.getCode(), //
					ReplyMessage.PRODUCT_IS_NOTFOUND.getMessage());
		}

		// 檢查user是否為商品傭有者
		if (product.getUserId() != id) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), //
					ReplyMessage.NO_PERMISSIONS.getMessage());
		}

		// 狀態必須是「未上架」才能更新（已上架商品不允許修改） //改如果是交易中或販售中不允許更改
		if (product.getStatus().equals(ProductStatus.OnSale.getMassage())
				|| product.getStatus().equals(ProductStatus.Trade.getMassage())) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), //
					ReplyMessage.NO_PERMISSIONS.getMessage());
		}

		// 判斷是否有新圖片（base64 開頭）還是舊圖片（http 開頭）
		List<String> imgUrls = new ArrayList<>();

		if (req.getImgList() != null) {
			for (String img : req.getImgList()) {
				if (img == null || img.isBlank())
					continue;

				if (img.startsWith("http")) {
					// 舊圖片，直接保留網址
					imgUrls.add(img);
				} else {
					// 新圖片（base64），上傳 Cloudinary
					try {
						imgUrls.add(cloudinaryService.uploadBase64(img));
					} catch (Exception e) {
						return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(),
								ReplyMessage.PLEASE_TRY_LATE.getMessage());
					}
				}
			}
		}
		// 先判斷 imgUrls 是否為空
		String imgPathJson;
		if (imgUrls.isEmpty()) {
			imgPathJson = product.getImgPath(); // 保留原本的
		} else {
			try {
				imgPathJson = mapper.writeValueAsString(imgUrls);
			} catch (Exception e) {
				return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(), //
						ReplyMessage.PLEASE_TRY_LATE.getMessage());
			}
		}

		// 轉成 JSON 字串存入 DB（草稿允許欄位為 null/空，直接序列化即可）
		try {
			String typeJson = mapper.writeValueAsString(req.getType());
			String locationJson = mapper.writeValueAsString(req.getLocation());
			String gradeJson = mapper.writeValueAsString(req.getGrade());
			String deptGroupJson = mapper.writeValueAsString(req.getDeptGroup());

			productDao.update(req.getProductId(), req.getProductName(), req.getDescription(), req.getPrice(),
					imgPathJson, typeJson, req.getProductCondition(), gradeJson, locationJson, deptGroupJson);

		} catch (Exception e) {
			return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(), //
					ReplyMessage.PLEASE_TRY_LATE.getMessage());
		}

		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), //
				ReplyMessage.SUCCESS.getMessage());
	}

	// 以下吳新增
	// ── 新增草稿（不驗證欄位，status 寫死 NotSale）──
	public BasicResponse createDraft(int userId, ProductReq req) {
		User user = userDao.getById(userId);
		if (user == null || !user.getStatus().equals(UserStatus.Normal.getMessage())) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), ReplyMessage.NO_PERMISSIONS.getMessage());
		}
		try {
			String imgPathJson = mapper.writeValueAsString(req.getImgList() != null ? req.getImgList() : List.of());
			String typeJson = mapper.writeValueAsString(req.getType() != null ? req.getType() : List.of());
			String locationJson = mapper.writeValueAsString(req.getLocation() != null ? req.getLocation() : List.of());
			String gradeJson = mapper.writeValueAsString(req.getGrade() != null ? req.getGrade() : List.of());
			String deptGroupJson = mapper
					.writeValueAsString(req.getDeptGroup() != null ? req.getDeptGroup() : List.of());

			productDao.insert(userId, req.getProductName() != null ? req.getProductName() : "",
					req.getDescription() != null ? req.getDescription() : "", req.getPrice(), imgPathJson, typeJson,
					LocalDate.now(), req.getProductCondition() != null ? req.getProductCondition() : "",
					ProductStatus.NotSale.getMassage(), gradeJson, locationJson, deptGroupJson);

			int newId = productDao.getLastInsertIdByUser(userId);

			return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage()) {
				private final int productId = newId;

				public int getProductId() {
					return productId;
				}
			};

		} catch (Exception e) {
			return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(), ReplyMessage.PLEASE_TRY_LATE.getMessage());
		}
	}

	// ── 更新草稿 ──
	public BasicResponse updateDraft(int userId, ProductReq req) {
		Product product = productDao.findByProductId(req.getProductId());
		if (product == null || product.getUserId() != userId) {
			return new BasicResponse(ReplyMessage.NO_PERMISSIONS.getCode(), ReplyMessage.NO_PERMISSIONS.getMessage());
		}
		try {
			String imgPathJson = mapper.writeValueAsString(req.getImgList() != null ? req.getImgList() : List.of());
			String typeJson = mapper.writeValueAsString(req.getType() != null ? req.getType() : List.of());
			String locationJson = mapper.writeValueAsString(req.getLocation() != null ? req.getLocation() : List.of());
			String gradeJson = mapper.writeValueAsString(req.getGrade() != null ? req.getGrade() : List.of());
			String deptGroupJson = mapper
					.writeValueAsString(req.getDeptGroup() != null ? req.getDeptGroup() : List.of());

			productDao.update(req.getProductId(), req.getProductName() != null ? req.getProductName() : "",
					req.getDescription() != null ? req.getDescription() : "", req.getPrice(), imgPathJson, typeJson,
					req.getProductCondition() != null ? req.getProductCondition() : "", gradeJson, locationJson,
					deptGroupJson);

			return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());

		} catch (Exception e) {
			return new BasicResponse(ReplyMessage.PLEASE_TRY_LATE.getCode(), ReplyMessage.PLEASE_TRY_LATE.getMessage());
		}
	}

	// ── 草稿清單 ──
	public GetProductDataRes getDraftsByUser(int userId) {
		List<Product> list = productDao.findByUserIdAndStatus(userId, ProductStatus.NotSale.getMassage());
		return convertToFrontEndFormat(list);
	}

	// ── 已上架清單 ──
	public GetProductDataRes getPublishedByUser(int userId) {
		List<Product> list = productDao.findByUserIdAndStatus(userId, ProductStatus.OnSale.getMassage());
		return convertToFrontEndFormat(list);
	}

	// ── 下架（改 status 回 NotSale）──
	public void unpublishById(int id) {
		productDao.updateStatus(id, ProductStatus.Removed.getMassage());
	}

	// ── 刪除草稿 ──
	public BasicResponse deleteDraft(int id) {
		productDao.deleteByProductId(id);
		return new BasicResponse(ReplyMessage.SUCCESS.getCode(), ReplyMessage.SUCCESS.getMessage());
	}

	// 逾期下架
	@Scheduled(cron = "0 0 1 * * *")
	public void cleanExpiredProducts() {
		LocalDateTime deadline = LocalDateTime.now().minusDays(30);
		productDao.RemoveFromShelves(deadline, ProductStatus.Removed.getMassage());
	}
}
