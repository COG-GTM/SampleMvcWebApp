package com.samplemvcwebapp.group01datalayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.SeedFiles;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.testsupport.DataLayerSliceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of Tests/UnitTests/Group01DataLayer/Test10SetupBlogs.cs (5 tests). Not wrapped in a test transaction: like the
 * legacy fixture (one DbContext per reset) and the /Posts/Reset action, each resetBlogs call runs in its own transaction.
 */
@DataJpaTest
@Import(DataLayerSliceConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Test10SetupBlogsTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired BlogRepository blogs;
    @Autowired PostRepository posts;
    @Autowired TagRepository tags;

    @Test
    void check01XmlFileLoadOk() {
        SeedFiles.Counts loaded = SeedFiles.load(TestDataSelection.SMALL.getClasspathResource());

        assertThat(loaded.blogs()).isEqualTo(2);
        assertThat(loaded.posts()).isEqualTo(3);
        assertThat(loaded.distinctTagSlugs()).isEqualTo(3);
    }

    @Test
    void check02XmlFileLoadBad() {
        assertThatThrownBy(() -> SeedFiles.load("badname.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not load seed data from badname.xml");
    }

    @Test
    void check10BlogsResetSmallOk() {
        initialiser.resetBlogs(TestDataSelection.SMALL);

        assertThat(blogs.count()).isEqualTo(2);
        assertThat(posts.count()).isEqualTo(3);
        assertThat(tags.count()).isEqualTo(3);
    }

    @Test
    void check11BlogsResetMediumOk() {
        initialiser.resetBlogs(TestDataSelection.MEDIUM);

        assertThat(blogs.count()).isEqualTo(4);
        assertThat(posts.count()).isEqualTo(17);
        assertThat(tags.count()).isEqualTo(8);
    }

    /** Legacy: reset with the EF "null initialiser" on an existing database. Java: reset an already-populated database. */
    @Test
    void check20NullInitialiserOk() {
        initialiser.resetBlogs(TestDataSelection.MEDIUM);
        check10BlogsResetSmallOk();

        initialiser.resetBlogs(TestDataSelection.SMALL);

        assertThat(blogs.count()).isEqualTo(2);
        assertThat(posts.count()).isEqualTo(3);
        assertThat(tags.count()).isEqualTo(3);
    }
}
