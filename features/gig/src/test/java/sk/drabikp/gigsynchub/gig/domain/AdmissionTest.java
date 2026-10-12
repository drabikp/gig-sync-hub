package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionTest {

    @Test
    void free_and_voluntary_carry_no_amount() {
        assertThat(Admission.free().type()).isEqualTo(EntryType.FREE);
        assertThat(Admission.free().amount()).isNull();
        assertThat(Admission.free().isPaid()).isFalse();
        assertThat(Admission.voluntary().type()).isEqualTo(EntryType.VOLUNTARY);
        assertThat(Admission.voluntary().amount()).isNull();
    }

    @Test
    void paid_carries_its_amount() {
        Admission paid = Admission.paid("150 Kč");
        assertThat(paid.type()).isEqualTo(EntryType.PAID);
        assertThat(paid.amount()).isEqualTo("150 Kč");
        assertThat(paid.isPaid()).isTrue();
    }

    @Test
    void paid_requires_a_non_blank_amount() {
        assertThatThrownBy(() -> Admission.paid(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Admission.paid("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_non_paid_admission_must_not_carry_an_amount() {
        assertThatThrownBy(() -> new Admission(EntryType.FREE, "150"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void type_is_required() {
        assertThatThrownBy(() -> new Admission(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
