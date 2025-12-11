package com.dlu.mtjbysj.knowledge;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 植物病害分布情况表实体类
 */
@Entity
@Table(name = "bhxx")
public class Bhxx {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "植物名称", length = 100)
    private String plantName;
    
    @Column(name = "病害名称", length = 200)
    private String diseaseName;
    
    @Column(name = "分布区域", columnDefinition = "TEXT")
    private String distributionArea;
    
    @Column(name = "分布时间", length = 200)
    private String distributionTime;
    
    @Column(name = "防治方法", columnDefinition = "TEXT")
    private String preventionMethod;
    
    @Column(name = "创建时间")
    private LocalDateTime createdAt;
    
    @Column(name = "更新时间")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPlantName() {
        return plantName;
    }
    
    public void setPlantName(String plantName) {
        this.plantName = plantName;
    }
    
    public String getDiseaseName() {
        return diseaseName;
    }
    
    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }
    
    public String getDistributionArea() {
        return distributionArea;
    }
    
    public void setDistributionArea(String distributionArea) {
        this.distributionArea = distributionArea;
    }
    
    public String getDistributionTime() {
        return distributionTime;
    }
    
    public void setDistributionTime(String distributionTime) {
        this.distributionTime = distributionTime;
    }
    
    public String getPreventionMethod() {
        return preventionMethod;
    }
    
    public void setPreventionMethod(String preventionMethod) {
        this.preventionMethod = preventionMethod;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

