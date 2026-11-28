# Built-in Adult Website Blocklist (`porn_domains.txt`)

- **Source:** [StevenBlack/hosts — `alternates/porn-only/hosts`](https://github.com/StevenBlack/hosts)
- **License:** MIT License (`Copyright © 2026 Steven Black`, permissive open-source license allowing redistribution and commercial/non-commercial use)
- **Snapshot Date:** 2026-11-28 (upstream timestamp `28 November 2026 02:11:33 UTC`)
- **Entries:** 46,725 unique registrable domains (sorted alphabetically, one domain per line)

## Processing Rules
1. Parsed all `0.0.0.0` / `127.0.0.1` host records from upstream `alternates/porn-only/hosts`.
2. Normalized to lowercase, stripped `www.` and trailing dots, and collapsed subdomains to registrable domains (respecting common multi-part public suffixes such as `.co.uk`, `.com.au`, `.com.br`, `.co.jp`, etc.).
3. Excluded shared multi-tenant hosting platforms (e.g., `blogspot.com`, `wordpress.com`, `github.io`, `reddit.com`, `tumblr.com`) so blocking registrable domains never over-blocks unrelated user sites.
4. Deduplicated and sorted lexicographically.

User-added domains, disabled built-in domains, and whitelisted domains are persisted separately in `filesDir/datastore/settings.json` so updating `porn_domains.txt` in future releases never erases user customizations.
