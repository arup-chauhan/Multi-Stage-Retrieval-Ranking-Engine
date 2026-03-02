# ADR 0001: Initial Architecture

## Status
Accepted

## Decision
Use a microservices-style local deployment:
- Java Spring Boot for orchestration (`query-service`)
- Python services for semantic retrieval and reranking
- Docker Compose for local development

## Rationale
- Clear separation between orchestration and ML/IR components
- Faster iteration for model/retrieval experimentation in Python
- Resume-credible distributed design without early Kubernetes complexity
