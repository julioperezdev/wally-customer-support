package com.wally.customersupport.infrastructure.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfiguration {

    /**
     * Spring Boot 4 no longer auto-configures Flyway from flyway-core alone.
     * Keep schema migration ahead of Hibernate validation explicitly.
     */
    @Bean
    static BeanFactoryPostProcessor migrationBeforeJpa() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
                definition.setDependsOn("flywayInitializer");
            }
        };
    }

    @Bean(name = "flyway")
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean(name = "flywayInitializer")
    InitializingBean flywayInitializer(Flyway flyway) {
        return flyway::migrate;
    }
}
