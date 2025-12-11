package com.dlu.mtjbysj.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FzwpRepository extends JpaRepository<Fzwp, Long> {

    // 后台管理分页搜索：按名称或应对病害模糊查询
    Page<Fzwp> findByItemNameContainingIgnoreCaseOrTargetDiseaseContainingIgnoreCase(
            String itemName,
            String targetDisease,
            Pageable pageable);

    // 根据植物名称和病害名称精确匹配推荐物品
    List<Fzwp> findByPlantNameAndTargetDisease(String plantName, String targetDisease);

    // 仅按植物名称匹配
    List<Fzwp> findByPlantName(String plantName);

    // 仅按病害名称匹配
    List<Fzwp> findByTargetDisease(String targetDisease);
}
