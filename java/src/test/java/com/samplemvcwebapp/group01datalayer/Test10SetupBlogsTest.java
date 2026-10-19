package com.samplemvcwebapp.group01datalayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.testsupport.DataLayerSliceConfig;
import com.samplemvcwebapp.testsupport.SeedXml;
import com.samplemvcwebapp.testsupport.SeedXml.SeedBlog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** Port of Tests/UnitTests/Group01DataLayer/Test10SetupBlogs.cs (5 tests). */
@DataJpaTest
@Import(DataLayerSliceConfig.class)
class Test10SetupBlogsTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired BlogRepository blogs;
    @Autowired PostRepository posts;
    @Autowired TagRepository tags;

    @Test
    void check01XmlFileLoadOk() {
        List<SeedBlog> bloggers = SeedXml.formBlogsWithPosts(TestDataSelection.SMALL.getClasspathResource());

        assertThat(bloggers).hasSize(2);
        assertThat(bloggers.stream().mapToLong(b -> b.posts().size()).sum()).isEqualTo(3);
        assertThat(bloggers.stream().flatMap(b -> b.posts().stream()).flatMap(p -> p.tagSlugs().stream()).distinct().count())
                .isEqualTo(3);
    }

    @Test
    void check02XmlFileLoadBad() {
        assertThatThrownBy(() -> SeedXml.formBlogsWithPosts("badname.xml"))
                .hasMessageStartingWith("Could not find the xml file you asked for.");
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
