from mcp.server.mcpserver import MCPServer


def register(mcp: MCPServer) -> None:
    raise NotImplementedError(
        "Register your MCP prompts in mcp_routers/prompts.py. "
        "See https://py.sdk.modelcontextprotocol.io/ for prompts examples."
    )
