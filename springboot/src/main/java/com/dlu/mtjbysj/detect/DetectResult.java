package com.dlu.mtjbysj.detect;

import com.dlu.mtjbysj.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "detect_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "model_label", nullable = false, length = 128)
    private String modelLabel;

    /** FastAPI 原始预测类别（兼容历史字段） */
    @Column(name = "predicted_class", length = 64)
    private String predictedClass;

    /** 冗余存储用户名，便于历史兼容 */
    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "confidence")
    private double confidence;

    @Column(name = "advice", length = 2000)
    private String advice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "image_url", length = 255)
    private String imageUrl;
    
    @Column(name = "report_url", length = 255)
    private String reportUrl;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (username == null && user != null && user.getUsername() != null) {
            username = user.getUsername();
        }
        if (predictedClass == null && modelLabel != null) {
            predictedClass = modelLabel;
        }
    }
    
    // Getter and Setter for reportUrl
    public String getReportUrl() {
        return reportUrl;
    }
    
    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }
}