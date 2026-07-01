package org.rowtown.rms.rrr.config;

import com.caucho.hessian.server.HessianServlet;
import org.rowtown.rms.rrr.hessian.RepositoryService;
import org.rowtown.rms.rrr.hessian.RepositoryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Hessian RPC endpoint.
 *
 * <p>Disabled under the "openapi" Spring profile: the Hessian servlet is loaded
 * on startup and instantiates its service class directly, which is unnecessary
 * for exporting the REST OpenAPI spec (Hessian is a separate binary RPC protocol
 * and is not represented in the spec).</p>
 */
@Configuration
@Profile("!openapi")
public class HessianConfig {

    @Autowired
    private RepositoryServiceImpl repositoryService;

    @Bean
    public ServletRegistrationBean<HessianServlet> hessianServlet() {
        HessianServlet hessianServlet = new HessianServlet();

        ServletRegistrationBean<HessianServlet> registrationBean = new ServletRegistrationBean<HessianServlet>(hessianServlet, "/hessian/repository");
        registrationBean.addInitParameter("service-class", RepositoryService.class.getName());
        registrationBean.addInitParameter("home-class", RepositoryServiceImpl.class.getName());
        registrationBean.addInitParameter("home-api", RepositoryService.class.getName());
        registrationBean.setLoadOnStartup(1);

        return registrationBean;
    }
}
