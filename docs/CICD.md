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
| `_contract.yml` | called by `ci.yml`, `nightly.yml` | Runs the Swift API-client tests against the real backend in Docker Compose. |
| `nightly.yml` | daily 03:17 UTC, manual | Full backend run, including the k6 **stress** test and a fresh CVE audit, plus the contract tests. |
| `ios-release.yml` | tag `ios/v*`, manual | Archive, sign (automatic, via App Store Connect API key) and upload to TestFlight. |
| `codeql.yml` | PR, `main`, weekly | CodeQL for Python, Actions and Swift. **Opt-in** (paid on private repos). |
| `dependency-review.yml` | PR | Blocks vulnerable or disallowed-license dependencies. **Opt-in** (paid on private repos). |
| `dependabot.yml` | weekly | Version updates for Actions, pip, Dockerfiles and Compose images. |

### Backend gates (`_backend.yml`)
1. **Lint & type-check**: `ruff check` and `pyright` (standard mode, `backend/pyrightconfig.json`). Both must be clean.
2. **Dependency audit**: `pip-audit` against `requirements.txt`.
3. **Tests & coverage**: the full pytest suite against a real Postgres container (testcontainers). `.coveragerc` enforces ≥ 94% coverage. JUnit, coverage XML and HTML reports are uploaded as artifacts, and a coverage table is written to the job summary.
4. **Container image**: Buildx build with GitHub Actions layer caching, then a **Grype** scan that fails on critical CVEs with a fix available, then an **SPDX SBOM** artifact. On `main` the image is pushed to `ghcr.io/<owner>/nextlevel-gymrat/backend:{latest,sha-…}`.
5. **Performance**: runs after tests and image build. Starts the full Compose stack and runs the k6 smoke and load tests as hard gates. The stress test runs nightly as a soft gate.

### iOS gates (`_ios.yml`)
1. **SwiftLint** `--strict` (Linux container).
2. **GymRatKit tests** on Linux (`swift:6.1` container), with warnings as errors.
3. **App build & tests** on `macos-latest` with the latest stable Xcode. Generates the project with XcodeGen, runs unit and UI tests on the newest iPhone simulator, writes an `xccov` coverage summary, uploads the `.xcresult`, and runs an unsigned **Release device build** to catch Release-only breakage.

Everything that can run on Linux does. macOS minutes count **10×** against a
private repo's included minutes, so only the app build/test job uses macOS.

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
2,000 included minutes. Backend-only PRs use ~15–20 Linux minutes. Path filtering
means a PR only pays for the side it touches.
