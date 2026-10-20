package com.fastdine.config;

import com.fastdine.interceptor.JwtTokenAdminInterceptor;
import com.fastdine.interceptor.JwtTokenUserInterceptor;
import com.fastdine.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.nio.file.Paths;
import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 本地文件存储目录
     */
    @Value("${fastdine.upload.path:upload}")
    private String uploadPath;

    /**
     * 本地文件访问前缀
     */
    @Value("${fastdine.upload.url-prefix:/upload}")
    private String uploadUrlPrefix;

    /**
     * 允许跨域访问的来源，多个用逗号分隔，默认 * （开发阶段便于前端独立启动调试）
     */
    @Value("${fastdine.cors.allowed-origins:*}")
    private String corsAllowedOrigins;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        //管理端
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");
        //用户端（微信小程序），登录接口放行
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login");
    }

    /**
     * 跨域配置。
     * 生产环境如果前后端同源（本服务直接托管前端页面），可以把 fastdine.cors.allowed-origins 改成具体域名。
     *
     * @param registry
     */
    protected void addCorsMappings(CorsRegistry registry) {
        log.info("开始注册跨域配置，允许来源：{}", corsAllowedOrigins);
        registry.addMapping("/**")
                //allowedOriginPatterns 才能在 allowCredentials=true 时支持通配符
                .allowedOriginPatterns(corsAllowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("token")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 通过knife4j生成接口文档
     * @return
     */
    @Bean
    public Docket docket() {
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("云岚外卖项目接口文档")
                .version("2.0")
                .description("云岚外卖项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.fastdine.controller"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        //管理端前端页面（由本服务直接托管，无需额外部署nginx）
        //no-cache + must-revalidate：浏览器每次都要回来校验（命中则 304，几乎无开销）。
        //不加的话前端改了 dist、文件名 hash 又没变，浏览器会一直吃旧缓存，改完看不到效果。
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
        //本地文件上传目录映射（图片等静态资源）
        registry.addResourceHandler(uploadUrlPrefix + "/**")
                .addResourceLocations(Paths.get(uploadPath).toAbsolutePath().normalize().toUri().toString());
    }

    /**
     * 扩展mvc框架的消息转换器，统一对日期类型进行格式化处理
     * @param converters
     */
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("开始扩展消息转换器...");

        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        //设置对象转换器，可以将Java对象转为json字符串
        converter.setObjectMapper(new JacksonObjectMapper());

        //将我们自己的转换器放入spring MVC框架的容器中
        converters.add(0, converter);
    }
}
