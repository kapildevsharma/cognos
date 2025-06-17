
package com.kapil.cognos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {
	@Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.url}")
    private String baseUrl;

    @Bean
    public DataSource dataSource() {
        Map<Object, Object> dataSources = new HashMap<>();

        DriverManagerDataSource defaultDataSource = new DriverManagerDataSource();
        defaultDataSource.setUrl(baseUrl);
        defaultDataSource.setUsername(dbUsername);
        defaultDataSource.setPassword(dbPassword);

        MultiTenantDataSource dataSource = new MultiTenantDataSource();
        dataSource.setDefaultTargetDataSource(defaultDataSource);
        dataSource.setTargetDataSources(dataSources);
        dataSource.afterPropertiesSet();
        return dataSource;
    }
}
