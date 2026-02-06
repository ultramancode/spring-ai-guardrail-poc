FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .

# Expose port
EXPOSE 8081

# Run the server using uvicorn
# Assuming server.py has 'mcp' object which exposes 'sse_app' or similar ASGI app
# If FastMCP, 'mcp.sse_app' is typical.
CMD ["uvicorn", "server:mcp.sse_app", "--host", "0.0.0.0", "--port", "8081"]
