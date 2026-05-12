# Fix stale package error

If GitHub Actions reports:

```text
Syntax error: Package name must be a '.'-separated identifier list
app/src/main/java/fun/example/cactusgemma/MainActivity.kt:1:9
```

then the repository still contains the old source file using:

```kotlin
package fun.example.cactusgemma
```

`fun` is a Kotlin keyword. Delete the old folder:

```bash
git rm -r app/src/main/java/fun
```

Keep only:

```text
app/src/main/java/com/example/cactusgemma/MainActivity.kt
```

whose first line must be:

```kotlin
package com.example.cactusgemma
```

This project also includes `scripts/repair_package.sh`, and the workflow runs it before building.
