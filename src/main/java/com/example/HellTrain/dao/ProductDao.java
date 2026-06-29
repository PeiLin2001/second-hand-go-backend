package com.example.HellTrain.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.HellTrain.entity.Product;

import jakarta.transaction.Transactional;

// 6/13 修改 : 只選取販售中的商品
@Repository
public interface ProductDao extends JpaRepository<Product, Integer> {

	@Query(value = "SELECT * FROM product WHERE status = '販售中'", nativeQuery = true)
	List<Product> getAllOnSale();

	@Query(value = "SELECT * FROM product WHERE price >= ?1 AND price <= ?2", nativeQuery = true)
	List<Product> findByPriceBetween(int minPrice, int maxPrice);

	@Query(value = "SELECT * FROM product WHERE user_id = ?1", nativeQuery = true)
	List<Product> findByUserId(int userId);

	// 修正：原本 %?% 語法錯誤，改用 CONCAT
	@Query(value = "SELECT * FROM product WHERE type LIKE CONCAT('%', ?1, '%') AND status = '販售中'", nativeQuery = true)
	List<Product> findByType(String type);

	// 新增：年級搜尋
	@Query(value = "SELECT * FROM product WHERE grade = ?1", nativeQuery = true)
	List<Product> findByGrade(String grade);
	
	// 新增：以校名搜尋
	@Query(value = "SELECT p.* FROM product p "//
			+ "INNER JOIN user u ON p.user_id = u.user_id "//
			+ "WHERE u.school = ?1 AND p.status = '販售中'", nativeQuery = true)
	List<Product> findBySchool(String school);

	// 新增：商品名稱模糊搜尋
	@Query(value = "SELECT * FROM product WHERE product_name LIKE CONCAT('%', ?1, '%')", nativeQuery = true)
	List<Product> findByProductName(String productName);

	// 新增：複合搜尋（productName + price，其餘在 Java 層過濾）
	@Query(value = """
			SELECT * FROM product
			WHERE (:keyword IS NULL 
			OR product_name LIKE CONCAT('%', :keyword, '%') 
			OR `type` LIKE CONCAT('%', :keyword, '%'))
			  AND (:minPrice IS NULL OR price >= :minPrice)
			  AND (:maxPrice IS NULL OR price <= :maxPrice)
			  AND status = '販售中'
			""", nativeQuery = true)
	List<Product> findByMultiConditions(@Param("keyword") String keyword, @Param("minPrice") Integer minPrice,
			@Param("maxPrice") Integer maxPrice);

	// 以商品ID搜尋
	@Query(value = "SELECT * FROM product WHERE product_id = ?1", nativeQuery = true)
	public Product findByProductId(int productId);

	// 更改商品狀況販售中、交易中、下架等等
	@Modifying
	@Transactional
	@Query(value = "update product set status = ?2 where product_id = ?1", nativeQuery = true)
	public void changeStatus(int productId, String status);

	//新增商品
	@Modifying
	@Transactional
	@Query(value = "insert ignore into product (user_id, product_name, description, price,"//
				+ " img_path, type, shelf_date,product_condition,status,grade,location,dept_group) "//
				+ " value (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12)", nativeQuery = true)
	public void insert(int id, String name, String description, int price, String img, String type, LocalDate shelfDate,//
			String condition, String status, String grade, String location, String deptGroup);

	//修改商品資訊
	@Modifying
	@Transactional
	@Query(value = "update product set product_name = ?2 ,description = ?3 ,price = ?4 ,img_path = ?5 ,type = ?6 ,"//
			+ "product_condition = ?7 ,grade = ?8 ,location = ?9 ,dept_group = ?10 where product_id = ?1", nativeQuery = true)
	public void update(int productId ,String productName ,String description ,int price ,String imgPath ,String type ,
			String condition ,String grade ,String location ,String deptGroup);
	
	//修改商品資訊
	@Modifying
	@Transactional
	@Query(value = "update product set status = ?2 where product_id = ?1", nativeQuery = true)
	public void updateStatus(int productId ,String status);
	

	//以下吳新增
	// 依 userId + status 撈清單（草稿或已上架）
	@Query(value = "SELECT * FROM product WHERE user_id = ?1 AND status = ?2", nativeQuery = true)
	List<Product> findByUserIdAndStatus(int userId, String status);

	// 刪除草稿
	@Modifying
	@Transactional
	@Query(value = "DELETE FROM product WHERE product_id = ?1", nativeQuery = true)
	void deleteByProductId(int productId);

	// 取該 userId 最後一筆插入的 product_id
	@Query(value = "SELECT MAX(product_id) FROM product WHERE user_id = ?1", nativeQuery = true)
	int getLastInsertIdByUser(int userId);

	//修改商品資訊
	@Modifying
	@Transactional
	@Query(value = "UPDATE product SET status = ?2 WHERE shelf_date <= ?1 AND status = '販售中'", nativeQuery = true)
	public void RemoveFromShelves(LocalDateTime shelfDate ,String status);

	
	// 黑魔法用
	@Query(value = "SELECT * FROM product WHERE user_id = ? ORDER BY product_id DESC LIMIT 1 ", nativeQuery = true)
	Product findProductIdDesc(int userId);
	
}