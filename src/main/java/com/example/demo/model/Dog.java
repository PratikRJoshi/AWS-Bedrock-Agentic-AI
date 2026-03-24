package com.example.demo.model;

/**
 * DOMAIN MODEL — represents a single dog in the adoption system.
 *
 * This is a Java Record (introduced in Java 16, used here with Java 17).
 *
 * WHY a record instead of a regular class?
 *   — A record auto-generates: constructor, getters (id(), name(), etc.), equals(),
 *     hashCode(), and toString(). This eliminates ~40 lines of boilerplate.
 *   — Records are immutable by design — fields are final. This is ideal for data carriers
 *     that represent a database row: you read it, use it, but don't mutate it.
 *   — Spring Data JDBC maps records to database tables automatically — the field names
 *     (id, name, owner, description) must match the column names in the 'dog' table.
 *
 * WHY a record instead of Lombok's @Data?
 *   — Records are a language feature (no extra dependency), whereas Lombok is a compile-time
 *     annotation processor that can cause issues with certain IDEs and build tools.
 *   — Records enforce immutability; @Data creates mutable objects with setters.
 *
 * WHY not a JPA @Entity?
 *   — This project uses Spring Data JDBC, not JPA/Hibernate. JDBC is lighter:
 *     no lazy loading, no dirty checking, no entity state machine. The record simply
 *     maps 1:1 to a SQL row. For a microservice with simple data access, JDBC is
 *     faster to reason about and has fewer surprises than JPA's proxy-based magic.
 *
 * FIELDS:
 *   id          — auto-generated primary key from the dog_id_seq sequence in PostgreSQL
 *   name        — the dog's name (e.g., "Buddy")
 *   owner       — the current owner's name; NULL means the dog is available for adoption
 *   description — a text description used for both display AND vector embedding in RAG
 *                  (this text gets turned into a vector by Cohere for semantic search)
 */
public record Dog(int id, String name, String owner, String description){

}
