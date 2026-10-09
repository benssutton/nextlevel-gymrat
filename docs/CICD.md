# CI/CD

All automation lives in `.github/`. It is written to work on a **private
repository on any GitHub plan**. The features that need a paid plan are opt-in
and are listed under [Plan-dependent features](#plan-dependent-features).

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | every PR, push to `main`, manual | Entry point. Detects changed paths and calls the reusable workflows below. Ends with **`CI OK`**, the only check to mark as required. |
| `_backend.yml` | called by `ci.yml`, `nightly.yml` | Backend gates (see below). Pushes the image to GHCR on `main`. |
| `_ios.yml` | called by `ci.yml` | iOS gates (see below). |
| `_android.yml` | called by `ci.yml` | Android gates (see below). |
| `_contract.yml` | called by `ci.yml`, `nightly.yml` | Runs both API clients' tests (Swift GymRatKit, Kotlin `android/core`) against the real backend in Docker Compose. |
| `nightly.yml` | manual (daily schedule disabled while the project is on hold) | Full backend run, including the k6 **stress** test and a fresh CVE audit, plus the contract tests. |
| `ios-release.yml` | tag `ios/v*`, manual | Archive, sign (automatic, via App Store Connect API key) and upload to TestFlight. |
| `android-release.yml` | tag `android/v*`, manual | Build a signed release bundle (AAB) and upload it to the Google Play **internal** testing track. |
| `codeql.yml` | PR, `main` (weekly schedule disabled) | CodeQL for Python, Actions, Kotlin and Swift. **Opt-in** (paid on private repos). |
| `dependency-review.yml` | PR | Blocks vulnerable or disallowed-license dependencies. **Opt-in** (paid on private repos). |
| `dependabot.yml` | weekly (version-update PRs disabled while on hold) | Config for Actions, pip, Gradle, Dockerfiles and Compose images. Security updates still open PRs. |

The **Repo hygiene** job in `ci.yml` also runs `.github/scripts/check_ui_parity.py`. It fails
when an iOS accessibility identifier has no matching Android test tag, or the reverse
(see [FEATURE_PARITY.md](FEATURE_PARITY.md)).

### Backend gates (`_backend.yml`)
1. **Lint, type-check & dependency audit**: `ruff check`, `pyright` (standard mode, `backend/pyrightconfig.json`) and `pip-audit` against `requirements.txt`, in one job to share the checkout and pip install.
2. **Tests & coverage**: the full pytest suite against a real Postgres container (testcontainers). `.coveragerc` enforces ≥ 94% coverage. JUnit, coverage XML and HTML reports are uploaded as artifacts, and a coverage table is written to the job summary.
3. **Container image**: Buildx build with GitHub Actions layer caching, then a **Grype** scan that fails on critical CVEs with a fix available, then an **SPDX SBOM** artifact. On `main` the image is pushed to `ghcr.io/<owner>/nextlevel-gymrat/backend:{latest,sha-…}`.
4. **Performance**: runs after tests and image build, on `main`, nightly and manual runs only (not on PRs). Starts the full Compose stack and runs the k6 smoke and load tests as hard gates. The stress test runs nightly as a soft gate.

### iOS gates (`_ios.yml`)
1. **SwiftLint** `--strict` (Linux container).
2. **GymRatKit tests** on Linux (`swift:6.1` container), with warnings as errors.
3. **App build & tests** on `macos-latest`, only after the two Linux jobs pass. with the latest stable Xcode. Generates the project with XcodeGen, runs unit and UI tests on the newest iPhone simulator, writes an `xccov` coverage summary, uploads the `.xcresult`, and runs an unsigned **Release device build** to catch Release-only breakage.

### Android gates (`_android.yml`)
All on Linux runners.
1. **Lint, tests & build** (one Gradle invocation): ktlint via Spotless and Android Lint (errors fail the build); `:app` JVM tests covering the ViewModel and the Compose UI rendered by **Robolectric** (no emulator); a debug APK (uploaded) and an **R8-minified release bundle**. `:core` unit tests run in `_contract.yml` against the live backend instead of being repeated here. Kotlin warnings are errors.
2. **Emulator**: runs only after step 1 passes: instrumented Compose UI tests on an API 35 emulator (KVM-accelerated), launching the real app. The equivalent of the iOS XCUITest run.

Everything that can run on Linux does. macOS minutes count **10×** against a
private repo's included minutes, so only the iOS app build/test job uses macOS.

### Minute-saving rules
- Pushes to `main` re-run only the backend (to publish the image); iOS, Android and contract already passed on the PR.
- Superseded PR runs are cancelled (`concurrency`).
- Expensive jobs (macOS app, Android emulator) `needs` the cheap gates, so a lint failure doesn't burn them.
- Path filters skip whole platforms that a change doesn't touch.

## One-time repository setup

1. **Actions permissions**: Settings → Actions → General → Workflow permissions:
   "Read repository contents" (the workflows request extra scopes per job).
2. **Required check** (needs a plan with branch protection on private repos, see
   below): Settings → Rules → Rulesets → target `main` → *Require status checks*
   → add **`CI OK`**. Also enable *Require a pull request* and *Block force pushes*.
3. **GHCR**: nothing to configure. After the first push to `main`, the package is
   under your profile's *Packages* tab and inherits the repo's private visibility.
4. **Dependabot**: Settings → Code security → enable *Dependabot alerts* and
   *Dependabot security updates* (free on private repos). Version updates come
   from `.github/dependabot.yml`.

### TestFlight releases (`ios-release.yml`)
1. In App Store Connect, create the app record with the bundle ID from
   `ios/Config/Shared.xcconfig`.
2. App Store Connect → Users and Access → Integrations → **Team Keys**: create a
   key with the *App Manager* role (or *Admin*, which cloud-managed signing may need).
   Download `AuthKey_<KEYID>.p8`.
3. Add these repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `APPLE_TEAM_ID` | 10-character Team ID |
   | `APP_STORE_CONNECT_KEY_ID` | Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | Issuer ID (top of the Keys page) |
   | `APP_STORE_CONNECT_KEY_P8` | `base64 -i AuthKey_<KEYID>.p8` output |

4. Add an app icon, then release with `git tag ios/v0.1.0 && git push origin ios/v0.1.0`
   or run the workflow manually. The build number is the workflow run number.

No certificates or provisioning profiles are stored anywhere. `xcodebuild
-allowProvisioningUpdates` with the API key creates and manages them.

### Google Play releases (`android-release.yml`)
1. In Play Console, create the app with the `applicationId` from
   `android/app/build.gradle.kts`, and enrol in **Play App Signing**. Google holds the
   app-signing key; CI only holds an *upload* key.
2. Create an upload keystore once:
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`.
   Keep it outside the repo (`*.jks` is git-ignored).
3. The Play Developer API only accepts uploads for an app that already has a release, so
   upload the first bundle **manually** (Play Console → Internal testing). Build it with the
   signing env vars set, as the workflow does.
4. Google Cloud → create a **service account** and a JSON key. Then Play Console → Users and
   permissions → invite the service-account email with *Release to testing tracks*.
5. Add these repository secrets:

   | Secret | Value |
   |---|---|
   | `ANDROID_UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload.jks` output |
   | `ANDROID_UPLOAD_KEYSTORE_PASSWORD` | keystore password |
   | `ANDROID_UPLOAD_KEY_ALIAS` | key alias (e.g. `upload`) |
   | `ANDROID_UPLOAD_KEY_PASSWORD` | key password |
   | `PLAY_SERVICE_ACCOUNT_JSON` | contents of the service-account JSON key |

6. Release with `git tag android/v0.1.0 && git push origin android/v0.1.0`, or run the
   workflow manually. The `versionCode` is the workflow run number. The R8 mapping file is
   kept as an artifact so crash stack traces can be de-obfuscated.

## Plan-dependent features

| Feature | Private repo on Free | Needs |
|---|---|---|
| GitHub Actions | ✅ 2,000 min/month (macOS counts 10×) | More minutes: Pro/Team or pay-as-you-go |
| Dependabot alerts, security and version updates | ✅ | — |
| GHCR private packages | ✅ 500 MB storage | — |
| Branch protection / rulesets (required checks) | ❌ | GitHub Pro, Team or Enterprise |
| Environments with required reviewers | ❌ | GitHub Pro, Team or Enterprise |
| CodeQL code scanning, dependency review | ❌ | GitHub Code Security add-on |
| Secret scanning, push protection | ❌ | GitHub Secret Protection add-on (gitleaks in `ci.yml` covers this for free) |
| Artifact attestations (build provenance) | ❌ | GitHub Enterprise Cloud |

To turn on the opt-in workflows after enabling Code Security: Settings → Secrets
and variables → Actions → **Variables** → add `ENABLE_CODE_SCANNING` = `true`.

### Minute budget
A typical iOS PR run uses ~10–15 macOS minutes, which bills as ~100–150 of the
2,000 included minutes. Android PRs use ~25–35 Linux minutes across four parallel
jobs, most of it the emulator job. Backend-only PRs use ~15–20 Linux minutes. Path
filtering means a PR only pays for the platforms it touches.
