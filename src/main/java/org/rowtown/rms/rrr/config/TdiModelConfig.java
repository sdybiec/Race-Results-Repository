package org.rowtown.rms.rrr.config;

import org.eclipse.emf.ecore.EPackage;
import org.rowtown.rms.tdi.TimingDataInterchangePackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the generated TDI (Timing Data Interchange) EMF model into the
 * application context.
 *
 * <p>
 * <strong>This is the single place that references the generated model artifact
 * ({@code tdi-model}).</strong> If the artifact's Maven coordinates or its
 * generated Java package/class names differ from the assumptions here, change
 * only:
 * </p>
 * <ol>
 * <li>the {@code import} and {@code eINSTANCE} reference below, and</li>
 * <li>the {@code tdi.model.*} coordinates in {@code pom.xml}.</li>
 * </ol>
 *
 * <p>
 * The generated package is registered per-{@code ResourceSet} by
 * {@link org.rowtown.rms.rrr.service.ModelSerializationService} rather than in
 * the global {@link EPackage.Registry#INSTANCE}, so no JVM-wide mutable state
 * is introduced and tests stay isolated.
 * </p>
 */
@Configuration
public class TdiModelConfig {

	/**
	 * Namespace URI of the TDI model.
	 *
	 * <p>
	 * Must match {@code nsURI} in {@code timingdatainterchange.ecore} and the
	 * {@code xmlns:tdi} of stored documents. Changing the model's {@code nsURI} in
	 * an incompatible way means previously stored documents will no longer load as
	 * typed objects — see {@code docs/tdi-model-integration.md} for the
	 * version-skew / migration policy.
	 * </p>
	 */
	public static final String TDI_NS_URI = "http://www.rowtown.org/TDI/1.0.0";

	/**
	 * Exposes the generated TDI {@link EPackage} as a bean so serialization and
	 * validation can register it. Referencing {@code eINSTANCE} triggers the
	 * package's static initialization, which registers its generated factory and
	 * implementation classes so XMI loads into the typed generated classes (e.g.
	 * {@code TimingRegatta}) rather than dynamic {@code EObject}s.
	 */
	@Bean
	public EPackage tdiPackage() {
		return TimingDataInterchangePackage.eINSTANCE;
	}
}
