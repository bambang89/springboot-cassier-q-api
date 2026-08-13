package com.cassierq.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// spring.docker.compose.skip.in-tests defaults to true (Boot assumes
// Testcontainers for tests); overridden here so this test reuses the same
// compose.yaml Postgres instead of requiring a separate Testcontainers setup.
@SpringBootTest
@TestPropertySource(properties = "spring.docker.compose.skip.in-tests=false")
class CassierQApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
