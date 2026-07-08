package com.app.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class AppBackendApplication {

    public static void main(String[] args) {
        // 앱 기본 타임존을 KST로 고정한다.
        // Spring/Hibernate 초기화 "전에" 설정해야 @CreationTimestamp(예: 알림 created_at)까지 KST로 저장된다.
        // (기존 @PostConstruct 방식은 컨텍스트 초기화 후라 Hibernate 자동 타임스탬프가 UTC로 굳는 문제가 있었음)
        System.setProperty("user.timezone", "Asia/Seoul");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(AppBackendApplication.class, args);
    }
}
