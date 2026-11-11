package com.mowoo.mowoo_backend.diary;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.util.Locale;

// 개발용 mowoo에 테스트 데이터를 쓰거나 DDL을 적용하기 전에 명시적인 테스트 DB를 요구한다.
abstract class DiaryDatabaseTest {

    @DynamicPropertySource
    static void requireTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getProperty("spring.datasource.url");
        if (url == null) {
            url = System.getenv("SPRING_DATASOURCE_URL");
        }
        if (url == null) {
            url = System.getenv("DB_URL");
        }
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("일기 DB 테스트에는 명시적인 MySQL 테스트 DB URL이 필요합니다.");
        }
        String database = URI.create(url.substring("jdbc:".length())).getPath()
                .replaceFirst("^/", "").toLowerCase(Locale.ROOT);
        if (!database.matches("(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("일기 DB 테스트에는 이름에 test가 구분된 전용 DB를 지정하세요.");
        }
        String testUrl = url;
        registry.add("spring.datasource.url", () -> testUrl);
    }
}
