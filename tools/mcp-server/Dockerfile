FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .

# 포트 개방
EXPOSE 8081

# uvicorn을 사용하여 서버 실행
# server.py의 'mcp' 객체가 sse_app 또는 유사한 ASGI 앱을 노출한다고 가정
# FastMCP의 경우 'mcp.sse_app'이 일반적임
CMD ["uvicorn", "server:mcp.sse_app", "--host", "0.0.0.0", "--port", "8081"]
