.PHONY: verify todo setup-hooks build test test-fast boot-all demo stop frontend-install frontend-dev frontend-build

# 검증의 단일 진입점. 성공은 한 줄, 실패는 로그와 함께 종료코드 1.
#
# 파이프로 감싸면 마지막 명령의 종료코드가 잡혀서 실패를 성공으로 읽는다. 실제로 그 오독이
# 반복됐으므로 파이프를 쓸 이유 자체를 없앤다.
#
# 도커가 필요하다. 저장소를 가진 여덟 서비스의 테스트가 실제 MySQL 위에서 돈다 (ADR-0013).
# 컨테이너를 재사용하려면 ~/.testcontainers.properties에 testcontainers.reuse.enable=true.
verify:
	@./gradlew test > .verify.log 2>&1 && echo "PASS  ./gradlew test" || \
		(echo "FAIL  ./gradlew test"; grep -E "FAILED|error:|Caused by" .verify.log | head -30; \
		 echo; echo "전체 출력: .verify.log"; exit 1)

todo:
	@grep -rn "TODO" --include="*.java" --include="*.ts" --include="*.tsx" apps libs frontend/storefront/src || echo "코드에 남은 TODO 없음"
	@echo
	@echo "우선순위는 docs/backlog/README.md, 내린 결정은 docs/adr/ 를 본다"

setup-hooks:
	git config core.hooksPath scripts/git-hooks
	@echo "pre-commit 훅 활성화: 커밋 전에 도커 없이 도는 테스트가 실행됩니다"

build:
	./gradlew clean build

test:
	./gradlew test

# 도커 없이 도는 것만. pre-commit이 이 형태로 돈다.
test-fast:
	./gradlew test -PexcludeTags=infra

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
