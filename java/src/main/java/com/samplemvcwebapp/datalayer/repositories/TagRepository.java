package com.samplemvcwebapp.datalayer.repositories;

import com.samplemvcwebapp.datalayer.entities.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Integer> {

    /** Projection for the Tags list (legacy TagListDto with PostsCount aggregate). */
    interface TagWithPostsCount {
        Integer getTagId();
        String getName();
        String getSlug();
        Integer getPostsCount();
    }

    List<Tag> findAllByOrderByTagIdAsc();

    List<Tag> findByTagIdIn(Iterable<Integer> tagIds);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndTagIdNot(String slug, Integer tagId);

    @Query("select t.tagId as tagId, t.name as name, t.slug as slug, size(t.posts) as postsCount from Tag t order by t.tagId")
    List<TagWithPostsCount> findAllWithPostsCount();
}
