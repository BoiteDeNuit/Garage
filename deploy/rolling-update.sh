#!/usr/bin/env bash
# Обновление без простоя: экземпляры по одному. Пока один перезапускается, nginx отдаёт запросы второму
# (max_fails, proxy_next_upstream), а новый адрес перезапущенного узнаёт у DNS Docker (resolve в nginx.conf).
# Остановка экземпляра ждёт текущие запросы до 20 секунд (профиль prod). Следующий экземпляр обновляется,
# только когда предыдущий прошёл healthcheck: упал новый образ — второй экземпляр продолжает работать на старом.
# Миграции Flyway применяет первый обновлённый экземпляр, пока второй ещё на старом коде: поэтому миграции
# только расширяющие (новая колонка, NOT VALID, CONCURRENTLY), а удаление старого — следующим релизом.
#   deploy/rolling-update.sh <тег образа>        SKIP_PULL=1 — образ уже есть локально
set -euo pipefail
cd "$(dirname "$0")/.."

export APP_TAG="${1:?укажите тег образа}"
compose=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ${COMPOSE_EXTRA:-})

if [[ "${SKIP_PULL:-0}" != "1" ]]; then
    "${compose[@]}" pull app app2
fi

for service in app app2; do
    echo "Обновляю $service до $APP_TAG"
    "${compose[@]}" up -d --no-deps --no-build "$service"
    container=$("${compose[@]}" ps -q "$service")
    for attempt in $(seq 1 60); do
        health=$(docker inspect -f '{{.State.Health.Status}}' "$container")
        if [[ "$health" == "healthy" ]]; then
            echo "$service готов"
            break
        fi
        if [[ "$attempt" == 60 ]]; then
            echo "$service не прошёл healthcheck за 2 минуты, обновление остановлено" >&2
            exit 1
        fi
        sleep 2
    done
done
echo "Оба экземпляра на $APP_TAG"
