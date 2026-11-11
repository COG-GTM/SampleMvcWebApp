package com.northwind.scheduling.reference;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Replicated reference entities (contract section 5). Columns are the ReferenceUpsert data fields. */
public enum RefEntity {
    EXAMS("exams", "Exam", "RefExam", "ExamId",
            col("ExamCode", 'S'), col("Title", 'S'), col("OwnerName", 'S'), col("DurationMinutes", 'I'),
            col("PassingScore", 'I'), col("RetakeWaitDays", 'I'), col("IsPublished", 'B')),
    TEST_CENTERS("test-centers", "TestCenter", "RefTestCenter", "TestCenterId",
            col("CenterCode", 'S'), col("Name", 'S'), col("City", 'S'), col("Region", 'S'), col("TimeZone", 'S'),
            col("IsActive", 'B')),
    ROOMS("rooms", "Room", "RefRoom", "RoomId",
            col("TestCenterId", 'I'), col("RoomName", 'S'), col("SeatCount", 'I'), col("AccommodationSeats", 'I'),
            col("IsActive", 'B')),
    CANDIDATES("candidates", "Candidate", "RefCandidate", "CandidateId",
            col("CandidateNumber", 'S'), col("FirstName", 'S'), col("LastName", 'S'), col("Email", 'S'),
            col("DefaultAccommodationCode", 'S'));

    public record Column(String name, char type) {
        public String jsonName() {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
    }

    private static Column col(String name, char type) {
        return new Column(name, type);
    }

    public final String path;
    public final String sourceTable;
    public final String replicaTable;
    public final String key;
    public final List<Column> columns;

    RefEntity(String path, String sourceTable, String replicaTable, String key, Column... columns) {
        this.path = path;
        this.sourceTable = sourceTable;
        this.replicaTable = replicaTable;
        this.key = key;
        this.columns = List.of(columns);
    }

    public static Optional<RefEntity> byPath(String path) {
        return Arrays.stream(values()).filter(e -> e.path.equalsIgnoreCase(path)).findFirst();
    }

    String columnList() {
        return String.join(", ", columns.stream().map(Column::name).toList());
    }
}
