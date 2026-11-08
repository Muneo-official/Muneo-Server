# 비회원 체험 배포

대상 저장소: `Muneo-official/Muneo-Server`, 기준 커밋 `7906efb`.
기존 피드백·관리자 이용내역 기능을 유지하고 체험 API를 추가했다.

Java 21과 전용 로컬 Redis를 준비한 뒤 검증한다.

```bash
TRIAL_TEST_REDIS_PORT=16389 ./gradlew test bootJar
```

기존 PostgreSQL·Redis·MongoDB·AI 연결 설정을 유지한다. 가견적 토큰 이전을 위해 Spring과 AI의 MongoDB 데이터베이스가 같아야 한다. `pending_estimates`는 기존 AI가 관리하는 컬렉션이며 신규 데이터베이스 마이그레이션은 없다.

기본 체험 한도는 세션당 30분 3회, 서비스 전체 시간당 60회다. Docker Compose에는 두 한도와 전역 파일 크기 20MB/요청 크기 21MB를 반영했고, 게스트의 파일당 10MB·합계 20MB 제한은 체험 컨트롤러에서 적용한다. 실제 서버 환경 변수에 별도 값이 있으면 배포 시 확인한다.

백엔드 배포 후 아래를 확인하고 프론트엔드 운영 도메인을 전환한다.

- `GET /api/v1/trials/analysis`: 204와 HttpOnly/Secure 체험 쿠키
- 비로그인 `GET /api/v1/trials/analysis/result`: 401
- 실제 입력으로 POST 성공 후 부분 응답에 전체 결과·원본 입력·저장 토큰이 없는지 확인
- 로그인 후 동일 전체 결과 조회·저장 및 기존 회원 경로 확인

실제 서버 접속·배포 경로 확인 전에는 운영 컨테이너나 데이터 볼륨을 변경하지 않는다.
