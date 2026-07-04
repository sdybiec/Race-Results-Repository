package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A regatta's definition — the RML (Regatta Modeling Language) document, one per
 * regatta edition (regatta name + start date).
 *
 * <p>The regatta key ({@code regattaId} + {@code regattaStartDate}) is derived
 * from the RML model itself ({@code Regatta.name} and {@code Regatta.startDate})
 * and is authoritative; see {@code DocumentManagerService}.</p>
 */
@Entity
@DiscriminatorValue("RML")
@Getter
@Setter
@NoArgsConstructor
public class RegattaDefinitionDocument extends Document {
    // No fields beyond the base; keyed solely by its regatta edition.
}
