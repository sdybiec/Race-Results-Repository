package org.rowtown.rms.rrr.config;

import org.eclipse.emf.ecore.EPackage;
import org.rowtown.rms.rrr.service.DefaultModelResourceSetFactory;
import org.rowtown.rms.rrr.service.ModelResourceSetFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Provides the default {@link ModelResourceSetFactory}.
 *
 * <p>Marked {@link ConditionalOnMissingBean}, so an application that supplies its
 * own {@code ModelResourceSetFactory} bean (e.g. one backed by an
 * {@code UpgradingResourceSet} that loads and auto-upgrades older model versions)
 * transparently overrides the default — no other code changes required.</p>
 */
@Configuration
public class ModelResourceSetConfig {

    @Bean
    @ConditionalOnMissingBean(ModelResourceSetFactory.class)
    public ModelResourceSetFactory modelResourceSetFactory(List<EPackage> modelPackages) {
        return new DefaultModelResourceSetFactory(modelPackages);
    }
}
