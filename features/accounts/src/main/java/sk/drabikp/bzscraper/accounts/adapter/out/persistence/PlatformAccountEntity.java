package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA persistence model for a band's platform account (table {@code band_platform_account}): its
 * settings as JSON, its secrets sealed ({@code secrets}, with {@code keyId}).
 */
@Entity
@Table(name = "band_platform_account")
@IdClass(PlatformAccountEntity.Key.class)
class PlatformAccountEntity {

    static final class Key implements Serializable {

        private UUID bandId;
        private String platform;

        Key() {
            // for JPA
        }

        Key(UUID bandId, String platform) {
            this.bandId = bandId;
            this.platform = platform;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(bandId, k.bandId) && Objects.equals(platform, k.platform);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bandId, platform);
        }
    }

    @Id
    private UUID bandId;
    @Id
    private String platform;
    private String settings;
    private byte[] secrets;
    private String keyId;
    private Instant checkedAt;
    private Boolean checkOk;
    private String checkMessage;
    private Instant updatedAt;

    public PlatformAccountEntity() {
        // for JPA
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public byte[] getSecrets() {
        return secrets;
    }

    public void setSecrets(byte[] secrets) {
        this.secrets = secrets;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public Boolean getCheckOk() {
        return checkOk;
    }

    public void setCheckOk(Boolean checkOk) {
        this.checkOk = checkOk;
    }

    public String getCheckMessage() {
        return checkMessage;
    }

    public void setCheckMessage(String checkMessage) {
        this.checkMessage = checkMessage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
