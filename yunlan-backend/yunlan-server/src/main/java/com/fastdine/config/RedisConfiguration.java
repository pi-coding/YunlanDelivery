package com.fastdine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 * <p>
 * 默认的 RedisTemplate 用 JDK 序列化 key，存进去的 key 会变成形如
 * \xac\xed\x00\x05t\x00\x0bSHOP_STATUS 的二进制，redis-cli 里既看不见也改不了。
 * 这里把 key / hashKey 换成 StringRedisSerializer，value 仍用默认 JDK 序列化，
 * 于是 shop 状态在 Redis 里就是可读的 SHOP_STATUS。
 */
@Configuration
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
