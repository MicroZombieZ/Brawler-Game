# Class reference maintenance

- Keep `CLASS_STATS.txt` updated in the same change whenever class stats,
  abilities, passives, controls, damage formulas, cooldowns, ranges, timings,
  movement rules, effects, or related testing commands change.
- Update its last-updated date when its contents change.
- Check implemented values against the Java source. Clearly distinguish
  implemented classes, selectable placeholders, and planned designs. Mark
  undecided values as TBD rather than inventing them.
- Keep relevant README descriptions consistent with the class reference.
- `CLASS_STATS.txt` is documentation, not a runtime configuration file.
- Movement and stamina are simulated in both `CubeMovement.java` (solo) and
  `LobbyWorld.java` (LAN host). Keep shared gameplay values consistent when
  changing them, and check both paths. Ability logic is shared in `CombatSystem.java`.

# Version control

- Check `git status` before editing and preserve existing user changes.
- Use small, descriptive local commits for completed, verified changes.
- Keep generated `.class` files and build outputs excluded from history.
- Record user-visible changes under Unreleased in `CHANGELOG.md`.
- For a named release, update `VERSION.txt`, date the changelog entry, and tag
  the corresponding commit as `v<version>`. Keep existing release tags intact.
- Keep versions in Git history rather than creating duplicate project folders.
- Publishing or pushing to a remote repository requires a user request.
