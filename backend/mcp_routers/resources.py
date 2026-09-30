from mcp.server.mcpserver import MCPServer


def register(mcp: MCPServer) -> None:
    raise NotImplementedError(
        "Register your MCP resources in mcp_routers/resources.py. "
        "See https://py.sdk.modelcontextprotocol.io/ for resources examples."
    )
