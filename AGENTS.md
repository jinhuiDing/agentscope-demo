# AgentScope Demo Agent Rules

- Work only inside this repository unless the user explicitly grants another directory.
- Prefer small, reviewable changes and preserve existing Spring Boot patterns.
- For Java changes, run `mvn -q -DskipTests package` before reporting success when feasible.
- For frontend JavaScript changes, run `node --check src/main/resources/static/app.js`.
- Never read or expose `.env`, `.env.*`, private keys, tokens, or files under `.git/`.
- Ask for confirmation before deleting, moving, or rewriting large sets of files.
- Final reports should include changed behavior, verification commands, and remaining risks.
