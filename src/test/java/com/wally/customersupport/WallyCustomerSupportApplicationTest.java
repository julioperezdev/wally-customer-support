package com.wally.customersupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WallyCustomerSupportApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void startsWithFlywayAndLocalMockAdapters() {
        assertNotNull(dataSource);
    }
}
