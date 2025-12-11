package com.dlu.mtjbysj.detect;

import com.dlu.mtjbysj.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "detect_error_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectErrorFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "detect_result_id")
    private DetectResult detectResult;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "farmer_id")
    private User farmer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "expert_id")
    private User expert;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "farmer_comment", length = 2000)
    private String farmerComment;

    @Column(name = "expert_comment", length = 2000)
    private String expertComment;

    @Column(name = "reply_message", length = 2000)
    private String replyMessage;

    @Column(name = "correct_plant", length = 64)
    private String correctPlant;

    @Column(name = "correct_disease", length = 128)
    private String correctDisease;

    @Column(name = "correct_model_label", length = 128)
    private String correctModelLabel;

    @Column(name = "added_to_dataset")
    private boolean addedToDataset;

    @Column(name = "retrain_required")
    private boolean retrainRequired;

    @Column(name = "retrain_triggered")
    private boolean retrainTriggered;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING_EXPERT";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
