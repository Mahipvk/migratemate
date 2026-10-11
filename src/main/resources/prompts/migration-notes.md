## jakarta
keywords: javax, jakarta, package javax
Spring Boot 3 uses Jakarta EE 9+. Replace `javax.persistence`, `javax.validation`, `javax.servlet`,
`javax.annotation` imports with `jakarta.persistence`, `jakarta.validation`, `jakarta.servlet`, `jakarta.annotation`.
Keep `javax.sql`, `javax.crypto`, `javax.net` (they are part of the JDK).

## paging-repository
keywords: PagingAndSortingRepository, save, findById, deleteById, CrudRepository, cannot find symbol
In Spring Data 3, `PagingAndSortingRepository` no longer extends `CrudRepository`.
A repository that needs `save`, `findById`, `findAll(Pageable)` should extend `JpaRepository<T, ID>`
(or extend both `PagingAndSortingRepository` and `CrudRepository`). Fix the repository interface, not the callers.

## http-status-code
keywords: HttpStatusCode, HttpStatus, getStatusCode, incompatible types
In Spring Framework 6, `ResponseEntity.getStatusCode()` and `RestClientResponseException.getStatusCode()`
return `HttpStatusCode`, not the `HttpStatus` enum. Use `HttpStatusCode` as the variable type,
or compare with `status.value()`, or use `HttpStatus.valueOf(status.value())`.

## exception-handler
keywords: ResponseEntityExceptionHandler, handleMethodArgumentNotValid, method does not override
In Spring 6, `ResponseEntityExceptionHandler` methods take `HttpStatusCode status` instead of `HttpStatus status`.
Change the parameter type in the overriding method.

## trailing-slash
keywords: 404, trailing, expected:<200> but was:<404>, Status expected
Spring Framework 6 no longer matches trailing slashes: `/api/items/1/` does not match `/api/items/{id}`.
If tests or clients call the URL with a trailing slash, map both paths explicitly,
for example `@GetMapping({"/{id}", "/{id}/"})`.

## security
keywords: WebSecurityConfigurerAdapter, antMatchers, authorizeRequests, EnableGlobalMethodSecurity
`WebSecurityConfigurerAdapter` was removed. Declare a `SecurityFilterChain` @Bean instead.
Use `authorizeHttpRequests` and `requestMatchers` instead of `authorizeRequests` / `antMatchers`,
and `@EnableMethodSecurity` instead of `@EnableGlobalMethodSecurity`.

## properties
keywords: ConfigurationProperties, ConstructorBinding, spring.redis, server.max-http-header-size
`@ConstructorBinding` is no longer needed on types with a single constructor. Some properties were renamed
(for example `spring.redis.*` became `spring.data.redis.*`).

## java21
keywords: removed, Thread.stop, finalize, SecurityManager, javax.xml.bind, DatatypeConverter
Java 11+ removed `javax.xml.bind` (use `java.util.Base64`), `Thread.stop(Throwable)` and
`Runtime.runFinalizersOnExit`. Replace removed JDK APIs with their modern equivalents.
