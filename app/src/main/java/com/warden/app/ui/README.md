# `ui` Package

Top-level Android host shell:

- `ui/activity/FragmentActivity.kt` — Single launcher `Activity` that checks first-run Terms acceptance off the main thread (`SharedPreferences("app_prefs")["agreed_to_terms"]`) and hosts `KeywordBlockerFragment`.

**Upgrade Invariant:** Do not rename or move `com.warden.app.ui.activity.FragmentActivity`; launcher icons and pinned shortcuts reference this exact FQCN.
