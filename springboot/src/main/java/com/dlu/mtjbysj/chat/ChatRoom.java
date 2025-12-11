package com.dlu.mtjbysj.chat;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** direct 或 group */
    @Column(nullable = false, length = 16)
    private String type;

    /** 群聊名称，单聊为空 */
    @Column(length = 128)
    private String name;

    /** 单聊的规范化唯一键（如 a|b），群聊为空；用于防止重复创建 */
    @Column(name = "direct_key", unique = true, length = 256)
    private String directKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}