package com.quantai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = getDbType(dataSource);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }

    private DbType getDbType(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return JdbcUtils.getDbType(conn.getMetaData().getURL());
        } catch (SQLException e) {
            return DbType.MYSQL;
        }
    }
}
