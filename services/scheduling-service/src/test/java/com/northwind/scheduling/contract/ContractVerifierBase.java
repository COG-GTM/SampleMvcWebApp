package com.northwind.scheduling.contract;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.importer.SnapshotImporter;

import io.restassured.RestAssured;

/**
 * Provider side of the consumer contracts in src/test/resources/contracts (MBA-3833): the generated *ContractIT
 * tests call the booted service over real HTTP (EXPLICIT mode) with the fixture inventory reset before each contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
public abstract class ContractVerifierBase extends SqlServerTestBase {

    @LocalServerPort
    int port;
    @Autowired
    SnapshotImporter importer;

    @BeforeEach
    void resetInventoryAndPointRestAssuredAtService() {
        importer.run(SnapshotImporter.Mode.RESET);
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }
}
