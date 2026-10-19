package com.samplemvcwebapp.datalayer.repositories;

import com.samplemvcwebapp.datalayer.entities.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Integer> {

    /** All posts in PostId order with blogger and tags fetched in one query. */
    @EntityGraph(attributePaths = {"blogger", "tags"})
    List<Post> findAllByOrderByPostIdAsc();

    /** Legacy list has no ORDER BY; SQL Server returns posts grouped by blog, then insertion order. */
    @EntityGraph(attributePaths = {"blogger", "tags"})
    List<Post> findAllByOrderByBlogger_BlogIdAscPostIdAsc();

    /** One blogger's posts in PostId order with blogger and tags fetched. */
    @EntityGraph(attributePaths = {"blogger", "tags"})
    List<Post> findByBlogger_BlogIdOrderByPostIdAsc(Integer blogId);

    /** A single post with blogger and tags fetched. */
    @EntityGraph(attributePaths = {"blogger", "tags"})
    Optional<Post> findWithBloggerAndTagsByPostId(Integer postId);
}
