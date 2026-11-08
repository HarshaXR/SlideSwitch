using System.Collections.ObjectModel;
using System.Security.Cryptography;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml.Media.Imaging;
using QRCoder;
using Windows.Storage.Streams;

namespace SlideSwitch.Services;

public sealed record ClientRow(string Name, string Address);
public sealed record LogRow(string Glyph, string Text, string Time);

/// <summary>
/// App-wide state shared by the pages: server, mDNS, settings, pairing code, connected phones
/// and the activity log. Raises <see cref="Changed"/> on the UI thread whenever something changes.
/// </summary>
public sealed class AppCore : IDisposable
{
    public static AppCore Current { get; private set; } = null!;

    private readonly RemoteServer _server = new();
    private readonly MdnsAdvertiser _mdns = new();
    private readonly DispatcherQueue _ui;
    private readonly object _authLock = new();

    public Settings Settings { get; } = Settings.Load();
    public ObservableCollection<ClientRow> Clients { get; } = new();
    public ObservableCollection<LogRow> Log { get; } = new();
    public string Code { get; private set; } = NewCodeValue();
    public string ServerName => _server.ServerName;
    public int Port => _server.Port;
    public string? IpAddress { get; private set; }
    public string? ServerError { get; private set; }
    public int PairedCount => Settings.Tokens.Count;

    /// <summary>Raised on the UI thread after any state change.</summary>
    public event Action? Changed;

    public AppCore(DispatcherQueue ui)
    {
        _ui = ui;
        Current = this;
        _server.ServerName = Environment.MachineName;
        _server.ServerId = Settings.ServerId;
        _server.Authenticate = Authenticate;
        _server.OnCommand = Perform;
        _server.ClientsChanged += clients => _ui.TryEnqueue(() => UpdateClients(clients));
    }

    public string FormattedCode => $"{Code[..3]} {Code[3..]}";

    public string? PairingUrl => IpAddress is { } ip && Port > 0
        ? $"slideswitch://pair?host={ip}&port={Port}&code={Code}&name={Uri.EscapeDataString(ServerName)}"
        : null;

    public void Start()
    {
        try
        {
            _server.Start();
            _mdns.Advertise(_server.ServerName, _server.Port, Settings.ServerId);
            ServerError = null;
        }
        catch (Exception ex)
        {
            ServerError = ex.Message;
        }
        IpAddress = NetworkInfo.PrimaryIPv4();
        Notify();
    }

    public void NewCode()
    {
        lock (_authLock) Code = NewCodeValue();
        AddLog("", "New pairing code");
        Notify();
    }

    public void ForgetAll()
    {
        lock (_authLock)
        {
            Settings.Tokens.Clear();
            Settings.Save();
        }
        _server.DisconnectAll();
        AddLog("", "Forgot all paired devices");
        Notify();
    }

    public void SaveSettings() => Settings.Save();

    public static async Task<BitmapImage> RenderQrAsync(string text)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        var png = new PngByteQRCode(data).GetGraphic(12);

        using var stream = new InMemoryRandomAccessStream();
        using (var writer = new DataWriter(stream.GetOutputStreamAt(0)))
        {
            writer.WriteBytes(png);
            await writer.StoreAsync();
            await writer.FlushAsync();
        }
        var bitmap = new BitmapImage();
        await bitmap.SetSourceAsync(stream);
        return bitmap;
    }

    public void Dispose()
    {
        _mdns.Dispose();
        _server.Dispose();
    }

    // ---- Server callbacks (thread pool) ----

    private string? Authenticate(string? code, string? token, string name, string deviceId)
    {
        lock (_authLock)
        {
            if (token is not null && Settings.Tokens.ContainsKey(token))
            {
                Settings.Tokens[token] = name;
                Settings.Save();
                return token;
            }
            var digits = new string((code ?? "").Where(char.IsDigit).ToArray());
            if (digits != Code)
            {
                AddLog("", $"Rejected pairing from {name}");
                return null;
            }
            var issued = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
            Settings.Tokens[issued] = name;
            Settings.Save();
            AddLog("", $"Paired with {name}");
            _ui.TryEnqueue(Notify);
            return issued;
        }
    }

    private CommandResult Perform(string action, ConnectedClient client)
    {
        var keys = KeyInjector.KeysFor(action);
        if (keys is null) return CommandResult.Unsupported;
        if (!KeyInjector.Send(keys))
        {
            AddLog("", $"Blocked {action}: Windows refused the key press");
            return CommandResult.PermissionDenied;
        }
        var target = KeyInjector.ForegroundAppName() is { } app ? $" → {app}" : "";
        AddLog(GlyphFor(action), $"{LabelFor(action)}{target}");
        return CommandResult.Ok;
    }

    private void UpdateClients(IReadOnlyList<ConnectedClient> clients)
    {
        var before = Clients.Select(c => c.Name + c.Address).ToHashSet();
        var after = clients.Select(c => c.DeviceName + c.Address).ToHashSet();
        foreach (var c in clients.Where(c => !before.Contains(c.DeviceName + c.Address)))
            AddLog("", $"{c.DeviceName} connected");
        foreach (var c in Clients.Where(c => !after.Contains(c.Name + c.Address)).ToList())
            AddLog("", $"{c.Name} disconnected");

        Clients.Clear();
        foreach (var c in clients) Clients.Add(new ClientRow(c.DeviceName, c.Address));
        Notify();
    }

    private void AddLog(string glyph, string text)
    {
        _ui.TryEnqueue(() =>
        {
            Log.Insert(0, new LogRow(glyph, text, DateTime.Now.ToShortTimeString()));
            while (Log.Count > 12) Log.RemoveAt(Log.Count - 1);
            Changed?.Invoke();
        });
    }

    private void Notify() => Changed?.Invoke();

    private static string NewCodeValue() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    private static string LabelFor(string action) => action switch
    {
        "next" => "Next slide",
        "previous" => "Previous slide",
        "start" => "Start presentation",
        "end" => "End presentation",
        "black" => "Black screen",
        _ => action,
    };

    // Segoe Fluent Icons code points.
    private static string GlyphFor(string action) => action switch
    {
        "next" => "",
        "previous" => "",
        "start" => "",
        "end" => "",
        "black" => "",
        _ => "",
    };
}
