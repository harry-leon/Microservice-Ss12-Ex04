# [Bài tập] Cập nhật giá khuyến mãi thời gian thực với Redis Pub/Sub

## 1. Mục tiêu
- **Kiến thức**: Giao tiếp nhanh giữa các Microservices bằng cơ chế Publish/Subscribe (Pub/Sub) của Redis (Session 11 - Lesson 03).
- **Kỹ năng**: Lắng nghe và xử lý tin nhắn từ Redis Channel theo thời gian thực (Real-time).

---

## 2. Mô tả chi tiết bài toán
- **Bối cảnh**: Khi quản trị viên thay đổi chương trình khuyến mãi, tất cả các Service đang hiển thị giá (Web, App, API Gateway, Cache) cần được thông báo để làm mới dữ liệu ngay lập tức.
- **Yêu cầu kỹ thuật**:
  - **Promotion-Service (Publisher)**: Gửi thông điệp chứa `product_id` (cùng thông tin giá mới) vào channel `promotion-updates`.
  - **Product-Service (Subscriber)**: Lắng nghe channel này qua `RedisMessageListenerContainer` và thực hiện xóa cache Redis của sản phẩm đó (`product:price:{id}`) để buộc hệ thống lấy giá mới từ Database ở lần truy vấn tiếp theo (mô hình Cache-Aside).
- **Kết quả mong muốn**:
  - Giá khuyến mãi mới được cập nhật đồng loạt trên toàn hệ thống trong chưa đầy 1 giây.

---

## 3. Cấu trúc thư mục dự án

```
Ss12-Ex04/
├── docker-compose.yml
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java
    │   │   └── com/example/promotion
    │   │       ├── RealtimePromotionApplication.java
    │   │       ├── config
    │   │       │   └── RedisPubSubConfig.java
    │   │       ├── controller
    │   │       │   └── PromotionController.java
    │   │       ├── dto
    │   │       │   └── PromotionUpdateMessage.java
    │   │       └── service
    │   │           ├── ProductSubscriber.java
    │   │           └── PromotionPublisher.java
    │   └── resources
    │       └── application.yml
    └── test
        └── java
            └── com/example/promotion
                └── service
                    └── RealtimePromotionPubSubTest.java
```

---

## 4. Hướng dẫn chạy và kiểm thử

### 4.1. Khởi chạy Redis
```bash
docker compose up -d
```

### 4.2. Khởi chạy ứng dụng Spring Boot
```bash
mvn spring-boot:run
```

### 4.3. API Endpoints
1. **Truy vấn giá sản phẩm (Client/App)**:
   - `GET /api/products/{productId}/price`
   - Lần đầu tiên: Cache Miss -> Đọc từ DB và lưu vào cache Redis `product:price:{productId}`.
   - Các lần tiếp theo: Cache Hit -> Trả về giá từ Redis cache siêu tốc.

2. **Quản trị viên cập nhật chương trình khuyến mãi (Publisher)**:
   - `POST /api/promotions/update?productId=1&promotionalPrice=79000&promotionName=Super+Flash+Sale`
   - Publisher phát tin nhắn vào channel `promotion-updates`.
   - Subscriber nhận tin nhắn trong thời gian thực (< 1 giây), cập nhật DB và **xóa cache Redis của sản phẩm đó**.
   - Lần truy vấn `GET /api/products/1/price` tiếp theo sẽ lập tức trả về giá khuyến mãi mới.

### 4.4. Chạy kiểm thử tự động
```bash
mvn clean test
```
**Kết quả kiểm thử**: 4/4 test cases passed (`BUILD SUCCESS`), đảm bảo tính chính xác và an toàn của hệ thống.
