# FinAdmin의 통합 실습 compose에서 이 이미지를 함께 빌드합니다.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --chown=app:app build/libs/finaccess-0.0.1-SNAPSHOT.jar app.jar
USER app
ENTRYPOINT ["java", "-Xms64m", "-Xmx384m", "-jar", "/app/app.jar"]
