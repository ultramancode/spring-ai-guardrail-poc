# 1. 고성능 인퍼런스를 위한 Python 베이스 이미지 (기존 MS 이미지보다 가볍고 제어가 쉬움)
FROM python:3.10-slim

# 2. 환경 변수 설정
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    ANALYZER_CONF_FILE=/etc/presidio/analyzer_config.yaml \
    LOG_LEVEL=INFO \
    PORT=3000

WORKDIR /app

# 3. 시스템 의존성 및 Python 라이브러리 설치 (레이어 최적화)
# 핵심: CPU 전용 PyTorch 설치로 이미지 용량을 획기적으로 줄임
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    curl \
    && pip install --no-cache-dir torch --extra-index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir \
    "numpy<2.0.0" \
    presidio-analyzer==2.2.360 \
    transformers==4.30.2 \
    spacy==3.6.1 \
    spacy-transformers==1.3.2 \
    spacy-huggingface-pipelines==0.0.4 \
    gunicorn flask accelerate huggingface_hub[cli] \
    # spaCy 한국어 토큰화 및 모델 설치
    "spacy[ko]" \
    https://github.com/explosion/spacy-models/releases/download/en_core_web_lg-3.6.0/en_core_web_lg-3.6.0-py3-none-any.whl \
    https://github.com/explosion/spacy-models/releases/download/ko_core_news_md-3.6.0/ko_core_news_md-3.6.0-py3-none-any.whl \
    # 빌드 도구 삭제로 최종 이미지 용량 절감
    && apt-get purge -y --auto-remove gcc g++ \
    && rm -rf /var/lib/apt/lists/*

# 4. 모델 사전 다운로드 (빌드 시점에 포함하여 런타임 지연 방지)
RUN mkdir -p /app/models/ko_ner /app/models/en_ner && \
    huggingface-cli download Leo97/KoELECTRA-small-v3-modu-ner --local-dir /app/models/ko_ner && \
    huggingface-cli download dslim/bert-base-NER --local-dir /app/models/en_ner

# 5. 가드레일 설정 파일 복사
# 한국어 모델의 PS, LC 등 태그를 Presidio 표준인 PERSON, LOCATION으로 매핑하는 핵심 로직이 담긴 파일.
COPY analyzer_config.yaml /etc/presidio/analyzer_config.yaml

# 6. 소스 코드 복사
# 6. 소스 코드 복사
COPY app.py /app/app.py

# 7. 실행 설정
EXPOSE 3000

# Gunicorn
CMD ["gunicorn", "-b", "0.0.0.0:3000", "app:app", \
     "--timeout", "300", \
     "--workers", "1", \
     "--log-level", "info", \
     "--access-logfile", "-"]
