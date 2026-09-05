package com.wally.customersupport.shared.infrastructure.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class FlywayConfiguration {

    @Bean
    HibernatePropertiesCustomizer hibernateDefaultSchema(
            @Value("${spring.flyway.default-schema:public}") String defaultSchema) {
        return hibernateProperties -> hibernateProperties.put("hibernate.default_schema", defaultSchema);
    }

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
    Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.default-schema:public}") String defaultSchema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(defaultSchema)
                .schemas(defaultSchema)
                .createSchemas(true)
                .load();
    }

    @Bean(name = "flywayInitializer")
    InitializingBean flywayInitializer(Flyway flyway) {
        return flyway::migrate;
    }
}
