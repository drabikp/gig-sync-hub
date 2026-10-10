package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** JPA persistence model for one of a band's settings (table {@code band_setting}). */
@Entity
@Table(name = "band_setting")
@IdClass(BandSettingEntity.Key.class)
class BandSettingEntity {

    static final class Key implements Serializable {

        private UUID bandId;
        private String name;

        Key() {
            // for JPA
        }

        Key(UUID bandId, String name) {
            this.bandId = bandId;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(bandId, k.bandId) && Objects.equals(name, k.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bandId, name);
        }
    }

    @Id
    private UUID bandId;
    @Id
    private String name;
    @Column(name = "setting_value")
    private String value;

    protected BandSettingEntity() {
        // for JPA
    }

    BandSettingEntity(UUID bandId, String name, String value) {
        this.bandId = bandId;
        this.name = name;
        this.value = value;
    }

    UUID getBandId() {
        return bandId;
    }

    String getValue() {
        return value;
    }
}
