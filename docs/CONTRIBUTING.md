# Contributing Guide

Thank you for your interest in **Snishaper-Droid**! We welcome all forms of
contribution: code, documentation, translations, testing, and feedback.

## Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Contributor Agreement

Please review the [Collaborator Agreement](COLLABORATOR_AGREEMENT.md) before
submitting substantial contributions.

## How to Contribute

- Report bugs — include the app version, Android version, device, reproduction
  steps, and a logcat excerpt
- Suggest new features
- Improve documentation in `docs/`
- Submit code: the Android app (Kotlin), the `gomobile` Go bridge, or the
  `frontend-android` React UI

Before opening a new issue, search existing issues to avoid duplicates.

## Submitting Pull Requests

1. Fork the repository
2. Create a new branch (use clear names such as `fix/xxx` or `feat/xxx`)
3. Make your changes and make sure the project builds
4. Write clear commit messages
5. Open a pull request against `main` describing what changed, why it is
   needed, and how you tested it

### Commit Messages

- Use [Conventional Commits](https://www.conventionalcommits.org) style,
  e.g. `fix(gomobile): ...`, `feat(app): ...`, `docs(build): ...`
- Write commit messages in English

## Development Environment

See [build.md](build.md) for the full build matrix (gomobile AAR, Gradle APK,
signing, MuMu smoke tests, CI). Summary:

- JDK 25, Android SDK (compileSdk 37), Android NDK 30.0.16248370
- Go 1.27+ (the `gomobile` bridge)
- Node.js 24+ with npm (`frontend-android`)
- Debug APK: `.\gradlew.bat :app:assembleDebug`
- gomobile AAR: `pwsh -File scripts/build-aar.ps1`

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not
open public issues for security problems.

## License

This project is licensed under the MIT License (see [LICENSE](../LICENSE)).
By contributing, you agree that your contributions will be licensed under the
same terms.

## Contact

Join the Matrix group
**[Snishaper and FlowWeaver building](https://matrix.to/#/!j7cXGinN6v1-6-FyjGKxAfoZrVjzQWRgrOmc3l6zxj4?via=matrix.org)**
to talk directly with Snishaper, Snishaper-Droid and FlowWeaver users and
developers, report issues and send suggestions.
