package org.rowtown.rms.rrr.config;

import com.caucho.hessian.server.HessianServlet;
import org.rowtown.rms.rrr.hessian.RepositoryService;
import org.rowtown.rms.rrr.hessian.RepositoryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Hessian RPC endpoint.
 */
@Configuration
public class HessianConfig {

    @Autowired
    private RepositoryServiceImpl repositoryService;

    @Bean
    public ServletRegistrationBean<HessianServlet> hessianServlet() {
        HessianServlet hessianServlet = new HessianServlet();

        ServletRegistrationBean<HessianServlet> registrationBean = new ServletRegistrationBean<>(hessianServlet, "/hessian/repository");
        registrationBean.addInitParameter("service-class", RepositoryService.class.getName());
        registrationBean.addInitParameter("home-class", RepositoryServiceImpl.class.getName());
        registrationBean.addInitParameter("home-api", RepositoryService.class.getName());
        registrationBean.setLoadOnStartup(1);

        return registrationBean;
    }
}
