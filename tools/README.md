# tools/

Python helpers that run on the Mac. Stdlib only unless DECISIONS.md says otherwise.

- `make_fixture.py` (Phase 3): YAML scenario → evidence fixture JSON.
- `fetch_iocs.py` (Phase 8): downloads the pinned MVT indicator index and writes the bundled snapshot.

Run with `uv run python tools/<script>.py` from the project root.
