package com.mowoo.mowoo_backend.emotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "emotion_types", uniqueConstraints = {
        @UniqueConstraint(name = "uq_emotion_types_code", columnNames = "code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmotionType {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "color_hex", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String colorHex;
    @Column(name = "icon_url", length = 2048)
    private String iconUrl;

    public EmotionType(String code, String name, String colorHex, String iconUrl) {
        this.code = code;
        this.name = name;
        this.colorHex = colorHex;
        this.iconUrl = iconUrl;
    }
}
