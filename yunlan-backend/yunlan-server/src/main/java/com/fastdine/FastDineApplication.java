package com.fastdine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement //开启注解方式的事务管理
@EnableCaching //开启缓存注解功能（C 端商品浏览走 Spring Cache + Redis）
@EnableScheduling //开启定时任务（订单超时未支付自动取消、派送中超时自动完成）
@Slf4j
public class FastDineApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastDineApplication.class, args);
        log.info("server started");
    }
}
