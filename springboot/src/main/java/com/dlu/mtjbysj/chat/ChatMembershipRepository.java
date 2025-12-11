package com.dlu.mtjbysj.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMembershipRepository extends JpaRepository<ChatMembership, Long> {
    List<ChatMembership> findByRoomId(Long roomId);
    boolean existsByRoomIdAndUser_Username(Long roomId, String username);
    boolean existsByRoomIdAndUser_Id(Long roomId, Long userId);
}