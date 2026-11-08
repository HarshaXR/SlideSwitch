using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace SlideSwitch.Services;

public enum CommandResult { Ok, Unsupported, PermissionDenied }

public sealed record ConnectedClient(Guid Id, string DeviceName, string Address);

/// <summary>
/// SlideSwitch protocol v1 server (shared/PROTOCOL.md): a minimal WebSocket server on top of
/// TcpListener (no HttpListener, so no URL ACL / admin rights needed).
/// Events are raised on thread-pool threads; the UI marshals them to its dispatcher.
/// </summary>
public sealed class RemoteServer : IDisposable
{
    public const int DefaultPort = 48620;
    public const int ProtocolVersion = 1;
    public static readonly string[] Capabilities = ["next", "previous", "start", "end", "black"];

    private readonly ConcurrentDictionary<Guid, Session> _sessions = new();
    private readonly ConcurrentDictionary<string, (int Count, DateTime LockedUntil)> _failures = new();
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    public string ServerName { get; set; } = Environment.MachineName;
    public string ServerId { get; set; } = Guid.NewGuid().ToString();
    public int Port { get; private set; }

    /// <summary>(code, token, deviceName, deviceId) → issued token, or null to reject.</summary>
    public Func<string?, string?, string, string, string?>? Authenticate { get; set; }
    public Func<string, ConnectedClient, CommandResult>? OnCommand { get; set; }
    public event Action<IReadOnlyList<ConnectedClient>>? ClientsChanged;

    public void Start()
    {
        Stop();
        _cts = new CancellationTokenSource();
        try
        {
            _listener = new TcpListener(IPAddress.IPv6Any, DefaultPort);
            _listener.Server.DualMode = true;
            _listener.Start();
        }
        catch (SocketException)
        {
            // Port taken: use any free port; mDNS and the QR code carry the real one.
            _listener = new TcpListener(IPAddress.IPv6Any, 0);
            _listener.Server.DualMode = true;
            _listener.Start();
        }
        Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
        _ = AcceptLoopAsync(_listener, _cts.Token);
    }

    public void Stop()
    {
        _cts?.Cancel();
        _listener?.Stop();
        _listener = null;
        DisconnectAll();
    }

    public void DisconnectAll()
    {
        foreach (var s in _sessions.Values) s.Abort();
        _sessions.Clear();
        PublishClients();
    }

