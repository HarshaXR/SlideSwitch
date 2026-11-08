using System.Text.Json;

namespace SlideSwitch.Services;

/// <summary>Small JSON settings file in %LOCALAPPDATA%\SlideSwitch (unpackaged apps have no ApplicationData).</summary>
public sealed class Settings
{
    public string ServerId { get; set; } = Guid.NewGuid().ToString();
    /// <summary>token → device name</summary>
    public Dictionary<string, string> Tokens { get; set; } = new();
    /// <summary>"System", "Light" or "Dark".</summary>
    public string Appearance { get; set; } = "System";
    /// <summary>Set once the first-launch welcome has been dismissed.</summary>
    public bool Onboarded { get; set; }

    private static readonly string Folder = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SlideSwitch");
    private static readonly string FilePath = Path.Combine(Folder, "settings.json");
    private readonly object _lock = new();

    public static Settings Load()
    {
        try
        {
            if (File.Exists(FilePath) && JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) is { } s)
                return s;
        }
        catch { /* corrupt file: start fresh */ }
        var fresh = new Settings();
        fresh.Save();
        return fresh;
    }

    public void Save()
    {
        lock (_lock)
        {
            Directory.CreateDirectory(Folder);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
    }
}
