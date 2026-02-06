from typing import Any
import asyncio
from mcp.server.fastmcp import FastMCP

# Start as SSE server:
# option 1: mcp run tools/mcp-server/server.py --transport sse --port 8081
# option 2 (if using uvicorn directly): uvicorn tools.mcp-server.server:mcp.sse_app --port 8081

# Initialize FastMCP server
mcp = FastMCP("demo-server")

# Mock Databases
ADDRESS_DB = {
    "010-2345-6789": "서울시 강남구",
    "010-1234-5678": "서울시 관악구 봉천동"
}

USER_DB = {
    "김태웅": "test12345"
}

@mcp.tool(name="searchAddress")
async def search_address(name: str, phone: str) -> str:
    """Search address by name and phone number."""
    # print(f"[MCP] search_address: name={name}, phone={phone}") # Logs to stderr (visible in console)
    name_match = name in USER_DB
    phone_match = phone in ADDRESS_DB
    
    if name_match and phone_match:
        return f'{{"status": "SUCCESS", "address": "{ADDRESS_DB[phone]}"}}'
    return '{"status": "FAILED"}'

@mcp.tool(name="searchUserByName")
async def search_user_by_name(name: str) -> str:
    """Search user by name."""
    # print(f"[MCP] search_user_by_name: name={name}")
    return f'{{"info": "{USER_DB.get(name, "Not found")}"}}'

@mcp.tool(name="verifyUser")
async def verify_user(name: str, phone: str) -> str:
    """Verify user identity."""
    # print(f"[MCP] verify_user: name={name}, phone={phone}")
    if name in USER_DB and phone in ADDRESS_DB:
        return '{"status": "VERIFIED", "userId": "test12345"}'
    return '{"status": "FAILED"}'

if __name__ == "__main__":
    mcp.run()
