package com.samplemvcwebapp.datalayer.repositories;

import com.samplemvcwebapp.datalayer.entities.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Integer> {
}
