package com.dlu.mtjbysj.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    /** 查找包含两个用户的单聊房间（简化：通过成员数=2且用户名匹配） */
    @Query("SELECT r FROM ChatRoom r WHERE r.type='direct' AND r.id IN (" +
            "SELECT m1.room.id FROM ChatMembership m1 JOIN ChatMembership m2 ON m1.room.id = m2.room.id " +
            "WHERE m1.user.username = :userA AND m2.user.username = :userB)")
    Optional<ChatRoom> findDirectRoom(@Param("userA") String userA, @Param("userB") String userB);

    /** 通过 directKey 快速查找单聊房间（顺序无关） */
    Optional<ChatRoom> findByDirectKey(String directKey);

    /** 用户参与的所有房间 */
    @Query("SELECT r FROM ChatRoom r WHERE r.id IN (SELECT m.room.id FROM ChatMembership m WHERE m.user.username = :username)")
    List<ChatRoom> findByMember(@Param("username") String username);

    /** 按类型和名称查找房间，用于系统通知群聊等特殊房间 */
    Optional<ChatRoom> findByTypeAndName(String type, String name);
}