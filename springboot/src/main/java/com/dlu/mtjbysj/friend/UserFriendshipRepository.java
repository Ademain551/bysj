package com.dlu.mtjbysj.friend;

import com.dlu.mtjbysj.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFriendshipRepository extends JpaRepository<UserFriendship, Long> {

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM UserFriendship f " +
            "WHERE (f.user = :a AND f.friend = :b) OR (f.user = :b AND f.friend = :a)")
    boolean existsBetween(@Param("a") User a, @Param("b") User b);

    @Query("SELECT CASE WHEN f.user = :user THEN f.friend ELSE f.user END FROM UserFriendship f " +
            "WHERE f.user = :user OR f.friend = :user")
    List<User> findFriendsOf(@Param("user") User user);
}
