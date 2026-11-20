package com.mowoo.mowoo_backend.user.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class UpdateUserProfileRequest {

    @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 50)
    @Pattern(regexp = "(?s).*\\S.*")
    private String nickname;

    @Email
    @Size(max = 254)
    private String email;

    private LocalDate birthDate;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean usePastDiaries;

    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean eveningReminderEnabled;

    @JsonIgnore
    private boolean emailSpecified;

    @JsonIgnore
    private boolean birthDateSpecified;

    @JsonSetter("email")
    public void setEmail(String email) {
        this.email = email;
        this.emailSpecified = true;
    }

    @JsonSetter("birthDate")
    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        this.birthDateSpecified = true;
    }
}
