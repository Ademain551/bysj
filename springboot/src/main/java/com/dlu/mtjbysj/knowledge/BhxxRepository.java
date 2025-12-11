package com.dlu.mtjbysj.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 植物病害分布情况表 Repository
 */
@Repository
public interface BhxxRepository extends JpaRepository<Bhxx, Long> {
    
    /**
     * 根据植物名称查找
     */
    List<Bhxx> findByPlantName(String plantName);
    
    /**
     * 根据植物名称分页查询
     */
    Page<Bhxx> findByPlantName(String plantName, Pageable pageable);
    
    /**
     * 根据病害名称查找
     */
    List<Bhxx> findByDiseaseName(String diseaseName);
    
    /**
     * 根据病害名称分页查询
     */
    Page<Bhxx> findByDiseaseName(String diseaseName, Pageable pageable);
    
    /**
     * 根据植物名称模糊查询
     */
    List<Bhxx> findByPlantNameContainingIgnoreCase(String plantName);
    
    /**
     * 根据病害名称模糊查询
     */
    List<Bhxx> findByDiseaseNameContainingIgnoreCase(String diseaseName);
    
    /**
     * 根据植物名称和病害名称查找
     */
    Optional<Bhxx> findByPlantNameAndDiseaseName(String plantName, String diseaseName);
    
    /**
     * 根据分布区域模糊查询
     */
    @Query("SELECT b FROM Bhxx b WHERE b.distributionArea LIKE %:area%")
    List<Bhxx> findByDistributionAreaContaining(@Param("area") String area);
    
    /**
     * 根据植物名称或病害名称模糊查询（分页）
     */
    @Query("SELECT b FROM Bhxx b WHERE b.plantName LIKE %:keyword% OR b.diseaseName LIKE %:keyword%")
    Page<Bhxx> findByPlantNameOrDiseaseNameContaining(@Param("keyword") String keyword, Pageable pageable);
}

