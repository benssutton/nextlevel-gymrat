# Common iOS tasks. Run `make bootstrap` once, then `make test`.
PROJECT := GymRat.xcodeproj
SCHEME  := GymRat

.PHONY: bootstrap generate open build test kit-test lint clean

bootstrap: ## Install XcodeGen and SwiftLint (Homebrew)
	brew install xcodegen swiftlint

generate: ## Regenerate GymRat.xcodeproj from project.yml
	xcodegen generate

open: generate ## Generate and open in Xcode
	open $(PROJECT)

build: generate
	xcodebuild build -project $(PROJECT) -scheme $(SCHEME) \
		-destination "$$(scripts/simulator-destination.sh)" CODE_SIGNING_ALLOWED=NO

test: generate ## App unit + UI tests on the newest iPhone simulator
	xcodebuild test -project $(PROJECT) -scheme $(SCHEME) \
		-destination "$$(scripts/simulator-destination.sh)" -enableCodeCoverage YES CODE_SIGNING_ALLOWED=NO

kit-test: ## GymRatKit tests (set GYMRAT_API_BASE_URL to include contract tests)
	cd Packages/GymRatKit && swift test

lint:
	swiftlint lint --strict

clean:
	rm -rf $(PROJECT) build Packages/GymRatKit/.build
