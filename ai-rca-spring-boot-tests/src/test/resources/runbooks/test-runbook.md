## NullPointerException Errors
NullPointerException occurs when code attempts to use a null reference where an object is expected.
Common causes: passing null as a method parameter without validation, returning null from a method
without checking the call site, or accessing fields on an uninitialized object reference.
Remediation: add null validation at the API boundary using explicit null checks or @NonNull annotations.
Use Optional<T> for return types that may legitimately be absent. Ensure userId and similar identifiers
are validated before any method invocation.

## Database Connection Failures
Database connection failures indicate the application cannot obtain a JDBC connection from the pool.
Common causes: HikariCP pool exhaustion when all connections are in use under high concurrency,
the database host is unreachable due to a network partition or misconfigured JDBC URL,
or the database process has crashed or restarted.
Remediation: check HikariCP pool sizing versus concurrent load, verify the database host is reachable,
review connection pool metrics (total, active, idle, waiting), and check for long-running transactions
that hold connections.

## OutOfMemoryError Heap Space
Heap space exhaustion occurs when the JVM cannot allocate new objects because the heap is full.
Common causes: unbounded in-memory caches that grow without eviction, loading large data sets entirely
into memory instead of streaming, classloader leaks in application servers, or undersized -Xmx setting
relative to the workload.
Remediation: take a heap dump with jmap -dump:live, analyze with Eclipse Memory Analyzer (MAT) to find
the largest retained object graphs, identify the owner holding references, and fix the root leak before
increasing heap size as a short-term workaround.