# rolabox
Offline music player

## Modules

| Module | Purpose |
| --- | --- |
| `:app` | Application shell: entry point, app identity, wires features together |
| `:core:model` | Pure Kotlin domain models (no Android dependencies) |
| `:core:common` | Shared utilities: coroutine dispatchers, result types |
| `:core:designsystem` | Theme and shared composables |
| `:core:data` | Repositories |
| `:core:datastore` | Local preferences |
| `:core:auth` | Authentication abstraction; the Firebase implementation lives behind an interface |
| `:core:sync` | Sync abstraction |
| `:feature:authentication` | Sign-in UI |
| `:feature:account` | Account UI |
| `:feature:settings` | Settings UI |

### Dependency rules

These are enforced by the build (see `build-logic/.../ModuleRules.kt`); breaking one fails configuration.

- Feature modules never depend on other feature modules.
- `:core:model` depends on no other module.

### Module graph

Generated from the build. After changing module dependencies, regenerate with `./gradlew moduleGraph`.

<!-- module-graph:start -->
```mermaid
graph TD
    app[":app"]
    core_auth[":core:auth"]
    core_common[":core:common"]
    core_data[":core:data"]
    core_datastore[":core:datastore"]
    core_designsystem[":core:designsystem"]
    core_model[":core:model"]
    core_sync[":core:sync"]
    feature_account[":feature:account"]
    feature_authentication[":feature:authentication"]
    feature_settings[":feature:settings"]
    app --> core_designsystem
    app --> core_sync
    app --> feature_account
    app --> feature_authentication
    app --> feature_settings
    core_auth --> core_common
    core_auth --> core_model
    core_data --> core_common
    core_data --> core_datastore
    core_data --> core_model
    core_datastore --> core_common
    core_datastore --> core_model
    core_sync --> core_common
    core_sync --> core_data
    feature_account --> core_auth
    feature_account --> core_data
    feature_account --> core_designsystem
    feature_account --> core_model
    feature_authentication --> core_auth
    feature_authentication --> core_data
    feature_authentication --> core_designsystem
    feature_authentication --> core_model
    feature_settings --> core_data
    feature_settings --> core_designsystem
    feature_settings --> core_model
```
<!-- module-graph:end -->
