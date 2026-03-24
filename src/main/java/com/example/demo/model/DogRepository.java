package com.example.demo.model;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * DATA ACCESS LAYER — provides CRUD operations for the Dog entity.
 *
 * @Repository marks this as a Spring-managed data access component. It also enables
 * automatic exception translation (SQL exceptions → Spring's DataAccessException hierarchy).
 *
 * HOW THIS WORKS:
 *   By extending CrudRepository<Dog, Integer>, Spring Data JDBC auto-generates
 *   implementations for these methods at runtime (no SQL writing needed):
 *     — findAll()       → SELECT id, name, owner, description FROM dog
 *     — findById(id)    → SELECT ... FROM dog WHERE id = ?
 *     — save(dog)       → INSERT INTO dog ... (or UPDATE if id exists)
 *     — deleteById(id)  → DELETE FROM dog WHERE id = ?
 *     — count()         → SELECT COUNT(*) FROM dog
 *     — existsById(id)  → SELECT EXISTS(...)
 *
 *   Spring inspects the Dog record's fields and maps them to columns automatically.
 *   The table name defaults to the class name lowercased ("dog").
 *
 * WHY CrudRepository instead of JpaRepository?
 *   — This project uses Spring Data JDBC (not JPA). JpaRepository adds JPA-specific
 *     methods (flush, saveAndFlush) that don't apply here. CrudRepository is the
 *     correct base interface for JDBC.
 *
 * WHY Spring Data JDBC instead of JPA/Hibernate?
 *   — Simplicity: no lazy loading, no session cache, no proxy objects, no merge/detach
 *     lifecycle. When you call findAll(), you get real Dog records — not proxied entities
 *     that might trigger surprise SQL queries when you access a field.
 *   — Performance: less overhead for simple read-heavy operations like loading dog data.
 *   — Alignment with records: Java records are immutable, and JDBC embraces that.
 *     JPA's entity lifecycle expects mutable objects with setters.
 *
 * WHY an interface with no method implementations?
 *   — Spring Data uses a proxy pattern: at startup, it generates a runtime implementation
 *     class (using Java's java.lang.reflect.Proxy or CGLIB) that contains the actual SQL
 *     execution logic. You declare the "what" (interface methods), Spring provides the "how."
 *   — Custom queries can be added by simply declaring method signatures that follow naming
 *     conventions, e.g.: List<Dog> findByOwnerIsNull() would auto-generate
 *     SELECT ... FROM dog WHERE owner IS NULL — to find dogs available for adoption.
 */
@Repository
public interface DogRepository extends CrudRepository<Dog, Integer> {
}
