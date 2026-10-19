package com.samplemvcwebapp.datalayer.repositories;

import com.samplemvcwebapp.datalayer.entities.Blog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Blog, Integer> {
}
