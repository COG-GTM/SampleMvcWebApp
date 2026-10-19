package com.samplemvcwebapp.datalayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:seedOnStartupLayerTest;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE",
        "app.seed-on-startup=true"})
class SeedOnStartupLayerTest {

    @Autowired BlogRepository blogRepository;
    @Autowired PostRepository postRepository;
    @Autowired TagRepository tagRepository;

    @Test
    void seedsMediumWhenDatabaseIsEmpty() {
        assertThat(blogRepository.count()).isEqualTo(4);
        assertThat(postRepository.count()).isEqualTo(17);
        assertThat(tagRepository.count()).isEqualTo(8);
    }
}
