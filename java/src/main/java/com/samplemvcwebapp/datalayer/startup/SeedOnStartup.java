package com.samplemvcwebapp.datalayer.startup;

import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** app.seed-on-startup=true: seed MEDIUM when the database is empty (legacy always starts empty, so default false). */
@Component
@ConditionalOnProperty(name = "app.seed-on-startup", havingValue = "true")
public class SeedOnStartup implements ApplicationRunner {

    private final DataLayerInitialiser initialiser;
    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    public SeedOnStartup(DataLayerInitialiser initialiser, BlogRepository blogRepository,
                         PostRepository postRepository, TagRepository tagRepository) {
        this.initialiser = initialiser;
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (blogRepository.count() == 0 && postRepository.count() == 0 && tagRepository.count() == 0)
            initialiser.resetBlogs(TestDataSelection.MEDIUM);
    }
}
