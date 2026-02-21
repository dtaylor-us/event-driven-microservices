#!/usr/bin/env bash
# Start local infrastructure (Phase 3). Usage: ./start-infra.sh
set -e
cd "$(dirname "$0")"
echo "Starting Kafka, Postgres, OTel Collector, Kafka Exporter, Prometheus, Grafana..."
docker compose up -d
echo "Waiting for Kafka (port 9092)..."
for i in {1..60}; do
  if (bash -c "echo >/dev/tcp/localhost/9092" 2>/dev/null) || (nc -z localhost 9092 2>/dev/null); then
    echo "Kafka is up."
    break
  fi
  sleep 2
done
echo "Waiting for Postgres (port 5432)..."
for i in {1..30}; do
  if docker compose exec -T postgres pg_isready -U grid -d grid 2>/dev/null; then
    echo "Postgres is up."
    break
  fi
  sleep 2
done
echo "Infrastructure ready. Ports: Kafka 9092, Postgres ${POSTGRES_PORT:-54320}, OTel 4317/4318, Prometheus 9090, Grafana 3000"
docker compose ps
