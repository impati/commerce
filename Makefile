.PHONY: setup-hooks build test boot-all demo stop frontend-install frontend-dev frontend-build

setup-hooks:
	git config core.hooksPath scripts/git-hooks
	@echo "pre-commit 훅 활성화: 커밋 전에 ./gradlew test가 실행됩니다"

build:
	./gradlew clean build

test:
	./gradlew test

boot-all:
	./scripts/run-all.sh

demo:
	./scripts/demo-checkout.sh

stop:
	./scripts/stop-all.sh

frontend-install:
	cd frontend/storefront && npm install

frontend-dev:
	cd frontend/storefront && npm run dev

frontend-build:
	cd frontend/storefront && npm run build
