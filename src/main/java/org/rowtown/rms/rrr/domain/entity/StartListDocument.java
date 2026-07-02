package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A regatta's Start List — one per regatta edition (regatta name + start date).
 *
 * <p>The start list model contains all of the regatta's races; individual races
 * are not separate documents.</p>
 */
@Entity
@DiscriminatorValue("START_LIST")
@Getter
@Setter
@NoArgsConstructor
public class StartListDocument extends Document {
    // No fields beyond the base; a start list is keyed solely by its regatta.
}