    public void Dispose() => Stop();

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await listener.AcceptTcpClientAsync(ct); }
            catch { break; }
            _ = Task.Run(() => HandleClientAsync(client, ct), ct);
        }
    }

    private async Task HandleClientAsync(TcpClient tcp, CancellationToken ct)
    {
        tcp.NoDelay = true;
        var address = (tcp.Client.RemoteEndPoint as IPEndPoint)?.Address is { } ip
            ? (ip.IsIPv4MappedToIPv6 ? ip.MapToIPv4() : ip).ToString()
            : "unknown";
        var stream = tcp.GetStream();

        if (!await HandshakeAsync(stream, ct)) { tcp.Dispose(); return; }

        using var ws = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, keepAliveInterval: TimeSpan.FromSeconds(30));
        var session = new Session(Guid.NewGuid(), ws, address);
        _sessions[session.Id] = session;

        var buffer = new byte[16 * 1024];
        try
        {
            while (ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
            {
                using var message = new MemoryStream();
                WebSocketReceiveResult result;
                do
                {
                    result = await ws.ReceiveAsync(buffer, ct);
                    if (result.MessageType == WebSocketMessageType.Close) break;
                    message.Write(buffer, 0, result.Count);
                    if (message.Length > 64 * 1024) throw new InvalidDataException("Message too large");
                } while (!result.EndOfMessage);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    await ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, null, CancellationToken.None);
                    break;
                }
                if (result.MessageType == WebSocketMessageType.Text)
                {
                    await HandleAsync(Encoding.UTF8.GetString(message.ToArray()), session);
                }
            }
        }
        catch (Exception) { /* connection dropped */ }
        finally
        {
            _sessions.TryRemove(session.Id, out _);
            tcp.Dispose();
            if (session.Paired) PublishClients();
        }
    }

    /// <summary>Reads the HTTP upgrade request and answers 101 Switching Protocols.</summary>
    private static async Task<bool> HandshakeAsync(NetworkStream stream, CancellationToken ct)
    {
        var header = new StringBuilder();
        var one = new byte[1];
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(TimeSpan.FromSeconds(5));
        try
        {
            while (!header.ToString().EndsWith("\r\n\r\n", StringComparison.Ordinal))
            {
                if (await stream.ReadAsync(one, timeout.Token) == 0 || header.Length > 8192) return false;
                header.Append((char)one[0]);
            }
        }
        catch { return false; }

        string? key = null;
        foreach (var line in header.ToString().Split("\r\n"))
        {
            var idx = line.IndexOf(':');
            if (idx > 0 && line[..idx].Trim().Equals("Sec-WebSocket-Key", StringComparison.OrdinalIgnoreCase))
                key = line[(idx + 1)..].Trim();
        }
        if (key is null)
        {
            var bad = Encoding.ASCII.GetBytes("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n");
            await stream.WriteAsync(bad, ct);
            return false;
        }

        var accept = Convert.ToBase64String(SHA1.HashData(Encoding.ASCII.GetBytes(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")));
        var response = "HTTP/1.1 101 Switching Protocols\r\n" +
                       "Upgrade: websocket\r\n" +
                       "Connection: Upgrade\r\n" +
                       $"Sec-WebSocket-Accept: {accept}\r\n\r\n";
        await stream.WriteAsync(Encoding.ASCII.GetBytes(response), ct);
        return true;
    }

    private async Task HandleAsync(string text, Session session)
    {
        JsonObject? obj;
        try { obj = JsonNode.Parse(text) as JsonObject; }
        catch (JsonException) { obj = null; }
        var type = obj?["type"]?.GetValueKind() == JsonValueKind.String ? (string?)obj["type"] : null;
        if (obj is null || type is null)
        {
            await session.SendAsync(new JsonObject { ["type"] = "error", ["code"] = "bad_message", ["message"] = "Expected a JSON object with a type" });
            return;
        }

        switch (type)
        {
            case "ping":
                await session.SendAsync(new JsonObject { ["type"] = "pong" });
                break;

            case "hello":
                await HandleHelloAsync(obj, session);
                break;

            case "command":
            {
                var id = Str(obj, "id");
                if (!session.Paired)
                {
                    await session.SendAsync(Error(id, "not_paired", "Send hello first"));
                    await session.CloseAsync();
                    return;
                }
                var action = Str(obj, "action");
                if (action is null)
                {
                    await session.SendAsync(Error(id, "bad_message", "Missing action"));
                    return;
                }
                var client = new ConnectedClient(session.Id, session.DeviceName, session.Address);
                var result = OnCommand?.Invoke(action, client) ?? CommandResult.Unsupported;
                await session.SendAsync(result switch
                {
                    CommandResult.Ok => new JsonObject { ["type"] = "ack", ["id"] = id, ["ok"] = true },
                    CommandResult.PermissionDenied => Error(id, "permission_denied", "Windows blocked the key press (is an elevated app in front?)"),
                    _ => Error(id, "unsupported_action", $"Unknown action {action}"),
                });
                break;
            }

            default:
                if (session.Paired)
                {
                    await session.SendAsync(Error(null, "unsupported_type", $"Unknown type {type}"));
                }
                else
                {
                    await session.SendAsync(Error(null, "not_paired", "Send hello first"));
                    await session.CloseAsync();
                }
                break;
        }
    }

    private async Task HandleHelloAsync(JsonObject obj, Session session)
    {
        var version = obj["v"]?.GetValueKind() == JsonValueKind.Number ? (int)obj["v"]! : 1;
        if (version > ProtocolVersion)
        {
            await session.SendAsync(Error(null, "version", $"Server speaks v{ProtocolVersion}"));
            await session.CloseAsync();
            return;
        }

        if (_failures.TryGetValue(session.Address, out var f) && f.LockedUntil > DateTime.UtcNow)
        {
            await session.SendAsync(Error(null, "bad_code", "Too many attempts, try again shortly"));
            await session.CloseAsync();
            return;
        }

        var deviceName = Str(obj, "device") is { Length: > 0 } d ? d : "Phone";
        var deviceId = Str(obj, "deviceId") ?? session.Address;
        var token = Authenticate?.Invoke(Str(obj, "code"), Str(obj, "token"), deviceName, deviceId);
        if (token is null)
        {
            var entry = _failures.GetValueOrDefault(session.Address, (0, DateTime.MinValue));
            entry.Count++;
            if (entry.Count >= 3) entry = (0, DateTime.UtcNow.AddSeconds(5));
            _failures[session.Address] = entry;
            await session.SendAsync(Error(null, "bad_code", "Wrong pairing code"));
            await session.CloseAsync();
            return;
        }

        _failures.TryRemove(session.Address, out _);
        session.Paired = true;
        session.DeviceName = deviceName;
        await session.SendAsync(new JsonObject
        {
            ["type"] = "welcome",
            ["v"] = ProtocolVersion,
            ["server"] = ServerName,
            ["serverId"] = ServerId,
            ["platform"] = "windows",
            ["token"] = token,
            ["capabilities"] = new JsonArray(Capabilities.Select(c => (JsonNode)JsonValue.Create(c)!).ToArray()),
        });
        PublishClients();
    }

    private void PublishClients()
    {
        var list = _sessions.Values.Where(s => s.Paired)
            .Select(s => new ConnectedClient(s.Id, s.DeviceName, s.Address))
            .OrderBy(c => c.DeviceName)
            .ToList();
        ClientsChanged?.Invoke(list);
    }

    private static string? Str(JsonObject obj, string key) =>
        obj[key]?.GetValueKind() == JsonValueKind.String ? (string?)obj[key] : null;

    private static JsonObject Error(string? id, string code, string message)
    {
        var o = new JsonObject { ["type"] = "error", ["code"] = code, ["message"] = message };
        if (id is not null) o["id"] = id;
        return o;
    }

    private sealed class Session(Guid id, WebSocket socket, string address)
    {
        private readonly SemaphoreSlim _sendLock = new(1, 1);
        public Guid Id { get; } = id;
        public string Address { get; } = address;
        public bool Paired { get; set; }
        public string DeviceName { get; set; } = "Phone";

        public async Task SendAsync(JsonObject message)
        {
            var bytes = Encoding.UTF8.GetBytes(message.ToJsonString());
            await _sendLock.WaitAsync();
            try { await socket.SendAsync(bytes, WebSocketMessageType.Text, true, CancellationToken.None); }
            catch { /* peer gone */ }
            finally { _sendLock.Release(); }
        }

        public async Task CloseAsync()
        {
            try { await socket.CloseOutputAsync(WebSocketCloseStatus.PolicyViolation, null, CancellationToken.None); }
            catch { }
        }

        public void Abort() => socket.Abort();
    }
}
