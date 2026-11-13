package com.bevans;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.bevans.config.FirebaseConfig;

@SpringBootTest(properties = {
        "spring.config.import=",
        "app.firebase.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:context-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class VanappBackendApplicationTests {
    @MockBean FirebaseConfig firebaseConfig;

	@Test
	void contextLoads() {
	}

}
