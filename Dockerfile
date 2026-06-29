# ---- 階段一：在雲端進行編譯 (Builder Stage) ----
# 使用官方 Java 17 的 Gradle 映像檔
FROM gradle:8.14.4-jdk17 AS builder
WORKDIR /app

# 將後端專案的所有原始碼複製到容器中
COPY --chown=gradle:gradle . .

# 1. 賦予 gradlew 執行權限（解決 Permission denied 126 錯誤）
RUN chmod +x gradlew

# 2. 在雲端執行打包（跳過測試以加速部署）
RUN ./gradlew bootJar -x test --no-daemon


# ---- 階段二：正式運行程式 (Run Stage) ----
# 編譯完成後，切換到乾淨、輕量級的 JRE 17 運行環境
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 從剛剛的 builder 階段中，把打包好的 jar 檔複製過來
COPY --from=builder /app/build/libs/*SNAPSHOT.jar app.jar

# 宣告容器內部運行的 Port（Spring Boot 預設為 8080）
EXPOSE 8080

# 啟動指令
ENTRYPOINT ["java", "-jar", "app.jar"]