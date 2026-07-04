package org.rowtown.rms.rrr.config;

import org.eclipse.emf.ecore.EPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.humanfactor.rw.model.regatta.RegattaPackage;
import com.humanfactor.rw.model.reportdesigner.ReportDesignerPackage;

/**
 * Wires the generated RML (Regatta Modeling Language) EMF model — and the
 * ReportDesign model it depends on — into the application context.
 *
 * <p><strong>This is the single place that references the generated
 * {@code rml-model} artifact.</strong> If the artifact's Maven coordinates or
 * its generated Java package/class names differ from the assumptions here,
 * change only:</p>
 * <ol>
 *   <li>the {@code import}s and {@code eINSTANCE} references below, and</li>
 *   <li>the {@code rml.model.*} coordinates in {@code pom.xml}.</li>
 * </ol>
 *
 * <p>RML {@code Regatta} references types from the ReportDesign model, so both
 * {@link EPackage}s must be registered for an RML document to load into the
 * typed generated classes. They are registered per-{@code ResourceSet} by
 * {@link org.rowtown.rms.rrr.service.ModelSerializationService}.</p>
 */
@Configuration
public class RmlModelConfig {

    /**
     * Namespace URI of the RML model (see {@code regatta.ecore}).
     */
    public static final String RML_NS_URI = "http://www.rowtown.org/RML/1.4.0";

    /**
     * Namespace URI of the ReportDesign model that RML depends on
     * (see {@code reportdesigner.ecore}).
     */
    public static final String REPORT_DESIGN_NS_URI = "http://www.rowtown.org/ReportDesign/1.0.1";

    /**
     * The generated RML {@link EPackage}.
     */
    @Bean
    public EPackage rmlPackage() {
        return RegattaPackage.eINSTANCE;
    }

    /**
     * The generated ReportDesign {@link EPackage} (RML depends on it).
     */
    @Bean
    public EPackage reportDesignPackage() {
        return ReportDesignerPackage.eINSTANCE;
    }
}
