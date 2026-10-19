package com.samplemvcwebapp.group03servicelayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.services.BlogService;
import com.samplemvcwebapp.servicelayer.services.PostAsyncService;
import com.samplemvcwebapp.servicelayer.services.PostService;
import com.samplemvcwebapp.servicelayer.services.TagAsyncService;
import com.samplemvcwebapp.servicelayer.services.TagService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Port of Tests/UnitTests/Group03ServiceLayer/Test11AutoFacModules.cs (5 tests). The Autofac DataLayerModule,
 * ServiceLayerModule and MVC AutofacDependencyResolver setup are replaced by Spring Boot auto-configuration and
 * component scanning, so each test asserts the equivalent bean wiring in the application context:
 * InstancePerLifetimeScope DbContext = transaction-scoped EntityManager/Session; GenericServices IListService etc.
 * = Spring Data JpaRepository&lt;Entity, Integer&gt; resolved by generic type.
 */
@SpringBootTest
@ActiveProfiles("test")
class Test11AutoFacModulesTest {

    @Autowired ApplicationContext context;
    @Autowired EntityManagerFactory emf;
    @Autowired TransactionTemplate tx;

    @Test
    void checkSetupDbContextLifetimeScopeItems() {
        EntityManager[] firstScope = new EntityManager[2];
        tx.executeWithoutResult(s -> {
            firstScope[0] = transactionalEntityManager();
            firstScope[1] = transactionalEntityManager();
        });
        EntityManager secondScope = tx.execute(s -> transactionalEntityManager());

        assertThat(firstScope[0]).isNotNull();
        assertThat(firstScope[0]).isSameAs(firstScope[1]);
        assertThat(secondScope).isNotSameAs(firstScope[0]);
    }

    @Test
    void test10ServiceSetupServiceLayer() {
        checkExampleServicesResolve();
    }

    @Test
    void test15SetupServiceLayerDirectGenerics() {
        assertThat(postRepository()).isNotNull();
    }

    @Test
    void test16UseServiceLayerDirectGenerics() {
        context.getBean(DataLayerInitialiser.class).resetBlogs(TestDataSelection.SMALL);

        assertThat(postRepository().findAll()).hasSize(3);
        assertThat(context.getBean(PostService.class).listPosts(null)).hasSize(3);
    }

    @Test
    void test20ViaMvcSetup() {
        assertThat(context).isInstanceOf(WebApplicationContext.class);
        assertThat(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                .getHandlerMethods()).isNotEmpty();
        checkExampleServicesResolve();
    }

    // ------------------------------------------------------------------ helpers

    /** The EntityManager actually bound to the current transaction (what the shared proxy delegates to). */
    private EntityManager transactionalEntityManager() {
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        assertThat(holder).as("transaction-bound EntityManager").isNotNull();
        return holder.getEntityManager();
    }

    private void checkExampleServicesResolve() {
        for (Class<?> service : new Class<?>[] {PostService.class, PostAsyncService.class, TagService.class,
                TagAsyncService.class, BlogService.class, DataLayerInitialiser.class}) {
            Object first = context.getBean(service);
            assertThat(first).as(service.getSimpleName()).isNotNull();
            assertThat(context.getBean(service)).as("stateless singleton " + service.getSimpleName()).isSameAs(first);
        }
    }

    @SuppressWarnings("unchecked")
    private JpaRepository<Post, Integer> postRepository() {
        return (JpaRepository<Post, Integer>) context.getBeanProvider(
                ResolvableType.forClassWithGenerics(JpaRepository.class, Post.class, Integer.class)).getObject();
    }
}
