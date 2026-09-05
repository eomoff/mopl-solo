package com.github.eomoff.mopl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스키마도 인프라이므로 테스트가 실제 마이그레이션 위에서 돈다.
 *
 * <p>ddl-auto로 대체하면 마이그레이션 오타를 CI가 잡지 못함.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void V1이_적용되어_사용자_인증_테이블이_만들어진다() {
    // given — Flyway가 컨텍스트 기동 시점에 마이그레이션 실행

    // when
    List<String> tables = jdbcTemplate.queryForList(
        "select table_name from information_schema.tables where table_schema = 'public'",
        String.class);

    // then
    assertThat(tables)
        .contains("flyway_schema_history", "users", "social_accounts", "refresh_tokens");
  }
}
