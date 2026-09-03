package com.github.eomoff.mopl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 인프라.
 *
 * <p>PostgreSQL은 가짜로 대체하지 않고 실제 컨테이너를 띄운다. 커서 페이지네이션·부분 검색·유니크 제약처럼
 * 이 서비스가 의존하는 동작이 방언에 걸려 있어, 인메모리 DB로 검증하면 통과해도 의미가 없기 때문이다.
 *
 * <p>Redis도 같은 이유로 실제 컨테이너가 필요하지만, 컨텍스트 로딩 시점에는 연결하지 않으므로
 * 실제로 Redis를 쓰는 테스트가 생길 때 추가한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:17-alpine");
  }
}
