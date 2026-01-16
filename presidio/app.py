import os
import logging
from flask import Flask, request, jsonify
from presidio_analyzer import AnalyzerEngine, RecognizerRegistry, EntityRecognizer, RecognizerResult
from presidio_analyzer.nlp_engine import SpacyNlpEngine
from transformers import pipeline

# 1. Logging Setup
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("presidio-analyzer-v2")

app = Flask(__name__)

# --- Custom Recognizer Class ---
class HuggingFaceDirectRecognizer(EntityRecognizer):
    def __init__(self, model_path, supported_entities, supported_language):
        super().__init__(supported_entities=supported_entities, supported_language=supported_language)
        self.pipeline = pipeline(
            "token-classification", 
            model=model_path, 
            tokenizer=model_path, 
            aggregation_strategy="simple",
            device=-1
        )
        logger.info(f"Loaded HF Model: {model_path} for {supported_language}")

    def analyze(self, text, entities, nlp_artifacts):
        results = []
        # Handle empty text
        if not text:
            return []
            
        predictions = self.pipeline(text)
        
        for pred in predictions:
            presidio_label = self._map_label(pred['entity_group'])
            if not presidio_label or (entities and presidio_label not in entities):
                continue

            results.append(RecognizerResult(
                entity_type=presidio_label,
                start=pred['start'],
                end=pred['end'],
                score=float(pred['score'])
            ))
        return results

    def _map_label(self, label):
        label = label.upper()
        mapping = {
            "PER": "PERSON", "PS": "PERSON", "PERSON": "PERSON",
            "LOC": "LOCATION", "LC": "LOCATION", "LOCATION": "LOCATION",
            "ORG": "ORGANIZATION", "OG": "ORGANIZATION", "ORGANIZATION": "ORGANIZATION",
            "QT": "QUANTITY"
        }
        return mapping.get(label)

# --- Engine Initialization Factory ---
def create_analyzer():
    logger.info("Initializing Analyzer Engine...")
    registry = RecognizerRegistry()
    registry.load_predefined_recognizers(languages=["en"])

    # Register Custom Models (Clean loop)
    model_configs = [
        {"lang": "ko", "path": os.getenv("KO_MODEL_PATH", "/app/models/ko_ner")},
        {"lang": "en", "path": os.getenv("EN_MODEL_PATH", "/app/models/en_ner")}
    ]

    for cfg in model_configs:
        if os.path.exists(cfg["path"]):
            logger.info(f"Loading custom model for {cfg['lang']} from {cfg['path']}")
            recon = HuggingFaceDirectRecognizer(
                model_path=cfg["path"],
                supported_entities=["PERSON", "LOCATION", "ORGANIZATION"],
                supported_language=cfg["lang"]
            )
            registry.add_recognizer(recon)
        else:
            logger.warning(f"Model path not found: {cfg['path']} for {cfg['lang']}")

    # Explicit NLP Engine Configuration
    nlp_engine = SpacyNlpEngine(models=[
        {"lang_code": "en", "model_name": "en_core_web_lg"},
        {"lang_code": "ko", "model_name": "ko_core_news_md"}
    ])

    # Initialize Engine without explicit supported_languages to avoid registry mismatch error
    engine = AnalyzerEngine(registry=registry, nlp_engine=nlp_engine)
    
    # WORKAROUND: Force add 'ko' support if registry didn't pick it up automatically
    if "ko" not in engine.supported_languages:
        logger.info("Manually patching 'ko' into analyzer.supported_languages")
        engine.supported_languages.append("ko")
    
    logger.info(f"Analyzer initialized. Supported languages: {engine.supported_languages}")
    return engine

# Global Engine Instance
analyzer = create_analyzer()

@app.route("/analyze", methods=["POST"])
def analyze():
    try:
        req = request.json
        if not req:
             return jsonify({"error": "Invalid JSON"}), 400
             
        text = req.get("text")
        language = req.get("language", "en")
        
        if not text:
            return jsonify({"error": "No text provided"}), 400

        results = analyzer.analyze(text=text, language=language)
        return jsonify([r.to_dict() for r in results])
    except Exception as e:
        logger.error(f"Analysis failed: {str(e)}")
        return jsonify({"error": "Internal Server Error"}), 500

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ready", 
        "engine": "Presidio V2 (Direct-HF)",
        "languages": analyzer.supported_languages
    }), 200

if __name__ == "__main__":
    port = int(os.getenv("PORT", 3000))
    app.run(host="0.0.0.0", port=port)
