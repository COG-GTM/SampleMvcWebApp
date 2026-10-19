package com.samplemvcwebapp;

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.servicelayer.services.BlogService;
import com.samplemvcwebapp.servicelayer.services.PostAsyncService;
import com.samplemvcwebapp.servicelayer.services.PostService;
import com.samplemvcwebapp.servicelayer.services.TagAsyncService;
import com.samplemvcwebapp.servicelayer.services.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:contextLoads;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE")
class SampleMvcWebAppApplicationTests {

    // Child A's implementations are not on this branch yet; the coordinator removes these mocks once A merges.
    @MockitoBean PostService postService;
    @MockitoBean PostAsyncService postAsyncService;
    @MockitoBean TagService tagService;
    @MockitoBean TagAsyncService tagAsyncService;
    @MockitoBean BlogService blogService;
    @MockitoBean DataLayerInitialiser dataLayerInitialiser;

    @Test
    void contextLoads() {
    }
}
