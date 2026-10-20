package com.fastdine.filter;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * 管理端前端（dist）中的请求统一以 /api 开头，
 * 这里把 /api/** 重写为 /admin/**，使前端可以直接由本服务托管，无需额外部署 nginx。
 */
@Component
public class ApiPathRewriteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            chain.doFilter(new RewriteRequest(req, "/admin/" + uri.substring("/api/".length())), response);
        } else if ("/".equals(uri) || uri == null || uri.isEmpty()) {
            //访问根路径时直接渲染管理端首页
            chain.doFilter(new RewriteRequest(req, "/index.html"), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class RewriteRequest extends HttpServletRequestWrapper {

        private final String newUri;

        RewriteRequest(HttpServletRequest request, String newUri) {
            super(request);
            this.newUri = newUri;
        }

        @Override
        public String getRequestURI() {
            return newUri;
        }

        @Override
        public String getServletPath() {
            return newUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(getScheme() + "://" + getServerName() + ":" + getServerPort() + newUri);
        }
    }
}
