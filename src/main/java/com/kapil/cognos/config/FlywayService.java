// FlywayService.java placeholder content
package com.kapil.cognos.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
@Service
public class FlywayService {
	
	@Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.url}")
    private String baseUrl;

    
    public String getDbUsername() {
		return dbUsername;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void runMigrations(String schemaName) {
        String jdbcUrlWithSchema = getTenantJdbcURL(schemaName);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrlWithSchema, dbUsername, dbPassword)
                .schemas(schemaName)
                .locations("classpath:db/migration") // SQLs must be in resources/db/migration
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }
    
    public String getTenantJdbcURL(String schemaName) {
        String tenantJdbcUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"))+"/" + schemaName;
        System.out.println("tenantJdbcUrl "+tenantJdbcUrl);
        return tenantJdbcUrl;
    }
    
    public JdbcTemplate getJdbcTemplateOfTenant(String jdbcUrl, String username, String password) {
        DataSource dataSource = DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver") // Adjust for your DB
                .build();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return jdbcTemplate;
    }
}
