package com.samplemvcwebapp.datalayer.repositories;

import com.samplemvcwebapp.datalayer.entities.Blog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlogRepository extends JpaRepository<Blog, Integer> {

    /** Projection for the Blogs list (legacy BlogListDto with PostsCount aggregate). */
    interface BlogWithPostsCount {
        Integer getBlogId();
        String getName();
        String getEmailAddress();
        Integer getPostsCount();
    }

    List<Blog> findAllByOrderByBlogIdAsc();

    @Query("select b.blogId as blogId, b.name as name, b.emailAddress as emailAddress, size(b.posts) as postsCount from Blog b order by b.blogId")
    List<BlogWithPostsCount> findAllWithPostsCount();
}
