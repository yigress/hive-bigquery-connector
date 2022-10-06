package com.google.cloud.hive.bigquery.connector;

import java.util.List;

import static com.google.cloud.hive.bigquery.connector.TestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.hive.bigquery.connector.config.HiveBigQueryConfig;
import org.junitpioneer.jupiter.cartesian.CartesianTest;

public class ViewIntegrationTests extends IntegrationTestsBase {

    @CartesianTest
    public void testViewsDisabled(
        @CartesianTest.Values(strings = {"mr", "tez"}) String engine,
        @CartesianTest.Values(strings = {HiveBigQueryConfig.ARROW, HiveBigQueryConfig.AVRO})
            String readDataFormat) {
        // Disable views
        hive.setHiveConfValue(HiveBigQueryConfig.VIEWS_ENABLED_KEY, "false");
        // Create the table in BigQuery
        runBqQuery(BIGQUERY_TEST_TABLE_CREATE_QUERY);
        // Create the corresponding BigQuery view
        createView(dataset, TEST_TABLE_NAME, TEST_VIEW_NAME);
        // Create the corresponding Hive table
        initHive(engine, readDataFormat);
        runHiveScript(HIVE_TEST_VIEW_CREATE_QUERY);
        // Query the view
        Throwable exception =
            assertThrows(
                RuntimeException.class,
                () -> runHiveStatement(String.format("SELECT * FROM %s", TEST_VIEW_NAME)));
        assertTrue(
            exception
                .getMessage()
                .contains("Views are not enabled"));
    }

    @CartesianTest
    public void testReadEmptyView(
        @CartesianTest.Values(strings = {"mr", "tez"}) String engine,
        @CartesianTest.Values(strings = {HiveBigQueryConfig.ARROW, HiveBigQueryConfig.AVRO})
            String readDataFormat) {
        // Enable views
        hive.setHiveConfValue(HiveBigQueryConfig.VIEWS_ENABLED_KEY, "true");
        // Create the table in BigQuery
        runBqQuery(BIGQUERY_TEST_TABLE_CREATE_QUERY);
        // Create the corresponding BigQuery view
        createView(dataset, TEST_TABLE_NAME, TEST_VIEW_NAME);
        // Create the corresponding Hive table
        initHive(engine, readDataFormat);
        runHiveScript(HIVE_TEST_VIEW_CREATE_QUERY);
        // Query the view
        List<Object[]> rows = runHiveStatement(String.format("SELECT * FROM %s", TEST_VIEW_NAME));
        assertThat(rows).isEmpty();
    }

    /** Test the WHERE clause */
    @CartesianTest
    public void testWhereClause(
        @CartesianTest.Values(strings = {"mr", "tez"}) String engine,
        @CartesianTest.Values(strings = {HiveBigQueryConfig.ARROW, HiveBigQueryConfig.AVRO})
            String readDataFormat) {
        // Enable views
        hive.setHiveConfValue(HiveBigQueryConfig.VIEWS_ENABLED_KEY, "true");
        // Create the table in BigQuery
        runBqQuery(BIGQUERY_TEST_TABLE_CREATE_QUERY);
        // Create the corresponding BigQuery view
        createView(dataset, TEST_TABLE_NAME, TEST_VIEW_NAME);
        // Create the corresponding Hive table
        initHive(engine, readDataFormat);
        runHiveScript(HIVE_TEST_VIEW_CREATE_QUERY);
        // Insert data into BQ using the BQ SDK
        runBqQuery(
            String.format(
                "INSERT `${dataset}.%s` VALUES (123, 'hello'), (999, 'abcd')", TEST_TABLE_NAME));
        // Make sure the initial data is there
        TableResult result =
            runBqQuery(String.format("SELECT * FROM `${dataset}.%s`", TEST_VIEW_NAME));
        assertEquals(2, result.getTotalRows());
        // Read filtered view using Hive
        List<Object[]> rows =
            runHiveStatement(
                String.format("SELECT * FROM %s WHERE number = 999", TEST_VIEW_NAME));
        // Verify we get the expected rows
        assertArrayEquals(
            new Object[] {
                new Object[] {999L, "abcd"},
            },
            rows.toArray());
        // TODO: Confirm that the predicate was in fact pushed down to BigQuery
    }
}