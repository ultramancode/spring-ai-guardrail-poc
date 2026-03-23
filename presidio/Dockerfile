FROM mcr.microsoft.com/presidio-analyzer:2.2.362

USER root

ENV PIP_NO_CACHE_DIR=1 \
    ANALYZER_CONF_FILE=/etc/presidio/analyzer.yaml \
    NLP_CONF_FILE=/etc/presidio/nlp.yaml \
    RECOGNIZER_REGISTRY_CONF_FILE=/etc/presidio/recognizers.yaml \
    LOG_LEVEL=INFO

RUN pip install --no-cache-dir \
    torch --extra-index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir \
    transformers==4.57.6 \
    accelerate==1.13.0 \
    huggingface_hub[cli]==0.36.2 \
    && pip install --no-cache-dir \
    https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.8.0/en_core_web_sm-3.8.0-py3-none-any.whl \
    https://github.com/explosion/spacy-models/releases/download/ko_core_news_sm-3.8.0/ko_core_news_sm-3.8.0-py3-none-any.whl

RUN mkdir -p /app/models/ko_ner /app/models/en_ner \
    && huggingface-cli download Leo97/KoELECTRA-small-v3-modu-ner --local-dir /app/models/ko_ner \
    && huggingface-cli download dslim/bert-base-NER --local-dir /app/models/en_ner

COPY conf/analyzer.yaml /etc/presidio/analyzer.yaml
COPY conf/nlp.yaml /etc/presidio/nlp.yaml
COPY conf/recognizers.yaml /etc/presidio/recognizers.yaml

RUN chown -R 1001:1001 /etc/presidio /app/models

USER 1001
