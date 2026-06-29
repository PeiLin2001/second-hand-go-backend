package com.example.HellTrain.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// 吳新增
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import com.example.HellTrain.constant.DeptGroupConst;
import com.example.HellTrain.constant.ReplyMessage;
import com.example.HellTrain.request.ProductReq;
import com.example.HellTrain.request.SearchProductReq;
import com.example.HellTrain.response.BasicResponse;
import com.example.HellTrain.response.GetProductDataRes;
import com.example.HellTrain.response.ProductRes;
import com.example.HellTrain.service.ProductService;

import jakarta.servlet.http.HttpSession;

@RestController
@CrossOrigin(origins = "http://localhost:4200/")
@RequestMapping("/product")
public class ProductController {
	@Autowired
	private ProductService productService;

	@GetMapping(value = "/getInfo")
	public GetProductDataRes getAllProductsInformation() {
		return productService.getAllProductsInformation();
	}
	
//	@GetMapping(value = "/search/price")
//	public GetProductDataRes searchByPrice(@RequestParam("minPrice") int minPrice, @RequestParam("maxPrice") int maxPrice) {
//		return productService.searchByPrice(minPrice, maxPrice);
//	}
	
	@GetMapping(value = "/search/userId")
	public GetProductDataRes searchBySellerId(@RequestParam("userId") int userId) {
		return productService.searchBySellerId(userId);
	}
	
	@GetMapping(value = "/search/productId")
	public GetProductDataRes searchByProductId(@RequestParam("productId") int productId) {
		return productService.searchByProductId(productId);
	}
	
	@GetMapping(value = "/search/type")
	public GetProductDataRes searchByType(@RequestParam("type") List<String> type) {
		return productService.searchByType(type);
	}
	
//	 // 年級
//    @GetMapping("/search/grade")
//    public GetProductDataRes searchByGrade(@RequestParam("grade") String grade) {
//        return productService.searchByGrade(grade);
//    }

    // 以使用者校名取得商品
    @GetMapping("/search/school")
    public GetProductDataRes getByUniversity(@RequestParam("school") String school) {
    	return productService.getByUniversity(school);
    }
    

    // ★ 複合搜尋（前端主要呼叫這個）
    // POST /product/search
    // Body: { "keyword":"微積分", "minPrice":0, "maxPrice":500, "grades":["大一"], "types":["教科書"] }
    @PostMapping("/search")
    public GetProductDataRes search(@RequestBody SearchProductReq req) {
        return productService.searchByConditions(req);
    }
	
    // 在 Controller 加一個端點讓前端拿清單 ( 但前端已經有清單了QQ
    @GetMapping("/deptGroups")
    public List<String> getDeptGroups() {
        return DeptGroupConst.ALL_GROUPS;
    }
    
    @PostMapping("/add")
    public ProductRes addProduct(HttpSession session, @RequestBody ProductReq req) {
		//檢查登入session是否過期
    	Integer id=(Integer)session.getAttribute("user_id");
		if(id==null) {
			return new ProductRes(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),//
					ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
		}
    	return productService.addProduct(id, req);
    }
    
    // 新增：發布商品
    @PostMapping("/publish")
    public BasicResponse publishProduct(HttpSession session, @RequestParam("productId") int productId) {
        Integer id = (Integer) session.getAttribute("user_id");
        if (id == null) {
            return new BasicResponse(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),
                    ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
        }
        return productService.publishProduct(id, productId);
    }
    
    @PostMapping("/update")
    public BasicResponse updateProduct(HttpSession session,@RequestBody ProductReq req) {
		//檢查登入session是否過期
    	Integer id=(Integer)session.getAttribute("user_id");
		if(id==null) {
			return new BasicResponse(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),//
					ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
		}
    	return productService.updateProduct(id, req);
    }
    
    //以下為吳新增
    // ── 新增草稿 ──
    @PostMapping("/draft")
    public BasicResponse createDraft(HttpSession session, @RequestBody ProductReq req) {
    		Integer userId = (Integer) session.getAttribute("user_id");
        if (userId == null) {
            return new BasicResponse(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),
                    ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
        }
        return productService.createDraft(userId, req);
    }
    
    // ── 更新草稿 ──
    @PutMapping("/draft/{id}")
    public BasicResponse updateDraft(HttpSession session,
            @PathVariable int id,
            @RequestBody ProductReq req) {
        Integer userId = (Integer) session.getAttribute("user_id");
        if (userId == null) {
            return new BasicResponse(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),
                    ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
        }
        req.setProductId(id);
        return productService.updateDraft(userId, req);
    }
    
 // ── 草稿清單 ──
    @GetMapping("/user/{userId}/drafts")
    public GetProductDataRes getDraftsByUser(@PathVariable int userId) {
        return productService.getDraftsByUser(userId);
    }

    // ── 已上架清單 ──
    @GetMapping("/user/{userId}/published")
    public GetProductDataRes getPublishedByUser(@PathVariable int userId) {
        return productService.getPublishedByUser(userId);
    }

    // ── 上架 ──
    @PutMapping("/{id}/publish")
    public BasicResponse publishById(HttpSession session, @PathVariable int id) {
        Integer userId = (Integer) session.getAttribute("user_id");
        if (userId == null) {
            return new BasicResponse(ReplyMessage.PLEASE_LOGIN_FIRST.getCode(),
                    ReplyMessage.PLEASE_LOGIN_FIRST.getMessage());
        }
        return productService.publishProduct(userId, id);
    }

    // ── 下架 ──  絲絨會用到不要刪
    @PostMapping("unpublish")
    public BasicResponse unpublishById(@RequestParam("productId") int productId) {
        productService.unpublishById(productId);
        return new BasicResponse(ReplyMessage.SUCCESS.getCode(),
                ReplyMessage.SUCCESS.getMessage());
    }

    // ── 刪除草稿 ──  絲絨會用到不要刪
    @PostMapping("/delete")
    public BasicResponse deleteById(@RequestParam("productId") int productId) {
        return productService.deleteDraft(productId);
    }
    
}
