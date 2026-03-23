from typing import Any
import asyncio
from mcp.server.fastmcp import FastMCP

# SSE 서버로 실행:
# 방법 1: mcp run tools/mcp-server/server.py --transport sse --port 8081
# 방법 2 (uvicorn 직접 사용 시): uvicorn tools.mcp-server.server:mcp.sse_app --port 8081

# FastMCP 서버 초기화
mcp = FastMCP("demo-server")

# 모의(Mock) 데이터베이스
ADDRESS_DB = {
    "010-2345-6789": "서울시 강남구",
    "010-1234-5678": "서울시 관악구 봉천동"
}

USER_DB = {
    "김태웅": "test12345"
}

@mcp.tool(name="searchAddress")
async def search_address(name: str, phone: str) -> str:
    """이름과 전화번호로 주소를 검색합니다."""
    # print(f"[MCP] search_address: name={name}, phone={phone}") # stderr로 로그 출력 (콘솔에서 확인 가능)
    name_match = name in USER_DB
    phone_match = phone in ADDRESS_DB
    
    if name_match and phone_match:
        return f'{{"status": "SUCCESS", "address": "{ADDRESS_DB[phone]}"}}'
    return '{"status": "FAILED"}'

@mcp.tool(name="searchUserByName")
async def search_user_by_name(name: str) -> str:
    """이름으로 사용자를 검색합니다."""
    # print(f"[MCP] search_user_by_name: name={name}")
    return f'{{"info": "{USER_DB.get(name, "Not found")}"}}'

@mcp.tool(name="verifyUser")
async def verify_user(name: str, phone: str) -> str:
    """사용자 본인 여부를 확인합니다."""
    # print(f"[MCP] verify_user: name={name}, phone={phone}")
    if name in USER_DB and phone in ADDRESS_DB:
        return '{"status": "VERIFIED", "userId": "test12345"}'
    return '{"status": "FAILED"}'

if __name__ == "__main__":
    mcp.run()
