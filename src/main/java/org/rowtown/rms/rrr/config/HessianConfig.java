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

        // Inject the fully-wired Spring bean as the Hessian service implementation.
        // This must be done instead of the "home-class"/"service-class" init
        // parameters: those make HessianServlet.init() instantiate the class via a
        // no-arg constructor, which fails because RepositoryServiceImpl uses
        // constructor injection. When the home object is pre-set, init() skips all
        // instantiation and uses this bean (with its dependencies) directly.
        hessianServlet.setHome(repositoryService);
        hessianServlet.setHomeAPI(RepositoryService.class);

        ServletRegistrationBean<HessianServlet> registrationBean =
            new ServletRegistrationBean<>(hessianServlet, "/hessian/repository");
        registrationBean.setLoadOnStartup(1);

        return registrationBean;
    }
}
