# AeroRoster collaboration rules

- Read `docs/01_SCOPE.md`, `docs/03_RULES_AND_DECISIONS.md` and `docs/05_PHASE1_CHECKLIST.md` before editing.
- Make one phase-sized change at a time; explain proposed approach, files, risks, and test plan before implementation.
- Implement Java-first business logic (services + domain records), not SQL-only procedures.
- Never invent aviation legal limits. Use configurable *illustrative* limits and state them in UI/report.
- No secrets, database dumps with personal data, or private keys in commits.
- SQL schemas only via additive, versioned Flyway migrations. Never edit an applied migration; create the next version.
- Avoid data loss and destructive `DROP TABLE` migration scripts.
- Treat every crew assignment, cancellation, and later schedule change as an atomic transaction. Use appropriate locking when implementing conflict checks.
- Scheduling writes require writable READ_COMMITTED transactions. Acquire affected crew rows in ascending ID order, then flight rows, then aircraft rows. Lock owned child records after their parents and refresh decision state. Batch operations must acquire all affected parent locks before processing individual changes.
- Reuse SchedulingPolicy and persisted duty snapshots for academic scheduling calculations. Preserve cancelled assignment history and recheck eligibility inside the assignment transaction.
- User must approve architecture or schema changes before work begins on the next milestone.
- Definition of done: unit tests, DB migration check, manual end-to-end evidence, updated status docs.
